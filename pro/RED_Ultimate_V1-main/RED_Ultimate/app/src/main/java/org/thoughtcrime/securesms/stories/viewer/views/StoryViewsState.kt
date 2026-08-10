package com.red.sovereign.stories.viewer.views

import com.red.sovereign.recipients.Recipient

data class StoryViewsState(
  val loadState: LoadState = LoadState.INIT,
  val storyRecipient: Recipient? = null,
  val views: List<StoryViewItemData> = emptyList()
) {
  enum class LoadState {
    INIT,
    READY,
    DISABLED
  }
}
