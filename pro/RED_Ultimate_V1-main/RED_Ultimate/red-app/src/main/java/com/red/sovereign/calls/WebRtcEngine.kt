package com.red.sovereign.calls

import android.content.Context
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
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

@Serializable data class IceConfigurationDto(val expiresAt: Long, val iceServers: List<IceServerDto>)
@Serializable data class IceServerDto(val urls: List<String>, val username: String? = null, val credential: String? = null)

data class LocalMedia(val audioTrack: AudioTrack, val videoTrack: VideoTrack?)

class WebRtcEngine(private val context: Context, private val events: Events) {
    interface Events {
        fun onLocalDescription(description: SessionDescription)
        fun onIceCandidate(candidate: IceCandidate)
        fun onRemoteVideo(track: VideoTrack)
        fun onConnectionState(state: PeerConnection.PeerConnectionState)
        fun onError(message: String)
    }

    private val egl = EglBase.create()
    val eglContext: EglBase.Context get() = egl.eglBaseContext
    private val audioDevice = JavaAudioDeviceModule.builder(context)
        .setUseHardwareAcousticEchoCanceler(true)
        .setUseHardwareNoiseSuppressor(true)
        .createAudioDeviceModule()
    private val factory: PeerConnectionFactory
    private var peer: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var videoSource: VideoSource? = null
    private var capturer: VideoCapturer? = null
    private var textureHelper: SurfaceTextureHelper? = null
    var localMedia: LocalMedia? = null; private set

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .setEnableInternalTracer(false).createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDevice)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .createPeerConnectionFactory()
    }

    suspend fun create(video: Boolean): ApiResult<Unit> {
        val ice = loadIce() ?: return ApiResult.Error(null, "ICE_CONFIGURATION_FAILED")
        val servers = ice.iceServers.map { value ->
            PeerConnection.IceServer.builder(value.urls)
                .setUsername(value.username.orEmpty()).setPassword(value.credential.orEmpty()).createIceServer()
        }
        val config = PeerConnection.RTCConfiguration(servers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        }
        peer = factory.createPeerConnection(config, observer) ?: return ApiResult.Error(null, "PEER_CONNECTION_FAILED")
        audioSource = factory.createAudioSource(MediaConstraints())
        val audio = factory.createAudioTrack("younes-audio", audioSource).apply { setEnabled(true) }
        peer?.addTrack(audio, listOf("younes-stream"))
        val videoTrack = if (video) createVideoTrack() else null
        if (videoTrack != null) peer?.addTrack(videoTrack, listOf("younes-stream"))
        localMedia = LocalMedia(audio, videoTrack)
        return ApiResult.Success(200, Unit)
    }

    fun offer() = peer?.createOffer(sdpObserver(setLocal = true), MediaConstraints())
    fun answer() = peer?.createAnswer(sdpObserver(setLocal = true), MediaConstraints())
    fun setRemote(description: SessionDescription, after: (() -> Unit)? = null) = peer?.setRemoteDescription(sdpObserver(after = after), description)
    fun addIce(candidate: IceCandidate) { peer?.addIceCandidate(candidate) }
    fun setMicrophoneEnabled(enabled: Boolean) { localMedia?.audioTrack?.setEnabled(enabled) }
    fun setCameraEnabled(enabled: Boolean) { localMedia?.videoTrack?.setEnabled(enabled) }
    fun switchCamera() { (capturer as? org.webrtc.CameraVideoCapturer)?.switchCamera(null) }

    fun release() {
        runCatching { capturer?.stopCapture() }; capturer?.dispose(); textureHelper?.dispose()
        localMedia?.audioTrack?.dispose(); localMedia?.videoTrack?.dispose(); audioSource?.dispose(); videoSource?.dispose()
        peer?.close(); peer?.dispose(); factory.dispose(); audioDevice.release(); egl.release()
        peer = null; localMedia = null
    }

    private suspend fun loadIce(): IceConfigurationDto? = withContext(Dispatchers.IO) {
        val client = AuthorizedApiClient(TokenStore(context)); val json = Json { ignoreUnknownKeys = true }
        when (val response = client.request("GET", "/api/calls/ice-servers")) {
            is ApiResult.Success -> runCatching { json.decodeFromString<IceConfigurationDto>(response.value) }.getOrNull()
            is ApiResult.Error -> null
        }
    }

    private fun createVideoTrack(): VideoTrack? {
        val selected = camera(Camera2Enumerator(context)) ?: return null
        capturer = selected
        videoSource = factory.createVideoSource(false)
        textureHelper = SurfaceTextureHelper.create("YounesCamera", egl.eglBaseContext)
        selected.initialize(textureHelper, context, videoSource?.capturerObserver)
        selected.startCapture(1280, 720, 30)
        return factory.createVideoTrack("younes-video", videoSource).apply { setEnabled(true) }
    }

    private fun camera(enumerator: CameraEnumerator): VideoCapturer? {
        enumerator.deviceNames.firstOrNull(enumerator::isFrontFacing)?.let { enumerator.createCapturer(it, null)?.let { camera -> return camera } }
        return enumerator.deviceNames.firstNotNullOfOrNull { enumerator.createCapturer(it, null) }
    }

    private fun sdpObserver(setLocal: Boolean = false, after: (() -> Unit)? = null): SdpObserver = object : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) {
            if (setLocal) peer?.setLocalDescription(sdpObserver(after = { events.onLocalDescription(description); after?.invoke() }), description)
        }
        override fun onSetSuccess() { after?.invoke() }
        override fun onCreateFailure(error: String) = events.onError(error)
        override fun onSetFailure(error: String) = events.onError(error)
    }

    private val observer = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) = Unit
        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
        override fun onIceCandidate(candidate: IceCandidate) = events.onIceCandidate(candidate)
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
        override fun onAddStream(stream: MediaStream) = Unit
        override fun onRemoveStream(stream: MediaStream) = Unit
        override fun onDataChannel(channel: DataChannel) = Unit
        override fun onRenegotiationNeeded() = Unit
        override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) { (receiver.track() as? VideoTrack)?.let(events::onRemoteVideo) }
        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) = events.onConnectionState(newState)
    }
}
