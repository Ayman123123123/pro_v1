package com.red.sovereign

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import com.red.sovereign.calls.IncomingCallUiPolicy
import com.red.sovereign.core.ServerEndpoint
import com.red.sovereign.settings.SettingsRuntime

/**
 * ÙŠÙˆÙ†Ø³ Application â€” Ù†Ù‚Ø·Ø© Ø§Ù„Ø¯Ø®ÙˆÙ„ Ø§Ù„Ù‚Ø§Ù†ÙˆÙ†ÙŠØ© Ù„Ù„ØªØ·Ø¨ÙŠÙ‚
 * ØªÙ‡ÙŠØ¦ ÙƒÙ„ Ø§Ù„Ø£Ù†Ø¸Ù…Ø© Ù‚Ø¨Ù„ Ø£ÙˆÙ„ Activity
 */
class YounesApplication : Application() {
    @Volatile private var resumedActivities = 0

    override fun onCreate() {
        super.onCreate()
        instance = this
        // SQLCipher 4.17 requires explicit native initialization before Room
        // creates SupportOpenHelperFactory. Fail early with a useful cause rather
        // than crashing later on the first encrypted database access.
        runCatching { System.loadLibrary("sqlcipher") }
            .getOrElse { throw IllegalStateException("SQLCipher native library is unavailable for this ABI", it) }
        // Ø­Ù…Ù‘Ù„ Ø³ÙŠØ§Ø³Ø© SPKI Ø§Ù„Ù…Ø´ÙØ±Ø© Ù‚Ø¨Ù„ Ø¨Ù†Ø§Ø¡ Ø£ÙˆÙ„ OkHttp/WebSocket client.
        com.red.sovereign.security.CertificatePinner.loadPins(this)
        if (BuildConfig.RED_TLS_PINS.isNotBlank()) {
            com.red.sovereign.security.CertificatePinner.provisionPins(this, BuildConfig.RED_TLS_PINS)
        }
        // ØªÙ‡ÙŠØ¦Ø© Ø¹Ù†ÙˆØ§Ù† Ø§Ù„Ø®Ø§Ø¯Ù… (Ù…Ù† BuildConfig + Ø§ÙƒØªØ´Ø§Ù Ø§Ù„Ø´Ø¨ÙƒØ©)
        ServerEndpoint.initialize(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) { resumedActivities++ }
            override fun onActivityPaused(activity: Activity) { resumedActivities = (resumedActivities - 1).coerceAtLeast(0) }
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        // ØªÙ‡ÙŠØ¦Ø© Ø«ÙŠÙ… ÙˆØ­Ø¬Ù… Ø§Ù„Ø®Ø·
        SettingsRuntime.initialize(this)
        // ØªØ¬Ø¯ÙŠØ¯ Ø§Ù„ØªÙˆÙƒÙ† ÙÙŠ Ø§Ù„Ø®Ù„ÙÙŠØ© Ø­ØªÙ‰ Ù„Ùˆ Ù„Ù… ÙŠÙØªØ­ Ø§Ù„ØªØ·Ø¨ÙŠÙ‚ 30 ÙŠÙˆÙ…Ø§Ù‹ + ÙØ­Øµ Ù…Ø²Ø§Ù…Ù†Ø© Ø§Ø­ØªÙŠØ§Ø· 60s
        try {
            val workManager = androidx.work.WorkManager.getInstance(this)
            val constraints = androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build()
            try {
                val refreshWork = androidx.work.PeriodicWorkRequestBuilder<com.red.sovereign.workers.AuthRefreshWorker>(7, java.util.concurrent.TimeUnit.DAYS)
                    .setConstraints(constraints)
                    .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.MINUTES)
                    .build()
                workManager.enqueueUniquePeriodicWork("auth_refresh", androidx.work.ExistingPeriodicWorkPolicy.KEEP, refreshWork)
            } catch (_: Exception) {
                val refreshWork2 = androidx.work.PeriodicWorkRequestBuilder<com.red.sovereign.workers.AuthRefreshWorker>(java.time.Duration.ofDays(7))
                    .setConstraints(constraints)
                    .build()
                workManager.enqueueUniquePeriodicWork("auth_refresh", androidx.work.ExistingPeriodicWorkPolicy.KEEP, refreshWork2)
            }
            val syncConstraints = androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build()
            try {
                val syncWork = androidx.work.PeriodicWorkRequestBuilder<com.red.sovereign.workers.AuthRefreshWorker>(15, java.util.concurrent.TimeUnit.MINUTES)
                    .setConstraints(syncConstraints)
                    .build()
                workManager.enqueueUniquePeriodicWork("sync_poll", androidx.work.ExistingPeriodicWorkPolicy.KEEP, syncWork)
            } catch (_: Exception) {
                val syncWork2 = androidx.work.PeriodicWorkRequestBuilder<com.red.sovereign.workers.AuthRefreshWorker>(java.time.Duration.ofMinutes(15))
                    .setConstraints(syncConstraints)
                    .build()
                workManager.enqueueUniquePeriodicWork("sync_poll", androidx.work.ExistingPeriodicWorkPolicy.KEEP, syncWork2)
            }
        } catch (_: Exception) {}
        // Ù‚Ù†ÙˆØ§Øª Ø§Ù„Ø¥Ø´Ø¹Ø§Ø±Ø§Øª â€” Ù…Ø·Ù„ÙˆØ¨Ø© Ù„Ù€ RedConnectionService Ùˆ YounesCallService
        createNotificationChannels()
        // تنظيف دوري للقصص المنتهية — بدونه تتراكم صفوفها في القاعدة بلا حد
        com.red.sovereign.core.workers.StoryCleanupWorker.enqueue(this)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        listOf(
            NotificationChannel("red_messages", getString(R.string.channel_messages_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = getString(R.string.channel_messages_desc)
            },
            NotificationChannel("red_calls", getString(R.string.channel_calls_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = getString(R.string.channel_calls_desc)
            },
            // Ù‚Ù†Ø§Ø© Ø§Ù„Ù…ÙƒØ§Ù„Ù…Ø§Øª Ø§Ù„ÙˆØ§Ø±Ø¯Ø© â€” Ø£ÙˆÙ„ÙˆÙŠØ© Ù‚ØµÙˆÙ‰ Ù…Ø¹ Ø±Ù†ÙŠÙ† (Ø¹Ù„Ù‰ Ø¹ÙƒØ³ Ù‚Ù†Ø§Ø© Ø§Ù„Ù…ÙƒØ§Ù„Ù…Ø© Ø§Ù„Ø¹Ø§Ø¯ÙŠØ©)
            NotificationChannel("red_calls_incoming", getString(R.string.channel_calls_incoming_name), NotificationManager.IMPORTANCE_MAX).apply {
                description = getString(R.string.channel_calls_incoming_desc)
                enableVibration(true)
                setBypassDnd(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            },
            NotificationChannel("red_service", getString(R.string.channel_service_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.channel_service_desc)
            }
        ).forEach { manager.createNotificationChannel(it) }
    }

    companion object {
        lateinit var instance: YounesApplication
            private set

        fun shouldLaunchIncomingActivity(): Boolean =
            IncomingCallUiPolicy.shouldLaunchIncomingActivity(instance.resumedActivities > 0)
    }
}
