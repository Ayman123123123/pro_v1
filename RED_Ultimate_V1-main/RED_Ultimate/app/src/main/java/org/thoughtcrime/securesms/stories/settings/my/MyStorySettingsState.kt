package com.red.sovereign.stories.settings.my

data class MyStorySettingsState(
  val myStoryPrivacyState: MyStoryPrivacyState = MyStoryPrivacyState(),
  val areRepliesAndReactionsEnabled: Boolean = false,
  val allREDConnectionsCount: Int = 0,
  val hasUserPerformedManualSelection: Boolean
)
