package com.red.sovereign.calls

import android.content.Context
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.BridgeResponse
import com.red.sovereign.auth.BridgeIceServerDto
import com.red.sovereign.auth.PstnApi
import com.red.sovereign.auth.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule
import com.red.sovereign.calls.WebRtcSipClient
import java.util.UUID

/**
 * Manages a WebRTC PeerConnection to Asterisk for PSTN calling via WSS.
 *
 * Flow:
 * 1. POST /api/pstn/bridge → get SIP credentials + WSS URL + ICE servers
 * 2. Register with Asterisk via SIP REGISTER over WSS
 * 3. Send SIP INVITE to target number
 * 4. Asterisk routes through from-red-client-webrtc → DINSTAR → GSM
 * 5. Audio flows bidirectionally over WebRTC
 *
 * This class does NOT use SIP protocol directly — it uses a SIP-over-WebSocket
 * library that speaks the SIP REGISTER/INVITE messages in the background.
 */
class PstnWebRtcManager(private val context: Context) {

    interface Events {
        fun onConnected()
        fun onRinging()
        fun onAnswered(usedToday: Int, dailyLimit: Int)
        fun onIncoming(sdp: String, fromNumber: String)
        fun onHangup(cause: String?)
        fun onError(message: String)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val egl = EglBase.create()
    private var factory: PeerConnectionFactory? = null
    private var peer: PeerConnection? = null
    private var audioDevice: JavaAudioDeviceModule? = null
    private var sipClient: WebRtcSipClient? = null
    private var callId: String? = null

    @Volatile
    var state: PstnCallState = PstnCallState.IDLE; private set
    var localAudioTrack: org.webrtc.AudioTrack? = null; private set
    var lastLocalSdp: String? = null; private set

    var isMuted: Boolean = false
        set(value) {
            field = value
            localAudioTrack?.setEnabled(!value)
        }

    var isSpeaker: Boolean = false
        set(value) {
            field = value
            // Speaker toggle is handled by Android AudioManager in the calling code
        }

    private fun ensureFactory() {
        if (factory != null) return
        WebRtcBootstrap.ensure(context)
        audioDevice = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(false)
            .setUseHardwareNoiseSuppressor(false)
            .createAudioDeviceModule()
        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDevice)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .createPeerConnectionFactory()
    }

    /**
     * Initiate a PSTN call via WebRTC bridge.
     * @param number Yemeni phone number (e.g., "+967777123456")
     * @param events callback interface for call events
     */
    suspend fun call(number: String, events: Events): PstnCallState = withContext(Dispatchers.IO) {
        if (state != PstnCallState.IDLE) {
            events.onError("ALREADY_IN_CALL")
            return@withContext state
        }
        state = PstnCallState.BRIDGING

        val api = PstnApi(TokenStore(context))
        val bridge = when (val result = api.bridge(number)) {
            is ApiResult.Success -> result.value
            is ApiResult.Error -> {
                val msg = result.message ?: "BRIDGE_FAILED"
                state = PstnCallState.ERROR
                events.onError(msg)
                return@withContext state
            }
        }

        callId = UUID.randomUUID().toString()

        ensureFactory()

        val servers = bridge.iceServers.iceServers.map { dto ->
            PeerConnection.IceServer.builder(dto.urls)
                .setUsername(dto.username.orEmpty())
                .setPassword(dto.credential.orEmpty())
                .createIceServer()
        }

        val config = PeerConnection.RTCConfiguration(servers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            keyType = PeerConnection.KeyType.ECDSA
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
            iceCandidatePoolSize = 2
        }

        val pc = factory!!.createPeerConnection(config, createObserver(events))
            ?: run {
                state = PstnCallState.ERROR
                events.onError("PEER_CONNECTION_FAILED")
                return@withContext state
            }
        peer = pc

        // Create audio-only track
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        }
        val audioSource = factory!!.createAudioSource(audioConstraints)
        val audioTrack = factory!!.createAudioTrack("pstn-audio", audioSource).apply { setEnabled(true) }
        pc.addTrack(audioTrack, listOf("pstn-stream"))
        localAudioTrack = audioTrack

        state = PstnCallState.REGISTERING
        // Register with Asterisk and initiate SIP INVITE via WebRTC
        sipClient = WebRtcSipClient(context, pc, object : WebRtcSipClient.Events {
            override fun onRegistered() {
                state = PstnCallState.INVITING
                sipClient?.invite(bridge.targetNumber)
            }
            override fun onInviteSent() {
                state = PstnCallState.RINGING
                events.onRinging()
            }
            override fun onAnswered() {
                state = PstnCallState.ACTIVE
                events.onAnswered(bridge.usedToday, bridge.dailyLimit)
            }
            override fun onIncomingInvite(sdp: String, fromNumber: String) {
                state = PstnCallState.INVITING
                val remoteSdp = SessionDescription(SessionDescription.Type.OFFER, sdp)
                pc.setRemoteDescription(object : org.webrtc.SdpObserver {
                    override fun onSetSuccess() {
                        pc.createAnswer(object : org.webrtc.SdpObserver {
                            override fun onCreateSuccess(answerSdp: SessionDescription) {
                                pc.setLocalDescription(object : org.webrtc.SdpObserver {
                                    override fun onSetSuccess() {
                                        sipClient?.sendAck()
                                        state = PstnCallState.ACTIVE
                                        events.onAnswered(bridge.usedToday, bridge.dailyLimit)
                                    }
                                    override fun onSetFailure(error: String) {
                                        events.onError("SET_ANSWER_FAILED: $error")
                                    }
                                    override fun onCreateSuccess(sdp: SessionDescription?) = Unit
                                    override fun onCreateFailure(error: String) = Unit
                                }, answerSdp)
                            }
                            override fun onCreateFailure(error: String) {
                                events.onError("CREATE_ANSWER_FAILED: $error")
                            }
                            override fun onSetSuccess() = Unit
                            override fun onSetFailure(error: String) = Unit
                        }, MediaConstraints())
                    }
                    override fun onCreateSuccess(sdp: SessionDescription?) = Unit
                    override fun onCreateFailure(error: String) = Unit
                    override fun onSetFailure(error: String) {
                        events.onError("SET_REMOTE_SDP_FAILED: $error")
                    }
                }, remoteSdp)
                events.onIncoming(sdp, fromNumber)
            }
            override fun onBye(cause: String?) {
                state = PstnCallState.ENDED
                events.onHangup(cause)
                release()
            }
            override fun onError(message: String) {
                state = PstnCallState.ERROR
                events.onError(message)
                release()
            }
        })

        sipClient?.register(
            sipServer = bridge.sipServer,
            username = bridge.sipUsername,
            password = bridge.sipPassword
        )

        state
    }

    fun hangup() {
        if (state == PstnCallState.IDLE || state == PstnCallState.ENDED) return
        sipClient?.bye()
        // Also notify backend to release the port lock
        val callId = this.callId ?: return
        scope.launch {
            runCatching {
                val api = PstnApi(TokenStore(context))
                api.hangup(callId)
            }
        }
        state = PstnCallState.ENDED
    }

    fun release() {
        sipClient?.dispose()
        sipClient = null
        peer?.close()
        peer?.dispose()
        peer = null
        localAudioTrack?.dispose()
        localAudioTrack = null
        audioDevice?.release()
        audioDevice = null
        factory?.dispose()
        factory = null
        egl.release()
        state = PstnCallState.IDLE
    }

    private fun createObserver(events: Events) = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
            if (state == PeerConnection.IceConnectionState.DISCONNECTED || state == PeerConnection.IceConnectionState.FAILED) {
                if (this@PstnWebRtcManager.state == PstnCallState.ACTIVE) {
                    this@PstnWebRtcManager.state = PstnCallState.ENDED
                    events.onHangup("ICE_DISCONNECTED")
                }
            }
        }
        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
        override fun onIceCandidate(candidate: IceCandidate) {
            sipClient?.addIceCandidate(candidate)
        }
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
        override fun onAddStream(stream: MediaStream) = Unit
        override fun onRemoveStream(stream: MediaStream) = Unit
        override fun onDataChannel(channel: DataChannel) = Unit
        override fun onRenegotiationNeeded() = Unit
        override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) = Unit
        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) = Unit
    }

    enum class PstnCallState {
        IDLE, BRIDGING, REGISTERING, INVITING, RINGING, ACTIVE, ENDED, ERROR
    }
}
