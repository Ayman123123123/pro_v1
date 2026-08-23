package com.red.sovereign.calls

import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.Log
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.features.sms.PstnEventSocket
import com.red.sovereign.features.sms.PstnWsEnvelope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * مستهلك دائم لأحداث PSTN الخاصة بالمستخدم المسجّل.
 *
 * لا يعتمد الاستقبال على فتح شاشة SMS أو لوحة DINSTAR؛ يبدأ من جذر التطبيق
 * بعد المصادقة (MainActivity) ويحوّل حدث PSTN_INCOMING إلى شاشة المكالمة
 * الواردة. يبقى WebSocket نفسه مصدر الإشارة فقط، أما الصوت فيُدار في مسار
 * PSTN/WebRTC.
 *
 * صنف عادي بـ CoroutineScope خاص (ليس ViewModel) حتى يمتلكه MainActivity
 * مباشرة ويعيش طوال جلسة الدخول بغضّ النظر عن الشاشات المفتوحة.
 */
class PstnIncomingCallCoordinator(private val application: Application) {

    companion object {
        private const val TAG = "PstnIncomingCoordinator"

        /** مرجع ثابت للمنسق النشط — تستخدمه شاشة الرنين (ViewModel) للقبول/الرفض. */
        @Volatile
        var active: PstnIncomingCallCoordinator? = null
            private set
    }
    init { active = this }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val socket = PstnEventSocket(
        tokens = TokenStore(application),
        onEnvelope = ::onEvent,
        onState = { connected = it }
    )

    @Volatile
    var connected: Boolean = false
        private set

    private var handledCallId: String? = null
    private var handledCallIdAt: Long = 0L
    private var started = false

    /** آخر مكالمة واردة نشطة (callId ↔ channel) لأوامر القبول/الرفض. */
    @Volatile
    var activeIncoming: ActiveIncoming? = null
        private set

    data class ActiveIncoming(val callId: String, val channel: String, val caller: String, val called: String?)

    /**
     * قبول المكالمة الواردة: (1) PSTN_ACCEPT للخادم ليوجّه قناة GSM عبر
     * AMI Redirect إلى red-webrtc-client، (2) المستمع المُسجَّل مسبقاً
     * يستقبل INVITE ويُجيب تلقائياً لأن المستخدم قبل قبل وصوله.
     */
    fun acceptIncoming(): Boolean {
        val inc = activeIncoming ?: return false
        val mgr = PstnWebRtcManager.incoming(application)
        mgr.acceptIncomingListener()
        val ok = socket.sendControl("PSTN_ACCEPT", mapOf("channel" to inc.channel, "callId" to inc.callId))
        Log.i(TAG, "PSTN_ACCEPT sent for ${inc.channel}: $ok")
        return ok
    }

    /** رفض المكالمة: الخادم ينهي قناة GSM فوراً ويحرر المنفذ. */
    fun rejectIncoming(): Boolean {
        val inc = activeIncoming ?: return false
        PstnWebRtcManager.incoming(application).stopIncomingListener()
        val ok = socket.sendControl("PSTN_REJECT", mapOf("channel" to inc.channel, "callId" to inc.callId))
        Log.i(TAG, "PSTN_REJECT sent for ${inc.channel}: $ok")
        if (ok) activeIncoming = null
        handledCallId = null
        return ok
    }

    fun start() {
        if (started) return
        started = true
        // تتبع المقدمة: foregroundActivities>0 يعني إطلاق مباشر آمن.
        application.registerActivityLifecycleCallbacks(lifecycleTracker)
        socket.connect()
    }

    private val lifecycleTracker = object : android.app.Application.ActivityLifecycleCallbacks {
        override fun onActivityStarted(a: android.app.Activity) { foregroundActivities++ }
        override fun onActivityStopped(a: android.app.Activity) { foregroundActivities = (foregroundActivities - 1).coerceAtLeast(0) }
        override fun onActivityCreated(a: android.app.Activity, s: android.os.Bundle?) = Unit
        override fun onActivityResumed(a: android.app.Activity) = Unit
        override fun onActivityPaused(a: android.app.Activity) = Unit
        override fun onActivitySaveInstanceState(a: android.app.Activity, s: android.os.Bundle) = Unit
        override fun onActivityDestroyed(a: android.app.Activity) = Unit
    }

    @Volatile private var foregroundActivities = 0

    fun stop() {
        started = false
        handledCallId = null
        connected = false
        // disconnect فقط — لا shutdown هنا ليظل الـscope صالحاً لإعادة start().
        socket.disconnect()
    }

    /** تحرير كامل للموارد عند تدمير المالك (Activity). */
    fun destroy() {
        if (active === this) active = null
        stop()
        scope.cancel()
    }

    /**
     * مدخل مكالمة واردة من خارج WebSocket (دفع FCM والتطبيق حي).
     * يمر عبر نفس منطق onEvent للاستفادة من منع التكرار والخدمة الأمامية.
     */
    fun onExternalRing(callId: String, caller: String, called: String?, channel: String?) {
        onEvent(
            com.red.sovereign.features.sms.PstnWsEnvelope(
                type = "PSTN_INCOMING",
                callId = callId,
                caller = caller,
                called = called,
                channel = channel
            )
        )
    }

    private fun onEvent(event: PstnWsEnvelope) {
        if (event.type != "PSTN_INCOMING") return
        val callId = event.callId?.trim().orEmpty()
        val caller = (event.caller ?: event.number).orEmpty()
        // الخادم يرسل channel صراحة؛ fallback إلى id ثم callId للتوافق القديم.
        val channel = (event.channel ?: event.id ?: event.callId).orEmpty()
        if (callId.isBlank() || channel.isBlank()) {
            Log.w(TAG, "Ignoring incomplete PSTN incoming event callId=$callId")
            return
        }
        if (handledCallId == callId && System.currentTimeMillis() - handledCallIdAt < 120_000L) return
        handledCallId = callId
        handledCallIdAt = System.currentTimeMillis()
        activeIncoming = ActiveIncoming(
            callId = callId,
            channel = channel,
            caller = caller.ifBlank { "رقم غير معروف" },
            called = event.called
        )
        // سجّل red-webrtc-client فوراً بالتوازي مع الرنين — الاعتماد على
        // /api/pstn/incoming-bridge (offer قصير العمر مرتبط بـ callId).
        runCatching {
            PstnWebRtcManager.incoming(application).startIncomingListener(callId)
        }.onFailure { Log.w(TAG, "incoming listener: ${it.message}") }

        // حافظ على العملية حية أثناء الرنين والمكالمة (كانت الخدمة غير مسجلة
        // في Manifest ولا مستدعاة من أي مكان — الآن تُشغَّل من هنا).
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) {
                application.startForegroundService(
                    Intent(application, PstnCallForegroundService::class.java).apply {
                        action = PstnCallForegroundService.ACTION_START
                        putExtra("number", caller.ifBlank { "PSTN" })
                        putExtra("isIncoming", true)
                    }
                )
            } else {
                application.startService(Intent(application, PstnCallForegroundService::class.java))
            }
        }.onFailure { Log.w(TAG, "Could not start PSTN foreground service: ${it.message}") }

        // OkHttp ينفذ callback خارج main thread، بينما Activity يجب إطلاقها من main.
        scope.launch {
            val peerName = caller.ifBlank { "رقم غير معروف" }
            // Android 10+: الإطلاق المباشر من الخلفية محظور. المسار الصحيح
            // إشعار fullScreenIntent على قناة MAX — النظام يطلق الـActivity
            // فوق شاشة القفل إن سمح المستخدم، وإلا يظهر كإشعار رأس عادي.
            if (!postFullScreenRing(callId, peerName)) {
                // التطبيق في المقدمة أو فشل الإشعار → إطلاق مباشر آمن.
                IncomingCallActivity.launchPstn(application, callId = callId, peer = peerName)
            }
        }
    }

    /**
     * ينشر إشعار رنين PSTN بـ fullScreenIntent. يُرجع false إذا كان
     * التطبيق في المقدمة (الإطلاق المباشر أنسب) أو فشل النشر.
     * على Android 13+ يتطلب POST_NOTIFICATIONS؛ إذا لم يُمنح نُسجّل تحذيراً
     * ونُعيد false ليتولى المسار الاحتياطي إطلاق IncomingCallActivity مباشرة.
     */
    private fun postFullScreenRing(callId: String, peer: String): Boolean = runCatching {
        // في المقدمة؟ الإطلاق المباشر أفضل (بلا خطوات إشعار).
        if (foregroundActivities > 0) return false
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                application, android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Log.w(TAG, "POST_NOTIFICATIONS not granted — skipping notify for $callId, falling back to direct launch")
                return false
            }
        }

        val launchIntent = Intent(application, IncomingCallActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(IncomingCallActivity.EXTRA_CALL_TYPE, IncomingCallActivity.CALL_TYPE_PSTN)
            putExtra(IncomingCallActivity.EXTRA_CALL_ID, callId)
            putExtra(IncomingCallActivity.EXTRA_PEER, peer)
            putExtra(IncomingCallActivity.EXTRA_MODE, "PSTN")
        }
        val pi = android.app.PendingIntent.getActivity(
            application, callId.hashCode(), launchIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val acceptIntent = android.app.PendingIntent.getBroadcast(
            application, 1001,
            Intent(application, PstnRingActionReceiver::class.java).apply {
                action = PstnRingActionReceiver.ACTION_ACCEPT
                putExtra("callId", callId)
            },
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val declineIntent = android.app.PendingIntent.getBroadcast(
            application, 1002,
            Intent(application, PstnRingActionReceiver::class.java).apply {
                action = PstnRingActionReceiver.ACTION_DECLINE
                putExtra("callId", callId)
            },
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val notif = androidx.core.app.NotificationCompat.Builder(application, "red_calls_incoming")
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle("مكالمة واردة")
            .setContentText(peer)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
            .setCategory(android.app.Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(true)
            .setFullScreenIntent(pi, true)
            .addAction(0, "قبول", acceptIntent)
            .addAction(0, "رفض", declineIntent)
            .build()
        androidx.core.app.NotificationManagerCompat.from(application)
            .notify(callId.hashCode(), notif)
        true
    }.getOrDefault(false)
}
