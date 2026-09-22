/*
 * Copyright 2026 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.components.settings.conversation

import androidx.fragment.app.FragmentActivity
import com.red.sovereign.main.MainNavigationChatDetailRouter
import com.red.sovereign.main.MainNavigationDetailLocation
import com.red.sovereign.recipients.Recipient

/**
 * Routes to the conversation settings screen, handling split-pane vs. standalone activity automatically.
 */
object ConversationSettingsNavigator {
  @JvmStatic
  fun navigate(
    activity: FragmentActivity,
    recipient: Recipient
  ) {
    if (activity is MainNavigationChatDetailRouter) {
      activity.goToChatDetail(MainNavigationDetailLocation.Chats.ConversationSettings(recipient.id))
      return
    }

    val intent = if (recipient.isPushGroup) {
      ConversationSettingsActivity.forGroup(activity, recipient.requireGroupId())
    } else {
      ConversationSettingsActivity.forRecipient(activity, recipient.id)
    }
    activity.startActivity(intent)
  }
}
