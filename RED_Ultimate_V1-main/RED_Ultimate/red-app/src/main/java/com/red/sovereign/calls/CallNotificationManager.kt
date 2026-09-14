package com.red.sovereign.calls

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.red.sovereign.R
import kotlinx.coroutines.launch
import com.red.sovereign.settings.DndPolicy
import com.red.sovereign.settings.SettingsRuntime

/**
 * مدير إشعارات المكالمات — Call Notification Manager
 *
 * يدير عرض الإشعارات للمكالمات الواردة والصادرة والمكتملة.
 * يدعم:
 * - قناة إشعارات مخصصة للمكالمات
 * - أزرار تحكم (قبول/رفض/كتم)
 * - إشعارات مكالمات PSTN
 * - إشعارات المكالمات الجماعية
 * - إشعارات المؤتمر
 * - إشعارات البث المباشر
 */
object CallNotificationManager {

    /**
     * تصحيح: كانت القناة `red_call_channel` وهي قناة لا يُنشئها أحد فعلياً —
     * الدالة [createNotificationChannel] تُستدعى فقط من
     * [CallSystemIntegration.initialize] وهي بدورها غير مُستدعاة من أي مكان في
     * التطبيق، فكل إشعار هنا كان يُرفَض بصمت على Android 8+. القناتان أدناه هما
     * القناتان الحقيقيتان المُنشأتان دائماً في
     * `YounesApplication.createNotificationChannels` وتستخدمهما [YounesCallService].
     */
    const val CHANNEL_ID = "red_calls"
    const val CHANNEL_ID_INCOMING = "red_calls_incoming"
    const val CHANNEL_NAME = "إشعارات المكالمات"
    const val CHANNEL_DESCRIPTION = "إشعارات المكالمات الواردة والصادرة"

    // إصلاح تسرب/تصادم: notificationId++ غير ذري — استدعاءان متزامنان قد
    // يُنتجان نفس المعرف فيُسقط إشعار قبول/رفض. AtomicInteger يضمن التفرد.
    private val notificationIdSeq = java.util.concurrent.atomic.AtomicInteger(1000)

    fun isSuppressedByDnd(isFavorite: Boolean = false, isRepeatCaller: Boolean = false): Boolean =
        DndPolicy.shouldSuppress(SettingsRuntime.current, isFavorite, isRepeatCaller)

    fun shouldShowIncoming(isFavorite: Boolean = false, isRepeatCaller: Boolean = false): Boolean {
        if (!SettingsRuntime.current.callNotifications) return false
        return !isSuppressedByDnd(isFavorite, isRepeatCaller)
    }

    fun applyDndFilter(context: Context): Boolean = DndPolicy.applySystemFilter(context)

    /**
     * ضمان وجود قناتَي المكالمات.
     *
     * لا نعيد إنشاء قناة موجودة (نفس نمط [PstnCallForegroundService] و
     * [PhoneStateReceiver]) حتى لا نغيّر الاسم/الوصف الذي أنشأه التطبيق ويراه
     * المستخدم؛ الإنشاء هنا شبكة أمان فقط إن استُدعيت الدالة قبل تهيئة التطبيق.
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = CHANNEL_DESCRIPTION
                        enableLights(true)
                        enableVibration(true)
                    }
                )
            }
            // قناة المكالمات الواردة — أولوية قصوى مع رنين (نفس إعدادات YounesApplication)
            if (nm.getNotificationChannel(CHANNEL_ID_INCOMING) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID_INCOMING,
                        context.getString(R.string.channel_calls_incoming_name),
                        NotificationManager.IMPORTANCE_MAX
                    ).apply {
                        description = context.getString(R.string.channel_calls_incoming_desc)
                        enableVibration(true)
                        setBypassDnd(true)
                        lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    }
                )
            }
        }
    }

    /**
     * إنشاء إشعار لمكالمة واردة
     */
    fun buildIncomingCallNotification(
        context: Context,
        peer: String,
        isVideo: Boolean,
        callType: String,
        callId: String,
        myUserId: String,
        hostId: String? = null
    ): NotificationCompat.Builder {
        // LEGENDARY FIX: معرفات فريدة لكل callId+إجراء (كان contentIntent بـ requestCode=0 ثابت يتصادم بين مكالمتين + أيقونتا قبول/رفض متطابقتين)
        val codeBase = callId.hashCode()
        val acceptCode = codeBase xor 0xA11CE7
        val rejectCode = codeBase xor 0x81EC77
        val contentCode = codeBase xor 0xC07E17
        val acceptIntent = CallNotificationActionReceiver.receiverIntent(
            context,
            CallNotificationActionReceiver.ACTION_ACCEPT,
            callType,
            acceptCode,
            callId,
            myUserId,
            hostId ?: "",
            isVideo
        )
        val rejectIntent = CallNotificationActionReceiver.receiverIntent(
            context,
            CallNotificationActionReceiver.ACTION_REJECT,
            callType,
            rejectCode,
            callId,
            myUserId
        )
        // UX يتفوق على واتساب: fullScreenIntent للقفز فوق القفل + رد سريع
        // مدمج + لون العلامة + اهتزاز عبر القناة (red_calls_incoming).
        val fullScreen = PendingIntent.getActivity(
            context, codeBase,
            Intent(context, com.red.sovereign.MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("open_call_id", callId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID_INCOMING)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle("مكالمة واردة من $peer")
            .setContentText(if (isVideo) "مكالمة فيديو • انقر للرد" else "مكالمة صوتية • انقر للرد")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(PendingIntent.getActivity(context, contentCode,
                Intent(context, com.red.sovereign.MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("open_call_id", callId)
                }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            .setFullScreenIntent(fullScreen, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(0xFF00C98C.toInt())
            .setTimeoutAfter(45_000) // LEGENDARY: مهلة رنين 45s موحدة (كانت بلا مهلة = شبح رنين)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "رفض",
                rejectIntent
            )
            .addAction(
                android.R.drawable.ic_menu_call,
                "قبول",
                acceptIntent
            )
    }

    /**
     * إنشاء إشعار لمكالمة صادرة
     */
    fun buildOutgoingCallNotification(
        context: Context,
        peer: String,
        isVideo: Boolean,
        callType: String,
        callId: String,
        myUserId: String
    ): NotificationCompat.Builder {
        val endIntent = CallNotificationActionReceiver.receiverIntent(
            context,
            CallNotificationActionReceiver.ACTION_END,
            callType,
            notificationIdSeq.getAndIncrement(),
            callId,
            myUserId
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle("جارٍ الاتصال بـ $peer")
            .setContentText(if (isVideo) "مكالمة فيديو" else "مكالمة صوتية")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(0xFF00C98C.toInt())
            .addAction(
                android.R.drawable.ic_menu_call,
                "إنهاء",
                endIntent
            )
    }

    /**
     * إنشاء إشعار لمكالمة نشطة
     */
    fun buildActiveCallNotification(
        context: Context,
        peer: String,
        isVideo: Boolean,
        isMuted: Boolean,
        isSpeaker: Boolean,
        durationSeconds: Long,
        callType: String,
        callId: String,
        myUserId: String
    ): NotificationCompat.Builder {
        val endIntent = CallNotificationActionReceiver.receiverIntent(
            context,
            CallNotificationActionReceiver.ACTION_END,
            callType,
            notificationIdSeq.getAndIncrement(),
            callId,
            myUserId
        )
        val micIntent = CallNotificationActionReceiver.receiverIntent(
            context,
            CallNotificationActionReceiver.ACTION_TOGGLE_MIC,
            callType,
            notificationIdSeq.getAndIncrement(),
            callId,
            myUserId
        )
        val speakerIntent = CallNotificationActionReceiver.receiverIntent(
            context,
            CallNotificationActionReceiver.ACTION_TOGGLE_SPEAKER,
            callType,
            notificationIdSeq.getAndIncrement(),
            callId,
            myUserId
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_call)
            .setContentTitle("مكالمة نشطة مع $peer")
            .setContentText(formatDuration(durationSeconds))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .addAction(
                if (isMuted) android.R.drawable.ic_menu_close_clear_cancel else android.R.drawable.ic_menu_call,
                if (isMuted) "تفعيل الميكروفون" else "كتم",
                micIntent
            )
            .addAction(
                if (isSpeaker) android.R.drawable.ic_menu_mylocation else android.R.drawable.ic_menu_compass,
                if (isSpeaker) "إيقاف المكبر" else "تفعيل المكبر",
                speakerIntent
            )
            .addAction(
                android.R.drawable.ic_menu_call,
                "إنهاء",
                endIntent
            )
    }

    /**
     * تنسيق المدة للعرض
     */
    private fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    /**
     * إزالة إشعار المكالمات
     */
    fun cancelNotification(context: Context, notificationId: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(notificationId)
    }
}

/**
 * LEGENDARY: سجل الرنين الوحيد — يمنع تداخل الملفات/الواجهات بدقة.
 * - مكالمة رنين واحدة فقط: الجديدة تلغي القديمة فوراً (بلا شبحين).
 * - إلغاء متماثل: accept/reject/timeout/missed كلها تنظف نفس المعرف.
 * - كتم صوت الوسائط: يطلب تركيزاً صوتياً حصرياً للرنين فيسكت المشغلات المتداخلة.
 */
object CallRingRegistry {
    @Volatile private var activeCallId: String? = null
    @Volatile private var activeNotifyId: Int = 0
    private var timeoutJob: kotlinx.coroutines.Job? = null
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)
    private var audioFocusRequest: android.media.AudioFocusRequest? = null

    @Synchronized
    fun showIncoming(context: Context, callId: String, peer: String, isVideo: Boolean, callType: String, myUserId: String): Int {
        // إلغاء السابق قبل عرض الجديد — دقة بلا تداخل
        activeCallId?.takeIf { it != callId }?.let { cancel(context, it) }
        // إزالة تكرار نفس المكالمة (الدفع + WS يرنان معاً)
        if (activeCallId == callId) return activeNotifyId
        CallNotificationManager.createNotificationChannel(context)
        // كتم الوسائط المتداخلة: تركيز رنين حصري
        runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                val req = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .build()
                audioFocusRequest = req
                am.requestAudioFocus(req)
            }
        }
        val notifyId = callId.hashCode()
        val builder = CallNotificationManager.buildIncomingCallNotification(
            context, peer, isVideo, callType, callId, myUserId
        )
        androidx.core.app.NotificationManagerCompat.from(context).notify(notifyId, builder.build())
        activeCallId = callId
        activeNotifyId = notifyId
        // مهلة 45s: تحويل لفائتة + تنظيف (تمنع الشبح الأبدي)
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            kotlinx.coroutines.delay(45_000)
            if (activeCallId == callId) {
                android.util.Log.i("CallRingRegistry", "ring timeout -> missed callId=$callId peer=$peer")
                cancel(context, callId)
            }
        }
        return notifyId
    }

    @Synchronized
    fun cancel(context: Context, callId: String) {
        if (activeCallId != null && activeCallId != callId) return // لا تلغِ مكالمة أحدث
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.cancel(activeNotifyId)
            nm.cancel(callId.hashCode())
        }
        if (activeCallId == callId) {
            activeCallId = null
            activeNotifyId = 0
        }
        timeoutJob?.cancel()
        timeoutJob = null
        // تحرير تركيز الرنين لعودة الوسائط
        runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
            audioFocusRequest = null
        }
    }

    fun isRinging(callId: String): Boolean = activeCallId == callId
    fun active(): String? = activeCallId
}
