package com.red.sovereign.megaphone

import com.red.sovereign.keyvalue.REDStore
import kotlin.time.Duration.Companion.days

/**
 * Calculates if the verify key megaphone should be shown based on the following rules
 * - 1 reminder within 14 days of creation, every 6 months after that
 * - Allow snooze only once, for a week
 * - Do not show within 1 week of showing the PIN reminder
 */
class VerifyBackupKeyReminderSchedule : MegaphoneSchedule {

  override fun shouldDisplay(seenCount: Int, lastSeen: Long, firstVisible: Long, currentTime: Long): Boolean {
    if (!REDStore.backup.areBackupsEnabled) {
      return false
    }

    if (REDStore.account.isLinkedDevice) {
      return false
    }

    val lastVerifiedTime = REDStore.backup.lastVerifyKeyTime
    val previouslySnoozed = REDStore.backup.hasSnoozedVerified
    val isFirstReminder = !REDStore.backup.hasVerifiedBefore

    val intervalTime = if (isFirstReminder) 14.days.inWholeMilliseconds else 183.days.inWholeMilliseconds

    val intervalHasPassed = currentTime > (lastVerifiedTime + intervalTime)
    val snoozeHasExpired = !previouslySnoozed || currentTime > (lastSeen + 7.days.inWholeMilliseconds)
    val hasShownPinReminderRecently = currentTime < REDStore.pin.lastReminderTime + 7.days.inWholeMilliseconds

    return intervalHasPassed && snoozeHasExpired && !hasShownPinReminderRecently
  }
}
