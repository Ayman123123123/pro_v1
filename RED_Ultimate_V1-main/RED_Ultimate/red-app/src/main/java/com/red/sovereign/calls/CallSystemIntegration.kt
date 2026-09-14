package com.red.sovereign.calls

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.core.sync.CallLogSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * مدير تكامل نظام المكالمات — Call System Integration Manager
 *
 * يوحّد جميع مكونات نظام المكالمات (WebRTC, RED, Push Notifications, Telecom)
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
        // تصحيح: بوّابة التهيئة الحقيقية هي WebRtcBootstrap.ensure (تعمل مرة
        // واحدة لكل عملية) — لا توجد دالة initialize إطلاقاً.
        WebRtcBootstrap.ensure(context)

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

        // 4. بدء خدمةForeground للمكالمات RED
        // تصحيح: PstnCallService غير موجود؛ الخدمة الحقيقية هي
        // PstnCallForegroundService، ومصدر "هل RED متاح" هو صلاحية الحساب
        // المحفوظة في TokenStore.pstnEnabled (تُحدّث من /api/auth/me).
        // الخدمة تُشغَّل عبر مصنعها الحقيقي start(context, number) لأن
        // ACTION_START يتوقع extra باسم "number" لبناء الإشعار.
        val tokens = TokenStore(context)
        if (tokens.pstnEnabled) {
            PstnCallForegroundService.start(context, tokens.pstnNumber.orEmpty())
        }

        // 5. تهيئة مدير الإشعارات
        // تصحيح: لا يوجد NotificationHelper في المشروع — إنشاء القناة (الخطوة 2)
        // هو كل ما يحتاجه نظام المكالمات فعلياً، وقنوات باقي التطبيق تُنشأ في
        // YounesApplication.createNotificationChannels.
    }

    /**
     * إكمال التهيئة بعد اكتمال تسجيل المستخدم
     *
     * تصحيح: اسم دالة التسجيل الحقيقي هو [VoipPushRegistrar.register] وهي
     * تقرأ الهوية من TokenStore بنفسها (لا تأخذ userId)، وجدولة المزامنة
     * تعيش في [CallLogSyncScheduler.schedulePeriodicSync] لا في الـ Worker،
     * وهي دالة عادية غير suspend.
     */
    fun completeInitialization(context: Context, userId: String) {
        // تسجيل مستمع الأحداث
        VoipPushRegistrar.register(context)

        // مزامنة سجل المكالمات
        scope.launch {
            CallLogSyncScheduler.schedulePeriodicSync(context)
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
     *
     * تصحيح: كان يعتمد على NotificationHelper غير الموجود — الفحص الحقيقي
     * متاح مباشرةً في androidx عبر NotificationManagerCompat.
     */
    fun areNotificationsEnabled(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
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
