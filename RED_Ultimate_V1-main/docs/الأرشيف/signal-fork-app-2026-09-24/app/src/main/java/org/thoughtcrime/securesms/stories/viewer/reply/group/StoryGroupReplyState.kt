package com.red.sovereign.stories.viewer.reply.group

import com.red.sovereign.conversation.colors.NameColor
import com.red.sovereign.recipients.RecipientId

data class StoryGroupReplyState(
  val threadId: Long = 0L,
  val replies: List<ReplyBody> = emptyList(),
  val nameColors: Map<RecipientId, NameColor> = emptyMap(),
  val loadState: LoadState = LoadState.INIT
) {
  val noReplies: Boolean = replies.isEmpty()

  enum class LoadState {
    INIT,
    READY
  }
}
