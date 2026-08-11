package com.red.sovereign.calls

import android.content.Context
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.core.RedQualityManager
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
import org.webrtc.RtpSender
import org.webrtc.RtpParameters
import org.webrtc.RtpTransceiver
import org.webrtc.RTCStatsReport
import org.webrtc.RTCStatsCollectorCallback
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

/**
 * Real-time network quality stats for a call. Updated by [WebRtcEngine.pollStats].
 * Drives adaptive bitrate control and UI indicators.
 */
data class NetworkStats(
    val rttMs: Long = 0L,
    val packetLossPercent: Double = 0.0,
    val bandwidthKbps: Long = 0L,
    val availableBitrateKbps: Long = 0L,
    val jitterMs: Long = 0L,
    val framesPerSecond: Int = 0,
    val quality: Quality = Quality.UNKNOWN
) {
    enum class Quality { UNKNOWN, POOR, FAIR, GOOD, EXCELLENT }

    companion object {
        /**
         * تصنيف الجودة بناء على RTT وفقدان الحزم. الـ thresholds مأخوذة من توصيات WebRTC
         * للجودة الممتازة / الجيدة / المقبولة / السيئة.
         */
        fun classify(rttMs: Long, lossPct: Double, availableKbps: Long = 0): Quality = when {
            rttMs == 0L && availableKbps == 0L -> Quality.UNKNOWN
            rttMs > 400 || lossPct > 10 -> Quality.POOR
            rttMs > 200 || lossPct > 5 -> Quality.FAIR
            rttMs > 100 || lossPct > 2 -> Quality.GOOD
            else -> Quality.EXCELLENT
        }

        /**
         * يختار maxBitrate وmaxFramerate بناءً على الجودة المكتشفة.
         * الـ "Profile" ثابت لكن نطبقه ديناميكياً بناءً على الإحصائيات.
         */
        fun recommendBitrate(quality: Quality): BitrateProfile = when (quality) {
            Quality.UNKNOWN -> BitrateProfile.STANDARD
            Quality.POOR -> BitrateProfile.AUDIO_ONLY
            Quality.FAIR -> BitrateProfile.LOW
            Quality.GOOD -> BitrateProfile.STANDARD
            Quality.EXCELLENT -> BitrateProfile.HD
        }
    }

    enum class BitrateProfile(
        val videoMaxBitrateKbps: Int,
        val videoFramerate: Int,
        val videoWidth: Int,
        val videoHeight: Int
    ) {
        AUDIO_ONLY(0, 0, 0, 0),    // نوقف الفيديو
        LOW(200, 15, 320, 240),     // 240p @ 15fps
        STANDARD(800, 24, 640, 480), // 480p @ 24fps
        HD(1800, 30, 1280, 720)    // 720p @ 30fps
    }
}

/**
 * WebRtcEngine — المحرك الأساسي للمكالمات.
 * يتضمن:
 * - Audio constraints كاملة (AEC, NS, AGC, HighPass, Stereo, TypingNoise)
 * - Hardware vs Software AEC toggle
 * - Video simulcast (3 طبقات: HD, SD, LD)
 * - Adaptive bitrate بناءً على NetworkStats
 * - Connection state machine كامل
 * - ICE servers من backend (HMAC time-limited)
 */
class WebRtcEngine(private val context: Context, private val events: Events) {
    interface Events {
        fun onLocalDescription(description: SessionDescription)
        fun onIceCandidate(candidate: IceCandidate)
        fun onRemoteVideo(track: VideoTrack)
        fun onConnectionState(state: PeerConnection.PeerConnectionState)
        fun onNetworkStats(stats: NetworkStats)
        fun onError(message: String)
    }

    private val egl = EglBase.create()
    val eglContext: EglBase.Context get() = egl.eglBaseContext

    /**
     * Hardware vs Software AEC:
     * - WebRTC's built-in AEC is generally more reliable across devices.
     * - Hardware AEC varies wildly by device + OS version.
     * - We disable HW AEC + NS to use WebRTC's software implementation.
     * This is the production-recommended setup per WebRTC maintainers.
     */
    private val audioDevice = JavaAudioDeviceModule.builder(context)
        .setUseHardwareAcousticEchoCanceler(false)
        .setUseHardwareNoiseSuppressor(false)
        .setAudioRecordErrorCallback { error -> events.onError("AUDIO_RECORD_ERROR: $error") }
        .setAudioTrackErrorCallback { error -> events.onError("AUDIO_TRACK_ERROR: $error") }
        .createAudioDeviceModule()

    private val factory: PeerConnectionFactory
    private var peer: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var videoSource: VideoSource? = null
    private var capturer: VideoCapturer? = null
    private var textureHelper: SurfaceTextureHelper? = null
    private var videoSender: RtpSender? = null
    var localMedia: LocalMedia? = null; private set

    // الإعدادات الحالية
    private var currentBitrateProfile: NetworkStats.BitrateProfile = NetworkStats.BitrateProfile.STANDARD
    private var cameraRequestedByUser: Boolean = true
    private var hasVideo: Boolean = false
    private var svcEnabled: Boolean = false
    private var lastStats: NetworkStats = NetworkStats()

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .setEnableInternalTracer(false)
                .setFieldTrials("WebRTC-Audio-MinimizeResamplingOnMobile/Enabled/")
                .createInitializationOptions()
        )
        // Encoder/decoder factories مع hardware acceleration حيث متاح
        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDevice)
            .setVideoEncoderFactory(
                DefaultVideoEncoderFactory(egl.eglBaseContext, true /* enableIntelVp8Encoder */, true /* enableH264HighProfile */)
            )
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .createPeerConnectionFactory()
    }

    /**
     * ينشئ PeerConnection مع ICE servers و media tracks.
     * @param video true إذا مكالمة فيديو
     * @param simulcastEnabled true لإرسال 3 طبقات (HD/SD/LD) — يقلل الـ bandwidth للـ SFU
     */
    suspend fun create(video: Boolean, simulcastEnabled: Boolean = true, svc: Boolean = false): ApiResult<Unit> {
        hasVideo = video
        cameraRequestedByUser = video
        svcEnabled = svc
        currentBitrateProfile = initialBitrateProfile()
        val ice = loadIce() ?: return ApiResult.Error(null, "ICE_CONFIGURATION_FAILED")
        val servers = ice.iceServers.map { value ->
            PeerConnection.IceServer.builder(value.urls)
                .setUsername(value.username.orEmpty())
                .setPassword(value.credential.orEmpty())
                .createIceServer()
        }
        val config = PeerConnection.RTCConfiguration(servers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            // استخدام unified plan + multi-stream
            keyType = PeerConnection.KeyType.ECDSA
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
            // Enable DTLS 1.2+ for security
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        peer = factory.createPeerConnection(config, observer) ?: return ApiResult.Error(null, "PEER_CONNECTION_FAILED")

        // Audio constraints كاملة — AEC, NS, AGC, HighPass, Stereo, TypingNoise
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation2", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googDAEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl2", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression2", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googTypingNoiseDetection", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAudioMirroring", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("stereo", "true"))
        }
        audioSource = factory.createAudioSource(audioConstraints)
        val audio = factory.createAudioTrack("younes-audio", audioSource).apply { setEnabled(true) }
        peer?.addTrack(audio, listOf("younes-stream"))

        val videoTrack = if (video) createVideoTrack() else null
        if (videoTrack != null) {
            val sender = peer?.addTrack(videoTrack, listOf("younes-stream"))
            videoSender = sender
            // ضبط أولوية الكوديكس بناءً على نوع المكالمة (H.264 للفردي / VP9 للجماعي)
            applyCodecPreferences(svcEnabled)
            // Simulcast أو VP9-SVC حسب الوضع
            if (simulcastEnabled) {
                applySimulcast(sender, currentBitrateProfile, svcEnabled)
            }
        }
        localMedia = LocalMedia(audio, videoTrack)
        return ApiResult.Success(200, Unit)
    }

    /**
     * تضبط أولوية الكوديكس عبر RtpTransceiver.setCodecPreferences.
     * المكالمات الفردية 1:1 -> تفضيل H.264 (تسريع عتادي وتقليل حرارة الجهاز واستهلاك البطارية).
     * المؤتمرات والجماعي -> تفضيل VP9 / VP9-SVC (توفير البنطاق العريض وطبقات الجودة التكيفية).
     */
    private fun applyCodecPreferences(svcEnabled: Boolean) {
        val pc = peer ?: return
        val videoTransceivers = pc.transceivers.filter {
            it.mediaType == org.webrtc.MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO
        }
        if (videoTransceivers.isEmpty()) return

        val capabilities = factory.getRtpSenderCapabilities(org.webrtc.MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO)
        val sortedCodecs = capabilities.codecs.sortedByDescending { codec ->
            when {
                svcEnabled && codec.name.equals("VP9", ignoreCase = true) -> 10
                !svcEnabled && codec.name.equals("H264", ignoreCase = true) -> 10
                codec.name.equals("VP8", ignoreCase = true) -> 5
                else -> 1
            }
        }
        videoTransceivers.forEach { transceiver ->
            runCatching { transceiver.setCodecPreferences(sortedCodecs) }
        }
    }

    /**
     * يطبق Simulcast على video track: 3 طبقات بقدرات مختلفة.
     * الـ SFU/Receiver يختار أفضل طبقة حسب الشبكة.
     */
    private fun applySimulcast(sender: RtpSender?, profile: NetworkStats.BitrateProfile, svcEnabled: Boolean = false) {
        val s = sender ?: return
        val params = s.parameters
        val encodings = params.encodings.toMutableList()
        encodings.clear()
        if (svcEnabled) {
            // VP9-SVC: طبقات مكانية (L2) + زمنية (T3) — أوفر للبنطاق في المؤتمرات/البث
            encodings.add(RtpParameters.Encoding("h", profile.videoMaxBitrateKbps * 1000L, 0L, profile.videoFramerate, 3, 1.0, true, "L2T3"))
        } else {
            // Simulcast: 3 طبقات جودة مستقلة (المكالمات الفردية)
            encodings.add(RtpParameters.Encoding("h", profile.videoMaxBitrateKbps * 1000L, 0L, profile.videoFramerate, 2, 1.0, true, "L1T2"))
            encodings.add(RtpParameters.Encoding("m", (profile.videoMaxBitrateKbps * 1000L / 3), 0L, profile.videoFramerate / 2, 2, 2.0, true, "L1T2"))
            encodings.add(RtpParameters.Encoding("l", 100_000L, 0L, 15, 1, 4.0, true, "L1T1"))
        }
        params.encodings = encodings
        s.parameters = params
    }

    fun offer() = peer?.createOffer(sdpObserver(setLocal = true), MediaConstraints())
    fun answer() = peer?.createAnswer(sdpObserver(setLocal = true), MediaConstraints())

    /**
     * Re-negotiates ICE candidates with IceRestart=true constraint.
     * Essential for seamless network transitions (Wi-Fi -> 4G/LTE or IP change) during an active call.
     */
    fun restartIce() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
        }
        peer?.createOffer(sdpObserver(setLocal = true), constraints)
    }

    fun setRemote(description: SessionDescription, after: (() -> Unit)? = null) = peer?.setRemoteDescription(sdpObserver(after = after), description)
    fun addIce(candidate: IceCandidate) { peer?.addIceCandidate(candidate) }
    fun setMicrophoneEnabled(enabled: Boolean) { localMedia?.audioTrack?.setEnabled(enabled) }
    fun setCameraEnabled(enabled: Boolean) {
        cameraRequestedByUser = enabled
        applyEffectiveCameraState()
    }
    fun switchCamera() { (capturer as? org.webrtc.CameraVideoCapturer)?.switchCamera(null) }

    /**
     * Adaptive bitrate: يضبط الـ simulcast بناءً على جودة الشبكة.
     * يستدعى من الـ service بعد كل stats poll.
     */
    fun applyAdaptiveBitrate(stats: NetworkStats) {
        lastStats = stats
        val recommended = NetworkStats.recommendBitrate(stats.quality)
        if (recommended == currentBitrateProfile) return
        currentBitrateProfile = recommended
        // Network adaptation must never overwrite the user's camera choice.
        // It may suspend video temporarily, then restore it only if the user
        // still requested video when network quality recovers.
        applyEffectiveCameraState()
        if (recommended != NetworkStats.BitrateProfile.AUDIO_ONLY) {
            applySimulcast(videoSender, recommended, svcEnabled)
        }
    }

    private fun applyEffectiveCameraState() {
        val networkAllowsVideo = currentBitrateProfile != NetworkStats.BitrateProfile.AUDIO_ONLY
        localMedia?.videoTrack?.setEnabled(hasVideo && cameraRequestedByUser && networkAllowsVideo)
    }

    fun currentBitrate() = currentBitrateProfile

    /**
     * Polls peer connection stats every 2s. Parses RTCStatsReport for RTT, packet loss,
     * bandwidth, jitter, framerate. Triggers adaptive bitrate via [applyAdaptiveBitrate].
     */
    fun pollStats() {
        val pc = peer ?: return
        pc.getStats(object : RTCStatsCollectorCallback {
            override fun onStatsDelivered(report: RTCStatsReport) {
                var rtt = 0L
                var packetsLost = 0L
                var packetsReceived = 0L
                var bytesReceived = 0L
                var availableBitrate = 0L
                var jitter = 0L
                var fps = 0
                report.statsMap.values.forEach { stat ->
                    when (stat.type) {
                        "remote-inbound-rtp" -> {
                            rtt = ((stat.members["roundTripTime"] as? Number)?.toDouble() ?: 0.0).times(1000).toLong()
                            availableBitrate = (stat.members["availableOutgoingBitrate"] as? Number)?.toLong() ?: 0L
                        }
                        "inbound-rtp" -> {
                            packetsLost += (stat.members["packetsLost"] as? Number)?.toLong() ?: 0L
                            packetsReceived += (stat.members["packetsReceived"] as? Number)?.toLong() ?: 0L
                            bytesReceived += (stat.members["bytesReceived"] as? Number)?.toLong() ?: 0L
                            jitter = ((stat.members["jitter"] as? Number)?.toDouble() ?: 0.0).times(1000).toLong()
                            fps = (stat.members["framesPerSecond"] as? Number)?.toInt() ?: 0
                        }
                    }
                }
                val total = packetsLost + packetsReceived
                val lossPct = if (total > 0) (packetsLost.toDouble() / total * 100) else 0.0
                val kbps = bytesReceived * 8L / 1024L
                val quality = NetworkStats.classify(rtt, lossPct, availableBitrate / 1000L)
                val ns = NetworkStats(rtt, lossPct, kbps, availableBitrate / 1000L, jitter, fps, quality)
                events.onNetworkStats(ns)
                // تطبيق adaptive bitrate تلقائياً
                applyAdaptiveBitrate(ns)
            }
        })
    }

    fun release() {
        runCatching { capturer?.stopCapture() }; capturer?.dispose(); textureHelper?.dispose()
        localMedia?.audioTrack?.dispose(); localMedia?.videoTrack?.dispose()
        audioSource?.dispose(); videoSource?.dispose()
        peer?.close(); peer?.dispose()
        factory.dispose()
        audioDevice.release(); audioDevice.cleanup()
        egl.release()
        peer = null; localMedia = null
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
        val selected = camera(Camera2Enumerator(context)) ?: return null
        capturer = selected
        videoSource = factory.createVideoSource(false)
        textureHelper = SurfaceTextureHelper.create("YounesCamera", egl.eglBaseContext)
        selected.initialize(textureHelper, context, videoSource?.capturerObserver)
        val profile = currentBitrateProfile.takeUnless { it == NetworkStats.BitrateProfile.AUDIO_ONLY }
            ?: NetworkStats.BitrateProfile.LOW
        selected.startCapture(profile.videoWidth, profile.videoHeight, profile.videoFramerate)
        return factory.createVideoTrack("younes-video", videoSource).apply {
            setEnabled(cameraRequestedByUser && currentBitrateProfile != NetworkStats.BitrateProfile.AUDIO_ONLY)
        }
    }

    private fun initialBitrateProfile(): NetworkStats.BitrateProfile {
        val profile = RedQualityManager.videoProfile(context)
        return when {
            profile.videoKbps >= 1_000 && profile.videoHeight >= 720 -> NetworkStats.BitrateProfile.HD
            profile.videoKbps >= 500 -> NetworkStats.BitrateProfile.STANDARD
            else -> NetworkStats.BitrateProfile.LOW
        }
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
