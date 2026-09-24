package com.red.sovereign.jobs;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.signal.core.util.Util;
import org.signal.core.util.logging.Log;
import org.signal.libsignal.protocol.NoSessionException;
import com.red.sovereign.attachments.Attachment;
import com.red.sovereign.attachments.DatabaseAttachment;
import com.red.sovereign.database.GroupReceiptTable;
import com.red.sovereign.database.MessageTable;
import com.red.sovereign.database.NoSuchMessageException;
import com.red.sovereign.database.SentStorySyncManifest;
import com.red.sovereign.database.REDDatabase;
import com.red.sovereign.database.documents.IdentityKeyMismatch;
import com.red.sovereign.database.documents.NetworkFailure;
import com.red.sovereign.database.model.MessageId;
import com.red.sovereign.jobmanager.Job;
import com.red.sovereign.jobmanager.JobLogger;
import com.red.sovereign.jobmanager.JobManager;
import com.red.sovereign.jobmanager.JsonJobData;
import com.red.sovereign.jobmanager.impl.NetworkConstraint;
import com.red.sovereign.jobmanager.impl.SealedSenderConstraint;
import com.red.sovereign.messages.GroupSendUtil;
import com.red.sovereign.messages.StorySendUtil;
import com.red.sovereign.mms.MmsException;
import com.red.sovereign.mms.OutgoingMessage;
import com.red.sovereign.recipients.Recipient;
import com.red.sovereign.recipients.RecipientId;
import com.red.sovereign.stories.Stories;
import com.red.sovereign.transport.RetryLaterException;
import com.red.sovereign.transport.UndeliverableMessageException;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SendMessageResult;
import org.whispersystems.signalservice.api.messages.REDServiceAttachment;
import org.whispersystems.signalservice.api.messages.REDServiceStoryMessage;
import org.whispersystems.signalservice.api.messages.REDServiceStoryMessageRecipient;
import org.whispersystems.signalservice.api.push.exceptions.ServerRejectedException;
import org.whispersystems.signalservice.internal.push.BodyRange;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * A job that lets us send a message to a distribution list. Currently the only supported message type is a story.
 */
public final class PushDistributionListSendJob extends PushSendJob {

  public static final String KEY = "PushDistributionListSendJob";

  private static final String TAG = Log.tag(PushDistributionListSendJob.class);

  private static final String KEY_MESSAGE_ID             = "message_id";
  private static final String KEY_FILTERED_RECIPIENT_IDS = "filtered_recipient_ids";

  private final long             messageId;
  private final Set<RecipientId> filterRecipientIds;

  public PushDistributionListSendJob(long messageId, @NonNull RecipientId destination, boolean hasMedia, @NonNull Set<RecipientId> filterRecipientIds) {
    this(new Parameters.Builder()
             .setQueue(destination.toQueueKey(hasMedia))
             .addConstraint(NetworkConstraint.KEY)
             .addConstraint(SealedSenderConstraint.KEY)
             .setLifespan(TimeUnit.DAYS.toMillis(1))
             .setMaxAttempts(Parameters.UNLIMITED)
             .build(),
         messageId,
         filterRecipientIds
    );
  }

  private PushDistributionListSendJob(@NonNull Parameters parameters, long messageId, @NonNull Set<RecipientId> filterRecipientIds) {
    super(parameters);
    this.messageId          = messageId;
    this.filterRecipientIds = filterRecipientIds;
  }

  @WorkerThread
  public static void enqueue(@NonNull Context context,
                             @NonNull JobManager jobManager,
                             long messageId,
                             @NonNull RecipientId destination,
                             @NonNull Set<RecipientId> filterRecipientIds)
  {
    try {
      Recipient listRecipient = Recipient.resolved(destination);

      if (!listRecipient.isDistributionList()) {
        throw new AssertionError("Not a distribution list! MessageId: " + messageId);
      }

      OutgoingMessage message = REDDatabase.messages().getOutgoingMessage(messageId);

      if (!message.getStoryType().isStory()) {
        throw new AssertionError("Only story messages are currently supported! MessageId: " + messageId);
      }

      if (!message.getStoryType().isTextStory()) {
        if (message.getAttachments().isEmpty()) {
          Log.w(TAG, "No attachments found for message " + messageId + ". Ignoring.");
          return;
        }
        
        DatabaseAttachment storyAttachment = (DatabaseAttachment) message.getAttachments().get(0);
        REDDatabase.attachments().updateAttachmentCaption(storyAttachment.attachmentId, message.getBody());
      }

      Set<String> attachmentUploadIds = enqueueCompressingAndUploadAttachmentsChains(jobManager, message);

      jobManager.add(new PushDistributionListSendJob(messageId, destination, !attachmentUploadIds.isEmpty(), filterRecipientIds), attachmentUploadIds, attachmentUploadIds.isEmpty() ? null : destination.toQueueKey());
    } catch (NoSuchMessageException | MmsException e) {
      Log.w(TAG, "Failed to enqueue message.", e);
      REDDatabase.messages().markAsSentFailed(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);
    }
  }

  @Override
  public @Nullable byte[] serialize() {
    return new JsonJobData.Builder().putLong(KEY_MESSAGE_ID, messageId)
                                    .putString(KEY_FILTERED_RECIPIENT_IDS, RecipientId.toSerializedList(filterRecipientIds))
                                    .serialize();
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onAdded() {
    REDDatabase.messages().markAsSending(messageId);
  }

  @Override
  public void onPushSend()
      throws IOException, MmsException, NoSuchMessageException, RetryLaterException
  {
    MessageTable             database                   = REDDatabase.messages();
    OutgoingMessage          message                    = database.getOutgoingMessage(messageId);
    Set<NetworkFailure>      existingNetworkFailures    = new HashSet<>(message.getNetworkFailures());
    Set<IdentityKeyMismatch> existingIdentityMismatches = new HashSet<>(message.getIdentityKeyMismatches());

    if (!message.getStoryType().isStory()) {
      throw new MmsException("Only story sends are currently supported!");
    }

    if (database.isSent(messageId)) {
      log(TAG, String.valueOf(message.getSentTimeMillis()), "Message " + messageId + " was already sent. Ignoring.");
      return;
    }

    Recipient listRecipient = message.getThreadRecipient().resolve();

    if (!listRecipient.isDistributionList()) {
      throw new MmsException("Message recipient isn't a distribution list!");
    }

    try {
      log(TAG, String.valueOf(message.getSentTimeMillis()), "Sending message: " + messageId + ", Recipient: " + message.getThreadRecipient().getId() + ", Attachments: " + buildAttachmentString(message.getAttachments()));

      List<Recipient> targets;
      List<RecipientId> skipped = Collections.emptyList();

      if (Util.hasItems(filterRecipientIds)) {
        targets = new ArrayList<>(filterRecipientIds.size() + existingNetworkFailures.size());
        targets.addAll(filterRecipientIds.stream().map(Recipient::resolved).collect(Collectors.toList()));
        targets.addAll(existingNetworkFailures.stream().map(NetworkFailure::getRecipientId).distinct().map(Recipient::resolved).collect(Collectors.toList()));
      } else if (!existingNetworkFailures.isEmpty()) {
        targets = existingNetworkFailures.stream().map(NetworkFailure::getRecipientId).distinct().map(Recipient::resolved).collect(Collectors.toList());
      } else {
        Stories.SendData data = Stories.getRecipientsToSendTo(messageId, message.getSentTimeMillis(), message.getStoryType().isStoryWithReplies());
        targets = data.getTargets();
        skipped = data.getSkipped();
      }

      List<SendMessageResult> results = deliver(message, targets);
      Log.i(TAG, JobLogger.format(this, "Finished send."));

      PushGroupSendJob.processGroupMessageResults(context, messageId, -1, null, message, results, targets, skipped, existingNetworkFailures, existingIdentityMismatches);

    } catch (UntrustedIdentityException | UndeliverableMessageException | NoSessionException e) {
      warn(TAG, String.valueOf(message.getSentTimeMillis()), e);
      database.markAsSentFailed(messageId);
      notifyMediaMessageDeliveryFailed(context, messageId);
    }
  }

  @Override
  public void onFailure() {
    REDDatabase.messages().markAsSentFailed(messageId);
  }

  private List<SendMessageResult> deliver(@NonNull OutgoingMessage message, @NonNull List<Recipient> destinations)
      throws IOException, UntrustedIdentityException, UndeliverableMessageException, NoSessionException
  {
    try {
      List<Attachment>                    attachments        = message.getAttachments().stream().filter(attachment -> !attachment.isSticker()).collect(Collectors.toList());
      List<REDServiceAttachment> attachmentPointers = getAttachmentPointersFor(attachments);
      List<BodyRange>               bodyRanges         = getBodyRanges(message);
      boolean                             isRecipientUpdate  = REDDatabase.groupReceipts().getGroupReceiptInfo(messageId).stream()
                                                                             .anyMatch(info -> info.getStatus() > GroupReceiptTable.STATUS_UNDELIVERED);

      final REDServiceStoryMessage storyMessage;
      if (message.getStoryType().isTextStory()) {
        storyMessage = REDServiceStoryMessage.forTextAttachment(Recipient.self().getProfileKey(), null, StorySendUtil.deserializeBodyToStoryTextAttachment(message, this::getPreviewsFor), message.getStoryType().isStoryWithReplies(), bodyRanges);
      } else if (!attachmentPointers.isEmpty()) {
        storyMessage = REDServiceStoryMessage.forFileAttachment(Recipient.self().getProfileKey(), null, attachmentPointers.get(0), message.getStoryType().isStoryWithReplies(), bodyRanges);
      } else {
        throw new UndeliverableMessageException("No attachment on non-text story.");
      }

      SentStorySyncManifest                   manifest           = REDDatabase.storySends().getFullSentStorySyncManifest(messageId, message.getSentTimeMillis());
      Set<REDServiceStoryMessageRecipient> manifestCollection = manifest != null ? manifest.toRecipientsSet() : Collections.emptySet();

      Log.d(TAG, "[" + messageId + "] Sending a story message with a manifest of size " + manifestCollection.size());

      return GroupSendUtil.sendStoryMessage(context, message.getThreadRecipient().requireDistributionListId(), destinations, isRecipientUpdate, new MessageId(messageId), message.getSentTimeMillis(), storyMessage, manifestCollection);
    } catch (ServerRejectedException e) {
      throw new UndeliverableMessageException(e);
    }
  }

  public static class Factory implements Job.Factory<PushDistributionListSendJob> {
    @Override
    public @NonNull PushDistributionListSendJob create(@NonNull Parameters parameters, @Nullable byte[] serializedData) {
      JsonJobData data = JsonJobData.deserialize(serializedData);

      Set<RecipientId> recipientIds = new HashSet<>(RecipientId.fromSerializedList(data.getStringOrDefault(KEY_FILTERED_RECIPIENT_IDS, "")));
      return new PushDistributionListSendJob(parameters, data.getLong(KEY_MESSAGE_ID), recipientIds);
    }
  }
}
