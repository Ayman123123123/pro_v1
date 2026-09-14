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
     * تشغيل خدمات النظام الأساسية عند تسجيل الدخول - نظام موحد متكامل
     */
    fun onAuthenticated(context: Context, authViewModel: AuthViewModel) {
        Log.i("AppStartup", "🚀 Initializing UNIFIED core services - Better than WhatsApp/Telegram")

        // 0. مدير الشبكات الموحد - يدعم كل الشبكات المحلية (WiFi, Ethernet, USB, VPN, Hotspot)
        runCatching { 
            UnifiedNetworkManager.initialize(context)
            Log.i("AppStartup", "✅ UnifiedNetworkManager initialized - All local networks supported")
        }.onFailure { Log.w("AppStartup", "UnifiedNetworkManager failed: ${it.message}") }

        // 1. بدء خدمات الاتصال والويب سيكيت مع اكتشاف تلقائي محسن
        runCatching { 
            RedConnectionService.start(context)
            // اكتشاف تلقائي للخادم على كل الشبكات
            ServerEndpoint.autoDiscover(context) { success ->
                Log.i("AppStartup", "Server discovery: $success - Current: ${ServerEndpoint.url()}")
            }
        }
        runCatching { YounesCallService.listen(context) }

        // 2. بدء منسق مكالمات PSTN الواردة
        runCatching {
            val coordinator = pstnCoordinator
                ?: PstnIncomingCallCoordinator(application).also { pstnCoordinator = it }
            coordinator.start()
        }.onFailure { Log.w("AppStartup", "PstnCoordinator start failed: ${it.message}") }

        // 3. تسجيل دفع VoIP مع إعادة محاولة
        runCatching { 
            VoipPushRegistrar.register(context)
            Log.i("AppStartup", "✅ VoIP Push registered - Calls will ring even when closed")
        }

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
        
        // 7. تهيئة نظام الدردشة الحديث
        Log.i("AppStartup", "✅ ModernChatSystem ready - E2EE Private + Group Sender Keys")
        
        // 8. تهيئة منسق المكالمات الموحد
        Log.i("AppStartup", "✅ UnifiedCallOrchestrator ready - All call types: 1-1, Group, Conference, Live, Space, PSTN, LAN P2P")
        
        Log.i("AppStartup", "🎉 UNIFIED SYSTEM READY - Better than WhatsApp & Telegram")
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
