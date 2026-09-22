package com.red.sovereign.util

import android.content.Context
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import org.signal.core.util.ByteSize
import org.signal.core.util.bytes
import org.signal.core.util.kibiBytes
import org.signal.core.util.logging.Log
import org.signal.core.util.mebiBytes
import com.red.sovereign.attachments.Attachment
import com.red.sovereign.attachments.Cdn
import com.red.sovereign.attachments.DatabaseAttachment
import com.red.sovereign.database.MediaTable
import com.red.sovereign.database.NoSuchMessageException
import com.red.sovereign.database.REDDatabase.Companion.attachments
import com.red.sovereign.database.REDDatabase.Companion.messages
import com.red.sovereign.database.REDDatabase.Companion.threads
import com.red.sovereign.database.model.MessageRecord
import com.red.sovereign.jobmanager.impl.NotInCallConstraint
import com.red.sovereign.jobs.MultiDeviceDeleteSyncJob.Companion.enqueueAttachmentDelete
import com.red.sovereign.recipients.Recipient
import org.whispersystems.signalservice.api.crypto.AttachmentCipherStreamUtil
import org.whispersystems.signalservice.internal.crypto.PaddingInputStream

object AttachmentUtil {
  private val TAG = Log.tag(AttachmentUtil::class.java)

  private val MAX_AUTO_DOWNLOAD_SIZE: ByteSize = 200.mebiBytes
  val SMALL_ATTACHMENT_SIZE: ByteSize = 100.kibiBytes

  @JvmStatic
  @MainThread
  fun isRestoreOnOpenPermitted(context: Context, attachment: Attachment?): Boolean {
    if (attachment == null) {
      return true
    }

    val contentType = attachment.contentType ?: return false
    if (!MediaUtil.isImageType(contentType)) {
      return false
    }

    val allowedTypes = getAllowedAutoDownloadTypes(context)
    return NotInCallConstraint.isNotInConnectedCall() && allowedTypes.contains(MediaUtil.getDiscreteMimeType(contentType))
  }

  @JvmStatic
  @WorkerThread
  fun isAutoDownloadPermitted(context: Context, attachment: DatabaseAttachment?): Boolean {
    if (attachment == null) {
      return true
    }

    if (!isFromTrustedConversation(context, attachment)) {
      Log.w(TAG, "Not allowing download due to untrusted conversation")
      return false
    }

    if (attachment.size <= 0 && attachment.cdn != Cdn.S3) {
      Log.w(TAG, "Not auto downloading. Attachment has no declared size.")
      return false
    }

    val ciphertextSize: ByteSize = AttachmentCipherStreamUtil.getCiphertextLength(PaddingInputStream.getPaddedSize(attachment.size)).bytes

    if (ciphertextSize > MAX_AUTO_DOWNLOAD_SIZE) {
      Log.w(TAG, "Not auto downloading. Attachment ciphertext size $ciphertextSize exceeds max auto download size $MAX_AUTO_DOWNLOAD_SIZE")
      return false
    }

    val allowedTypes = getAllowedAutoDownloadTypes(context)
    val contentType = attachment.contentType

    return when {
      MediaUtil.isLongTextType(contentType) -> attachment.size <= MessageUtil.MAX_TOTAL_BODY_SIZE_BYTES
      attachment.isSticker -> ciphertextSize <= SMALL_ATTACHMENT_SIZE || allowedForType(allowedTypes, "image", "sticker")
      attachment.voiceNote -> ciphertextSize <= SMALL_ATTACHMENT_SIZE || allowedForType(allowedTypes, "audio", "voice message")
      attachment.videoGif -> allowedForType(allowedTypes, "image", "video gif")
      contentType != null && isNonDocumentType(contentType) -> allowedForType(allowedTypes, MediaUtil.getDiscreteMimeType(contentType), contentType)
      else -> allowedForType(allowedTypes, "documents", "document")
    }
  }

  /**
   * Deletes the specified attachment. If its the only attachment for its linked message, the entire
   * message is deleted.
   *
   * @return message record of deleted message if a message is deleted
   */
  @JvmStatic
  @WorkerThread
  fun deleteAttachment(attachment: DatabaseAttachment): MessageRecord? {
    val attachmentId = attachment.attachmentId
    val mmsId = attachment.mmsId
    val attachmentCount = attachments.getAttachmentsForMessage(mmsId).size

    if (attachmentCount <= 1) {
      val deletedMessageRecord = messages.getMessageRecordOrNull(mmsId)
      messages.deleteMessage(mmsId)
      return deletedMessageRecord
    }

    attachments.deleteAttachment(attachmentId)
    enqueueAttachmentDelete(messages.getMessageRecordOrNull(mmsId), attachment)
    return null
  }

  /**
   * Version of [deleteAttachment] optimized for bulk-delete. Suppresses observer notifications and bulk notifies at the end.
   *
   * @param onProgress invoked with the running count (1-based) after each item.
   * @return the set of [MessageRecord]s that were fully deleted (i.e. items where the attachment
   *         was the last one on its message)
   */
  @JvmStatic
  @WorkerThread
  fun deleteAttachments(records: Collection<MediaTable.MediaRecord>, onProgress: (Int) -> Unit): Set<MessageRecord> {
    val deletedMessageRecords = mutableSetOf<MessageRecord>()
    val touchedThreadIds = mutableSetOf<Long>()

    records.forEachIndexed { index, record ->
      val attachment = record.attachment
      if (attachment != null) {
        val mmsId = attachment.mmsId
        val attachmentCount = attachments.getAttachmentsForMessage(mmsId).size

        // If it's the only attachment, just delete the message
        if (attachmentCount <= 1) {
          val deletedMessageRecord = messages.getMessageRecordOrNull(mmsId)
          if (deletedMessageRecord != null) {
            messages.deleteMessage(mmsId, deletedMessageRecord.threadId, notify = false, updateThread = false)
            touchedThreadIds += deletedMessageRecord.threadId
            deletedMessageRecords += deletedMessageRecord
          }
        } else {
          attachments.deleteAttachment(attachment.attachmentId)
          enqueueAttachmentDelete(messages.getMessageRecordOrNull(mmsId), attachment)
        }
      } else {
        Log.w(TAG, "No attachment found for message ${record.messageId}")
      }

      onProgress(index + 1)
    }

    messages.flushBulkDeleteNotifications(touchedThreadIds)

    return deletedMessageRecords
  }

  private fun allowedForType(allowedTypes: Set<String>, typeKey: String?, label: String): Boolean {
    val notInCall = NotInCallConstraint.isNotInConnectedCall()
    val typeAllowed = typeKey != null && allowedTypes.contains(typeKey)
    val allowed = notInCall && typeAllowed
    if (!allowed) {
      Log.w(TAG, "Not auto downloading $label. inCall: ${!notInCall} allowedType: $typeAllowed")
    }
    return allowed
  }

  private fun isNonDocumentType(contentType: String): Boolean {
    return MediaUtil.isImageType(contentType) ||
      MediaUtil.isVideoType(contentType) ||
      MediaUtil.isAudioType(contentType)
  }

  private fun getAllowedAutoDownloadTypes(context: Context): Set<String> {
    return when {
      NetworkUtil.isConnectedWifi(context) -> TextSecurePreferences.getWifiMediaDownloadAllowed(context)
      NetworkUtil.isConnectedRoaming(context) -> TextSecurePreferences.getRoamingMediaDownloadAllowed(context)
      NetworkUtil.isConnectedMobile(context) -> TextSecurePreferences.getMobileMediaDownloadAllowed(context)
      else -> emptySet()
    }
  }

  @WorkerThread
  private fun isFromTrustedConversation(context: Context, attachment: DatabaseAttachment): Boolean {
    return try {
      val message = messages.getMessageRecord(attachment.mmsId)
      val fromRecipient = message.fromRecipient
      val toRecipient = threads.getRecipientForThreadId(message.threadId)

      if (toRecipient != null && toRecipient.isGroup) {
        toRecipient.isProfileSharing || isTrustedIndividual(fromRecipient, message)
      } else {
        isTrustedIndividual(fromRecipient, message)
      }
    } catch (e: NoSuchMessageException) {
      Log.w(TAG, "Message could not be found! Assuming not a trusted contact.")
      false
    }
  }

  private fun isTrustedIndividual(recipient: Recipient, message: MessageRecord): Boolean {
    return recipient.isSystemContact ||
      recipient.isProfileSharing ||
      message.isOutgoing ||
      recipient.isSelf ||
      recipient.isReleaseNotes
  }
}
