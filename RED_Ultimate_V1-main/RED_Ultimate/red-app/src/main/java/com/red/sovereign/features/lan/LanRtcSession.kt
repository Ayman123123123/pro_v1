package com.red.sovereign.features.lan

import android.content.Context
import com.red.sovereign.calls.WebRtcBootstrap
import com.red.sovereign.calls.WebRtcEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
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

/**
 * P2-LAN — جلسة WebRTC ذاتية 1:1 لنفس الواي فاي (بلا خادم ICE).
 *
 * - `RTCConfiguration(emptyList())`: مرشحو host فقط — كافٍ تماما داخل نفس
 *   الشبكة، ويمنع تسرب أي بايت إشارة لخارج LAN.
 * - TCP مفعّل (بعض نقاط الوصول تمنع UDP بين العملاء).
 * - الوسائط DTLS-SRTP افتراضيا (تشفير طرف-لطرف حقيقي على LAN).
 * - دورة حياة صريحة: [prepare] ثم عرض/إجابة ثم [release].
 */
class LanRtcSession(
    private val context: Context,
    private val events: Events
) {
    interface Events {
        fun onLocalDescription(description: SessionDescription)
        fun onIceCandidate(candidate: IceCandidate)
        fun onRemoteAudio(track: AudioTrack)
        fun onRemoteVideo(track: VideoTrack)
        fun onConnectionState(state: PeerConnection.PeerConnectionState)
        fun onError(message: String)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val egl = WebRtcBootstrap.sharedEgl
    val eglContext: EglBase.Context get() = egl.eglBaseContext

    private val hwAec = WebRtcEngine.hasVendorAudioEffect(
        android.media.audiofx.AcousticEchoCanceler::class.java,
        android.media.audiofx.AudioEffect.EFFECT_TYPE_AEC
    )
    private val hwNs = WebRtcEngine.hasVendorAudioEffect(
        android.media.audiofx.NoiseSuppressor::class.java,
        android.media.audiofx.AudioEffect.EFFECT_TYPE_NS
    )
    private val audioDevice = JavaAudioDeviceModule.builder(context)
        .setUseHardwareAcousticEchoCanceler(hwAec)
        .setUseHardwareNoiseSuppressor(hwNs)
        .createAudioDeviceModule()

    private val factory: PeerConnectionFactory
    private var peer: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var localAudio: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var capturer: VideoCapturer? = null
    private var textureHelper: SurfaceTextureHelper? = null
    private var localVideoTrack: VideoTrack? = null
    private var wantVideo = false
    private var haveLocalOffer = false
    private var remoteReady = false
    private val pendingIce = ArrayDeque<IceCandidate>()
    val localVideo: VideoTrack? get() = localVideoTrack

    init {
        WebRtcBootstrap.ensure(context)
        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDevice)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .createPeerConnectionFactory()
    }

    /** تهيئة الصوت (+ فيديو اختياريا). */
    fun prepare(video: Boolean) {
        wantVideo = video
        val audioConstraints = MediaConstraints().apply {
            if (!hwAec) mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            if (!hwNs) mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        }
        audioSource = factory.createAudioSource(audioConstraints)
        localAudio = factory.createAudioTrack("lan-audio", audioSource).apply { setEnabled(true) }
        if (video) startCapture()
    }

    fun attach() {
        if (peer != null) return
        // قائمة خوادم فارغة عمدا: host فقط داخل LAN — بلا STUN/TURN خارجي.
        val config = PeerConnection.RTCConfiguration(emptyList()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            candidateNetworkPolicy = PeerConnection.CandidateNetworkPolicy.ALL
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            keyType = PeerConnection.KeyType.ECDSA
            iceCandidatePoolSize = 2
        }
        peer = factory.createPeerConnection(config, observer)
        localAudio?.let { peer?.addTrack(it, listOf("lan-stream")) }
        localVideoTrack?.let { peer?.addTrack(it, listOf("lan-stream")) }
    }

    fun createOffer() {
        val pc = peer ?: run { events.onError("LAN_NO_PEER"); return }
        haveLocalOffer = true
        pc.createOffer(sdpObserver(setLocal = true), offerAnswerConstraints())
    }

    fun handleOffer(sdp: String) {
        if (sdp.isBlank()) return
        attach()
        val pc = peer ?: return
        haveLocalOffer = false
        pc.setRemoteDescription(sdpObserver(), SessionDescription(SessionDescription.Type.OFFER, sdp))
        drainPending()
        pc.createAnswer(sdpObserver(setLocal = true), offerAnswerConstraints())
    }

    fun handleAnswer(sdp: String) {
        if (sdp.isBlank()) return
        val pc = peer ?: return
        haveLocalOffer = false
        pc.setRemoteDescription(sdpObserver(), SessionDescription(SessionDescription.Type.ANSWER, sdp))
        drainPending()
    }

    fun handleIce(candidate: IceCandidate) {
        val pc = peer
        if (pc == null || !remoteReady) pendingIce.addLast(candidate)
        else pc.addIceCandidate(candidate)
    }

    fun setMicrophoneEnabled(enabled: Boolean) {
        localAudio?.setEnabled(enabled)
    }

    fun setCameraEnabled(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
    }

    fun switchCamera() {
        (capturer as? org.webrtc.CameraVideoCapturer)?.switchCamera(null)
    }

    fun release() {
        runCatching { capturer?.stopCapture() }
        runCatching { capturer?.dispose() }
        capturer = null
        runCatching { textureHelper?.dispose() }
        textureHelper = null
        runCatching { videoSource?.dispose() }
        videoSource = null
        runCatching { localVideoTrack?.dispose() }
        localVideoTrack = null
        runCatching { localAudio?.dispose() }
        localAudio = null
        runCatching { audioSource?.dispose() }
        audioSource = null
        runCatching { peer?.close() }
        runCatching { peer?.dispose() }
        peer = null
        runCatching { factory.dispose() }
        runCatching { audioDevice.release() }
        // FIX: sharedEgl لا يُحرر — كان يسبب EGL_BAD_CONTEXT للعرض الحي
        // runCatching { egl.release() }
        pendingIce.clear()
        haveLocalOffer = false
        remoteReady = false
    }

    private fun drainPending() {
        remoteReady = true
        val pc = peer ?: return
        while (pendingIce.isNotEmpty()) pc.addIceCandidate(pendingIce.removeFirst())
    }

    private fun startCapture() {
        if (localVideoTrack != null) return
        val enumerator = Camera2Enumerator(context)
        val names = enumerator.deviceNames
        val front = names.firstOrNull { enumerator.isFrontFacing(it) } ?: names.firstOrNull()
        if (front == null) {
            android.util.Log.w("LanRtcSession", "no camera — audio-only")
            wantVideo = false
            return
        }
        val cap: VideoCapturer? = enumerator.createCapturer(front, null)
        if (cap == null) {
            wantVideo = false
            return
        }
        textureHelper = SurfaceTextureHelper.create("lan-capture", egl.eglBaseContext)
        videoSource = factory.createVideoSource(cap.isScreencast)
        cap.initialize(textureHelper, context, videoSource?.capturerObserver)
        // محاولة بدقات متعددة — بعض الأجهزة ترفض 640x480 (نمط MeshRtcSession)
        var started = false
        for ((w, h, fps) in listOf(Triple(640, 480, 24), Triple(640, 360, 24), Triple(320, 240, 15))) {
            if (runCatching { cap.startCapture(w, h, fps) }.isSuccess) {
                started = true
                break
            }
            runCatching { cap.stopCapture() }
        }
        if (!started) {
            android.util.Log.w("LanRtcSession", "capture failed — audio-only")
            wantVideo = false
            return
        }
        capturer = cap
        localVideoTrack = factory.createVideoTrack("lan-video", videoSource)
        peer?.addTrack(localVideoTrack, listOf("lan-stream"))
    }

    private fun offerAnswerConstraints() = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", wantVideo.toString()))
    }

    private fun sdpObserver(setLocal: Boolean = false, after: (() -> Unit)? = null): SdpObserver = object : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription) {
            if (setLocal) {
                peer?.setLocalDescription(sdpObserver(after = {
                    events.onLocalDescription(desc)
                    after?.invoke()
                }), desc)
            } else {
                events.onLocalDescription(desc)
            }
        }

        override fun onSetSuccess() {
            after?.invoke()
        }

        override fun onCreateFailure(error: String) {
            events.onError("LAN_SDP_CREATE: $error")
        }

        override fun onSetFailure(error: String) {
            events.onError("LAN_SDP_SET: $error")
        }
    }

    private val observer = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) = Unit
        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
        override fun onIceCandidate(candidate: IceCandidate) {
            events.onIceCandidate(candidate)
        }

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
        override fun onAddStream(stream: MediaStream) {
            stream.audioTracks.forEach { track -> track.setEnabled(true); events.onRemoteAudio(track) }
            stream.videoTracks.forEach { events.onRemoteVideo(it) }
        }

        override fun onRemoveStream(stream: MediaStream) = Unit
        override fun onDataChannel(channel: DataChannel) = Unit
        override fun onRenegotiationNeeded() = Unit
        override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {
            val track = receiver.track()
            when (track) {
                is AudioTrack -> {
                    track.setEnabled(true)
                    events.onRemoteAudio(track)
                }
                is VideoTrack -> events.onRemoteVideo(track)
            }
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
            events.onConnectionState(newState)
        }
    }
}
