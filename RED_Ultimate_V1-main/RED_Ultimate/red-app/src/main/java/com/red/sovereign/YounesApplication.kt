package com.red.sovereign

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.red.sovereign.core.ServerEndpoint
import com.red.sovereign.settings.SettingsRuntime

/**
 * يونس Application — نقطة الدخول القانونية للتطبيق
 * تهيئ كل الأنظمة قبل أول Activity
 */
class YounesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        // SQLCipher 4.17 requires explicit native initialization before Room
        // creates SupportOpenHelperFactory. Fail early with a useful cause rather
        // than crashing later on the first encrypted database access.
        runCatching { System.loadLibrary("sqlcipher") }
            .getOrElse { throw IllegalStateException("SQLCipher native library is unavailable for this ABI", it) }
        // حمّل سياسة SPKI المشفرة قبل بناء أول OkHttp/WebSocket client.
        com.red.sovereign.security.CertificatePinner.loadPins(this)
        if (BuildConfig.RED_TLS_PINS.isNotBlank()) {
            com.red.sovereign.security.CertificatePinner.provisionPins(this, BuildConfig.RED_TLS_PINS)
        }
        // تهيئة عنوان الخادم (من BuildConfig + اكتشاف الشبكة)
        ServerEndpoint.initialize(this)
        // تهيئة ثيم وحجم الخط
        SettingsRuntime.initialize(this)
        // قنوات الإشعارات — مطلوبة لـ RedConnectionService و YounesCallService
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
            // قناة المكالمات الواردة — أولوية قصوى مع رنين (على عكس قناة المكالمة العادية)
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
    }
}
