package com.red.sovereign.stories.viewer.views

import com.red.sovereign.recipients.Recipient

data class StoryViewItemData(
  val recipient: Recipient,
  val timeViewedInMillis: Long
)
