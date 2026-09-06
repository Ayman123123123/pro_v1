package com.red.sovereign.calls

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
 * initializeApp قيمة null فيخرج المستقبِل بصمت — لا انهيار، والبناء سليم.
 * بمجرد إضافة الملف وتعبئة FCM_V1_SERVICE_ACCOUNT على الخادم يصبح المسار حياً.
 */
class PstnFcmListenerService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data ?: return
        val type = data["type"] ?: return
        if (!type.equals("VOIP", true)) return

        val callId = data["callId"]?.takeIf { it.isNotBlank() } ?: return
        val caller = data["body"]?.substringBefore(" ")?.filter { it.isDigit() }.orEmpty()
            .ifBlank { "رقم غير معروف" }
        val called = data["called"]
        val channel = data["channel"]

        // مرّر للمنسق إن كان حياً (نفس منطق WS مع تجاهل التكرار داخلياً)،
        // وإلا شغّل مسار الاستيقاظ المستقل بالحد الأدنى.
        val coord = PstnIncomingCallCoordinator.active
        if (coord != null) {
            coord.onExternalRing(callId = callId, caller = caller, called = called, channel = channel)
            return
        }

        // لا منسق (المستخدم خارج الجلسة؟) — أطلق إشعار الرنين فقط ليفتح التطبيق.
        runCatching {
            PstnRingFallbackNotifier.show(applicationContext, callId, caller)
        }
    }

    override fun onNewToken(token: String) {
        // يُرفع تلقائياً بواسطة VoipPushRegistrar عند إطلاق MainActivity لاحقاً؛
        // نخزنه محلياً هنا لتسريع الرفع حتى قبل الإطلاق التالي.
        runCatching {
            getSharedPreferences("pstn_fcm", MODE_PRIVATE)
                .edit().putString("last_token", token).apply()
        }
        // ارفع الرمز إلى Linphone لدعم المكالمات الواردة في الخلفية عبر التسجيل المباشر.
        runCatching { PstnLinphoneManager.incoming(applicationContext).setPushToken(token) }
    }
}
