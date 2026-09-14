package com.red.sovereign.core

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.red.sovereign.auth.AuthState
import com.red.sovereign.auth.AuthViewModel
import com.red.sovereign.calls.VoipPushRegistrar
import com.red.sovereign.calls.YounesCallService
import com.red.sovereign.core.network.SovereignNotificationRouter

/**
 * منسق بداية التطبيق — يستخرج المنطق الثقيل من MainActivity لضمان
 * استقرار البداية وتسهيل صيانتها.
 */
class AppStartupCoordinator(private val application: Application) {

    /**
     * تشغيل خدمات النظام الأساسية عند تسجيل الدخول.
     */
    fun onAuthenticated(context: Context, authViewModel: AuthViewModel) {
        Log.i("AppStartup", "Initializing core services for authenticated user")

        // 1. بدء خدمات الاتصال والويب سيكيت
        runCatching { RedConnectionService.start(context) }
        runCatching { YounesCallService.listen(context) }

        // 2. تسجيل دفع VoIP
        runCatching { VoipPushRegistrar.register(context) }

        // 3. بدء راوتر الإشعارات السيادي (لمنع التأخير في الخلفية وضمان وصول الـ VoIP)
        val routerIntent = Intent(context, SovereignNotificationRouter::class.java)
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(routerIntent)
            } else {
                context.startService(routerIntent)
            }
        } catch (e: Exception) {
            Log.w("AppStartup", "NotificationRouter start failed: ${e.message}")
        }

        // 4. تفعيل مراقبة الجودة والاتصال الذكي
        RedQualityManager.initialize(context)

    }

    /**
     * إيقاف كافة الخدمات عند تسجيل الخروج.
     */
    fun onLoggedOut(context: Context) {
        Log.i("AppStartup", "Stopping core services on logout")
        runCatching { RedConnectionService.stop(context) }
        runCatching { YounesCallService.stop(context) }
        runCatching { context.stopService(Intent(context, SovereignNotificationRouter::class.java)) }
    }

    fun onDestroy() {
    }
}
