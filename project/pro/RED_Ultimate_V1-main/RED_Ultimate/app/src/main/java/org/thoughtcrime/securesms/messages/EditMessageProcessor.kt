package com.red.sovereign.messages

import android.content.Context
import org.signal.core.util.UuidUtil
import org.signal.core.util.concurrent.REDExecutors
import org.signal.core.util.orNull
import com.red.sovereign.database.MessageTable.InsertResult
import com.red.sovereign.database.MessageType
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.MessageId
import com.red.sovereign.database.model.MmsMessageRecord
import com.red.sovereign.database.model.databaseprotos.BodyRangeList
import com.red.sovereign.database.model.toBodyRangeList
import com.red.sovereign.database.withAttachments
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.groups.GroupId
import com.red.sovereign.jobs.AttachmentDownloadJob
import com.red.sovereign.jobs.PushProcessEarlyMessagesJob
import com.red.sovereign.jobs.SendDeliveryReceiptJob
import com.red.sovereign.messages.MessageContentProcessor.Companion.log
import com.red.sovereign.messages.MessageContentProcessor.Companion.warn
import com.red.sovereign.messages.REDServiceProtoUtil.groupId
import com.red.sovereign.messages.REDServiceProtoUtil.isMediaMessage
import com.red.sovereign.messages.REDServiceProtoUtil.toPointersWithinLimit
import com.red.sovereign.mms.IncomingMessage
import com.red.sovereign.mms.QuoteModel
import com.red.sovereign.notifications.v2.ConversationId.Companion.forConversation
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.util.EarlyMessageCacheEntry
import com.red.sovereign.util.MediaUtil
import com.red.sovereign.util.MessageConstraintsUtil
import com.red.sovereign.util.hasAudio
import com.red.sovereign.util.hasSharedContact
import org.whispersystems.signalservice.api.crypto.EnvelopeMetadata
import org.whispersystems.signalservice.internal.push.Content
import org.whispersystems.signalservice.internal.push.DataMessage
import org.whispersystems.signalservice.internal.push.Envelope
import org.whispersystems.signalservice.internal.util.Util

object EditMessageProcessor {
  fun process(
    context: Context,
    senderRecipient: Recipient,
    threadRecipient: Recipient,
    envelope: Envelope,
    content: Content,
    metadata: EnvelopeMetadata,
    earlyMessageCacheEntry: EarlyMessageCacheEntry?
  ) {
    val editMessage = content.editMessage!!

    log(envelope.clientTimestamp!!, "[handleEditMessage] Edit message for " + editMessage.targetSentTimestamp)

    var targetMessage: MmsMessageRecord? = REDDatabase.messages.getMessageFor(editMessage.targetSentTimestamp!!, senderRecipient.id) as? MmsMessageRecord
    val targetThreadRecipient: Recipient? = if (targetMessage != null) REDDatabase.threads.getRecipientForThreadId(targetMessage.threadId) else null

    if (targetMessage == null || targetThreadRecipient == null) {
      warn(envelope.clientTimestamp!!, "[handleEditMessage] Could not find matching message! timestamp: ${editMessage.targetSentTimestamp}  author: ${senderRecipient.id}")

      if (earlyMessageCacheEntry != null) {
        AppDependencies.earlyMessageCache.store(senderRecipient.id, editMessage.targetSentTimestamp!!, earlyMessageCacheEntry)
        PushProcessEarlyMessagesJob.enqueue()
      }

      return
    }

    val message = editMessage.dataMessage!!
    val isMediaMessage = message.isMediaMessage
    val groupId: GroupId.V2? = message.groupV2?.groupId

    val originalMessageWithoutAttachments = targetMessage.originalMessageId?.let { REDDatabase.messages.getMessageRecord(it.id) } ?: targetMessage
    val originalMessage = originalMessageWithoutAttachments.withAttachments()
    val validTiming = MessageConstraintsUtil.isValidEditMessageReceive(originalMessage, senderRecipient, envelope.serverTimestamp!!)
    val validAuthor = senderRecipient.id == originalMessage.fromRecipient.id
    val validGroup = groupId == targetThreadRecipient.groupId.orNull()
    val validTarget = !originalMessage.isViewOnce && !originalMessage.hasAudio() && !originalMessage.hasSharedContact()

    if (!validTiming || !validAuthor || !validGroup || !validTarget) {
      warn(envelope.clientTimestamp!!, "[handleEditMessage] Invalid message edit! editTime: ${envelope.serverTimestamp}, targetTime: ${originalMessage.serverTimestamp}, editAuthor: ${senderRecipient.id}, targetAuthor: ${originalMessage.fromRecipient.id}, editThread: ${threadRecipient.id}, targetThread: ${targetThreadRecipient.id}, validity: (timing: $validTiming, author: $validAuthor, group: $validGroup, target: $validTarget)")
      return
    }

    if (groupId != null && MessageContentProcessor.handleGv2PreProcessing(context, envelope.clientTimestamp!!, content, metadata, groupId, message.groupV2!!, senderRecipient) == MessageContentProcessor.Gv2PreProcessResult.IGNORE) {
      warn(envelope.clientTimestamp!!, "[handleEditMessage] Group processor indicated we should ignore this.")
      return
    }

    DataMessageProcessor.notifyTypingStoppedFromIncomingMessage(context, senderRecipient, threadRecipient.id, metadata.sourceDeviceId)

    targetMessage = targetMessage.withAttachments(REDDatabase.attachments.getAttachmentsForMessage(targetMessage.id))

    val insertResult: InsertResult? = if (isMediaMessage || targetMessage.quote != null || targetMessage.slideDeck.slides.isNotEmpty()) {
      handleEditMediaMessage(senderRecipient.id, groupId, envelope, metadata, message, targetMessage)
    } else {
      handleEditTextMessage(senderRecipient.id, groupId, envelope, metadata, message, targetMessage)
    }

    if (insertResult != null) {
      REDExecutors.BOUNDED.execute {
        AppDependencies.jobManager.add(SendDeliveryReceiptJob(senderRecipient.id, message.timestamp!!, MessageId(insertResult.messageId)))
      }

      if (targetMessage.expireStarted > 0) {
        AppDependencies.expiringMessageManager
          .scheduleDeletion(
            insertResult.messageId,
            true,
            targetMessage.expireStarted,
            targetMessage.expiresIn
          )
      }

      AppDependencies.messageNotifier.updateNotification(context, forConversation(insertResult.threadId))
    }
  }

  private fun handleEditMediaMessage(
    senderRecipientId: RecipientId,
    groupId: GroupId.V2?,
    envelope: Envelope,
    metadata: EnvelopeMetadata,
    message: DataMessage,
    targetMessage: MmsMessageRecord
  ): InsertResult? {
    val cappedBodyRanges = message.bodyRanges.take(DataMessageProcessor.BODY_RANGE_PROCESSING_LIMIT)
    val messageRanges: BodyRangeList? = cappedBodyRanges.filter { Util.allAreNull(it.mentionAci, it.mentionAciBinary) }.toList().toBodyRangeList()
    val targetQuote = targetMessage.quote
    val quote: QuoteModel? = if (targetQuote != null && (message.quote != null || (targetMessage.parentStoryId != null && message.storyContext != null))) {
      QuoteModel(
        targetQuote.id,
        targetQuote.author,
        targetQuote.displayText.toString(),
        targetQuote.isOriginalMissing,
        null,
        null,
        targetQuote.quoteType,
        null
      )
    } else {
      null
    }
    val attachments = message.attachments.toPointersWithinLimit().filter {
      MediaUtil.SlideType.LONG_TEXT == MediaUtil.getSlideTypeFromContentType(it.contentType)
    }
    val mediaMessage = IncomingMessage(
      type = MessageType.NORMAL,
      from = senderRecipientId,
      sentTimeMillis = message.timestamp!!,
      serverTimeMillis = envelope.serverTimestamp!!,
      receivedTimeMillis = targetMessage.dateReceived,
      expiresIn = targetMessage.expiresIn,
      isViewOnce = message.isViewOnce == true,
      isUnidentified = metadata.sealedSender,
      body = message.body,
      groupId = groupId,
      attachments = attachments,
      quote = quote,
      parentStoryId = targetMessage.parentStoryId,
      sharedContacts = emptyList(),
      linkPreviews = DataMessageProcessor.getLinkPreviews(message.preview, message.body ?: "", false),
      mentions = DataMessageProcessor.getMentions(cappedBodyRanges),
      serverGuid = UuidUtil.getStringUUID(envelope.serverGuid, envelope.serverGuidBinary),
      messageRanges = messageRanges
    )

    val insertResult = REDDatabase.messages.insertEditMessageInbox(mediaMessage, targetMessage).orNull()
    if (insertResult?.insertedAttachments != null) {
      REDDatabase.runPostSuccessfulTransaction {
        val downloadJobs: List<AttachmentDownloadJob> = insertResult.insertedAttachments.mapNotNull { (_, attachmentId) ->
          AttachmentDownloadJob(insertResult.messageId, attachmentId, false)
        }
        AppDependencies.jobManager.addAll(downloadJobs)
      }
    }
    return insertResult
  }

  private fun handleEditTextMessage(
    senderRecipientId: RecipientId,
    groupId: GroupId.V2?,
    envelope: Envelope,
    metadata: EnvelopeMetadata,
    message: DataMessage,
    targetMessage: MmsMessageRecord
  ): InsertResult? {
    val textMessage = IncomingMessage(
      type = MessageType.NORMAL,
      from = senderRecipientId,
      sentTimeMillis = envelope.clientTimestamp!!,
      serverTimeMillis = envelope.clientTimestamp!!,
      receivedTimeMillis = targetMessage.dateReceived,
      body = message.body,
      groupId = groupId,
      parentStoryId = targetMessage.parentStoryId,
      expiresIn = targetMessage.expiresIn,
      isUnidentified = metadata.sealedSender,
      serverGuid = UuidUtil.getStringUUID(envelope.serverGuid, envelope.serverGuidBinary)
    )

    return REDDatabase.messages.insertEditMessageInbox(textMessage, targetMessage).orNull()
  }
}
