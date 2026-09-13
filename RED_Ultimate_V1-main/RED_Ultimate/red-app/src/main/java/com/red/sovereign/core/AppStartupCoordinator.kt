package com.red.sovereign.core

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.red.sovereign.auth.AuthState
import com.red.sovereign.auth.AuthViewModel
import com.red.sovereign.calls.PstnIncomingCallCoordinator
import com.red.sovereign.calls.VoipPushRegistrar
import com.red.sovereign.calls.YounesCallService
import com.red.sovereign.core.network.SovereignNotificationRouter

/**
 * منسق بداية التطبيق — يستخرج المنطق الثقيل من MainActivity لضمان
 * استقرار البداية وتسهيل صيانتها.
 */
class AppStartupCoordinator(private val application: Application) {

    private var pstnCoordinator: PstnIncomingCallCoordinator? = null

    /**
     * تشغيل خدمات النظام الأساسية عند تسجيل الدخول.
     */
    fun onAuthenticated(context: Context, authViewModel: AuthViewModel) {
        Log.i("AppStartup", "Initializing core services for authenticated user")

        // 1. بدء خدمات الاتصال والويب سيكيت
        runCatching { RedConnectionService.start(context) }
        runCatching { YounesCallService.listen(context) }

        // 2. بدء منسق مكالمات PSTN الواردة
        runCatching {
            val coordinator = pstnCoordinator
                ?: PstnIncomingCallCoordinator(application).also { pstnCoordinator = it }
            coordinator.start()
        }.onFailure { Log.w("AppStartup", "PstnCoordinator start failed: ${it.message}") }

        // 3. تسجيل دفع VoIP
        runCatching { VoipPushRegistrar.register(context) }

        // 4. بدء راوتر الإشعارات السيادي (لمنع التأخير في الخلفية وضمان وصول الـ VoIP)
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

        // 5. تفعيل مراقبة الجودة والاتصال الذكي
        RedQualityManager.initialize(context)

        // 6. تحديث أولي لصلاحيات PSTN
        authViewModel.refreshPstnEntitlement()
    }

    /**
     * إيقاف كافة الخدمات عند تسجيل الخروج.
     */
    fun onLoggedOut(context: Context) {
        Log.i("AppStartup", "Stopping core services on logout")
        runCatching { RedConnectionService.stop(context) }
        runCatching { YounesCallService.stop(context) }
        runCatching { context.stopService(Intent(context, SovereignNotificationRouter::class.java)) }
        pstnCoordinator?.stop()
    }

    fun onDestroy() {
        pstnCoordinator?.destroy()
        pstnCoordinator = null
    }
}
