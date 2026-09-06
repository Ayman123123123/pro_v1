package com.red.sovereign.calls

import android.content.Context
import kotlinx.coroutines.flow.StateFlow
import org.linphone.core.TransportType

/**
 * PstnLinphoneManager — بديل لـ [PstnWebRtcManager] مبني على [LinphoneSipClient].
 *
 * يحافظ على نفس الواجهة العامة (Events / PstnCallState / companion incoming/activeUi/
 * controls / startIncomingListener / stopIncomingListener) ليظل [PstnCallScreen] و
 * [IncomingCallActivity] و[PstnIncomingCallCoordinator] تعمل بلا تعديل.
 *
 * الفرق الجوهري: التسجيل يكون مباشرةً على UC200 Pro (IP-PBX) لا عبر جسر الخادم
 * وWebRTC. المكالمات الواردة تصل عبر التسجيل المستمر (لا حاجة لـ incomingBridge).
 *
 * إعدادات التسجيل (extension/password/pbxHost) تُملأ في [PstnLinphoneConfig] —
 * من إعدادات الأدمن أو عبر تزويد من الخادم (كانت تُرجَع سابقاً من api.bridge).
 */
class PstnLinphoneManager(private val context: Context) {

    private val client = LinphoneSipClient(context.applicationContext)
    private var started = false

    /** معرّف المكالمة (لا يوجد callId من الخادم مع التسجيل المباشر — نستخدم الرقم). */
    var currentCallId: String? = null
        private set

    /**
     * أحداث عامة للمكالمات الواردة (تُربط من AuthViewModel). عند وصول مكالمة
     * واردة عبر التسجيل المستمر يُستخدم هذا المعالج لعرض شاشة الرنين.
     */
    private var globalEvents: PstnWebRtcManager.Events? = null
    fun setGlobalEvents(e: PstnWebRtcManager.Events) { globalEvents = e }

    /** يضبط رمز Push (FCM) على المحرك لدعم الوارد في الخلفية. */
    fun setPushToken(token: String) { client.setPushToken(token) }

    val stateFlow: StateFlow<PstnWebRtcManager.PstnCallState> get() = client.stateFlow
    val remoteNumber: String? get() = client.remoteNumber

    var isMuted: Boolean
        get() = client.isMuted
        set(v) { client.isMuted = v }

    var isSpeaker: Boolean
        get() = client.isSpeakerphoneOn
        set(v) { client.isSpeakerphoneOn = v }

    /** يبدأ النواة ويسجّل على UC200 Pro إن لم تبدأ بعد. */
    private fun ensureStarted(events: PstnWebRtcManager.Events) {
        if (started) { client.setEvents(events); return }
        val cfg = PstnLinphoneConfig
        if (cfg.extension.isBlank()) {
            events.onError("PSTN_NOT_CONFIGURED: set PstnLinphoneConfig before calling")
            return
        }
        client.setEvents(events)
        client.start(
            LinphoneSipClient.Credentials(
                extension = cfg.extension,
                password = cfg.password,
                pbxHost = cfg.pbxHost,
                pbxPort = cfg.pbxPort,
                transport = cfg.transport
            ),
            events
        )
        started = true
        Companion.activeUi = this
    }

    fun call(number: String, events: PstnWebRtcManager.Events) {
        ensureStarted(events)
        currentCallId = number
        client.call(number)
    }

    fun hangup() {
        client.hangup()
        if (Companion.activeUi === this) Companion.activeUi = null
    }

    fun answerIncoming(offerSdp: String? = null) {
        client.answer()
    }

    fun rejectIncoming() {
        client.hangup()
    }

    fun release() {
        client.destroy()
        started = false
        if (Companion.activeUi === this) Companion.activeUi = null
    }

    /**
     * وارد: مع Linphone التسجيل مستمر، فالمكالمة الواردة تُلتقط عبر
     * CoreListener مباشرة (onIncoming). هذه الدالة تُبقي واجهة المنسّق دون
     * تغيير؛ تضمن فقط بدء التسجيل إن لم يكن قد بدأ.
     */
    fun startIncomingListener(callId: String) {
        // المكالمات الواردة تصل عبر التسجيل المستمر؛ نضمن بدء النواة بأحداث
        // الوارد العامة (globalEvents) إن وُرِبت، وإلا أحداث صامتة.
        if (!started) ensureStarted(globalEvents ?: SilentEvents)
    }

    fun stopIncomingListener() {
        // التسجيل يبقى للاستقبال المستمر — لا نوقفه.
    }

    private object SilentEvents : PstnWebRtcManager.Events {
        override fun onConnected() = Unit
        override fun onRinging() = Unit
        override fun onAnswered(usedToday: Int, dailyLimit: Int) = Unit
        override fun onIncoming(sdp: String, fromNumber: String) = Unit
        override fun onHangup(cause: String?) = Unit
        override fun onError(message: String) = Unit
    }

    companion object {
        @Volatile private var incomingInstance: PstnLinphoneManager? = null
        @Volatile var activeUi: PstnLinphoneManager? = null
            private set

        fun incoming(context: Context): PstnLinphoneManager =
            incomingInstance ?: synchronized(this) {
                incomingInstance ?: PstnLinphoneManager(context.applicationContext).also { incomingInstance = it }
            }

        fun controls(context: Context): PstnLinphoneManager? = activeUi ?: incomingInstance
    }
}

/**
 * إعدادات تسجيل SIP على UC200 Pro. يملؤها التطبيق من إعدادات الأدمن أو عبر
 * تزويد من الخادم. بدونها لا يمكن التسجيل (كانت تُرجَع سابقاً من api.bridge).
 *
 * عناوين IP المؤكَّدة من المستخدم (شبكة الإدارة):
 *  - UC200 Pro (IP-PBX)  = 192.168.11.3
 *  - UC2000-VE-8G (GSM)  = 192.168.11.2  (يسجّل كـ trunk على UC200 Pro)
 */
object PstnLinphoneConfig {
    // ⚠️ قيم افتراضية مؤقتة للاختبار (Extension 100 على UC200 Pro 192.168.11.3).
    // للإنتاج: امسح هذه القيم واجعلها فارغة، وسيأخذها المستخدم من شاشة
    // «المكالمات ← خط PSTN» (تُحفظ عبر DataStore في PstnSettingsRepository).
    var extension: String = "100"
    var password: String = "admin1234!@#$"
    var pbxHost: String = "192.168.11.3"
    var pbxPort: Int = 5060
    var transport: TransportType = TransportType.Udp

    /** يُحمّل القيم المخزَّنة (DataStore) فوق الافتراضية — يُستدعى عند الإقلاع. */
    suspend fun load(context: Context) {
        val d = PstnSettingsRepository.load(context)
        if (d.extension.isNotBlank()) extension = d.extension
        if (d.password.isNotBlank()) password = d.password
        if (d.host.isNotBlank()) pbxHost = d.host
        pbxPort = d.port
        transport = d.transport
    }

    /** يحفظ القيم الحالية في DataStore. */
    suspend fun persist(context: Context) {
        PstnSettingsRepository.save(context, PstnSipData(extension, password, pbxHost, pbxPort, transport))
    }
}
