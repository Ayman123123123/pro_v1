package com.red.sovereign.conversation.v2.groups

import com.red.sovereign.groups.GroupId
import com.red.sovereign.recipients.Recipient

/**
 * Represents detected duplicate recipients that should be displayed
 * to the user as a warning.
 *
 * @param groupId   The groupId for the conversation
 * @param recipient The first recipient in the list of duplicates
 * @param count     The number of duplicates
 */
data class ConversationGroupReviewState(
  val groupId: GroupId.V2?,
  val recipient: Recipient,
  val count: Int
) {
  companion object {
    val EMPTY = ConversationGroupReviewState(null, Recipient.UNKNOWN, 0)
  }
}
