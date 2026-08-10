package com.red.sovereign.jobs

import android.content.Context
import androidx.annotation.WorkerThread
import okio.utf8Size
import org.signal.core.util.UuidUtil.parseOrThrow
import org.signal.core.util.logging.Log
import org.signal.libsignal.protocol.NoSessionException
import com.red.sovereign.crypto.SealedSenderAccessUtil
import com.red.sovereign.database.NoSuchMessageException
import com.red.sovereign.database.RecipientTable.SealedSenderAccessMode
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.MessageId
import com.red.sovereign.database.model.MessageRecord
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobmanager.Job
import com.red.sovereign.jobmanager.JobManager
import com.red.sovereign.jobmanager.JsonJobData
import com.red.sovereign.jobmanager.impl.NetworkConstraint
import com.red.sovereign.jobmanager.impl.SealedSenderConstraint
import com.red.sovereign.jobs.ConversationShortcutRankingUpdateJob.Companion.enqueueForOutgoingIfNecessary
import com.red.sovereign.jobs.RetrieveProfileJob.Companion.enqueue
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.mms.MmsException
import com.red.sovereign.mms.OutgoingMessage
import com.red.sovereign.ratelimit.ProofRequiredExceptionHandler
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientUtil
import com.red.sovereign.transport.RetryLaterException
import com.red.sovereign.transport.UndeliverableMessageException
import com.red.sovereign.util.MessageUtil
import com.red.sovereign.util.RemoteConfig
import com.red.sovereign.util.REDLocalMetrics
import org.whispersystems.signalservice.api.REDServiceMessageSender.IndividualSendEvents
import org.whispersystems.signalservice.api.crypto.ContentHint
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException
import org.whispersystems.signalservice.api.messages.REDServiceAttachment
import org.whispersystems.signalservice.api.messages.REDServiceDataMessage
import org.whispersystems.signalservice.api.messages.REDServiceDataMessage.PaymentActivation
import org.whispersystems.signalservice.api.messages.REDServiceEditMessage
import org.whispersystems.signalservice.api.messages.REDServicePreview
import org.whispersystems.signalservice.api.messages.shared.SharedContact
import org.whispersystems.signalservice.api.push.exceptions.ProofRequiredException
import org.whispersystems.signalservice.api.push.exceptions.ServerRejectedException
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException
import org.whispersystems.signalservice.internal.push.BodyRange
import org.whispersystems.signalservice.internal.push.DataMessage
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Optional
import java.util.concurrent.TimeUnit

class IndividualSendJob private constructor(parameters: Parameters, private val messageId: Long) : PushSendJob(parameters) {

  companion object {
    const val KEY: String = "PushMediaSendJob"

    private val TAG = Log.tag(IndividualSendJob::class.java)

    private const val KEY_MESSAGE_ID = "message_id"

    @JvmStatic
    fun create(messageId: Long, recipient: Recipient, hasMedia: Boolean, isScheduledSend: Boolean): Job {
      if (!recipient.hasServiceId) {
        throw AssertionError("No ServiceId!")
      }

      if (recipient.isGroup) {
        throw AssertionError("This job does not send group messages!")
      }

      return if (RemoteConfig.useIndividualSendJobV2) {
        IndividualSendJobV2.create(messageId, recipient, hasMedia, isScheduledSend)
      } else {
        IndividualSendJob(messageId, recipient, hasMedia, isScheduledSend)
      }
    }

    @JvmStatic
    @WorkerThread
    fun enqueue(context: Context, jobManager: JobManager, messageId: Long, recipient: Recipient, isScheduledSend: Boolean) {
      if (RemoteConfig.useIndividualSendJobV2) {
        IndividualSendJobV2.enqueue(context, messageId, recipient, isScheduledSend)
        return
      }

      try {
        val message = REDDatabase.messages.getOutgoingMessage(messageId)
        if (message.scheduledDate != -1L) {
          AppDependencies.scheduledMessageManager.scheduleIfNecessary()
          return
        }

        val attachmentUploadIds: Set<String> = enqueueCompressingAndUploadAttachmentsChains(jobManager, message)
        val hasMedia = attachmentUploadIds.isNotEmpty()
        val addHardDependencies = hasMedia && !isScheduledSend

        jobManager.add(
          create(messageId, recipient, hasMedia, isScheduledSend),
          attachmentUploadIds,
          if (addHardDependencies) recipient.id.toQueueKey() else null
        )
      } catch (e: NoSuchMessageException) {
        Log.w(TAG, "Failed to enqueue message.", e)
        REDDatabase.messages.markAsSentFailed(messageId)
        notifyMediaMessageDeliveryFailed(context, messageId)
      } catch (e: MmsException) {
        Log.w(TAG, "Failed to enqueue message.", e)
        REDDatabase.messages.markAsSentFailed(messageId)
        notifyMediaMessageDeliveryFailed(context, messageId)
      }
    }

    @JvmStatic
    fun getMessageId(serializedData: ByteArray?): Long {
      val data = JsonJobData.deserialize(serializedData)
      return data.getLong(KEY_MESSAGE_ID)
    }
  }

  constructor(messageId: Long, recipient: Recipient, hasMedia: Boolean, isScheduledSend: Boolean) : this(
    parameters = Parameters.Builder()
      .setQueue(if (isScheduledSend) recipient.id.toScheduledSendQueueKey() else recipient.id.toQueueKey(hasMedia))
      .addConstraint(NetworkConstraint.KEY)
      .addConstraint(SealedSenderConstraint.KEY)
      .setLifespan(TimeUnit.DAYS.toMillis(1))
      .setMaxAttempts(Parameters.UNLIMITED)
      .build(),
    messageId = messageId
  )

  override fun serialize(): ByteArray? {
    return JsonJobData.Builder().putLong(KEY_MESSAGE_ID, messageId).serialize()
  }

  override fun getFactoryKey(): String {
    return KEY
  }

  override fun onAdded() {
    REDDatabase.messages.markAsSending(messageId)
  }

  @Throws(IOException::class, MmsException::class, NoSuchMessageException::class, UndeliverableMessageException::class, RetryLaterException::class)
  public override fun onPushSend() {
    REDLocalMetrics.IndividualMessageSend.onJobStarted(messageId)

    val expirationManager = AppDependencies.expiringMessageManager
    val message = REDDatabase.messages.getOutgoingMessage(messageId)
    val threadId = REDDatabase.messages.getMessageRecord(messageId).threadId
    val originalEditedMessage = if (message.messageToEdit > 0) REDDatabase.messages.getMessageRecordOrNull(message.messageToEdit) else null

    if (REDDatabase.messages.isSent(messageId)) {
      warn(TAG, message.sentTimeMillis.toString(), "Message $messageId was already sent. Ignoring.")
      return
    }

    try {
      log(TAG, message.sentTimeMillis.toString(), "Sending message: $messageId, Recipient: ${message.threadRecipient.id}, Thread: $threadId, Attachments: ${buildAttachmentString(message.attachments)}, Editing: ${originalEditedMessage?.dateSent ?: "N/A"}")

      RecipientUtil.shareProfileIfFirstSecureMessage(message.threadRecipient)

      val recipient = message.threadRecipient.fresh()
      val profileKey = recipient.profileKey
      val accessMode = recipient.sealedSenderAccessMode

      val unidentified = try {
        deliver(message, originalEditedMessage)
      } catch (e: NoSessionException) {
        warn(TAG, message.sentTimeMillis.toString(), "Failed to send message, likely due to a missing or corrupt session. Archiving sessions and retrying.", e)

        val recipientId = message.threadRecipient.id
        AppDependencies.protocolStore.aci().sessions().archiveSessions(recipientId)
        AppDependencies.protocolStore.pni().sessions().archiveSessions(recipientId)

        throw RetryLaterException()
      }

      REDDatabase.messages.markAsSent(messageId)
      markAttachmentsUploaded(messageId, message)
      REDDatabase.messages.markUnidentified(messageId, unidentified)

      // For scheduled messages, which may not have updated the thread with its snippet yet
      REDDatabase.threads.updateSilently(threadId, false)

      if (recipient.isSelf) {
        REDDatabase.messages.incrementDeliveryReceiptCount(message.sentTimeMillis, recipient.id, System.currentTimeMillis())
        REDDatabase.messages.incrementReadReceiptCount(message.sentTimeMillis, recipient.id, System.currentTimeMillis())
        REDDatabase.messages.incrementViewedReceiptCount(message.sentTimeMillis, recipient.id, System.currentTimeMillis())
      }

      if (unidentified && accessMode == SealedSenderAccessMode.UNKNOWN && profileKey == null) {
        log(TAG, message.sentTimeMillis.toString(), "Marking recipient as UD-unrestricted following a UD send.")
        REDDatabase.recipients.setSealedSenderAccessMode(recipient.id, SealedSenderAccessMode.UNRESTRICTED)
      } else if (unidentified && accessMode == SealedSenderAccessMode.UNKNOWN) {
        log(TAG, message.sentTimeMillis.toString(), "Marking recipient as UD-enabled following a UD send.")
        REDDatabase.recipients.setSealedSenderAccessMode(recipient.id, SealedSenderAccessMode.ENABLED)
      } else if (!unidentified && accessMode != SealedSenderAccessMode.DISABLED) {
        log(TAG, message.sentTimeMillis.toString(), "Marking recipient as UD-disabled following a non-UD send.")
        REDDatabase.recipients.setSealedSenderAccessMode(recipient.id, SealedSenderAccessMode.DISABLED)
      }

      if (originalEditedMessage != null && originalEditedMessage.expireStarted > 0) {
        REDDatabase.messages.markExpireStarted(messageId, originalEditedMessage.expireStarted)
        expirationManager.scheduleDeletion(messageId, true, originalEditedMessage.expireStarted, originalEditedMessage.expiresIn)
      } else if (message.expiresIn > 0 && !message.isExpirationUpdate) {
        REDDatabase.messages.markExpireStarted(messageId)
        expirationManager.scheduleDeletion(messageId, true, message.expiresIn)
      }

      if (message.isViewOnce) {
        REDDatabase.attachments.deleteAttachmentFilesForViewOnceMessage(messageId)
      }

      enqueueForOutgoingIfNecessary(recipient)

      log(TAG, message.sentTimeMillis.toString(), "Sent message: $messageId")
    } catch (uue: UnregisteredUserException) {
      warn(TAG, "Failure", uue)

      REDDatabase.messages.markAsSentFailed(messageId)
      notifyMediaMessageDeliveryFailed(context, messageId)
      AppDependencies.jobManager.add(DirectoryRefreshJob(false))
    } catch (uie: UntrustedIdentityException) {
      warn(TAG, "Failure", uie)

      val recipient = Recipient.external(uie.identifier)
      if (recipient == null) {
        Log.w(TAG, "Failed to create a Recipient for the identifier!")
        return
      }

      REDDatabase.messages.addMismatchedIdentity(messageId, recipient.id, uie.getIdentityKey())
      REDDatabase.messages.markAsSentFailed(messageId)
      enqueue(recipient.id, true)
    } catch (e: ProofRequiredException) {
      val result = ProofRequiredExceptionHandler.handle(context, e, REDDatabase.threads.getRecipientForThreadId(threadId), threadId, messageId)
      if (result.isRetry()) {
        throw RetryLaterException()
      } else {
        throw e
      }
    }

    REDLocalMetrics.IndividualMessageSend.onJobFinished(messageId)
  }

  public override fun onRetry() {
    REDLocalMetrics.IndividualMessageSend.cancel(messageId)
    super.onRetry()
  }

  override fun onFailure() {
    REDLocalMetrics.IndividualMessageSend.cancel(messageId)
    REDDatabase.messages.markAsSentFailed(messageId)
    notifyMediaMessageDeliveryFailed(context, messageId)
  }

  @Throws(IOException::class, UnregisteredUserException::class, UntrustedIdentityException::class, UndeliverableMessageException::class)
  private fun deliver(message: OutgoingMessage, originalEditedMessage: MessageRecord?): Boolean {
    if (message.body.utf8Size() > MessageUtil.MAX_INLINE_BODY_SIZE_BYTES) {
      throw UndeliverableMessageException("The total body size was greater than our limit of " + MessageUtil.MAX_INLINE_BODY_SIZE_BYTES + " bytes.")
    }

    try {
      var messageRecipient = message.threadRecipient.fresh()

      if (messageRecipient.isUnregistered) {
        throw UndeliverableMessageException(messageRecipient.id.toString() + " not registered!")
      }

      if (!messageRecipient.hasServiceId) {
        messageRecipient = messageRecipient.fresh()

        if (!messageRecipient.hasServiceId) {
          throw UndeliverableMessageException(messageRecipient.id.toString() + " has no serviceId!")
        }
      }

      val messageSender = AppDependencies.signalServiceMessageSender
      val address = RecipientUtil.toREDServiceAddress(context, messageRecipient)
      val attachments = message.attachments.filter { !it.isSticker }
      val serviceAttachments: List<REDServiceAttachment> = getAttachmentPointersFor(attachments)
      val profileKey: Optional<ByteArray> = getProfileKey(messageRecipient)
      val sticker: Optional<REDServiceDataMessage.Sticker> = getStickerFor(message)
      val sharedContacts: List<SharedContact> = getSharedContactsFor(message)
      val previews: List<REDServicePreview> = getPreviewsFor(message)
      val giftBadge = getGiftBadgeFor(message)
      val payment = getPayment(message)
      val bodyRanges: List<BodyRange>? = getBodyRanges(message)
      val pollCreate = getPollCreate(message)
      val pollTerminate = getPollTerminate(message)
      val pinnedMessage = getPinnedMessage(message)
      val mediaMessageBuilder = REDServiceDataMessage.newBuilder()
        .withBody(message.body)
        .withAttachments(serviceAttachments)
        .withTimestamp(message.sentTimeMillis)
        .withExpiration((message.expiresIn / 1000).toInt())
        .withExpireTimerVersion(message.expireTimerVersion)
        .withViewOnce(message.isViewOnce)
        .withProfileKey(profileKey.orElse(null))
        .withSticker(sticker.orElse(null))
        .withSharedContacts(sharedContacts)
        .withPreviews(previews)
        .withGiftBadge(giftBadge)
        .asExpirationUpdate(message.isExpirationUpdate)
        .withPayment(payment)
        .withBodyRanges(bodyRanges)
        .withPollCreate(pollCreate)
        .withPollTerminate(pollTerminate)
        .withPinnedMessage(pinnedMessage)

      if (message.parentStoryId != null) {
        try {
          val storyRecord = REDDatabase.messages.getMessageRecord(message.parentStoryId.asMessageId().id)
          val storyRecipient = storyRecord.fromRecipient

          val storyContext = REDServiceDataMessage.StoryContext(storyRecipient.requireServiceId(), storyRecord.dateSent)
          mediaMessageBuilder.withStoryContext(storyContext)

          val reaction: Optional<REDServiceDataMessage.Reaction> = getStoryReactionFor(message, storyContext)
          if (reaction.isPresent) {
            mediaMessageBuilder.withReaction(reaction.get())
            mediaMessageBuilder.withBody(null)
          }
        } catch (e: NoSuchMessageException) {
          throw UndeliverableMessageException(e)
        }
      } else {
        mediaMessageBuilder.withQuote(getQuoteFor(message).orElse(null))
      }

      if (message.giftBadge != null || message.isPaymentsNotification) {
        mediaMessageBuilder.withBody(null)
      }

      val mediaMessage = mediaMessageBuilder.build()

      if (originalEditedMessage != null) {
        if (REDStore.account.aci == address.serviceId) {
          val result = messageSender.sendSelfSyncEditMessage(REDServiceEditMessage(originalEditedMessage.dateSent, mediaMessage))
          REDDatabase.messageLog.insertIfPossible(messageRecipient.id, message.sentTimeMillis, result, ContentHint.RESENDABLE, MessageId(messageId), false)

          return SealedSenderAccessUtil.getSealedSenderCertificate() != null
        } else {
          val result = messageSender.sendEditMessage(
            address,
            SealedSenderAccessUtil.getSealedSenderAccessFor(messageRecipient),
            ContentHint.RESENDABLE,
            mediaMessage,
            IndividualSendEvents.EMPTY,
            message.isUrgent,
            originalEditedMessage.dateSent
          )
          REDDatabase.messageLog.insertIfPossible(messageRecipient.id, message.sentTimeMillis, result, ContentHint.RESENDABLE, MessageId(messageId), false)

          return result.success.isUnidentified
        }
      } else if (REDStore.account.aci == address.serviceId) {
        val result = messageSender.sendSyncMessage(mediaMessage)
        REDDatabase.messageLog.insertIfPossible(messageRecipient.id, message.sentTimeMillis, result, ContentHint.RESENDABLE, MessageId(messageId), false)
        return SealedSenderAccessUtil.getSealedSenderCertificate() != null
      } else {
        REDLocalMetrics.IndividualMessageSend.onDeliveryStarted(messageId, message.sentTimeMillis)
        val result = messageSender.sendDataMessage(
          address,
          SealedSenderAccessUtil.getSealedSenderAccessFor(messageRecipient),
          ContentHint.RESENDABLE,
          mediaMessage,
          MetricEventListener(messageId),
          message.isUrgent,
          messageRecipient.needsPniSignature
        )

        REDDatabase.messageLog.insertIfPossible(messageRecipient.id, message.sentTimeMillis, result, ContentHint.RESENDABLE, MessageId(messageId), message.isUrgent)

        if (messageRecipient.needsPniSignature) {
          REDDatabase.pendingPniSignatureMessages.insertIfNecessary(messageRecipient.id, message.sentTimeMillis, result)
        }

        return result.success.isUnidentified
      }
    } catch (e: FileNotFoundException) {
      warn(TAG, message.sentTimeMillis.toString(), e)
      throw UndeliverableMessageException(e)
    } catch (e: ServerRejectedException) {
      throw UndeliverableMessageException(e)
    }
  }

  private fun getPayment(message: OutgoingMessage): REDServiceDataMessage.Payment? {
    if (message.isPaymentsNotification) {
      val paymentUuid = parseOrThrow(message.body)
      val payment = REDDatabase.payments.getPayment(paymentUuid)

      if (payment == null) {
        Log.w(TAG, "Could not find payment, cannot send notification $paymentUuid")
        return null
      }

      if (payment.receipt == null) {
        Log.w(TAG, "Could not find payment receipt, cannot send notification $paymentUuid")
        return null
      }

      return REDServiceDataMessage.Payment(REDServiceDataMessage.PaymentNotification(payment.receipt!!, payment.note), null)
    } else {
      var type: DataMessage.Payment.Activation.Type? = null

      if (message.isRequestToActivatePayments) {
        type = DataMessage.Payment.Activation.Type.REQUEST
      } else if (message.isPaymentsActivated) {
        type = DataMessage.Payment.Activation.Type.ACTIVATED
      }

      return if (type != null) {
        REDServiceDataMessage.Payment(null, PaymentActivation(type))
      } else {
        null
      }
    }
  }

  private class MetricEventListener(private val messageId: Long) : IndividualSendEvents {
    override fun onMessageEncrypted() {
      REDLocalMetrics.IndividualMessageSend.onMessageEncrypted(messageId)
    }

    override fun onMessageSent() {
      REDLocalMetrics.IndividualMessageSend.onMessageSent(messageId)
    }

    override fun onSyncMessageEncrypted() {
      REDLocalMetrics.IndividualMessageSend.onSyncMessageEncrypted(messageId)
    }

    override fun onSyncMessageSent() {
      REDLocalMetrics.IndividualMessageSend.onSyncMessageSent(messageId)
    }
  }

  class Factory : Job.Factory<IndividualSendJob?> {
    override fun create(parameters: Parameters, serializedData: ByteArray?): IndividualSendJob {
      val data = JsonJobData.deserialize(serializedData)
      return IndividualSendJob(parameters, data.getLong(KEY_MESSAGE_ID))
    }
  }
}
