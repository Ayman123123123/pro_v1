package com.red.sovereign.service;


import android.content.Context;

import androidx.annotation.NonNull;

import com.red.sovereign.jobs.LocalBackupJob;
import com.red.sovereign.keyvalue.REDStore;
import com.red.sovereign.util.JavaTimeExtensionsKt;
import com.red.sovereign.util.TextSecurePreferences;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class LocalBackupListener extends PersistentAlarmManagerListener {

  private static final int BACKUP_JITTER_WINDOW_SECONDS = Math.toIntExact(TimeUnit.MINUTES.toSeconds(10));

  @Override
  protected boolean shouldScheduleExact() {
    return true;
  }

  @Override
  protected long getNextScheduledExecutionTime(Context context) {
    return TextSecurePreferences.getNextBackupTime(context);
  }

  @Override
  protected long onAlarm(Context context, long scheduledTime) {
    if (REDStore.settings().isBackupEnabled()) {
      LocalBackupJob.enqueue(false);
    }

    if (REDStore.backup().getNewLocalBackupsEnabled()) {
      LocalBackupJob.enqueueArchive(REDStore.settings().isBackupEnabled());
    }

    return setNextBackupTimeToIntervalFromNow(context);
  }

  public static void schedule(Context context) {
    if (REDStore.settings().isBackupEnabled() || REDStore.backup().getNewLocalBackupsEnabled()) {
      new LocalBackupListener().onReceive(context, getScheduleIntent());
    }
  }

  public static long setNextBackupTimeToIntervalFromNow(@NonNull Context context) {
    LocalDateTime now    = LocalDateTime.now();
    int           hour   = REDStore.settings().getBackupHour();
    int           minute = REDStore.settings().getBackupMinute();
    LocalDateTime next   = MessageBackupListener.getNextDailyBackupTimeFromNowWithJitter(now, hour, minute, BACKUP_JITTER_WINDOW_SECONDS, new Random());

    long nextTime = JavaTimeExtensionsKt.toMillis(next);

    TextSecurePreferences.setNextBackupTime(context, nextTime);

    return nextTime;
  }
}
