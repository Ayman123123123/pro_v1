package com.red.sovereign.calls

import android.content.Context
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraEnumerator
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
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.ConcurrentHashMap

/**
 * One shared camera/mic + one PeerConnection per remote user.
 * This is how a 3+ person WhatsApp/Telegram voice chat actually works on mesh
 * before an SFU takes over. A single WebRtcEngine cannot do that.
 */
class MeshRtcSession(
    private val context: Context,
    private val localUserId: String,
    private val events: Events
) {
    interface Events {
        fun onLocalDescription(peerId: String, description: SessionDescription)
        fun onIceCandidate(peerId: String, candidate: IceCandidate)
        fun onRemoteVideo(peerId: String, track: VideoTrack)
        fun onConnectionState(peerId: String, state: PeerConnection.PeerConnectionState)
        fun onNetworkStats(stats: NetworkStats)
        fun onError(message: String)
    }

    private val egl = EglBase.create()
    val eglContext: EglBase.Context get() = egl.eglBaseContext

    private val audioDevice = JavaAudioDeviceModule.builder(context)
        .setUseHardwareAcousticEchoCanceler(false)
        .setUseHardwareNoiseSuppressor(false)
        .createAudioDeviceModule()

    private val factory: PeerConnectionFactory
    private val peers = ConcurrentHashMap<String, PeerSlot>()
    private var audioSource: AudioSource? = null
    private var videoSource: VideoSource? = null
    private var capturer: VideoCapturer? = null
    private var textureHelper: SurfaceTextureHelper? = null
    private var localAudio: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null
    private var iceServers: List<PeerConnection.IceServer> = emptyList()
    private var mediaKind: CallMediaKind = CallMediaKind.SPACE
    private var cameraRequested = true
    val localVideo: VideoTrack? get() = localVideoTrack

    init {
        WebRtcBootstrap.ensure(context)
        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDevice)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .createPeerConnectionFactory()
    }

    suspend fun start(kind: CallMediaKind): ApiResult<Unit> {
        mediaKind = kind
        cameraRequested = kind.wantsVideo
        val ice = loadIce() ?: return ApiResult.Error(null, "ICE_CONFIGURATION_FAILED")
        iceServers = ice.iceServers.map { value ->
            PeerConnection.IceServer.builder(value.urls)
                .setUsername(value.username.orEmpty())
                .setPassword(value.credential.orEmpty())
                .createIceServer()
        }
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("stereo", if (kind.stereoAudio) "true" else "false"))
        }
        audioSource = factory.createAudioSource(audioConstraints)
        localAudio = factory.createAudioTrack("younes-mesh-audio", audioSource).apply { setEnabled(true) }
        if (kind.wantsVideo) {
            localVideoTrack = createVideoTrack()
        }
        return ApiResult.Success(200, Unit)
    }

    fun attachPeer(peerId: String): Boolean {
        if (peerId.isBlank() || peerId == localUserId) return false
        if (peers.containsKey(peerId)) return true
        if (!MeshNegotiation.canAttach(peers.size, false)) {
            events.onError("MESH_PEER_LIMIT")
            return false
        }
        val config = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
            iceCandidatePoolSize = 0
        }
        val slot = PeerSlot(peerId)
        val pc = factory.createPeerConnection(config, slot.observer) ?: return false
        slot.peer = pc
        localAudio?.let { pc.addTrack(it, listOf("younes-mesh")) }
        localVideoTrack?.let { pc.addTrack(it, listOf("younes-mesh")) }
        peers[peerId] = slot
        return true
    }

    fun detachPeer(peerId: String) {
        peers.remove(peerId)?.release()
    }

    fun offerTo(peerId: String) {
        val slot = peers[peerId] ?: return
        slot.haveLocalOffer = true
        slot.peer?.createOffer(slot.sdpObserver(setLocal = true), offerAnswerConstraints())
    }

    fun handleOffer(fromUserId: String, sdp: String) {
        if (fromUserId.isBlank() || sdp.isBlank()) return
        attachPeer(fromUserId)
        val slot = peers[fromUserId] ?: return
        if (!MeshNegotiation.shouldAcceptRemoteOffer(localUserId, fromUserId, slot.haveLocalOffer)) return
        slot.haveLocalOffer = false
        slot.setRemote(SessionDescription(SessionDescription.Type.OFFER, sdp)) {
            slot.peer?.createAnswer(slot.sdpObserver(setLocal = true), offerAnswerConstraints())
        }
    }

    fun handleAnswer(fromUserId: String, sdp: String) {
        val slot = peers[fromUserId] ?: return
        slot.haveLocalOffer = false
        slot.setRemote(SessionDescription(SessionDescription.Type.ANSWER, sdp))
    }

    fun handleIce(fromUserId: String, candidate: IceCandidate) {
        val slot = peers[fromUserId] ?: return
        if (slot.remoteReady) slot.peer?.addIceCandidate(candidate) else slot.pendingIce += candidate
    }

    fun setMicrophoneEnabled(enabled: Boolean) {
        localAudio?.setEnabled(enabled)
    }

    fun setCameraEnabled(enabled: Boolean) {
        cameraRequested = enabled
        localVideoTrack?.setEnabled(enabled && mediaKind.wantsVideo)
    }

    fun switchCamera() {
        (capturer as? org.webrtc.CameraVideoCapturer)?.switchCamera(null)
    }

    fun restartIce() {
        peers.values.forEach { slot ->
            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
            }
            slot.haveLocalOffer = true
            slot.peer?.createOffer(slot.sdpObserver(setLocal = true), constraints)
        }
    }

    fun pollStats() {
        peers.values.firstOrNull()?.peer?.getStats { report ->
            var rtt = 0L
            var lost = 0L
            var received = 0L
            report.statsMap.values.forEach { stat ->
                when (stat.type) {
                    "remote-inbound-rtp" -> rtt = ((stat.members["roundTripTime"] as? Number)?.toDouble() ?: 0.0).times(1000).toLong()
                    "inbound-rtp" -> {
                        lost += (stat.members["packetsLost"] as? Number)?.toLong() ?: 0L
                        received += (stat.members["packetsReceived"] as? Number)?.toLong() ?: 0L
                    }
                }
            }
            val total = lost + received
            val loss = if (total > 0) lost.toDouble() / total * 100 else 0.0
            events.onNetworkStats(NetworkStats(rtt, loss, 0, 0, 0, 0, NetworkStats.classify(rtt, loss)))
        }
    }

    fun release() {
        peers.keys.toList().forEach(::detachPeer)
        runCatching { capturer?.stopCapture() }
        capturer?.dispose()
        textureHelper?.dispose()
        localAudio?.dispose()
        localVideoTrack?.dispose()
        audioSource?.dispose()
        videoSource?.dispose()
        factory.dispose()
        audioDevice.release()
        egl.release()
        localAudio = null
        localVideoTrack = null
    }

    private fun offerAnswerConstraints() = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (mediaKind.wantsVideo) "true" else "false"))
    }

    private suspend fun loadIce(): IceConfigurationDto? = withContext(Dispatchers.IO) {
        val client = AuthorizedApiClient(TokenStore(context))
        val json = Json { ignoreUnknownKeys = true }
        when (val response = client.request("GET", "/api/calls/ice-servers")) {
            is ApiResult.Success -> runCatching { json.decodeFromString<IceConfigurationDto>(response.value) }.getOrNull()
            is ApiResult.Error -> null
        }
    }

    private fun createVideoTrack(): VideoTrack? {
        val enumerator = Camera2Enumerator(context)
        val selected = enumerator.deviceNames.firstOrNull(enumerator::isFrontFacing)?.let { enumerator.createCapturer(it, null) }
            ?: enumerator.deviceNames.firstNotNullOfOrNull { enumerator.createCapturer(it, null) }
            ?: return null
        capturer = selected
        videoSource = factory.createVideoSource(false)
        textureHelper = SurfaceTextureHelper.create("YounesMeshCamera", egl.eglBaseContext)
        selected.initialize(textureHelper, context, videoSource?.capturerObserver)
        selected.startCapture(640, 480, 24)
        return factory.createVideoTrack("younes-mesh-video", videoSource).apply { setEnabled(cameraRequested) }
    }

    private inner class PeerSlot(val peerId: String) {
        var peer: PeerConnection? = null
        var haveLocalOffer = false
        var remoteReady = false
        val pendingIce = mutableListOf<IceCandidate>()

        fun setRemote(description: SessionDescription, after: (() -> Unit)? = null) {
            peer?.setRemoteDescription(sdpObserver(after = {
                remoteReady = true
                pendingIce.forEach { peer?.addIceCandidate(it) }
                pendingIce.clear()
                after?.invoke()
            }), description)
        }

        fun sdpObserver(setLocal: Boolean = false, after: (() -> Unit)? = null): SdpObserver = object : SdpObserver {
            override fun onCreateSuccess(description: SessionDescription) {
                val optimized = SessionDescription(description.type, SdpMediaOptimizer.optimize(description.description, mediaKind))
                if (setLocal) {
                    peer?.setLocalDescription(sdpObserver(after = {
                        events.onLocalDescription(peerId, optimized)
                        after?.invoke()
                    }), optimized)
                }
            }
            override fun onSetSuccess() { after?.invoke() }
            override fun onCreateFailure(error: String) = events.onError(error)
            override fun onSetFailure(error: String) = events.onError(error)
        }

        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) = Unit
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
            override fun onIceCandidate(candidate: IceCandidate) = events.onIceCandidate(peerId, candidate)
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
            override fun onAddStream(stream: MediaStream) = Unit
            override fun onRemoveStream(stream: MediaStream) = Unit
            override fun onDataChannel(channel: DataChannel) = Unit
            override fun onRenegotiationNeeded() = Unit
            override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {
                (receiver.track() as? VideoTrack)?.let { events.onRemoteVideo(peerId, it) }
            }
            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                events.onConnectionState(peerId, newState)
            }
        }

        fun release() {
            peer?.close()
            peer?.dispose()
            peer = null
            pendingIce.clear()
        }
    }
}
