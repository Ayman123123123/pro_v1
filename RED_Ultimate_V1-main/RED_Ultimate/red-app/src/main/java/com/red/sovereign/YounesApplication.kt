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
            NotificationChannel("red_messages", "رسائل يونس", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "إشعارات الرسائل المشفرة"
            },
            NotificationChannel("red_calls", "مكالمات يونس", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "إشعارات المكالمات الواردة"
                setSound(null, null)
            },
            NotificationChannel("red_service", "خدمة يونس", NotificationManager.IMPORTANCE_LOW).apply {
                description = "الاتصال الدائم المشفر"
            }
        ).forEach { manager.createNotificationChannel(it) }
    }

    companion object {
        lateinit var instance: YounesApplication
            private set
    }
}
