/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.components.settings.conversation.sounds

import com.red.sovereign.database.RecipientTable.NotificationSetting
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId

data class SoundsAndNotificationsSettingsState2(
  val recipientId: RecipientId = Recipient.UNKNOWN.id,
  val muteUntil: Long = 0L,
  val mentionSetting: NotificationSetting = NotificationSetting.ALWAYS_NOTIFY,
  val callNotificationSetting: NotificationSetting = NotificationSetting.ALWAYS_NOTIFY,
  val replyNotificationSetting: NotificationSetting = NotificationSetting.ALWAYS_NOTIFY,
  val hasCustomNotificationSettings: Boolean = false,
  val hasMentionsSupport: Boolean = false,
  val channelConsistencyCheckComplete: Boolean = false
) {
  val isMuted = muteUntil > 0
}
