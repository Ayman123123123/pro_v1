package com.red.sovereign.calls

import android.content.Context
import android.media.AudioManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.MediaEncryption
import org.linphone.core.RegistrationState
import org.linphone.core.TransportType

/**
 * LinphoneSipClient — محرك SIP مبني على liblinphone (Linphone SDK 5.3+).
 *
 * يسجّل على UC200 Pro (IP-PBX) كـ Extension، ثم يتصل داخلياً (extension↔extension)
 * أو خارجياً (أي رقم شبكة) عبر المسارات الصادرة في UC200 Pro التي تمرّ إلى
 * trunk بوابة UC2000-VE-8G → GSM.
 *
 * صُمّم ليُستخدم كبديل لطبقة WebRTC داخل [PstnWebRtcManager]:
 *  - نفس قيم [PstnWebRtcManager.PstnCallState] → شاشة [PstnCallScreen] تبقى كما هي.
 *  - نفس شكل أحداث [Events] → ربط بسيط.
 *
 * المتطلّب قبل التجميع: إضافة تبعية linphone-sdk-android (انظر
 * LINPHONE_INTEGRATION_GUIDE.md — قسم «التبعية»).
 *
 * ملاحظة أمان: الإعداد الافتراضي هنا TLS للإشارة + SRTP للوسائط على الجانب
 * تطبيق↔UC200 Pro، مع تعطيل تحقّق شهادة الجذر لأن بوابة UC200 Pro تستخدم
 * شهادة ذاتية التوقيع على شبكة الإدارة. للإنتاج فعّل التحقق وثبّت شهادة الـ PBX
 * (core.rootCa = ...) بدل تعطيله.
 */
class LinphoneSipClient(private val context: Context) {

    // نستخدم PstnWebRtcManager.Events كعقد أحداث مشترك (نفس التوقيعات)
    // لتفادي ازدواجية الواجهات وضمان التوافق مع PstnLinphoneManager/الـ UI.

    /** معاملات التسجيل على UC200 Pro. */
    data class Credentials(
        val extension: String,
        val password: String,
        val pbxHost: String,
        val pbxPort: Int = 5061,
        val transport: TransportType = TransportType.Tls,
        val domain: String? = null // يساوي pbxHost عادةً
    )

    @Volatile
    var state: PstnWebRtcManager.PstnCallState = PstnWebRtcManager.PstnCallState.IDLE
        private set(value) {
            field = value
            _stateFlow.value = value
        }
    private val _stateFlow = MutableStateFlow(state)
    val stateFlow: StateFlow<PstnWebRtcManager.PstnCallState> = _stateFlow

    var remoteNumber: String? = null
        private set

    private var core: Core? = null
    private var activeCall: Call? = null
    private var events: PstnWebRtcManager.Events? = null
    private var registeredDomain: String? = null

    private val listener = object : CoreListenerStub() {
        override fun onRegistrationStateChanged(
            core: Core,
            cfg: org.linphone.core.ProxyConfig?,
            state: RegistrationState,
            message: String
        ) {
            when (state) {
                RegistrationState.Ok -> events?.onConnected()
                RegistrationState.Failed -> events?.onError("REGISTER_FAILED: $message")
                else -> { /* Progress / Cleared — لا إجراء */ }
            }
        }

        override fun onCallStateChanged(core: Core, call: Call, state: Call.State, message: String) {
            activeCall = if (state == Call.State.Released) null else call
            when (state) {
                Call.State.OutgoingInit, Call.State.OutgoingProgress -> {
                    this@LinphoneSipClient.state = PstnWebRtcManager.PstnCallState.INVITING
                }
                Call.State.OutgoingRinging -> {
                    this@LinphoneSipClient.state = PstnWebRtcManager.PstnCallState.RINGING
                    events?.onRinging()
                }
                Call.State.IncomingReceived -> {
                    remoteNumber = call.remoteAddress.username
                    this@LinphoneSipClient.state = PstnWebRtcManager.PstnCallState.INVITING
                    events?.onIncoming(call.remoteAddress.asStringUriOnly(), remoteNumber ?: "")
                }
                Call.State.Connected, Call.State.StreamsRunning -> {
                    this@LinphoneSipClient.state = PstnWebRtcManager.PstnCallState.ACTIVE
                    // usedToday/dailyLimit كانا من خادم RED (الجسر)؛ مع تسجيل مباشر على
                    // UC200 Pro لا توجد قيمة — مرّر 0,0 أو استدعِ خدمة الحدود في الباك-إند لاحقاً.
                    events?.onAnswered(0, 0)
                }
                Call.State.Released, Call.State.End -> {
                    events?.onHangup(message.ifBlank { null })
                    cleanup()
                }
                Call.State.Error -> {
                    events?.onError("CALL_ERROR: $message")
                    cleanup()
                }
                else -> { /* States أخرى: Paused, Updated, ... لا إجراء */ }
            }
        }
    }

    /**
     * يبدأ النواة ويسجّل على UC200 Pro.
     * يجب استدعاؤه مرة واحدة قبل [call].
     */
    fun start(credentials: Credentials, events: PstnWebRtcManager.Events) {
        this.events = events
        registeredDomain = credentials.domain ?: credentials.pbxHost

        val core = Factory.instance().createCore(null, null, context.applicationContext)
        core.addListener(listener)

        // تشفير الإشارات (TLS) والوسائط (SRTP) على الجانب تطبيق↔UC200 Pro.
        core.mediaEncryption = MediaEncryption.SRTP
        // شهادة UC200 Pro ذاتية التوقيع على LAN → عطّل تحقّق الجذر (للاختبار فقط).
        core.isSslRootCaVerificationEnabled = false
        core.isNetworkReachable = true

        val authInfo = Factory.instance().createAuthInfo(
            credentials.extension, null, credentials.password, null, registeredDomain, null
        )
        core.addAuthInfo(authInfo)

        val accountParams = core.createAccountParams()
        accountParams.identityAddress =
            Factory.instance().createAddress("sip:${credentials.extension}@$registeredDomain")
        accountParams.serverAddr =
            "${credentials.pbxHost}:${credentials.pbxPort};transport=${credentials.transport.name.lowercase()}"
        accountParams.registerEnabled = true
        accountParams.transport = credentials.transport
        val account = core.createAccount(accountParams)
        core.addAccount(account)
        core.defaultAccount = account

        // اختياري: STUN/ICE لتجاوز NAT خلف جدار ناري صارم.
        // core.stunServer = "stun:stun.example.com:3478"

        core.start()
        applyPush()
        this.core = core
    }

    /**
     * يبدأ مكالمة (داخلية أو خارجية). الفرق كله عند UC200 Pro حسب الرقم:
     *  - رقم extension آخر → وصلة داخلية مباشرة.
     *  - أي رقم شبكة (مثل 77xxxx) → مسار صادر → trunk UC2000 → GSM.
     */
    fun call(number: String) {
        val core = this.core ?: run { events?.onError("NOT_REGISTERED"); return }
        if (core.defaultAccount == null) { events?.onError("NO_ACCOUNT"); return }
        state = PstnWebRtcManager.PstnCallState.BRIDGING
        val domain = registeredDomain ?: core.defaultAccount?.params?.identityAddress?.domain
        val target = if (number.startsWith("sip:")) number else "sip:$number@$domain"
        activeCall = core.invite(target)
        state = PstnWebRtcManager.PstnCallState.REGISTERING
    }

    /** قبول مكالمة واردة. */
    fun answer() {
        runCatching { activeCall?.accept() }
    }

    /** إنهاء المكالمة الحالية. */
    fun hangup() {
        runCatching { activeCall?.terminate() ?: core?.currentCall?.terminate() }
        state = PstnWebRtcManager.PstnCallState.ENDED
    }

    /** تحرير النواة (عند الخروج / إنهاء المكالمة). */
    fun destroy() {
        runCatching { core?.stop() }
        core = null
        activeCall = null
        registeredDomain = null
        state = PstnWebRtcManager.PstnCallState.IDLE
    }

    /** كتم/إلغاء كتم الميكروفون. */
    var isMuted: Boolean = false
        set(value) {
            field = value
            runCatching { core?.micEnabled = !value }
        }

    /** مكبّر/مكبّر خارجي (سماعة vs أذن). */
    var isSpeakerphoneOn: Boolean = false
        set(value) {
            field = value
            runCatching {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                am.isSpeakerphoneOn = value
            }
        }

    /** تحديث مستمع الأحداث (يُستخدم قبل كل مكالمة صادرة/واردة). */
    fun setEvents(e: PstnWebRtcManager.Events?) { this.events = e }

    /** يضبط رمز Push (FCM) لدعم المكالمات الواردة في الخلفية. */
    fun setPushToken(token: String) {
        pendingPushToken = token
        applyPush()
    }

    private var pendingPushToken: String? = null

    private fun applyPush() {
        val token = pendingPushToken ?: return
        runCatching {
            val pc = core?.pushConfig ?: return@runCatching
            pc.pim = token
            pc.endpoint = "firebase"
        }
    }

    private fun cleanup() {
        activeCall = null
        state = PstnWebRtcManager.PstnCallState.IDLE
    }
}
