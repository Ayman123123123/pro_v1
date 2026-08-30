package com.red.sovereign.calls

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.red.sovereign.core.notifications.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * مدير تكامل نظام المكالمات — Call System Integration Manager
 *
 * يوحّد جميع مكونات نظام المكالمات (WebRTC, PSTN, Push Notifications, Telecom)
 * في واجهة واحدة مركزية. يتعامل مع:
 * - تهيئة محرك WebRTC
 * - تسجيل إشعارات FCM للمكالمات
 * - ربط خدماتTelecom (Android ConnectionService)
 * - مزامنة سجل المكالمات مع الخادم
 * - إدارة حالة التطبيق أثناء المكالمات (foreground service)
 */
object CallSystemIntegration {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val _callEvents = MutableSharedFlow<CallSystemEvent>(extraBufferCapacity = 64)
    val callEvents: SharedFlow<CallSystemEvent> = _callEvents.asSharedFlow()

    /**
     * تهيئة نظام المكالمات بالكامل
     * يجب استدعاؤها مرة واحدة عند بدء تشغيل التطبيق
     */
    fun initialize(context: Context) {
        // 1. تهيئة WebRTC
        WebRtcBootstrap.initialize(context)

        // 2. إنشاء قناة الإشعارات
        CallNotificationManager.createNotificationChannel(context)

        // 3. تسجيل مستقبل إشعارات الفتح
        ContextCompat.registerReceiver(
            context,
            CallBootReceiver(),
            IntentFilter("android.intent.action.BOOT_COMPLETED"),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.RECEIVER_NOT_EXPORTED
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                0
            }
        )

        // 4. بدء خدمةForeground للمكالمات PSTN
        if (PstnCallService.isPstnAvailable(context)) {
            val intent = Intent(context, PstnCallForegroundService::class.java).apply {
                action = PstnCallForegroundService.ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        // 5. تهيئة مدير الإشعارات
        NotificationHelper.init(context)
    }

    /**
     * إكمال التهيئة بعد اكتمال تسجيل المستخدم
     */
    fun completeInitialization(context: Context, userId: String) {
        // تسجيل مستمع الأحداث
        VoipPushRegistrar.registerForVoipNotifications(context, userId)

        // مزامنة سجل المكالمات
        scope.launch {
            CallLogSyncWorker.scheduleSync(context)
        }
    }

    /**
     * إنهاء نظام المكالمات (عند الخروج)
     */
    fun shutdown(context: Context) {
        // إيقاف خدماتForeground
        val intent = Intent(context, PstnCallForegroundService::class.java).apply {
            action = PstnCallForegroundService.ACTION_STOP
        }
        context.startService(intent)
    }

    /**
     * إرسال حدث نظام المكالمات
     */
    suspend fun emitEvent(event: CallSystemEvent) {
        _callEvents.emit(event)
    }

    /**
     * التحقق من صلاحية الإشعارات
     */
    fun areNotificationsEnabled(context: Context): Boolean {
        return NotificationHelper.areNotificationsEnabled(context)
    }

    /**
     * طلب صلاحية الإشعارات (Android 13+)
     */
    fun requestNotificationPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Use ActivityResultContracts.RequestNotificationPermission
        }
    }
}

/**
 * أحداث نظام المكالمات
 */
sealed class CallSystemEvent {
    data class CallStarted(val callId: String, val peer: String, val isVideo: Boolean) : CallSystemEvent()
    data class CallEnded(val callId: String, val peer: String, val durationMs: Long) : CallSystemEvent()
    data class CallWaiting(val incomingCallId: String, val peer: String) : CallSystemEvent()
    data class NetworkQualityChanged(val quality: NetworkQuality) : CallSystemEvent()
    data class RecordingStarted(val callId: String) : CallSystemEvent()
    data class RecordingStopped(val callId: String, val recordingPath: String) : CallSystemEvent()
    data class ParticipantJoined(val callId: String, val participantId: String, val name: String) : CallSystemEvent()
    data class ParticipantLeft(val callId: String, val participantId: String) : CallSystemEvent()
}
