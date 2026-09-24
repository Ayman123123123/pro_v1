package com.red.sovereign.service;


import android.content.Context;

import com.red.sovereign.dependencies.AppDependencies;
import com.red.sovereign.jobs.DirectoryRefreshJob;
import com.red.sovereign.keyvalue.REDStore;
import com.red.sovereign.util.RemoteConfig;
import com.red.sovereign.util.TextSecurePreferences;

import java.util.concurrent.TimeUnit;

public class DirectoryRefreshListener extends PersistentAlarmManagerListener {

  @Override
  protected long getNextScheduledExecutionTime(Context context) {
    return TextSecurePreferences.getDirectoryRefreshTime(context);
  }

  @Override
  protected long onAlarm(Context context, long scheduledTime) {
    if (scheduledTime != 0 && REDStore.account().isRegistered()) {
      AppDependencies.getJobManager().add(new DirectoryRefreshJob(true));
    }

    long newTime;

    if (REDStore.misc().isCdsBlocked()) {
      newTime = Math.min(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(6),
                         REDStore.misc().getCdsBlockedUtil());
    } else {
      newTime = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(RemoteConfig.cdsRefreshIntervalSeconds());
      TextSecurePreferences.setDirectoryRefreshTime(context, newTime);
    }

    TextSecurePreferences.setDirectoryRefreshTime(context, newTime);

    return newTime;
  }

  public static void schedule(Context context) {
    new DirectoryRefreshListener().onReceive(context, getScheduleIntent());
  }
}
