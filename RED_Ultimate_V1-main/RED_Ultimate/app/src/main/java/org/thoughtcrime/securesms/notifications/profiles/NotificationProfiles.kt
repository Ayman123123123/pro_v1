package com.red.sovereign.notifications.profiles

import android.content.Context
import org.signal.core.util.concurrent.REDExecutors
import org.signal.core.util.logging.Log
import com.red.sovereign.R
import com.red.sovereign.database.REDDatabase
import com.red.sovereign.keyvalue.NotificationProfileValues
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.recipients.Recipient
import com.red.sovereign.storage.StorageSyncHelper
import com.red.sovereign.util.formatHours
import com.red.sovereign.util.toLocalDateTime
import com.red.sovereign.util.toLocalTime
import com.red.sovereign.util.toMillis
import com.red.sovereign.util.toOffset
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Helper for determining the single, currently active Notification Profile (if any) and also how to describe
 * how long the active profile will be on for.
 */
object NotificationProfiles {

  val TAG = Log.tag(NotificationProfiles::class.java)

  @JvmStatic
  @JvmOverloads
  fun getActiveProfile(profiles: List<NotificationProfile>, now: Long = System.currentTimeMillis(), zoneId: ZoneId = ZoneId.systemDefault(), shouldSync: Boolean = false): NotificationProfile? {
    val storeValues: NotificationProfileValues = REDStore.notificationProfile
    val localNow: LocalDateTime = now.toLocalDateTime(zoneId)

    val manualProfile: NotificationProfile? = if (now < storeValues.manuallyEnabledUntil) {
      profiles.firstOrNull { it.id == storeValues.manuallyEnabledProfile }
    } else {
      null
    }

    val scheduledProfile: NotificationProfile? = profiles.sortedDescending().filter { it.schedule.isCurrentlyActive(now, zoneId) }.firstOrNull { profile ->
      profile.schedule.startDateTime(localNow).toMillis(zoneId.toOffset()) > storeValues.manuallyDisabledAt
    }

    if (shouldSync && shouldClearManualOverride(manualProfile, scheduledProfile)) {
      REDExecutors.UNBOUNDED.execute {
        REDDatabase.recipients.markNeedsSync(Recipient.self().id)
        StorageSyncHelper.scheduleSyncForDataChange()
      }
    }

    if (manualProfile == null || scheduledProfile == null) {
      return manualProfile ?: scheduledProfile
    }

    return manualProfile
  }

  private fun shouldClearManualOverride(manualProfile: NotificationProfile?, scheduledProfile: NotificationProfile?): Boolean {
    val storeValues: NotificationProfileValues = REDStore.notificationProfile
    var shouldScheduleSync = false

    if (manualProfile == null && storeValues.manuallyEnabledProfile != 0L) {
      Log.i(TAG, "Clearing override: ${storeValues.manuallyEnabledProfile} and ${storeValues.manuallyEnabledUntil}")
      storeValues.manuallyEnabledProfile = 0
      storeValues.manuallyEnabledUntil = 0
      shouldScheduleSync = true
    }

    if (scheduledProfile != null && storeValues.manuallyDisabledAt != 0L) {
      Log.i(TAG, "Clearing override: ${storeValues.manuallyDisabledAt}")
      storeValues.manuallyDisabledAt = 0
      shouldScheduleSync = true
    }

    return shouldScheduleSync
  }

  fun getActiveProfileDescription(context: Context, profile: NotificationProfile, now: Long = System.currentTimeMillis()): String {
    val storeValues: NotificationProfileValues = REDStore.notificationProfile

    if (profile.id == storeValues.manuallyEnabledProfile) {
      if (storeValues.manuallyEnabledUntil.isForever()) {
        return context.getString(R.string.NotificationProfilesFragment__on)
      } else if (now < storeValues.manuallyEnabledUntil) {
        return context.getString(R.string.NotificationProfileSelection__on_until_s, storeValues.manuallyEnabledUntil.toLocalTime().formatHours(context))
      }
    }

    return context.getString(R.string.NotificationProfileSelection__on_until_s, profile.schedule.endTime().formatHours(context))
  }

  private fun Long.isForever(): Boolean {
    return this == Long.MAX_VALUE
  }
}
