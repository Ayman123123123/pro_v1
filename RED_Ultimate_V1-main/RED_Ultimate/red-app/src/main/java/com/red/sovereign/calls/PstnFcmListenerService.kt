package com.red.sovereign.calls

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * مستقبِل FCM لمكالمات PSTN الواردة — الحلقة التي كانت مفقودة كلياً.
 *
 * يعمل حتى والتطبيق مقفول: رسالة DATA عالية الأولوية (من NotificationService)
 * توقظ العملية، فنشغّل خدمة الواجهة phoneCall وننشر إشعار fullScreenIntent،
 * ثم يكمل المنسق المسار كالمعتاد (مستمع SIP + قبول عبر /ws/pstn).
 *
 * التهيئة اليدوية الآمنة: بدون google-services.json تعيد FirebaseApp
 * initializeApp قيمة null فيخرج المستقبِل مع تسجيل تحذيري (لا انهيار)،
 * والبناء سليم. بمجرد إضافة الملف (انظر `red-app/google-services.json.example`
 * و`red-app/README.md` قسم FCM) وتعبئة FCM_V1_SERVICE_ACCOUNT على الخادم
 * يصبح المسار حياً.
 *
 * سياسة التسجيل: لا خروج صامت — كل تجاهل لرسالة يُسجَّل بالسبب
 * (نوع خاطئ / callId مفقود / بلا منسق) لتسهيل تشخيص FCM في logcat.
 */
class PstnFcmListenerService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data.isEmpty()) {
            Log.w(TAG, "FCM ignored: empty data payload (from=${message.from})")
            return
        }
        val type = data["type"]
        if (type == null) {
            Log.w(TAG, "FCM ignored: missing 'type' in data payload (keys=${data.keys})")
            return
        }
        if (!type.equals("VOIP", true)) {
            // LEGENDARY: استقبال مكالمات RED 1-1/جماعية (كانت تُسقط بصمت — التطبيق المقتول لا يرن)
            if (type.equals("VOIP_RED", true)) {
                handleRedVoip(data)
                return
            }
            Log.d(TAG, "FCM ignored: non-VOIP type='$type'")
            return
        }

        val callId = data["callId"]?.takeIf { it.isNotBlank() }
        if (callId == null) {
            Log.w(TAG, "FCM VOIP ignored: missing/blank 'callId' (keys=${data.keys})")
            return
        }
        val caller = data["body"]?.substringBefore(" ")?.filter { it.isDigit() }.orEmpty()
            .ifBlank { "رقم غير معروف" }
        val called = data["called"]
        val channel = data["channel"]
        Log.i(TAG, "FCM VOIP ring: callId=$callId caller=$caller channel=$channel")

        // مرّر للمنسق إن كان حياً (نفس منطق WS مع تجاهل التكرار داخلياً)،
        // وإلا شغّل مسار الاستيقاظ المستقل بالحد الأدنى.
        val coord = PstnIncomingCallCoordinator.active
        if (coord != null) {
            coord.onExternalRing(callId = callId, caller = caller, called = called, channel = channel)
            return
        }

        Log.w(TAG, "FCM VOIP: no active coordinator — showing fallback ring notification (callId=$callId)")
        // لا منسق (المستخدم خارج الجلسة؟) — أطلق إشعار الرنين فقط ليفتح التطبيق.
        runCatching {
            PstnRingFallbackNotifier.show(applicationContext, callId, caller)
        }.onFailure { e ->
            Log.e(TAG, "FCM VOIP: fallback ring notification failed (callId=$callId)", e)
        }
    }

    override fun onNewToken(token: String) {
        Log.i(TAG, "FCM new token received (len=${token.length}) — caching locally")
        // يُرفع تلقائياً بواسطة VoipPushRegistrar عند إطلاق MainActivity لاحقاً؛
        // نخزنه محلياً هنا لتسريع الرفع حتى قبل الإطلاق التالي.
        runCatching {
            getSharedPreferences("pstn_fcm", MODE_PRIVATE)
                .edit().putString("last_token", token).apply()
        }.onFailure { e ->
            Log.e(TAG, "FCM: failed to cache new token locally", e)
        }
    }

    // LEGENDARY: إيقاظ مكالمات RED من القتل — إشعار واحد عبر السجل (بلا تداخل ملفين)
    private fun handleRedVoip(data: Map<String, String>) {
        val callId = data["callId"]?.takeIf { it.isNotBlank() } ?: run {
            Log.w(TAG, "FCM VOIP_RED ignored: missing callId"); return
        }
        val from = data["callerId"] ?: data["from"] ?: "مجهول"
        val mode = data["mode"] ?: "VOICE"
        val isVideo = mode.equals("VIDEO", true)
        Log.i(TAG, "FCM VOIP_RED ring: callId=$callId from=$from mode=$mode")
        // dedup مزدوج FCM+WS + إلغاء السابق — دقة واحدة فقط
        if (CallRingRegistry.isRinging(callId)) {
            Log.d(TAG, "VOIP_RED dedup: already ringing callId=$callId")
            return
        }
        runCatching {
            val myId = runCatching {
                getSharedPreferences("younes_auth", MODE_PRIVATE).getString("redId", "") ?: ""
            }.getOrDefault("")
            // مسار واحد فقط عبر السجل (كان إشعاران فوق بعضهما بنفس المعرف)
            CallRingRegistry.showIncoming(applicationContext, callId, from, isVideo, "RED", myId.ifBlank { from })
        }.onFailure { e ->
            Log.e(TAG, "VOIP_RED ring failed, fallback", e)
            runCatching { PstnRingFallbackNotifier.show(applicationContext, callId, from) }
        }
    }

    companion object {
        private const val TAG = "PstnFcmListener"
    }
}
