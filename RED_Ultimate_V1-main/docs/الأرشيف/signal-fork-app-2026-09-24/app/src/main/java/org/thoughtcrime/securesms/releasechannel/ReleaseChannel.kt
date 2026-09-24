package com.red.sovereign.releasechannel

import com.red.sovereign.attachments.Cdn
import com.red.sovereign.attachments.PointerAttachment
import com.red.sovereign.database.MessageTable
import com.red.sovereign.database.MessageType
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.database.model.StoryType
import com.red.sovereign.database.model.databaseprotos.BodyRangeList
import com.red.sovereign.mms.IncomingMessage
import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.util.MediaUtil
import org.whispersystems.signalservice.api.messages.REDServiceAttachment
import org.whispersystems.signalservice.api.messages.REDServiceAttachmentPointer
import org.whispersystems.signalservice.api.messages.REDServiceAttachmentRemoteId
import java.util.Optional
import java.util.UUID

/**
 * One stop shop for inserting Release Channel messages.
 */
object ReleaseChannel {

  fun insertReleaseChannelMessage(
    recipientId: RecipientId,
    body: String,
    threadId: Long,
    media: String? = null,
    mediaWidth: Int = 0,
    mediaHeight: Int = 0,
    mediaType: String = "image/webp",
    mediaAttachmentUuid: UUID? = UUID.randomUUID(),
    messageRanges: BodyRangeList? = null,
    storyType: StoryType = StoryType.NONE
  ): MessageTable.InsertResult? {
    val attachments: Optional<List<REDServiceAttachment>> = if (media != null) {
      val attachment = REDServiceAttachmentPointer(
        cdnNumber = Cdn.S3.cdnNumber,
        remoteId = REDServiceAttachmentRemoteId.S3,
        contentType = mediaType,
        key = null,
        size = Optional.empty(),
        preview = Optional.empty(),
        width = mediaWidth,
        height = mediaHeight,
        digest = Optional.empty(),
        incrementalDigest = Optional.empty(),
        incrementalMacChunkSize = 0,
        fileName = Optional.of(media),
        voiceNote = false,
        isBorderless = false,
        isGif = MediaUtil.isVideo(mediaType),
        caption = Optional.empty(),
        blurHash = Optional.empty(),
        uploadTimestamp = System.currentTimeMillis(),
        uuid = mediaAttachmentUuid
      )

      Optional.of(listOf(attachment))
    } else {
      Optional.empty()
    }

    val message = IncomingMessage(
      type = MessageType.NORMAL,
      from = recipientId,
      sentTimeMillis = System.currentTimeMillis(),
      serverTimeMillis = System.currentTimeMillis(),
      receivedTimeMillis = System.currentTimeMillis(),
      body = body,
      attachments = PointerAttachment.forPointers(attachments),
      serverGuid = UUID.randomUUID().toString(),
      messageRanges = messageRanges,
      storyType = storyType
    )

    return REDDatabase.messages.insertMessageInbox(message, threadId).orElse(null)
  }
}
