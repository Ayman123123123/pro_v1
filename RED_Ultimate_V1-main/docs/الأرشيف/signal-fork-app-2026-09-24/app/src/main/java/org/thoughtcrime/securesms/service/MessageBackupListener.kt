/*
 * Copyright 2024 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.service

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.red.sovereign.jobs.BackupMessagesJob
import com.red.sovereign.keyvalue.REDStore
import com.red.sovereign.util.toMillis
import com.red.sovereign.util.toOffset
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Random
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class MessageBackupListener : PersistentAlarmManagerListener() {
  override fun shouldScheduleExact(): Boolean {
    return true
  }

  @VisibleForTesting
  public override fun getNextScheduledExecutionTime(context: Context): Long {
    val nextTime = REDStore.backup.nextBackupTime
    return if (nextTime < 0 || nextTime > (System.currentTimeMillis() + 2.days.inWholeMilliseconds)) {
      setNextBackupTimeToIntervalFromNow()
    } else {
      nextTime
    }
  }

  override fun onAlarm(context: Context, scheduledTime: Long): Long {
    if (REDStore.backup.areBackupsEnabled) {
      BackupMessagesJob.enqueue()
    }
    return setNextBackupTimeToIntervalFromNow()
  }

  companion object {
    private val BACKUP_JITTER_WINDOW_SECONDS = 10.minutes.inWholeSeconds.toInt()

    @JvmStatic
    fun schedule(context: Context?) {
      if (REDStore.backup.areBackupsEnabled) {
        MessageBackupListener().onReceive(context, getScheduleIntent())
      }
    }

    @VisibleForTesting
    @JvmStatic
    fun getNextDailyBackupTimeFromNowWithJitter(now: LocalDateTime, hour: Int, minute: Int, maxJitterSeconds: Int, randomSource: Random = Random()): LocalDateTime {
      var next = now.withHour(hour).withMinute(minute).withSecond(0)

      val endOfJitterWindowForNow = now.plusSeconds(maxJitterSeconds.toLong() / 2)
      while (!endOfJitterWindowForNow.isBefore(next)) {
        next = next.plusDays(1)
      }

      val jitter = randomSource.nextInt(maxJitterSeconds) - maxJitterSeconds / 2
      return next.plusSeconds(jitter.toLong())
    }

    @VisibleForTesting
    fun setNextBackupTimeToIntervalFromNow(zoneId: ZoneId = ZoneId.systemDefault(), now: LocalDateTime = LocalDateTime.now(zoneId), maxJitterSeconds: Int = BACKUP_JITTER_WINDOW_SECONDS, randomSource: Random = Random()): Long {
      val hour = REDStore.settings.signalBackupHour
      val minute = REDStore.settings.signalBackupMinute
      val next = getNextDailyBackupTimeFromNowWithJitter(now, hour, minute, maxJitterSeconds, randomSource)
      val nextTime = next.toMillis(zoneId.toOffset())
      REDStore.backup.nextBackupTime = nextTime
      return nextTime
    }
  }
}
