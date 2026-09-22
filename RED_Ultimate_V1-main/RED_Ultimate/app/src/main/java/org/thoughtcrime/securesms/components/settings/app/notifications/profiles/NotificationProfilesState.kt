package com.red.sovereign.components.settings.app.notifications.profiles

import com.red.sovereign.notifications.profiles.NotificationProfile
import com.red.sovereign.notifications.profiles.NotificationProfiles

data class NotificationProfilesState(
  val profiles: List<NotificationProfile>,
  val activeProfile: NotificationProfile? = NotificationProfiles.getActiveProfile(profiles)
)
