package com.red.sovereign.features.calls

import android.content.Context
import android.util.Log
import com.red.sovereign.features.calls.data.CallRepository
import com.red.sovereign.features.calls.data.IceServerDto
import com.red.sovereign.features.calls.data.CallResult
import com.red.sovereign.features.calls.signaling.CallSignalingClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import org.webrtc.*
import com.red.sovereign.features.calls.service.NetworkMonitor
import com.red.sovereign.features.calls.service.NetworkType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

// ─── Session State ─────────────────────────────────────────────────────────

enum class VoipState {
    IDLE,
    RINGING_OUTGOING,   // نحن من بدأ المكالمة
    RINGING_INCOMING,   // شخص آخر يتصل بنا
    CONNECTING,         // ICE negotiation جارية
    ACTIVE,             // المكالمة متصلة
    ON_HOLD,
    ENDED
}

data class VoipSession(
    val callId: String,
    val remoteUserId: String,
    val isVideo: Boolean,
    val isIncoming: Boolean,
    val startTime: Long = System.currentTimeMillis()
)

// ─── Callbacks ─────────────────────────────────────────────────────────────

interface VoipEventListener {
    fun onLocalVideoTrack(track: VideoTrack)
    fun onRemoteVideoTrack(track: VideoTrack)
    fun onRemoteAudioTrack(track: AudioTrack)
    fun onCallStateChanged(state: VoipState)
    fun onCallError(message: String)
    fun onIceConnected()
    fun onIceDisconnected()
}

// ─── RedVoipMaster ─────────────────────────────────────────────────────────

/**
 * RED VoIP Master — полный WebRTC движок для мکалмات P2P.
 *
 * دورة حياة المكالمة الصادرة:
 *  startSecureCall → fetchIce → createOffer → sendOffer → waitForAnswer → setRemoteAnswer → ICE → ACTIVE
 *
 * دورة حياة المكالمة الواردة:
 *  onIncomingOffer → answerCall → createAnswer → sendAnswer → ICE → ACTIVE
 */
@Singleton
class RedVoipMaster @Inject constructor(
    @ApplicationContext private val context: Context,
    private val signalingClient: CallSignalingClient,
    private val callRepository: CallRepository,
    private val networkMonitor: NetworkMonitor
) {
    companion object {
        private const val TAG = "RedVoipMaster"
    }

    // ── State ─────────────────────────────────────────────────────────

    private val _state = MutableStateFlow(VoipState.IDLE)
    val state: StateFlow<VoipState> = _state

    private val _session = MutableStateFlow<VoipSession?>(null)
    val session: StateFlow<VoipSession?> = _session

    private var eventListener: VoipEventListener? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ── WebRTC Objects ────────────────────────────────────────────────

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localVideoSource: VideoSource? = null
    private var localAudioSource: AudioSource? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoSender: RtpSender? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private var eglBase: EglBase? = null
    private var pendingIceCandidates = mutableListOf<IceCandidate>()
    
    private var networkJob: Job? = null
    private var isAutoFallbackTriggered = false

    // ── Initialization ────────────────────────────────────────────────

    fun initialize(eglBaseContext: EglBase.Context? = null) {
        if (peerConnectionFactory != null) return
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        eglBase = EglBase.create()
        val videoCodecHwAcceleration = VideoDecoderFallback(
            fallback = SoftwareVideoDecoderFactory(),
            primary = DefaultVideoDecoderFactory(eglBase!!.eglBaseContext)
        )
        val encoderFactory = DefaultVideoEncoderFactory(eglBase!!.eglBaseContext, true, true)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(videoCodecHwAcceleration)
            .setVideoEncoderFactory(encoderFactory)
            .setOptions(PeerConnectionFactory.Options().apply {
                disableEncryption = false
                disableNetworkMonitor = false
            })
            .createPeerConnectionFactory()

        Log.i(TAG, "PeerConnectionFactory initialized")
    }

    fun setEventListener(listener: VoipEventListener?) {
        eventListener = listener
    }

    fun getEglBaseContext(): EglBase.Context? = eglBase?.eglBaseContext

    // ── Outgoing Call ─────────────────────────────────────────────────

    fun startSecureCall(target: String, videoEnabled: Boolean = true): VoipSession {
        require(_state.value == VoipState.IDLE) { "A call is already active" }
        initialize()
        val callId = UUID.randomUUID().toString()
        val session = VoipSession(callId, target, videoEnabled, isIncoming = false)
        _session.value = session
        setState(VoipState.RINGING_OUTGOING)

        scope.launch {
            networkMonitor.startMonitoring()
            observeNetworkConditions()
            
            val iceResult = callRepository.getIceServers()
            val iceServers = when (iceResult) {
                is CallResult.Success -> iceResult.data.iceServers
                is CallResult.Error -> {
                    Log.w(TAG, "ICE fetch failed: ${iceResult.message}, using defaults")
                    emptyList()
                }
            }
            createPeerConnection(iceServers)
            if (videoEnabled) setupLocalVideo()
            setupLocalAudio()

            // Create offer
            val offerConstraints = buildSdpConstraints(receiveAudio = true, receiveVideo = videoEnabled)
            peerConnection?.createOffer(object : SdpObserverAdapter() {
                override fun onCreateSuccess(sdp: SessionDescription) {
                    peerConnection?.setLocalDescription(SdpObserverAdapter(), sdp)
                    signalingClient.sendOffer(
                        targetUserId = target,
                        callId = callId,
                        mode = if (videoEnabled) "VIDEO" else "VOICE",
                        sdp = sdp.description
                    )
                    Log.i(TAG, "OFFER sent to $target (callId=$callId)")
                }
                override fun onCreateFailure(error: String) {
                    handleError("Failed to create offer: $error")
                }
            }, offerConstraints)
        }
        return session
    }

    // ── Incoming Call ─────────────────────────────────────────────────

    fun onIncomingOffer(
        callId: String,
        fromUserId: String,
        sdp: String,
        mode: String
    ) {
        if (_state.value != VoipState.IDLE) {
            // Busy — reject immediately
            signalingClient.sendReject(fromUserId, callId)
            return
        }
        initialize()
        val isVideo = mode.equals("VIDEO", ignoreCase = true)
        val session = VoipSession(callId, fromUserId, isVideo, isIncoming = true)
        _session.value = session
        // Store SDP for later when user answers
        pendingRemoteSdp = SessionDescription(SessionDescription.Type.OFFER, sdp)
        setState(VoipState.RINGING_INCOMING)
    }

    private var pendingRemoteSdp: SessionDescription? = null

    fun answerCall() {
        val session = _session.value ?: return
        val remoteSdp = pendingRemoteSdp ?: return
        setState(VoipState.CONNECTING)

        scope.launch {
            networkMonitor.startMonitoring()
            observeNetworkConditions()
            
            val iceResult = callRepository.getIceServers()
            val iceServers = when (iceResult) {
                is CallResult.Success -> iceResult.data.iceServers
                is CallResult.Error -> emptyList()
            }
            createPeerConnection(iceServers)
            if (session.isVideo) setupLocalVideo()
            setupLocalAudio()

            // Set remote description (the offer)
            peerConnection?.setRemoteDescription(SdpObserverAdapter(), remoteSdp)

            // Add buffered ICE candidates
            pendingIceCandidates.forEach { peerConnection?.addIceCandidate(it) }
            pendingIceCandidates.clear()

            // Create answer
            val answerConstraints = buildSdpConstraints(receiveAudio = true, receiveVideo = session.isVideo)
            peerConnection?.createAnswer(object : SdpObserverAdapter() {
                override fun onCreateSuccess(sdp: SessionDescription) {
                    peerConnection?.setLocalDescription(SdpObserverAdapter(), sdp)
                    signalingClient.sendAnswer(
                        targetUserId = session.remoteUserId,
                        callId = session.callId,
                        mode = if (session.isVideo) "VIDEO" else "VOICE",
                        sdp = sdp.description
                    )
                    Log.i(TAG, "ANSWER sent to ${session.remoteUserId}")
                }
                override fun onCreateFailure(error: String) {
                    handleError("Failed to create answer: $error")
                }
            }, answerConstraints)
        }
    }

    // ── Handle Signaling Events ───────────────────────────────────────

    fun onRemoteAnswer(sdp: String) {
        val desc = SessionDescription(SessionDescription.Type.ANSWER, sdp)
        peerConnection?.setRemoteDescription(object : SdpObserverAdapter() {
            override fun onSetSuccess() {
                Log.i(TAG, "Remote ANSWER set — ICE negotiation in progress")
                // Add any pending ICE candidates received before answer
                pendingIceCandidates.forEach { peerConnection?.addIceCandidate(it) }
                pendingIceCandidates.clear()
            }
        }, desc)
    }

    fun onRemoteIceCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) {
        val ic = IceCandidate(sdpMid, sdpMLineIndex, candidate)
        if (peerConnection?.remoteDescription != null) {
            peerConnection?.addIceCandidate(ic)
        } else {
            pendingIceCandidates.add(ic)
        }
    }

    // ── Call Controls ─────────────────────────────────────────────────

    fun endCall() {
        val session = _session.value ?: return
        signalingClient.sendEnd(session.remoteUserId, session.callId)
        cleanup()
    }

    fun rejectCall() {
        val session = _session.value ?: return
        signalingClient.sendReject(session.remoteUserId, session.callId)
        cleanup()
    }

    fun holdCall() {
        val session = _session.value ?: return
        localAudioTrack?.setEnabled(false)
        signalingClient.sendHold(session.remoteUserId, session.callId)
        setState(VoipState.ON_HOLD)
    }

    fun resumeCall() {
        val session = _session.value ?: return
        localAudioTrack?.setEnabled(true)
        signalingClient.sendResume(session.remoteUserId, session.callId)
        setState(VoipState.ACTIVE)
    }

    fun toggleMute(muted: Boolean) {
        localAudioTrack?.setEnabled(!muted)
    }

    fun toggleVideo(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
    }

    fun switchCamera() {
        (videoCapturer as? CameraVideoCapturer)?.switchCamera(null)
    }

    fun isCallActive(): Boolean = _state.value == VoipState.ACTIVE
    fun getActiveSession(): VoipSession? = _session.value

    // ── Private: PeerConnection Setup ────────────────────────────────

    private fun observeNetworkConditions() {
        networkJob?.cancel()
        networkJob = scope.launch(Dispatchers.Main) {
            networkMonitor.networkState.collectLatest { state ->
                val session = _session.value ?: return@collectLatest
                if (_state.value != VoipState.ACTIVE) return@collectLatest

                // ICE Restart on Network Switch
                if (state.isConnected && state.type != NetworkType.UNKNOWN) {
                    Log.i(TAG, "Network switched to ${state.type}, attempting ICE restart...")
                    peerConnection?.createOffer(object : SdpObserverAdapter() {
                        override fun onCreateSuccess(sdp: SessionDescription) {
                            peerConnection?.setLocalDescription(SdpObserverAdapter(), sdp)
                            signalingClient.sendOffer(
                                targetUserId = session.remoteUserId,
                                callId = session.callId,
                                mode = if (session.isVideo) "VIDEO" else "VOICE",
                                sdp = sdp.description
                            )
                        }
                    }, buildSdpConstraints(receiveAudio = true, receiveVideo = session.isVideo).apply {
                        mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
                    })
                }

                // Auto-Fallback to Audio on Bad Network
                if (session.isVideo && state.isConnected) {
                    if (state.bandwidthKbps > 0 && state.bandwidthKbps < 150) {
                        if (!isAutoFallbackTriggered) {
                            Log.w(TAG, "Network condition is worst (${state.bandwidthKbps}kbps). Auto-falling back to Audio only.")
                            isAutoFallbackTriggered = true
                            localVideoTrack?.setEnabled(false)
                        }
                    } else if (isAutoFallbackTriggered && state.bandwidthKbps > 300) {
                        Log.i(TAG, "Network recovered (${state.bandwidthKbps}kbps). Resuming Video.")
                        isAutoFallbackTriggered = false
                        localVideoTrack?.setEnabled(true)
                    }
                }
            }
        }
    }

    private fun createPeerConnection(iceServersDto: List<IceServerDto>) {
        val rtcIceServers = iceServersDto.map { dto ->
            PeerConnection.IceServer.builder(dto.urls)
                .also { builder ->
                    if (!dto.username.isNullOrBlank()) builder.setUsername(dto.username)
                    if (!dto.credential.isNullOrBlank()) builder.setPassword(dto.credential)
                }
                .createIceServer()
        }.takeIf { it.isNotEmpty() } ?: listOf(
            // Fallback public STUN if ICE fetch fails
            PeerConnection.IceServer.builder(listOf("stun:stun.l.google.com:19302")).createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(rtcIceServers).apply {
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            candidateNetworkPolicy = PeerConnection.CandidateNetworkPolicy.ALL
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                val sess = _session.value ?: return
                signalingClient.sendIceCandidate(
                    targetUserId = sess.remoteUserId,
                    callId = sess.callId,
                    candidate = candidate.sdp,
                    sdpMid = candidate.sdpMid,
                    sdpMLineIndex = candidate.sdpMLineIndex
                )
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                Log.d(TAG, "ICE state: $state")
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> {
                        setState(VoipState.ACTIVE)
                        scope.launch(Dispatchers.Main) { eventListener?.onIceConnected() }
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        scope.launch(Dispatchers.Main) { eventListener?.onIceDisconnected() }
                    }
                    PeerConnection.IceConnectionState.FAILED -> handleError("ICE connection failed")
                    PeerConnection.IceConnectionState.CLOSED -> cleanup()
                    else -> {}
                }
            }

            override fun onTrack(transceiver: RtpTransceiver) {
                val track = transceiver.receiver.track() ?: return
                scope.launch(Dispatchers.Main) {
                    when (track) {
                        is VideoTrack -> {
                            track.setEnabled(true)
                            eventListener?.onRemoteVideoTrack(track)
                        }
                        is AudioTrack -> {
                            track.setEnabled(true)
                            eventListener?.onRemoteAudioTrack(track)
                        }
                    }
                }
            }

            override fun onAddStream(stream: MediaStream) {}
            override fun onRemoveStream(stream: MediaStream) {}
            override fun onDataChannel(dc: DataChannel) {}
            override fun onRenegotiationNeeded() {}
            override fun onSignalingChange(state: PeerConnection.SignalingState) {}
            override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {}
            override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {}
            override fun onSelectedCandidatePairChanged(event: CandidatePairChangeEvent) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        }) ?: run {
            handleError("Failed to create PeerConnection")
            return
        }
    }

    private fun setupLocalAudio() {
        val factory = peerConnectionFactory ?: return
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        }
        localAudioSource = factory.createAudioSource(audioConstraints)
        localAudioTrack = factory.createAudioTrack("red_audio_0", localAudioSource).apply {
            setEnabled(true)
        }
        peerConnection?.addTrack(localAudioTrack!!, listOf("red_stream_0"))
    }

    private fun setupLocalVideo() {
        val factory = peerConnectionFactory ?: return
        videoCapturer = createCameraCapturer()
        val surfaceHelper = SurfaceTextureHelper.create("CaptureThread", eglBase!!.eglBaseContext)
        localVideoSource = factory.createVideoSource(false)
        videoCapturer?.initialize(surfaceHelper, context, localVideoSource!!.capturerObserver)
        videoCapturer?.startCapture(1280, 720, 30)

        localVideoTrack = factory.createVideoTrack("red_video_0", localVideoSource).apply {
            setEnabled(true)
        }
        
        val init = RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV)
        val transceiver = peerConnection?.addTransceiver(localVideoTrack, init)
        videoSender = transceiver?.sender
        
        // Configure Simulcast (3 layers) for worst conditions
        videoSender?.parameters?.let { parameters ->
            if (parameters.encodings.isEmpty()) {
                parameters.encodings.add(RtpParameters.Encoding("f", true, 1.0))
            }
            // If the WebRTC implementation supports configuring multiple encodings directly
            if (parameters.encodings.size == 1) {
                // High Quality
                parameters.encodings[0].apply {
                    active = true
                    maxBitrateBps = 1500000
                    scaleResolutionDownBy = 1.0
                }
            }
            peerConnection?.setBitrate(200_000, 1_500_000, 2_000_000)
            videoSender?.parameters = parameters
        }

        scope.launch(Dispatchers.Main) {
            eventListener?.onLocalVideoTrack(localVideoTrack!!)
        }
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(context)
        // Prefer front camera for calls
        for (name in enumerator.deviceNames) {
            if (enumerator.isFrontFacing(name)) {
                return enumerator.createCapturer(name, null)
            }
        }
        for (name in enumerator.deviceNames) {
            if (enumerator.isBackFacing(name)) {
                return enumerator.createCapturer(name, null)
            }
        }
        return null
    }

    private fun buildSdpConstraints(receiveAudio: Boolean, receiveVideo: Boolean) = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", receiveAudio.toString()))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", receiveVideo.toString()))
    }

    // ── Cleanup ───────────────────────────────────────────────────────

    private fun cleanup() {
        setState(VoipState.ENDED)
        scope.launch(Dispatchers.Main) {
            delay(500) // Let UI see ENDED state briefly
            setState(VoipState.IDLE)
            _session.value = null
        }

        networkMonitor.stopMonitoring()
        networkJob?.cancel()
        networkJob = null
        isAutoFallbackTriggered = false

        videoCapturer?.stopCapture()
        videoCapturer?.dispose()
        videoCapturer = null

        localVideoTrack?.dispose()
        localVideoTrack = null
        localAudioTrack?.dispose()
        localAudioTrack = null

        localVideoSource?.dispose()
        localVideoSource = null
        localAudioSource?.dispose()
        localAudioSource = null

        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null

        pendingIceCandidates.clear()
        pendingRemoteSdp = null
    }

    private fun setState(state: VoipState) {
        _state.value = state
        scope.launch(Dispatchers.Main) { eventListener?.onCallStateChanged(state) }
    }

    private fun handleError(message: String) {
        Log.e(TAG, "VoIP Error: $message")
        scope.launch(Dispatchers.Main) { eventListener?.onCallError(message) }
        cleanup()
    }
}

// ─── SdpObserver Adapter ──────────────────────────────────────────────────

open class SdpObserverAdapter : SdpObserver {
    override fun onCreateSuccess(sdp: SessionDescription) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String) { Log.e("SdpObserver", "createFailure: $error") }
    override fun onSetFailure(error: String) { Log.e("SdpObserver", "setFailure: $error") }
}
