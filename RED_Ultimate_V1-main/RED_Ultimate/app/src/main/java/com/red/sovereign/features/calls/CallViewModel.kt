package com.red.sovereign.features.calls

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.red.sovereign.features.calls.data.CallHistoryItemDto
import com.red.sovereign.features.calls.data.CallRepository
import com.red.sovereign.features.calls.data.CallResult
import com.red.sovereign.features.calls.data.CallTelemetryDto
import com.red.sovereign.features.calls.signaling.CallSignalingClient
import com.red.sovereign.features.calls.signaling.CallSignal
import com.red.sovereign.features.calls.signaling.SignalType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.webrtc.AudioTrack
import org.webrtc.VideoTrack
import java.time.Instant
import javax.inject.Inject

// ─── UI State ──────────────────────────────────────────────────────────────

data class CallUiState(
    // Call lifecycle
    val callState: VoipState = VoipState.IDLE,
    val session: VoipSession? = null,

    // Remote peer info (filled when incoming or connected)
    val remoteUserId: String = "",
    val remoteDisplayName: String = "",
    val remoteAvatarUrl: String? = null,

    // Media state
    val isMuted: Boolean = false,
    val isVideoEnabled: Boolean = true,
    val isSpeakerOn: Boolean = false,
    val isOnHold: Boolean = false,

    // Duration (updated every second when ACTIVE)
    val durationSeconds: Long = 0L,

    // Error
    val errorMessage: String? = null,

    // Call history
    val callHistory: List<CallHistoryItemDto> = emptyList(),
    val isLoadingHistory: Boolean = false
)

// ─── CallViewModel ─────────────────────────────────────────────────────────

/**
 * ViewModel المكالمات الرئيسي.
 *
 * يربط:
 * - [RedVoipMaster] — محرك WebRTC
 * - [CallSignalingClient] — إشارات WebSocket
 * - [CallRepository] — REST API
 *
 * يُصدر [callUiState] للواجهات ليعكسن الحالة بالكامل.
 */
@HiltViewModel
class CallViewModel @Inject constructor(
    private val voipMaster: RedVoipMaster,
    private val signalingClient: CallSignalingClient,
    private val callRepository: CallRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CallUiState())
    val callUiState: StateFlow<CallUiState> = _uiState.asStateFlow()

    // Expose video tracks to UI for SurfaceViewRenderer
    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    private var durationJob: kotlinx.coroutines.Job? = null
    private var callStartTime: Long = 0L

    init {
        // Connect signaling client
        signalingClient.connect()

        // Observe VoIP state from engine
        viewModelScope.launch {
            voipMaster.state.collect { state ->
                _uiState.update { it.copy(callState = state) }
                when (state) {
                    VoipState.ACTIVE -> startDurationTimer()
                    VoipState.IDLE -> {
                        stopDurationTimer()
                        _uiState.update { it.copy(durationSeconds = 0L, session = null) }
                        _localVideoTrack.value = null
                        _remoteVideoTrack.value = null
                    }
                    else -> stopDurationTimer()
                }
            }
        }

        // Observe VoIP session
        viewModelScope.launch {
            voipMaster.session.collect { session ->
                _uiState.update { it.copy(session = session, remoteUserId = session?.remoteUserId ?: "") }
            }
        }

        // Observe incoming signaling events
        viewModelScope.launch {
            signalingClient.incoming.collect { signal ->
                handleIncomingSignal(signal)
            }
        }

        // Set event callbacks
        voipMaster.setEventListener(object : VoipEventListener {
            override fun onLocalVideoTrack(track: VideoTrack) {
                _localVideoTrack.value = track
            }
            override fun onRemoteVideoTrack(track: VideoTrack) {
                _remoteVideoTrack.value = track
            }
            override fun onRemoteAudioTrack(track: AudioTrack) { /* AudioManager handles routing */ }
            override fun onCallStateChanged(state: VoipState) { /* handled above */ }
            override fun onCallError(message: String) {
                _uiState.update { it.copy(errorMessage = message) }
            }
            override fun onIceConnected() {
                _uiState.update { it.copy(errorMessage = null) }
            }
            override fun onIceDisconnected() {
                _uiState.update { it.copy(errorMessage = "الاتصال ضعيف — إعادة المحاولة...") }
            }
        })
    }

    // ── Outgoing Call ─────────────────────────────────────────────────

    fun startCall(targetUserId: String, videoEnabled: Boolean = false) {
        if (_uiState.value.callState != VoipState.IDLE) return
        _uiState.update { it.copy(remoteUserId = targetUserId, isVideoEnabled = videoEnabled) }
        voipMaster.startSecureCall(targetUserId, videoEnabled)
    }

    fun startVideoCall(targetUserId: String) = startCall(targetUserId, videoEnabled = true)
    fun startAudioCall(targetUserId: String) = startCall(targetUserId, videoEnabled = false)

    // ── Incoming Call ─────────────────────────────────────────────────

    fun answerCall() {
        voipMaster.answerCall()
    }

    fun rejectCall() {
        voipMaster.rejectCall()
    }

    // ── Active Call Controls ──────────────────────────────────────────

    fun endCall() {
        val session = _uiState.value.session
        if (session != null) {
            // Upload telemetry asynchronously
            viewModelScope.launch {
                val durationMs = _uiState.value.durationSeconds * 1000L
                callRepository.uploadTelemetry(
                    CallTelemetryDto(
                        callId = session.callId,
                        type = if (session.isVideo) "VIDEO" else "VOICE",
                        route = "RED",
                        durationMs = durationMs,
                        avgRttMs = 0L,
                        maxPacketLoss = 0.0,
                        qualityAtEnd = if (durationMs > 5000) "GOOD" else "UNKNOWN",
                        wasRecorded = false,
                        wasHeld = if (_uiState.value.isOnHold) 1 else 0
                    )
                )
            }
        }
        voipMaster.endCall()
    }

    fun toggleMute() {
        val muted = !_uiState.value.isMuted
        voipMaster.toggleMute(muted)
        _uiState.update { it.copy(isMuted = muted) }
    }

    fun toggleVideo() {
        val enabled = !_uiState.value.isVideoEnabled
        voipMaster.toggleVideo(enabled)
        _uiState.update { it.copy(isVideoEnabled = enabled) }
    }

    fun toggleSpeaker() {
        val speaker = !_uiState.value.isSpeakerOn
        _uiState.update { it.copy(isSpeakerOn = speaker) }
        // AudioManager will pick this up via state
    }

    fun switchCamera() = voipMaster.switchCamera()

    fun holdCall() {
        voipMaster.holdCall()
        _uiState.update { it.copy(isOnHold = true) }
    }

    fun resumeCall() {
        voipMaster.resumeCall()
        _uiState.update { it.copy(isOnHold = false) }
    }

    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    // ── Call History ──────────────────────────────────────────────────

    fun loadCallHistory() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingHistory = true) }
            when (val result = callRepository.getCallHistory()) {
                is CallResult.Success -> _uiState.update {
                    it.copy(callHistory = result.data, isLoadingHistory = false)
                }
                is CallResult.Error -> _uiState.update {
                    it.copy(isLoadingHistory = false, errorMessage = result.message)
                }
            }
        }
    }

    // ── Private: Signal Handling ──────────────────────────────────────

    private fun handleIncomingSignal(signal: CallSignal) {
        when (signal.type) {
            SignalType.OFFER -> {
                // Incoming call — extract SDP from payload
                val sdp = signal.payload["sdp"]?.toString() ?: return
                voipMaster.onIncomingOffer(
                    callId = signal.callId ?: return,
                    fromUserId = signal.sourceUserId,
                    sdp = sdp,
                    mode = signal.mode
                )
                _uiState.update { it.copy(remoteUserId = signal.sourceUserId) }
            }

            SignalType.ANSWER -> {
                val sdp = signal.payload["sdp"]?.toString() ?: return
                voipMaster.onRemoteAnswer(sdp)
            }

            SignalType.ICE -> {
                val candidate = signal.payload["candidate"]?.toString() ?: return
                val sdpMid = signal.payload["sdpMid"]?.toString()
                val sdpMLineIndex = (signal.payload["sdpMLineIndex"] as? Number)?.toInt() ?: 0
                voipMaster.onRemoteIceCandidate(candidate, sdpMid, sdpMLineIndex)
            }

            SignalType.END -> {
                // Remote ended the call
                voipMaster.endCall()
            }

            SignalType.REJECT -> {
                // Remote rejected — cleanup
                _uiState.update { it.copy(errorMessage = "المكالمة مرفوضة") }
                voipMaster.endCall()
            }

            SignalType.HOLD -> {
                _uiState.update { it.copy(isOnHold = true) }
            }

            SignalType.RESUME -> {
                _uiState.update { it.copy(isOnHold = false) }
            }

            SignalType.CONFERENCE_INVITE -> {
                val roomId = signal.callId ?: return
                val title = signal.payload["title"]?.toString() ?: "دعوة مؤتمر"
                _uiState.update { it.copy(errorMessage = null) } // Will trigger conference flow
                // TODO: Emit conference invite event for navigation
            }

            else -> { /* ACK, RINGING_PUSH_SENT, UNKNOWN — log only */ }
        }
    }

    // ── Duration Timer ────────────────────────────────────────────────

    private fun startDurationTimer() {
        callStartTime = System.currentTimeMillis()
        durationJob?.cancel()
        durationJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                val elapsed = (System.currentTimeMillis() - callStartTime) / 1000L
                _uiState.update { it.copy(durationSeconds = elapsed) }
            }
        }
    }

    private fun stopDurationTimer() {
        durationJob?.cancel()
        durationJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopDurationTimer()
    }
}
