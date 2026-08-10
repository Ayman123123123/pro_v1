package com.red.sovereign.database

import com.red.sovereign.database.model.ParentStoryId
import com.red.sovereign.database.model.StoryType
import com.red.sovereign.database.model.databaseprotos.GiftBadge
import com.red.sovereign.mms.IncomingMessage
import com.red.sovereign.mms.OutgoingMessage
import com.red.sovereign.recipients.Recipient
import java.util.Optional

/**
 * Helper methods for inserting an MMS message into the MMS table.
 */
object MmsHelper {

  fun insert(
    recipient: Recipient = Recipient.UNKNOWN,
    body: String = "body",
    sentTimeMillis: Long = System.currentTimeMillis(),
    expiresIn: Long = 0,
    viewOnce: Boolean = false,
    distributionType: Int = ThreadTable.DistributionTypes.DEFAULT,
    threadId: Long = REDDatabase.threads.getOrCreateThreadIdFor(recipient, distributionType),
    storyType: StoryType = StoryType.NONE,
    parentStoryId: ParentStoryId? = null,
    isStoryReaction: Boolean = false,
    giftBadge: GiftBadge? = null,
    secure: Boolean = true
  ): Long {
    val message = OutgoingMessage(
      recipient = recipient,
      body = body,
      timestamp = sentTimeMillis,
      expiresIn = expiresIn,
      viewOnce = viewOnce,
      distributionType = distributionType,
      storyType = storyType,
      parentStoryId = parentStoryId,
      isStoryReaction = isStoryReaction,
      giftBadge = giftBadge,
      isSecure = secure
    )

    return insert(
      message = message,
      threadId = threadId
    )
  }

  fun insert(
    message: OutgoingMessage,
    threadId: Long
  ): Long {
    return REDDatabase.messages.insertMessageOutbox(message, threadId, false, GroupReceiptTable.STATUS_UNKNOWN, null).messageId
  }

  fun insert(
    message: IncomingMessage,
    threadId: Long
  ): Optional<MessageTable.InsertResult> {
    return REDDatabase.messages.insertMessageInbox(message, threadId)
  }
}
