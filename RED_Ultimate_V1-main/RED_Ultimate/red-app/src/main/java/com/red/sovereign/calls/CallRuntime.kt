package com.red.sovereign.calls

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.webrtc.AudioTrack
import org.webrtc.VideoTrack

/**
 * CallRuntime - نظيف بدون RED/RED
 * مكالمات يونس فقط: صوت منفصل وفيديو منفصل
 * جودة عالية، رنين، وصول، واجهات أفضل من واتس وتيليجرام
 */
enum class CallMode {
    AUDIO,       // مكالمة صوتية منفصلة
    VIDEO,       // مكالمة فيديو منفصلة
    CONFERENCE,  // مؤتمر
    GROUP_VOICE, // مكالمة جماعية صوتية
    GROUP_VIDEO, // مكالمة جماعية فيديو
    LIVE_STREAM, // بث مباشر
    AUDIO_SPACE  // مساحة صوتية
}

data class CallPeer(val userId: String, val displayName: String)

sealed interface CallUiState {
    data object Idle : CallUiState
    data class Incoming(val callId: String, val peer: String, val mode: String) : CallUiState
    data class Connecting(
        val callId: String,
        val peer: String,
        val mode: String,
        val presenceState: CallPresenceMonitor.PresenceState = CallPresenceMonitor.PresenceState.CONNECTING,
        val presenceLabel: String = labelFor(presenceState)
    ) : CallUiState {
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
    data class ActiveWithIncoming(val active: Active, val waiting: Incoming) : CallUiState
    data class Error(val message: String) : CallUiState
    data class Busy(val peer: String, val mode: String = DEFAULT_MODE) : CallUiState
    data class Declined(val peer: String, val mode: String = DEFAULT_MODE) : CallUiState
    data class NoAnswer(
        val peer: String,
        val mode: String = DEFAULT_MODE,
        val outgoing: Boolean = true
    ) : CallUiState
    data class CallEnded(
        val peer: String,
        val mode: String,
        val durationMs: Long,
        val callId: String = "",
        val canRedial: Boolean = true
    ) : CallUiState
    data class Reconnecting(val callId: String, val peer: String, val mode: String, val attempt: Int = 1, val startedAt: Long = 0L) : CallUiState

    companion object {
        const val DEFAULT_MODE = "VOICE"
        const val TERMINAL_DISPLAY_MS: Long = 4_000L

        fun isTerminal(state: CallUiState): Boolean = when (state) {
            is Busy, is Declined, is NoAnswer, is CallEnded, is Error -> true
            is Idle, is Incoming, is Connecting, is Active, is ActiveWithIncoming, is Reconnecting -> false
        }
    }
}

object CallRuntime {
    var state: CallUiState by mutableStateOf(CallUiState.Idle)

    /**
     * مدة عرض الحالة النهائية (منتهية/فائتة) قبل إخفاء شاشة المكالمة.
     * تُبقى هنا للتوافق مع نداءات قائمة؛ المصدر [CallUiState.TERMINAL_DISPLAY_MS].
     */
    const val TERMINAL_DISPLAY_MS: Long = CallUiState.TERMINAL_DISPLAY_MS

    var eglContext: org.webrtc.EglBase.Context? by mutableStateOf(null)
    var localVideo: VideoTrack? by mutableStateOf(null)
    var localVideoTrack: VideoTrack? by mutableStateOf(null)
    var localAudioTrack: AudioTrack? by mutableStateOf(null)
    var remoteVideo: VideoTrack? by mutableStateOf(null)
    var speaker by mutableStateOf(false)
    var isMinimized by mutableStateOf(false)
    var networkStats: NetworkStats by mutableStateOf(NetworkStats())
    var isRecording by mutableStateOf(false)
    var cameraNotice by mutableStateOf(false)
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
        localVideo = null
        localVideoTrack = null
        localAudioTrack = null
        remoteVideo = null
        speaker = false
        isRecording = false
        cameraNotice = false
        isMinimized = false
        switchCameraFacing = null
        networkStats = NetworkStats()
    }

    fun toggleMute() {
        isMuted = !isMuted
        localAudioTrack?.setEnabled(!isMuted)
    }

    fun toggleSpeaker() { speaker = !speaker }

    fun toggleHold() { (state as? CallUiState.Active)?.let { state = it.copy(isHeld = !it.isHeld) } }

    fun toggleCamera() {
        val track = localVideoTrack ?: localVideo
        track?.let {
            val enabled = !it.enabled()
            it.setEnabled(enabled)
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
