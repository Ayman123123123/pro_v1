/*
 * Copyright 2025 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.components.settings.conversation.sounds

import com.red.sovereign.database.RecipientTable.NotificationSetting

/**
 * Represents all user-driven actions that can occur on the Sounds & Notifications settings screen.
 */
sealed interface SoundsAndNotificationsEvent {

  /**
   * Mutes notifications for this recipient until the given epoch-millisecond timestamp.
   *
   * @param muteUntil Epoch-millisecond timestamp after which notifications should resume.
   *                  Use [Long.MAX_VALUE] to mute indefinitely.
   */
  data class SetMuteUntil(val muteUntil: Long) : SoundsAndNotificationsEvent

  /**
   * Clears any active mute, immediately restoring notifications for this recipient.
   */
  data object Unmute : SoundsAndNotificationsEvent

  /**
   * Updates the mention notification setting for this recipient.
   * Only relevant for group conversations that support @mentions.
   *
   * @param setting The new [NotificationSetting] to apply for @mention notifications.
   */
  data class SetMentionSetting(val setting: NotificationSetting) : SoundsAndNotificationsEvent

  /**
   * Updates the call notification setting for this recipient.
   * Controls whether incoming calls still produce notifications while the conversation is muted.
   *
   * @param setting The new [NotificationSetting] to apply for call notifications.
   */
  data class SetCallNotificationSetting(val setting: NotificationSetting) : SoundsAndNotificationsEvent

  /**
   * Updates the reply notification setting for this recipient.
   * Controls whether replies directed at the current user still produce notifications while muted.
   *
   * @param setting The new [NotificationSetting] to apply for reply notifications.
   */
  data class SetReplyNotificationSetting(val setting: NotificationSetting) : SoundsAndNotificationsEvent

  /**
   * REDs that the user tapped the "Custom Notifications" row and wishes to navigate to the
   * [custom notifications settings screen][com.red.sovereign.components.settings.conversation.sounds.custom.CustomNotificationsSettingsFragment].
   */
  data object NavigateToCustomNotifications : SoundsAndNotificationsEvent
}
