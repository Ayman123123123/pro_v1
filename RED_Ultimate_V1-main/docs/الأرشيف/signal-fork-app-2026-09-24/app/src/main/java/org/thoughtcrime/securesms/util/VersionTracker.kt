package com.red.sovereign.util

import android.content.Context
import android.content.pm.PackageManager
import org.signal.core.util.logging.Log
import com.red.sovereign.BuildConfig
import com.red.sovereign.dependencies.AppDependencies
import com.red.sovereign.jobs.RefreshAttributesJob
import com.red.sovereign.jobs.RemoteConfigRefreshJob
import com.red.sovereign.jobs.RetrieveRemoteAnnouncementsJob
import com.red.sovereign.keyvalue.REDStore
import java.time.Duration

object VersionTracker {
  private val TAG = Log.tag(VersionTracker::class.java)

  @JvmStatic
  fun getLastSeenVersion(context: Context): Int {
    return TextSecurePreferences.getLastVersionCode(context)
  }

  @JvmStatic
  fun updateLastSeenVersion(context: Context) {
    val currentVersionCode = BuildConfig.VERSION_CODE
    val lastVersionCode = TextSecurePreferences.getLastVersionCode(context)

    if (currentVersionCode != lastVersionCode) {
      Log.i(TAG, "Upgraded from $lastVersionCode to $currentVersionCode. Clearing client deprecation.", true)
      REDStore.misc.isClientDeprecated = false
      REDStore.remoteConfig.eTag = ""
      val jobChain = listOf(RemoteConfigRefreshJob(), RefreshAttributesJob())
      AppDependencies.jobManager.startChain(jobChain).enqueue()
      RetrieveRemoteAnnouncementsJob.enqueue(true)
      LocalMetrics.getInstance().clear()
    }

    TextSecurePreferences.setLastVersionCode(context, currentVersionCode)
  }

  @JvmStatic
  fun getDaysSinceFirstInstalled(context: Context): Long {
    return try {
      val installTimestamp = context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
      Duration.ofMillis(System.currentTimeMillis() - installTimestamp).toDays()
    } catch (e: PackageManager.NameNotFoundException) {
      Log.w(TAG, e)
      0
    }
  }
}
