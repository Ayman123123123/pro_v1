package com.red.sovereign.calls

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class PstnWebRtcManager(private val context: Context) {

    interface Events {
        fun onConnected() {}
        fun onRinging() {}
        fun onAnswered(usedToday: Int, dailyLimit: Int) {}
        fun onIncoming(sdp: String, fromNumber: String) {}
        fun onHangup(cause: String?) {}
        fun onError(message: String) {}
        fun onEarlyMedia() {}
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    var state: PstnCallState = PstnCallState.IDLE
        private set(value) {
            field = value
            _stateFlow.value = value
        }
    private val _stateFlow = MutableStateFlow(PstnCallState.IDLE)
    val stateFlow: StateFlow<PstnCallState> = _stateFlow
    var localAudioTrack: org.webrtc.AudioTrack? = null; private set
    var lastLocalSdp: String? = null; private set
    val currentCallId: String? get() = null
    @Volatile var remoteNumber: String? = null; private set
    var isMuted: Boolean = false
    var isSpeaker: Boolean = false

    fun ensureAudioSetup() {}
    fun sendDtmf(digits: String): Boolean = false
    fun hangup() {}
    fun answerIncoming(offerSdp: String? = null) {}
    fun rejectIncoming() {}
    fun release() {}
    fun startIncomingListener(callId: String) {}
    fun acceptIncomingListener() {}
    fun stopIncomingListener() {}

    suspend fun call(number: String, events: Events): PstnCallState = state

    enum class PstnCallState {
        IDLE, BRIDGING, REGISTERING, INVITING, RINGING,
        EARLY_MEDIA,
        ACTIVE, ENDED, ERROR
    }

    companion object {
        @Volatile private var incomingInstance: PstnWebRtcManager? = null
        fun incoming(context: Context): PstnWebRtcManager =
            incomingInstance ?: synchronized(this) {
                incomingInstance ?: PstnWebRtcManager(context.applicationContext).also { incomingInstance = it }
            }
        @Volatile var activeUi: PstnWebRtcManager? = null
            private set
        fun controls(context: Context): PstnWebRtcManager? = activeUi ?: incomingInstance
    }
}
