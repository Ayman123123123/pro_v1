package com.red.sovereign.components.settings.conversation.sounds

import com.red.sovereign.database.RecipientTable
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.recipients.RecipientId

data class SoundsAndNotificationsSettingsState(
  val recipientId: RecipientId = Recipient.UNKNOWN.id,
  val muteUntil: Long = 0L,
  val mentionSetting: RecipientTable.NotificationSetting = RecipientTable.NotificationSetting.DO_NOT_NOTIFY,
  val hasCustomNotificationSettings: Boolean = false,
  val hasMentionsSupport: Boolean = false,
  val channelConsistencyCheckComplete: Boolean = false
)
