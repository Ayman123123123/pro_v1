package com.red.sovereign.calls

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.webrtc.AudioTrack
import org.webrtc.VideoTrack

enum class CallMode { AUDIO, VIDEO, CONFERENCE }

data class CallPeer(val userId: String, val displayName: String)

sealed interface CallUiState {
    data object Idle : CallUiState
    data class Incoming(val callId: String, val peer: String, val mode: String) : CallUiState
    /** حالة الاتصال الصادر — تحمل presenceState لتعكس وصول المكالمة للمستلم */
    data class Connecting(
        val callId: String,
        val peer: String,
        val mode: String,
        val presenceState: CallPresenceMonitor.PresenceState = CallPresenceMonitor.PresenceState.CONNECTING,
        /**
         * نص العرض في الـ Overlay. مُشتق افتراضيًا من [presenceState]، ويمكن
         * تجاوزه لرسالة أدق من طبقة أعلى (مثل «يرن على هاتف المستلم — 3»).
         *
         * لأنه معامل بانٍ لا خاصية مشتقة، فإن `copy(presenceState = …)` وحده
         * لا يُحدّث النص. استخدم [withPresence] دائمًا لتغيير الحضور.
         */
        val presenceLabel: String = labelFor(presenceState)
    ) : CallUiState {
        /** يغيّر الحضور ويُعيد حساب النص معه — الطريق الآمن الوحيد. */
        fun withPresence(next: CallPresenceMonitor.PresenceState): Connecting =
            copy(presenceState = next, presenceLabel = labelFor(next))

        companion object {
            fun labelFor(presenceState: CallPresenceMonitor.PresenceState): String = when (presenceState) {
                CallPresenceMonitor.PresenceState.CONNECTING -> "جارٍ الاتصال…"
                CallPresenceMonitor.PresenceState.RINGING -> "يرن على جهاز المستلم"
                CallPresenceMonitor.PresenceState.WAKING_UP -> "جارٍ إيقاظ الجهاز…"
                CallPresenceMonitor.PresenceState.NO_ANSWER -> "لا يوجد رد"
                else -> "جارٍ الاتصال…"
            }
        }
    }
    data class Active(val callId: String, val peer: String, val mode: String, val startedAt: Long, val isHeld: Boolean = false) : CallUiState
    /** مكالمة نشطة + مكالمة واردة ثانية (call waiting) */
    data class ActiveWithIncoming(val active: Active, val waiting: Incoming) : CallUiState
    data class Error(val message: String) : CallUiState
    // ── حالات نهائية ─────────────────────────────────────────────────────────
    /**
     * الطرف الآخر مشغول — تُشغَّل نغمة مشغول.
     * [mode] يسمح للشاشة النهائية بقول «مكالمة فيديو» بدل نص محايد.
     */
    data class Busy(val peer: String, val mode: String = DEFAULT_MODE) : CallUiState
    /** الطرف الآخر رفض المكالمة صراحةً */
    data class Declined(val peer: String, val mode: String = DEFAULT_MODE) : CallUiState
    /**
     * انتهت مهلة الرنين بلا رد.
     * [outgoing] يفصل «لم يتم الرد» (نحن المتصل) عن «مكالمة فائتة» (نحن المستلم)
     * — نفس الحالة تقنيًا لكن رسالتها للمستخدم معاكسة تمامًا.
     */
    data class NoAnswer(
        val peer: String,
        val mode: String = DEFAULT_MODE,
        val outgoing: Boolean = true
    ) : CallUiState
    /** انتهت المكالمة بشكل طبيعي */
    data class CallEnded(
        val peer: String,
        val mode: String,
        val durationMs: Long,
        val callId: String = "",
        val canRedial: Boolean = true
    ) : CallUiState
    /** جاري إعادة الاتصال بعد انقطاع مؤقت — يحمل مدة المكالمة الأصلية لاستعادتها */
    data class Reconnecting(val callId: String, val peer: String, val mode: String, val attempt: Int = 1, val startedAt: Long = 0L) : CallUiState

    companion object {
        const val DEFAULT_MODE = "VOICE"

        /** مدة عرض الحالة النهائية (منتهية/فائتة/مرفوضة) قبل إخفاء شاشة المكالمة. */
        const val TERMINAL_DISPLAY_MS: Long = 4_000L

        /**
         * هل انتهت المكالمة نهائيًا؟
         *
         * [Reconnecting] ليست نهائية: المكالمة قد تعود، وإخفاء الشاشة عندها
         * يفقد المستخدم مكالمة كانت ستستأنف. أما [Error] فنهائية.
         */
        fun isTerminal(state: CallUiState): Boolean = when (state) {
            is Busy, is Declined, is NoAnswer, is CallEnded, is Error -> true
            is Idle, is Incoming, is Connecting, is Active, is ActiveWithIncoming, is Reconnecting -> false
        }
    }
}

object CallRuntime {
    var state: CallUiState by mutableStateOf(CallUiState.Idle)

    // ── حالة مكالمة البوابة (PSTN/DINSTAR) — مصدر شاشة المراحل الغنية ──
    // كانت YounesPstnCallOverlay تشير إليها وكانت غير معرَّفة إطلاقًا فتُسقط
    // ترجمة الوحدة كلها؛ الآن مُعرَّفة ومُشغَّلة من أحداث /ws/pstn الحقيقية.
    var pstnStatus: PstnCallStatus by mutableStateOf(PstnCallStatus.IDLE)
    var pstnNumber: String by mutableStateOf("")
    var pstnCallId: String by mutableStateOf("")

    /**
     * مدة عرض الحالة النهائية (منتهية/فائتة) قبل إخفاء شاشة المكالمة.
     * تُبقى هنا للتوافق مع نداءات قائمة؛ المصدر [CallUiState.TERMINAL_DISPLAY_MS].
     */
    const val TERMINAL_DISPLAY_MS: Long = CallUiState.TERMINAL_DISPLAY_MS

    fun setPstn(status: PstnCallStatus, number: String = pstnNumber, callId: String = pstnCallId) {
        pstnStatus = status; pstnNumber = number; pstnCallId = callId
    }

    fun clearPstn() {
        pstnStatus = PstnCallStatus.IDLE
        pstnNumber = ""
        pstnCallId = ""
    }
    var eglContext: org.webrtc.EglBase.Context? = null
    var localVideo: VideoTrack? by mutableStateOf(null)
    var localVideoTrack: VideoTrack? by mutableStateOf(null)
    var localAudioTrack: AudioTrack? by mutableStateOf(null)
    var remoteVideo: VideoTrack? by mutableStateOf(null)
    var speaker by mutableStateOf(false)
    var isMinimized by mutableStateOf(false)
    var networkStats: NetworkStats by mutableStateOf(NetworkStats())
    /** هل تسجيل المكالمة يعمل الآن — تُعرض كشارة حمراء في واجهة المكالمة */
    var isRecording by mutableStateOf(false)
    /** فشل فتح الكاميرا (إذن/عتاد) — شارة "الكاميرا غير متاحة — مكالمة صوتية" */
    var cameraNotice by mutableStateOf(false)
    /** Callback to request camera facing change on the active PeerConnection */
    var switchCameraFacing: ((Boolean) -> Unit)? = null

    var isMuted by mutableStateOf(false)
    var isFrontCamera by mutableStateOf(true)

    fun startCall(mode: CallMode = CallMode.AUDIO, peer: CallPeer = CallPeer("", "")) {
        state = CallUiState.Active(callId = "", peer = peer.displayName, mode = mode.name, startedAt = System.currentTimeMillis())
    }

    fun endCall() {
        state = CallUiState.Idle
        isMuted = false
        isFrontCamera = true
    }

    fun toggleMute() {
        isMuted = !isMuted
        localAudioTrack?.setEnabled(!isMuted)
    }

    fun toggleSpeaker() { speaker = !speaker }

    fun toggleHold() { (state as? CallUiState.Active)?.let { state = it.copy(isHeld = !it.isHeld) } }

    fun toggleCamera() {
        localVideoTrack?.let { track ->
            val enabled = !track.enabled()
            track.setEnabled(enabled)
            cameraNotice = !enabled
        }
    }

    fun switchCamera() {
        isFrontCamera = !isFrontCamera
        switchCameraFacing?.invoke(isFrontCamera)
    }
}

@Composable
fun YounesCallOverlay(onDismiss: () -> Unit = {}) {
    when (CallRuntime.state) {
        is CallUiState.Idle -> Unit
        else -> com.red.sovereign.ui.screens.ActiveCallScreen()
    }
}
