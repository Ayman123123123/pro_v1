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
        // تهيئة عنوان الخادم (من BuildConfig + اكتشاف الشبكة)
        ServerEndpoint.initialize(this)
        // تهيئة ثيم وحجم الخط
        SettingsRuntime.initialize(this)
        // قنوات الإشعارات — مطلوبة لـ RedConnectionService و YounesCallService
        createNotificationChannels()
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
                setSound(null, null)
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
