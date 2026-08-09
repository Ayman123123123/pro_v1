package com.red.sovereign.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.signal.core.util.SetUtil;
import org.signal.core.util.Util;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.NoSessionException;
import com.red.sovereign.database.GroupTable;
import com.red.sovereign.database.MessageTable;
import com.red.sovereign.database.NoSuchMessageException;
import com.red.sovereign.database.RecipientTable.RegisteredState;
import com.red.sovereign.database.REDDatabase;
import com.red.sovereign.database.model.DistributionListId;
import com.red.sovereign.database.model.MessageId;
import com.red.sovereign.database.model.MessageRecord;
import com.red.sovereign.database.model.MmsMessageRecord;
import com.red.sovereign.database.model.RecipientRecord;
import com.red.sovereign.dependencies.AppDependencies;
import com.red.sovereign.jobmanager.Job;
import com.red.sovereign.jobmanager.JobManager;
import com.red.sovereign.jobmanager.JsonJobData;
import com.red.sovereign.jobmanager.impl.SealedSenderConstraint;
import com.red.sovereign.messages.GroupSendUtil;
import com.red.sovereign.net.NotPushRegisteredException;
import com.red.sovereign.recipients.Recipient;
import com.red.sovereign.recipients.RecipientId;
import com.red.sovereign.recipients.RecipientUtil;
import com.red.sovereign.transport.RetryLaterException;
import com.red.sovereign.transport.UndeliverableMessageException;
import com.red.sovereign.util.GroupUtil;
import org.whispersystems.signalservice.api.crypto.ContentHint;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.REDServiceDataMessage;
import org.whispersystems.signalservice.api.push.exceptions.ServerRejectedException;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RemoteDeleteSendJob extends BaseJob {

  public static final String KEY = "RemoteDeleteSendJob";

  private static final String TAG = Log.tag(RemoteDeleteSendJob.class);

  private static final String KEY_MESSAGE_ID              = "message_id";
  private static final String KEY_RECIPIENTS              = "recipients";
  private static final String KEY_INITIAL_RECIPIENT_COUNT = "initial_recipient_count";

  private final long              messageId;
  private final List<RecipientId> recipients;
  private final int               initialRecipientCount;


  @WorkerThread
  public static @NonNull JobManager.Chain create(long messageId)
      throws NoSuchMessageException
  {
    MessageRecord message = REDDatabase.messages().getMessageRecord(messageId);

    RecipientId conversationRecipientId = REDDatabase.threads().getRecipientIdForThreadId(message.getThreadId());

    if (conversationRecipientId == null) {
      throw new AssertionError("We have a message, but couldn't find the thread!");
    }

    RecipientRecord conversationRecipient = REDDatabase.recipients().getRecord(conversationRecipientId);

    List<RecipientId> recipients;
    if (conversationRecipient.getDistributionListId() != null) {
      recipients = REDDatabase.storySends().getRemoteDeleteRecipients(message.getId(), message.getTimestamp());
      if (recipients.isEmpty()) {
        return AppDependencies.getJobManager().startChain(MultiDeviceStorySendSyncJob.create(message.getDateSent(), messageId));
      }
    } else {
      recipients = conversationRecipient.getGroupId() != null
                   ? REDDatabase.groups().getGroupMemberIds(conversationRecipient.getGroupId(), GroupTable.MemberSet.FULL_MEMBERS_INCLUDING_SELF).stream().collect(Collectors.toList())
                   : Stream.of(conversationRecipient.getId()).collect(Collectors.toList());
    }

    recipients.remove(Recipient.self().getId());

    RemoteDeleteSendJob sendJob = new RemoteDeleteSendJob(messageId,
                                                          recipients,
                                                          recipients.size(),
                                                          new Parameters.Builder()
                                                                        .setQueue(conversationRecipient.getId().toQueueKey())
                                                                        .addConstraint(SealedSenderConstraint.KEY)
                                                                        .setLifespan(TimeUnit.DAYS.toMillis(1))
                                                                        .setMaxAttempts(Parameters.UNLIMITED)
                                                                        .build());

    if (conversationRecipient.getDistributionListId() != null) {
      return AppDependencies.getJobManager()
                            .startChain(sendJob)
                            .then(MultiDeviceStorySendSyncJob.create(message.getDateSent(), messageId));
    } else {
      return AppDependencies.getJobManager().startChain(sendJob);
    }
  }

  private RemoteDeleteSendJob(long messageId,
                              @NonNull List<RecipientId> recipients,
                              int initialRecipientCount,
                              @NonNull Parameters parameters)
  {
    super(parameters);

    this.messageId             = messageId;
    this.recipients            = recipients;
    this.initialRecipientCount = initialRecipientCount;
  }

  @Override
  public @Nullable byte[] serialize() {
    return new JsonJobData.Builder().putLong(KEY_MESSAGE_ID, messageId)
                                    .putString(KEY_RECIPIENTS, RecipientId.toSerializedList(recipients))
                                    .putInt(KEY_INITIAL_RECIPIENT_COUNT, initialRecipientCount)
                                    .serialize();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  protected void onRun() throws Exception {
    if (!Recipient.self().isRegistered()) {
      throw new NotPushRegisteredException();
    }

    MessageTable  db      = REDDatabase.messages();
    MessageRecord message = REDDatabase.messages().getMessageRecord(messageId);

    long      targetSentTimestamp   = message.getDateSent();
    RecipientId conversationRecipientId = REDDatabase.threads().getRecipientIdForThreadId(message.getThreadId());

    if (conversationRecipientId == null) {
      throw new AssertionError("We have a message, but couldn't find the thread!");
    }

    RecipientRecord conversationRecipient = REDDatabase.recipients().getRecord(conversationRecipientId);

    if (!message.isOutgoing()) {
      throw new IllegalStateException("Cannot delete a message that isn't yours!");
    }

    boolean isRegistered = conversationRecipient.getGroupId() != null ? !conversationRecipient.getGroupId().isMms()
                                                                       : (conversationRecipient.getDistributionListId() != null || conversationRecipient.getRegistered() == RegisteredState.REGISTERED);

    if (!isRegistered) {
      Log.w(TAG, "Unable to remote delete non-push messages");
      return;
    }

    if (conversationRecipient.getGroupId() != null && conversationRecipient.getGroupId().isV1()) {
      Log.w(TAG, "Unable to remote delete messages in GV1 groups");
      return;
    }

    if (conversationRecipient.getGroupId() != null && conversationRecipient.getGroupId().isV2() && !REDDatabase.groups().isActive(conversationRecipient.getGroupId())) {
      Log.w(TAG, "Unable to remote delete messages in terminated or inactive groups");
      return;
    }

    List<Recipient>   possible = recipients.stream().map(Recipient::resolved).collect(Collectors.toList());
    List<Recipient>   eligible = RecipientUtil.getEligibleForSending(recipients.stream().map(Recipient::resolved).filter(Recipient::getHasServiceId).collect(Collectors.toList()));
    List<RecipientId> skipped  = SetUtil.difference(possible, eligible).stream().map(Recipient::getId).collect(Collectors.toList());

    boolean            isForStory         = message.isMms() && (((MmsMessageRecord) message).getStoryType().isStory() || ((MmsMessageRecord) message).getParentStoryId() != null);
    DistributionListId distributionListId = isForStory ? message.getToRecipient().getDistributionListId().orElse(null) : null;

    GroupSendJobHelper.SendResult sendResult = deliver(conversationRecipient, eligible, targetSentTimestamp, isForStory, distributionListId);

    for (Recipient completion : sendResult.completed) {
      recipients.remove(completion.getId());
    }

    for (RecipientId unregistered : sendResult.unregistered) {
      REDDatabase.recipients().markUnregistered(unregistered);
    }

    for (RecipientId skip : skipped) {
      recipients.remove(skip);
    }

    List<RecipientId> totalSkips = Util.join(skipped, sendResult.skipped);

    Log.i(TAG, "Completed now: " + sendResult.completed.size() + ", Skipped: " + totalSkips.size() + ", Remaining: " + recipients.size());

    if (totalSkips.size() > 0 && message.getToRecipient().isGroup()) {
      REDDatabase.groupReceipts().setSkipped(totalSkips, messageId);
    }

    if (recipients.isEmpty()) {
      db.markAsSent(messageId);
    } else {
      Log.w(TAG, "Still need to send to " + recipients.size() + " recipients. Retrying.");
      throw new RetryLaterException();
    }
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    if (e instanceof ServerRejectedException) return false;
    if (e instanceof NotPushRegisteredException) return false;
    return e instanceof IOException ||
           e instanceof RetryLaterException;
  }

  @Override
  public long getNextRunAttemptBackoff(int pastAttemptCount, @NonNull Exception exception) {
    return SendJobUtil.getBackoffMillisFromException(this, TAG, pastAttemptCount, exception, () -> super.getNextRunAttemptBackoff(pastAttemptCount, exception));
  }

  @Override
  public void onFailure() {
    Log.w(TAG, "Failed to send remote delete to all recipients! (" + (initialRecipientCount - recipients.size() + "/" + initialRecipientCount + ")") );
  }

  private @NonNull GroupSendJobHelper.SendResult deliver(@NonNull RecipientRecord conversationRecipient,
                                                         @NonNull List<Recipient> destinations,
                                                         long targetSentTimestamp,
                                                         boolean isForStory,
                                                         @Nullable DistributionListId distributionListId)
      throws IOException, UntrustedIdentityException, NoSessionException, UndeliverableMessageException
  {
    REDServiceDataMessage.Builder dataMessageBuilder = REDServiceDataMessage.newBuilder()
                                                                                  .withTimestamp(System.currentTimeMillis())
                                                                                  .withRemoteDelete(new REDServiceDataMessage.RemoteDelete(targetSentTimestamp));

    if (conversationRecipient.getGroupId() != null) {
      GroupUtil.setDataMessageGroupContext(context, dataMessageBuilder, conversationRecipient.getGroupId().requirePush());
    }

    REDServiceDataMessage dataMessage = dataMessageBuilder.build();
    List<SendMessageResult>  results     = GroupSendUtil.sendResendableDataMessage(context,
                                                                                   conversationRecipient.getGroupId() != null ? conversationRecipient.getGroupId().requireV2() : null,
                                                                                   distributionListId,
                                                                                   destinations,
                                                                                   false,
                                                                                   ContentHint.RESENDABLE,
                                                                                   new MessageId(messageId),
                                                                                   dataMessage,
                                                                                   true,
                                                                                   isForStory,
                                                                                   null,
                                                                                   null);

    if (conversationRecipient.getId().equals(Recipient.self().getId())) {
      AppDependencies.getREDServiceMessageSender().sendSyncMessage(dataMessage);
    }

    return GroupSendJobHelper.getCompletedSends(destinations, results);
  }

  public static class Factory implements Job.Factory<RemoteDeleteSendJob> {

    @Override
    public @NonNull RemoteDeleteSendJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      JsonJobData data = JsonJobData.deserialize(serializedData);

      long              messageId             = data.getLong(KEY_MESSAGE_ID);
      List<RecipientId> recipients            = RecipientId.fromSerializedList(data.getString(KEY_RECIPIENTS));
      int               initialRecipientCount = data.getInt(KEY_INITIAL_RECIPIENT_COUNT);

      return new RemoteDeleteSendJob(messageId,  recipients, initialRecipientCount, parameters);
    }
  }
}