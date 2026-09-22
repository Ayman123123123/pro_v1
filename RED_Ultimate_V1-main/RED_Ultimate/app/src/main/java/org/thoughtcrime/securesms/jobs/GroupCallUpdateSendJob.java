package com.red.sovereign.jobs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.NoSessionException;
import com.red.sovereign.database.GroupTable;
import com.red.sovereign.database.REDDatabase;
import com.red.sovereign.database.model.RecipientRecord;
import com.red.sovereign.dependencies.AppDependencies;
import com.red.sovereign.groups.GroupId;
import com.red.sovereign.jobmanager.Job;
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

/**
 * Send a group call update message to every one in a V2 group. Used to indicate you
 * have joined or left a call.
 */
public class GroupCallUpdateSendJob extends BaseJob {

  public static final String KEY = "GroupCallUpdateSendJob";

  private static final String TAG = Log.tag(GroupCallUpdateSendJob.class);

  private static final String KEY_RECIPIENT_ID            = "recipient_id";
  private static final String KEY_ERA_ID                  = "era_id";
  private static final String KEY_RECIPIENTS              = "recipients";
  private static final String KEY_INITIAL_RECIPIENT_COUNT = "initial_recipient_count";
  static final String KEY_SYNC_TIMESTAMP          = "sync_timestamp";

  private final RecipientId       recipientId;
  private final String            eraId;
  private final List<RecipientId> recipients;
  private final int               initialRecipientCount;

  private long syncTimestamp;

  @WorkerThread
  public static @NonNull GroupCallUpdateSendJob create(@NonNull RecipientId recipientId, @Nullable String eraId) {
    RecipientRecord conversationRecipient = REDDatabase.recipients().getRecord(recipientId);

    if (conversationRecipient.getGroupId() == null || !conversationRecipient.getGroupId().isV2()) {
      throw new AssertionError("We have a recipient, but it's not a V2 Group");
    }

    List<RecipientId> recipientIds = RecipientUtil.getEligibleForSending(Recipient.resolvedList(REDDatabase.groups().getGroupMemberIds(conversationRecipient.getGroupId(), GroupTable.MemberSet.FULL_MEMBERS_EXCLUDING_SELF))).stream()
                                                  .map(Recipient::getId).collect(Collectors.toList());

    return new GroupCallUpdateSendJob(recipientId,
                                      eraId,
                                      recipientIds,
                                      recipientIds.size(),
                                      0L,
                                      new Parameters.Builder()
                                                    .setQueue(conversationRecipient.getId().toQueueKey())
                                                    .addConstraint(SealedSenderConstraint.KEY)
                                                    .setLifespan(TimeUnit.MINUTES.toMillis(5))
                                                    .setMaxAttempts(3)
                                                    .build());
  }

  private GroupCallUpdateSendJob(@NonNull RecipientId recipientId,
                                 @Nullable String eraId,
                                 @NonNull List<RecipientId> recipients,
                                 int initialRecipientCount,
                                 long syncTimestamp,
                                 @NonNull Parameters parameters)
  {
    super(parameters);

    this.recipientId           = recipientId;
    this.eraId                 = eraId;
    this.recipients            = recipients;
    this.initialRecipientCount = initialRecipientCount;
    this.syncTimestamp         = syncTimestamp;
  }

  @Override
  public @Nullable byte[] serialize() {
    return new JsonJobData.Builder().putString(KEY_RECIPIENT_ID, recipientId.serialize())
                                    .putString(KEY_ERA_ID, eraId)
                                    .putString(KEY_RECIPIENTS, RecipientId.toSerializedList(recipients))
                                    .putInt(KEY_INITIAL_RECIPIENT_COUNT, initialRecipientCount)
                                    .putLong(KEY_SYNC_TIMESTAMP, syncTimestamp)
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

    if (!REDDatabase.recipients().containsId(recipientId)) {
      Log.w(TAG, "Missing recipient record for id.");
      return;
    }

    RecipientRecord conversationRecipient = REDDatabase.recipients().getRecord(recipientId);

    if (conversationRecipient.getGroupId() == null || !conversationRecipient.getGroupId().isV2()) {
      throw new AssertionError("We have a recipient, but it's not a V2 Group");
    }

    if (!REDDatabase.groups().isActive(conversationRecipient.getGroupId())) {
      Log.w(TAG, "Not sending group call update to terminated or inactive group.");
      return;
    }

    List<Recipient> destinations = recipients.stream().map(Recipient::resolved).collect(Collectors.toList());
    List<Recipient> completions  = deliver(conversationRecipient.getGroupId(), destinations);

    for (Recipient completion : completions) {
      recipients.remove(completion.getId());
    }

    Log.i(TAG, "Completed now: " + completions.size() + ", Remaining: " + recipients.size());

    if (!recipients.isEmpty()) {
      Log.w(TAG, "Still need to send to " + recipients.size() + " recipients. Retrying.");
      throw new RetryLaterException();
    }

    setOutputData(new JsonJobData.Builder()
                      .putLong(KEY_SYNC_TIMESTAMP, syncTimestamp)
                      .serialize());
  }

  @Override
  protected boolean onShouldRetry(@NonNull Exception e) {
    if (e instanceof ServerRejectedException) return false;
    return e instanceof IOException ||
           e instanceof RetryLaterException;
  }

  @Override
  public long getNextRunAttemptBackoff(int pastAttemptCount, @NonNull Exception exception) {
    return SendJobUtil.getBackoffMillisFromException(this, TAG, pastAttemptCount, exception, () -> super.getNextRunAttemptBackoff(pastAttemptCount, exception));
  }

  @Override
  public void onFailure() {
    if (recipients.size() < initialRecipientCount) {
      Log.w(TAG, "Only sent a group update to " + recipients.size() + "/" + initialRecipientCount + " recipients. Still, it sent to someone, so it stays.");
      return;
    }

    Log.w(TAG, "Failed to send the group update to all recipients!");
  }

  private @NonNull List<Recipient> deliver(@NonNull GroupId groupId, @NonNull List<Recipient> destinations)
      throws IOException, UntrustedIdentityException, NoSessionException, UndeliverableMessageException
  {
    REDServiceDataMessage.Builder dataMessageBuilder = REDServiceDataMessage.newBuilder()
                                                                                  .withTimestamp(System.currentTimeMillis())
                                                                                  .withGroupCallUpdate(new REDServiceDataMessage.GroupCallUpdate(eraId));

    GroupUtil.setDataMessageGroupContext(context, dataMessageBuilder, groupId.requirePush());

    REDServiceDataMessage dataMessage         = dataMessageBuilder.build();
    List<Recipient>          nonSelfDestinations = destinations.stream().filter(r -> !r.isSelf()).collect(Collectors.toList());
    boolean                  includesSelf        = nonSelfDestinations.size() != destinations.size();
    List<SendMessageResult>  results             = GroupSendUtil.sendUnresendableDataMessage(context,
                                                                                             groupId.requireV2(),
                                                                                             nonSelfDestinations,
                                                                                             false,
                                                                                             ContentHint.DEFAULT,
                                                                                             dataMessage,
                                                                                             false,
                                                                                             null);

    if (includesSelf) {
      results.add(AppDependencies.getREDServiceMessageSender().sendSyncMessage(dataMessage));
      syncTimestamp = dataMessage.getTimestamp();
    }

    return GroupSendJobHelper.getCompletedSends(destinations, results).completed;
  }

  public static class Factory implements Job.Factory<GroupCallUpdateSendJob> {

    @Override
    public @NonNull
    GroupCallUpdateSendJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      JsonJobData data = JsonJobData.deserialize(serializedData);

      RecipientId       recipientId           = RecipientId.from(data.getString(KEY_RECIPIENT_ID));
      String            eraId                 = data.getString(KEY_ERA_ID);
      List<RecipientId> recipients            = RecipientId.fromSerializedList(data.getString(KEY_RECIPIENTS));
      int               initialRecipientCount = data.getInt(KEY_INITIAL_RECIPIENT_COUNT);
      long              syncTimestamp         = data.getLongOrDefault(KEY_SYNC_TIMESTAMP, 0L);

      return new GroupCallUpdateSendJob(recipientId, eraId, recipients, initialRecipientCount, syncTimestamp, parameters);
    }
  }
}
