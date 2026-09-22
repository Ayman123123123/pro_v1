/*
 * Copyright 2023 RED Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.red.sovereign.apkupdate;


import android.content.Context;

import org.signal.core.util.logging.Log;
import com.red.sovereign.BuildConfig;
import com.red.sovereign.dependencies.AppDependencies;
import com.red.sovereign.jobs.ApkUpdateJob;
import com.red.sovereign.service.PersistentAlarmManagerListener;
import com.red.sovereign.util.Environment;
import com.red.sovereign.util.TextSecurePreferences;

import java.util.concurrent.TimeUnit;

public class ApkUpdateRefreshListener extends PersistentAlarmManagerListener {

  private static final String TAG = Log.tag(ApkUpdateRefreshListener.class);

  private static final long INTERVAL = Environment.IS_NIGHTLY ? TimeUnit.HOURS.toMillis(2) : TimeUnit.HOURS.toMillis(6);

  @Override
  protected long getNextScheduledExecutionTime(Context context) {
    return TextSecurePreferences.getUpdateApkRefreshTime(context);
  }

  @Override
  protected long onAlarm(Context context, long scheduledTime) {
    Log.i(TAG, "onAlarm...");

    if (scheduledTime != 0 && BuildConfig.MANAGES_APP_UPDATES) {
      Log.i(TAG, "Queueing APK update job...");
      AppDependencies.getJobManager().add(new ApkUpdateJob());
    }

    long newTime = System.currentTimeMillis() + INTERVAL;
    TextSecurePreferences.setUpdateApkRefreshTime(context, newTime);

    return newTime;
  }

  public static void schedule(Context context) {
    new ApkUpdateRefreshListener().onReceive(context, getScheduleIntent());
  }

}
