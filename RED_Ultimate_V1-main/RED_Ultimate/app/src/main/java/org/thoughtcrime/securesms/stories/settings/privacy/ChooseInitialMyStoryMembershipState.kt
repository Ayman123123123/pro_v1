package com.red.sovereign.stories.settings.privacy

import com.red.sovereign.recipients.RecipientId
import com.red.sovereign.stories.settings.my.MyStoryPrivacyState

data class ChooseInitialMyStoryMembershipState(
  val recipientId: RecipientId? = null,
  val privacyState: MyStoryPrivacyState = MyStoryPrivacyState(),
  val allREDConnectionsCount: Int = 0,
  val hasUserPerformedManualSelection: Boolean = false
)
