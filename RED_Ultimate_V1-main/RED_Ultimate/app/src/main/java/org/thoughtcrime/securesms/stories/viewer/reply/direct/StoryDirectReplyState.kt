package com.red.sovereign.stories.viewer.reply.direct

import com.red.sovereign.database.model.MessageRecord
import com.red.sovereign.recipients.Recipient

data class StoryDirectReplyState(
  val groupDirectReplyRecipient: Recipient? = null,
  val storyRecord: MessageRecord? = null
)
