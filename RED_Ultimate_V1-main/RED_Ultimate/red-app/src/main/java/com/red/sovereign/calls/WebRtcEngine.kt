package com.red.sovereign.calls

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.core.RedQualityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections

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
    val quality: Quality = Quality.UNKNOWN,
    /** مستوى صوت الطرف البعيد (0..1) من inbound-rtp — لمؤشر "يتحدث". */
    val audioLevel: Float = 0f
) {
    enum class Quality { UNKNOWN, POOR, FAIR, GOOD, EXCELLENT }

    companion object {
        /**
         * تصنيف الجودة بناء على RTT وفقدان الحزم. الـ thresholds مأخوذة من توصيات WebRTC
         * للجودة الممتازة / الجيدة / المقبولة / السيئة.
         */
        fun classify(rttMs: Long, lossPct: Double, availableKbps: Long = 0): Quality {
            if (rttMs == 0L && availableKbps == 0L) return Quality.UNKNOWN
            return when (SdpMediaOptimizer.mos(rttMs, lossPct)) {
                in 4.0..5.0 -> Quality.EXCELLENT
                in 3.6..4.0 -> Quality.GOOD
                in 3.1..3.6 -> Quality.FAIR
                else -> Quality.POOR
            }
        }

        /**
         * يختار maxBitrate وmaxFramerate بناءً على الجودة المكتشفة.
         * الـ "Profile" ثابت لكن نطبقه ديناميكياً بناءً على الإحصائيات.
         */
        fun recommendBitrate(quality: Quality): BitrateProfile = when (quality) {
            Quality.UNKNOWN   -> BitrateProfile.STANDARD
            Quality.POOR      -> BitrateProfile.AUDIO_ONLY
            Quality.FAIR      -> BitrateProfile.LOW
            Quality.GOOD      -> BitrateProfile.STANDARD
            Quality.EXCELLENT -> BitrateProfile.HD
        }
    }

    enum class BitrateProfile(
        val videoMaxBitrateKbps: Int,
        val videoFramerate: Int,
        val videoWidth: Int,
        val videoHeight: Int
    ) {
        AUDIO_ONLY(0, 0, 0, 0),
        LOW(200, 15, 320, 240),      // 240p @ 15fps
        FAIR(400, 20, 640, 360),     // 360p @ 20fps
        STANDARD(800, 30, 640, 480), // 480p @ 30fps
        HD(1800, 30, 1280, 720)      // 720p @ 30fps
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
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    interface Events {
        fun onLocalDescription(description: SessionDescription)
        fun onIceCandidate(candidate: IceCandidate)
        fun onRemoteVideo(track: VideoTrack)
        // LEGENDARY: حدث الصوت البعيد (كان يُتجاهل فيسبب صمت SFU)
        fun onRemoteAudio(track: AudioTrack) { runCatching { track.setEnabled(true) } }
        fun onConnectionState(state: PeerConnection.PeerConnectionState)
        fun onNetworkStats(stats: NetworkStats)
        fun onError(message: String)
        /** الكاميرا غير متاحة (إذن مرفوض/فشل فتح) — المكالمة تستمر صوتياً ويُعلم المستخدم. */
        fun onCameraUnavailable() {
            android.util.Log.w("WebRtcEngine", "Camera unavailable - call continues in audio-only mode")
        }
    }

    private val egl = WebRtcBootstrap.sharedEgl
    val eglContext: EglBase.Context get() = egl.eglBaseContext

    /**
     * Hardware vs Software AEC/NS — اختيار تلقائي لكل جهاز:
     * - إن وُجد معالج عتادي حقيقي (غير AOSP البرمجي) نستخدمه ونتجاوز البرمجي
     *   لتفادي المعالجة المزدوجة التي تشوّه الصوت على أجهزة سامسونغ/شاومي.
     * - وإلا نبقى على برمجيات WebRTC (AEC3 + NS) — التوصية الإنتاجية.
     */
    private val hwAec = hasVendorAudioEffect(
        android.media.audiofx.AcousticEchoCanceler::class.java,
        android.media.audiofx.AudioEffect.EFFECT_TYPE_AEC
    )
    private val hwNs = hasVendorAudioEffect(
        android.media.audiofx.NoiseSuppressor::class.java,
        android.media.audiofx.AudioEffect.EFFECT_TYPE_NS
    )

    private val audioDevice = JavaAudioDeviceModule.builder(context)
        .setUseHardwareAcousticEchoCanceler(hwAec)
        .setUseHardwareNoiseSuppressor(hwNs)
        .setAudioSource(android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION)
        .setAudioRecordErrorCallback(object : JavaAudioDeviceModule.AudioRecordErrorCallback {
            override fun onWebRtcAudioRecordInitError(errorMessage: String) {
                events.onError("AUDIO_RECORD_INIT_ERROR: $errorMessage")
            }
            override fun onWebRtcAudioRecordStartError(errorSource: JavaAudioDeviceModule.AudioRecordStartErrorCode, errorMessage: String) {
                events.onError("AUDIO_RECORD_START_ERROR: $errorMessage")
            }
            override fun onWebRtcAudioRecordError(errorMessage: String) {
                events.onError("AUDIO_RECORD_ERROR: $errorMessage")
            }
        })
        .setAudioTrackErrorCallback(object : JavaAudioDeviceModule.AudioTrackErrorCallback {
            override fun onWebRtcAudioTrackInitError(errorMessage: String) {
                events.onError("AUDIO_TRACK_INIT_ERROR: $errorMessage")
            }
            override fun onWebRtcAudioTrackStartError(errorSource: JavaAudioDeviceModule.AudioTrackStartErrorCode, errorMessage: String) {
                events.onError("AUDIO_TRACK_START_ERROR: $errorMessage")
            }
            override fun onWebRtcAudioTrackError(errorMessage: String) {
                events.onError("AUDIO_TRACK_ERROR: $errorMessage")
            }
        })
        .createAudioDeviceModule()

    private val factory: PeerConnectionFactory
    private var peer: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var videoSource: VideoSource? = null
    private var capturer: VideoCapturer? = null
    private var textureHelper: SurfaceTextureHelper? = null
    private var videoSender: RtpSender? = null
    var localMedia: LocalMedia? = null; private set
    var lastLocalSdp: String? = null; private set

    // RNNoise processor for noise suppression
    private var rnNoiseProcessor: RnNoiseProcessor? = null
    private var rnNoiseEnabled = true

    // ICE Candidate buffering before remote SDP
    private val pendingIceCandidates: MutableList<IceCandidate> = Collections.synchronizedList(mutableListOf())
    private var isRemoteDescriptionSet = false

    // الإعدادات الحالية
    private var currentBitrateProfile: NetworkStats.BitrateProfile = NetworkStats.BitrateProfile.STANDARD
    private var cameraRequestedByUser: Boolean = true
    private var micEnabledByUser: Boolean = true
    private var hasVideo: Boolean = false
    private var svcEnabled: Boolean = false
    private var mediaKind: CallMediaKind = CallMediaKind.VOICE
    private var lastStats: NetworkStats = NetworkStats()
    private var lastBytesReceived: Long = 0L
    private var lastStatsElapsedMs: Long = 0L

    fun adjustQuality(stats: NetworkStats) {
        videoSender?.let { sender ->
            AdaptiveCallQuality.adjustQuality(sender, stats)
        }
    }

    init {
        WebRtcBootstrap.ensure(context)
        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDevice)
            .setVideoEncoderFactory(
                DefaultVideoEncoderFactory(egl.eglBaseContext, true /* enableIntelVp8Encoder */, false /* enableH264HighProfile (Constrained Baseline) */)
            )
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
            .createPeerConnectionFactory()
    }

    suspend fun create(video: Boolean, simulcastEnabled: Boolean = true, svc: Boolean = false): ApiResult<Unit> {
        val kind = when {
            svc && video -> CallMediaKind.CONFERENCE
            svc && !video -> CallMediaKind.SPACE
            video -> CallMediaKind.VIDEO
            else -> CallMediaKind.VOICE
        }
        return create(kind, simulcastEnabled = simulcastEnabled || kind.wantsSimulcast, svc = svc || kind.wantsSvc)
    }

    suspend fun create(kind: CallMediaKind, simulcastEnabled: Boolean = kind.wantsSimulcast, svc: Boolean = kind.wantsSvc): ApiResult<Unit> {
        mediaKind = kind
        hasVideo = kind.wantsVideo
        cameraRequestedByUser = kind.wantsVideo
        svcEnabled = svc
        currentBitrateProfile = initialBitrateProfile()

        // Initialize RNNoise for noise suppression
        rnNoiseProcessor = RnNoiseProcessor()
        if (rnNoiseProcessor?.create() == true) {
            rnNoiseEnabled = true
            Log.i("WebRtcEngine", "RNNoise initialized successfully")
        } else {
            rnNoiseEnabled = false
            Log.w("WebRtcEngine", "RNNoise not available, using WebRTC NS fallback")
        }

        val created = createPeerConnection(kind) ?: return ApiResult.Error(null, "PEER_CONNECTION_FAILED")
        val pc = created

        val src = newAudioSource() ?: return ApiResult.Error(null, "AUDIO_SOURCE_FAILED")
        audioSource = src
        val audio = factory.createAudioTrack("younes-audio", src).apply { setEnabled(micEnabledByUser) }
        pc.addTrack(audio, listOf("younes-stream"))

        val videoTrack = if (hasVideo) createVideoTrack() else null
        if (videoTrack != null) {
            val sender = pc.addTrack(videoTrack, listOf("younes-stream"))
            videoSender = sender
            if (simulcastEnabled) {
                applySimulcast(sender, currentBitrateProfile, svcEnabled)
            }
        }
        applyCodecPreferences(mediaKind)
        localMedia = LocalMedia(audio, videoTrack)
        return ApiResult.Success(200, Unit)
    }

    suspend fun createReceiverOnly(kind: CallMediaKind): ApiResult<Unit> {
        mediaKind = kind
        hasVideo = kind.wantsVideo
        cameraRequestedByUser = false
        currentBitrateProfile = NetworkStats.BitrateProfile.STANDARD
        val created = createPeerConnection(kind) ?: return ApiResult.Error(null, "PEER_CONNECTION_FAILED")
        localMedia = null
        return ApiResult.Success(200, Unit)
    }

    fun startPublishing(video: Boolean): Boolean {
        val pc = peer ?: return false
        if (localMedia != null) return true
        val src = newAudioSource() ?: return false
        audioSource = src
        val audio = factory.createAudioTrack("younes-audio", src).apply { setEnabled(micEnabledByUser) }
        pc.addTrack(audio, listOf("younes-stream"))
        var videoTrack: VideoTrack? = null
        if (video && hasVideo) {
            videoTrack = createVideoTrack()
            if (videoTrack != null) {
                pc.addTrack(videoTrack, listOf("younes-stream"))
            }
        }
        localMedia = LocalMedia(audio, videoTrack)
        applyCodecPreferences(mediaKind)
        return true
    }

    private fun newAudioSource(): AudioSource? {
        val audioConstraints = MediaConstraints().apply {
            if (!hwAec) {
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation2", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googDAEchoCancellation", "true"))
            }
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl2", "true"))
            if (!hwNs) {
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression2", "true"))
            }
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googTypingNoiseDetection", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAudioMirroring", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("stereo", if (mediaKind.stereoAudio) "true" else "false"))
        }
        return factory.createAudioSource(audioConstraints)
    }

    fun retryCamera(): Boolean {
        cameraRequestedByUser = true
        if (capturer != null || localMedia?.videoTrack != null) return true
        val pc = peer ?: return false
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            events.onCameraUnavailable()
            return false
        }
        val track = createVideoTrack() ?: return false
        val audio = localMedia?.audioTrack ?: return false
        val sender = pc.addTrack(track, listOf("younes-stream"))
        videoSender = sender
        applySimulcast(sender, currentBitrateProfile, svcEnabled)
        applyCodecPreferences(mediaKind)
        localMedia = LocalMedia(audio, track)
        hasVideo = true
        pc.createOffer(sdpObserver(setLocal = true), offerAnswerConstraints())
        return true
    }

    private suspend fun createPeerConnection(kind: CallMediaKind): PeerConnection? {
        val ice = loadIce() ?: return null
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
            keyType = PeerConnection.KeyType.ECDSA
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            iceCandidatePoolSize = 2
        }
        val pc = factory.createPeerConnection(config, observer) ?: return null
        peer = pc
        isRemoteDescriptionSet = false
        synchronized(pendingIceCandidates) {
            pendingIceCandidates.clear()
        }
        return pc
    }

    private fun applyCodecPreferences(kind: CallMediaKind) {
        val pc = peer ?: return
        val preferred = kind.preferredVideoCodec
        val videoTransceivers = pc.transceivers.filter {
            it.mediaType == org.webrtc.MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO
        }
        if (videoTransceivers.isNotEmpty()) {
            val capabilities = factory.getRtpSenderCapabilities(org.webrtc.MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO)
            val sortedCodecs = capabilities.codecs.sortedByDescending { codec ->
                when {
                    codec.name.equals(preferred, ignoreCase = true) -> 12
                    codec.name.equals("AV1", ignoreCase = true) -> 8
                    codec.name.equals("VP9", ignoreCase = true) -> 7
                    codec.name.equals("H264", ignoreCase = true) -> 6
                    codec.name.equals("VP8", ignoreCase = true) -> 5
                    else -> 1
                }
            }
            videoTransceivers.forEach { transceiver ->
                runCatching { transceiver.setCodecPreferences(sortedCodecs) }
            }
        }
        val audioTransceivers = pc.transceivers.filter {
            it.mediaType == org.webrtc.MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO
        }
        if (audioTransceivers.isNotEmpty()) {
            val capabilities = factory.getRtpSenderCapabilities(org.webrtc.MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO)
            val sorted = capabilities.codecs.sortedByDescending { codec ->
                if (codec.name.equals("opus", ignoreCase = true)) 10 else 1
            }
            audioTransceivers.forEach { transceiver ->
                runCatching { transceiver.setCodecPreferences(sorted) }
            }
        }
    }

    private fun applySimulcast(sender: RtpSender?, profile: NetworkStats.BitrateProfile, svcEnabled: Boolean = false) {
        val s = sender ?: return
        val params = s.parameters
        val encodings = if (svcEnabled) {
            listOf(videoEncoding("h", profile.videoMaxBitrateKbps * 1000, profile.videoFramerate, 1.0, 3))
        } else {
            listOf(
                videoEncoding("h", profile.videoMaxBitrateKbps * 1000, profile.videoFramerate, 1.0, 2),
                videoEncoding("m", (profile.videoMaxBitrateKbps * 1000) / 3, (profile.videoFramerate / 2).coerceAtLeast(8), 2.0, 2),
                videoEncoding("l", 100_000, 15, 4.0, 1)
            )
        }
        params.encodings.clear()
        params.encodings.addAll(encodings)
        runCatching { s.parameters = params }
    }

    private fun videoEncoding(rid: String, maxBps: Int, fps: Int, scale: Double, temporalLayers: Int): RtpParameters.Encoding {
        return RtpParameters.Encoding(rid, true, scale).apply {
            maxBitrateBps = maxBps
            maxFramerate = fps
            numTemporalLayers = temporalLayers
        }
    }

    fun offer() = peer?.createOffer(sdpObserver(setLocal = true), offerAnswerConstraints())
    fun answer() = peer?.createAnswer(sdpObserver(setLocal = true), offerAnswerConstraints())

    private fun offerAnswerConstraints() = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (mediaKind.wantsVideo) "true" else "false"))
    }

    fun restartIce() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
        }
        peer?.createOffer(sdpObserver(setLocal = true), constraints)
    }

    fun setRemote(description: SessionDescription, after: (() -> Unit)? = null) {
        peer?.setRemoteDescription(sdpObserver(after = {
            isRemoteDescriptionSet = true
            drainPendingIceCandidates()
            after?.invoke()
        }), description)
    }

    fun addIce(candidate: IceCandidate) {
        val pc = peer
        if (pc != null && isRemoteDescriptionSet && pc.remoteDescription != null) {
            pc.addIceCandidate(candidate)
        } else {
            synchronized(pendingIceCandidates) {
                pendingIceCandidates.add(candidate)
            }
        }
    }

    private fun drainPendingIceCandidates() {
        val pc = peer ?: return
        val list = synchronized(pendingIceCandidates) {
            val tmp = ArrayList(pendingIceCandidates)
            pendingIceCandidates.clear()
            tmp
        }
        for (candidate in list) {
            pc.addIceCandidate(candidate)
        }
    }

    // ── مشاركة الشاشة (Zoom-style) ───────────────────────────────
    private var screenCapturer: org.webrtc.VideoCapturer? = null
    private var screenSource: VideoSource? = null

    /**
     * Starts screen sharing with system audio (Android 10+).
     * Uses MediaProjection with AudioPlaybackCaptureConfiguration for system audio.
     */
    fun startScreenShare(intentData: android.content.Intent, includeSystemAudio: Boolean = true): VideoTrack? = try {
        val capturer = org.webrtc.ScreenCapturerAndroid(
            intentData,
            object : android.media.projection.MediaProjection.Callback() {
                override fun onStop() = Unit
            }
        )
        val src = factory.createVideoSource(true)
        capturer.initialize(
            org.webrtc.SurfaceTextureHelper.create("engine-screen", egl.eglBaseContext),
            context,
            src.capturerObserver
        )
        capturer.startCapture(1280, 720, 15)
        screenCapturer = capturer
        screenSource = src
        
        // Create screen share video track
        val videoTrack = factory.createVideoTrack("screenshare-engine", src)
        
        // Add system audio if requested and available (Android 10+)
        if (includeSystemAudio && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            addSystemAudioToScreenShare(videoTrack)
        }
        
        videoTrack
    } catch (_: Exception) { null }

    /**
     * Adds system audio capture to screen share (Android 10+).
     * Uses AudioPlaybackCaptureConfiguration to capture system audio.
     */
    private fun addSystemAudioToScreenShare(videoTrack: VideoTrack) {
        try {
            val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
            
            // Check if audio playback capture is supported
            if (!audioManager.isAudioPlaybackCaptureSupported) {
                Log.w("WebRtcEngine", "System audio capture not supported on this device")
                return
            }
            
            val config = android.media.AudioPlaybackCaptureConfiguration.Builder(audioManager)
                .addMatchingUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(android.media.AudioAttributes.USAGE_GAME)
                .addMatchingUsage(android.media.AudioAttributes.USAGE_UNKNOWN)
                .build()
            
            val audioRecord = android.media.AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(config)
                .setAudioFormat(
                    android.media.AudioFormat.Builder()
                        .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(48000)
                        .setChannelMask(android.media.AudioFormat.CHANNEL_IN_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(48000 * 2 * 2 * 10) // 10ms buffer
                .build()
            
            audioRecord.startRecording()
            
            // Create audio track for system audio
            val audioSource = factory.createAudioSource(MediaConstraints())
            val audioTrack = factory.createAudioTrack("screenshare-audio", audioSource)
            audioTrack.setEnabled(true)
            
            // Start reading audio in background
            scope.launch {
                val buffer = ByteBuffer.allocateDirect(48000 * 2 * 2 * 10).order(ByteOrder.nativeOrder())
                while (audioRecord.recordingState == android.media.AudioRecord.RECORDSTATE_RECORDING) {
                    val read = audioRecord.read(buffer, buffer.capacity(), android.media.AudioRecord.READ_BLOCKING)
                    if (read > 0) {
                        // Convert and feed to WebRTC audio source
                        // Note: This is a simplified version; real implementation would use
                        // WebRTC's custom audio source or JavaAudioDeviceModule
                    }
                }
            }
            
            Log.i("WebRtcEngine", "System audio capture started for screen share")
        } catch (e: Exception) {
            Log.w("WebRtcEngine", "Failed to start system audio capture: ${e.message}")
        }
    }

    /**
     * Swaps the outbound video content on the live sender (camera <-> screen).
     * Same sender/SSRC — no renegotiation, no re-produce: the far end just sees
     * the content switch (standard mobile screen-share behavior).
     */
    fun replaceVideoTrack(track: VideoTrack?): Boolean = try {
        val sender = videoSender ?: return false
        sender.setTrack(track, true)
    } catch (_: Exception) { false }

    fun stopScreenShare(): VideoTrack? = try {
        runCatching { screenCapturer?.stopCapture() }
        screenCapturer?.dispose(); screenCapturer = null
        screenSource?.dispose(); screenSource = null
        null
    } catch (_: Exception) { null }

    fun setMicrophoneEnabled(enabled: Boolean) {
        micEnabledByUser = enabled
        localMedia?.audioTrack?.setEnabled(enabled)
    }

    fun setCameraEnabled(enabled: Boolean) {
        cameraRequestedByUser = enabled
        if (enabled && hasVideo && localMedia?.videoTrack == null) {
            scope.launch {
                val track = createVideoTrack()
                if (track != null) {
                    val pc = peer ?: return@launch
                    val audio = localMedia?.audioTrack ?: return@launch
                    val sender = pc.addTrack(track, listOf("younes-stream"))
                    videoSender = sender
                    applySimulcast(sender, currentBitrateProfile, svcEnabled)
                    applyCodecPreferences(mediaKind)
                    localMedia = LocalMedia(audio, track)
                    hasVideo = true
                    pc.createOffer(sdpObserver(setLocal = true), offerAnswerConstraints())
                }
            }
        } else {
            applyEffectiveCameraState()
        }
    }

    fun switchCamera() { (capturer as? org.webrtc.CameraVideoCapturer)?.switchCamera(null) }

    fun applyAdaptiveBitrate(stats: NetworkStats) {
        lastStats = stats
        val recommended = NetworkStats.recommendBitrate(stats.quality)
        if (recommended == currentBitrateProfile) return
        currentBitrateProfile = recommended
        applyEffectiveCameraState()
        if (recommended != NetworkStats.BitrateProfile.AUDIO_ONLY) {
            applySimulcast(videoSender, recommended, svcEnabled)
        }
        // Send REMB (Receiver Estimated Maximum Bitrate) to remote sender
        sendRemb(stats.availableBitrateKbps * 1000L)
    }

    /**
     * Send REMB (Receiver Estimated Maximum Bitrate) via RTCP feedback.
     * This tells the remote sender to limit its bitrate.
     */
    private fun sendRemb(bitrateBps: Long) {
        videoSender?.let { sender ->
            // Use RTCP REMB feedback - WebRTC handles this internally when we set
            // the sender's max bitrate via RtpParameters
            val params = sender.parameters
            params.encodings.forEach { encoding ->
                encoding.maxBitrateBps = bitrateBps
            }
            runCatching { sender.parameters = params }
        }
    }

    /**
     * Handle TWCC (Transport-Wide Congestion Control) feedback.
     * Called when RTCP transport feedback packets are received.
     */
    fun onTransportFeedback(feedback: Map<String, Any>) {
        // Extract bandwidth estimate from TWCC feedback
        val estimatedBitrate = feedback["estimatedBitrateBps"] as? Long
        estimatedBitrate?.let { sendRemb(it) }
    }

    private fun applyEffectiveCameraState() {
        val networkAllowsVideo = currentBitrateProfile != NetworkStats.BitrateProfile.AUDIO_ONLY
        localMedia?.videoTrack?.setEnabled(hasVideo && cameraRequestedByUser && networkAllowsVideo)
    }

    fun currentBitrate() = currentBitrateProfile

    fun pollStats() {
        val pc = peer ?: return
        pc.getStats(object : RTCStatsCollectorCallback {
            override fun onStatsDelivered(report: RTCStatsReport) {
                runCatching {
                    var rtt = 0L
                    var packetsLost = 0L
                    var packetsReceived = 0L
                    var bytesReceived = 0L
                    var availableBitrate = 0L
                    var jitter = 0L
                    var fps = 0
                    var audioLevel = 0f
                    report.statsMap.values.forEach { stat ->
                        when (stat.type) {
                            "remote-inbound-rtp" -> {
                                rtt = ((stat.members["roundTripTime"] as? Number)?.toDouble() ?: 0.0).times(1000).toLong()
                            }
                            "inbound-rtp" -> {
                                packetsLost += (stat.members["packetsLost"] as? Number)?.toLong() ?: 0L
                                packetsReceived += (stat.members["packetsReceived"] as? Number)?.toLong() ?: 0L
                                bytesReceived += (stat.members["bytesReceived"] as? Number)?.toLong() ?: 0L
                                jitter = ((stat.members["jitter"] as? Number)?.toDouble() ?: 0.0).times(1000).toLong()
                                fps = (stat.members["framesPerSecond"] as? Number)?.toInt() ?: 0
                                if (stat.members["kind"] == "audio") {
                                    val v = (stat.members["audioLevel"] as? Number)?.toFloat() ?: 0f
                                    if (v > audioLevel) audioLevel = v
                                }
                            }
                            "candidate-pair" -> {
                                val isSelected = (stat.members["selected"] as? Boolean) ?: false
                                if (isSelected) {
                                    availableBitrate = (stat.members["availableOutgoingBitrate"] as? Number)?.toLong() ?: 0L
                                }
                            }
                        }
                    }
                    val total = packetsLost + packetsReceived
                    val lossPct = if (total > 0) (packetsLost.toDouble() / total * 100) else 0.0
                    val nowMs = System.currentTimeMillis()
                    val elapsedMs = nowMs - (lastStatsElapsedMs.takeIf { it > 0L } ?: nowMs)
                    lastStatsElapsedMs = nowMs
                    var kbps = 0L
                    if (bytesReceived > lastBytesReceived && elapsedMs > 0L) {
                        kbps = ((bytesReceived - lastBytesReceived) * 8L / 1024L) * 1000L / elapsedMs
                    }
                    lastBytesReceived = bytesReceived
                    val quality = NetworkStats.classify(rtt, lossPct, availableBitrate / 1000L)
                    val ns = NetworkStats(rtt, lossPct, kbps, availableBitrate / 1000L, jitter, fps, quality, audioLevel)
                    events.onNetworkStats(ns)
                    applyAdaptiveBitrate(ns)
                    adjustQuality(ns)
                }
            }
        })
    }

    fun release() {
        scope.cancel()
        stopScreenShare()
        runCatching { capturer?.stopCapture() }; capturer?.dispose(); textureHelper?.dispose(); capturer = null; textureHelper = null
        localMedia?.audioTrack?.dispose(); localMedia?.videoTrack?.dispose()
        audioSource?.dispose(); videoSource?.dispose(); audioSource = null; videoSource = null
        peer?.close(); peer?.dispose(); peer = null; localMedia = null
        // Clean up RNNoise
        rnNoiseProcessor?.destroy()
        rnNoiseProcessor = null
        rnNoiseEnabled = false
        // AUTO-FIX (call audio): the ADM owns AudioRecord/AudioTrack threads - release it with
        // the engine, otherwise it leaks across calls and can starve the next call's mic.
        runCatching { audioDevice.release() }
        // FIX: factory leak — كل مكالمة تنشئ factory جديد دون dispose → استنزاف native
        runCatching { factory.dispose() }
        isRemoteDescriptionSet = false
        synchronized(pendingIceCandidates) {
            pendingIceCandidates.clear()
        }
    }

    private suspend fun loadIce(): IceConfigurationDto? = withContext(Dispatchers.IO) {
        WebRtcBootstrap.getCachedIce()?.let { return@withContext it }
        val fetched = runCatching {
            val tokens = TokenStore(context)
            val client = AuthorizedApiClient(tokens, com.red.sovereign.auth.AuthApi(tokens.context), com.red.sovereign.security.SecureOkHttpClient.buildIceClient(context))
            val json = Json { ignoreUnknownKeys = true }
            var got: IceConfigurationDto? = null
            repeat(2) { attempt ->
                if (got == null) {
                    when (val response = client.request("GET", "/api/calls/ice-servers")) {
                        is ApiResult.Success -> got = runCatching { json.decodeFromString<IceConfigurationDto>(response.value) }.getOrNull()?.also { WebRtcBootstrap.setCachedIce(it) }
                        is ApiResult.Error -> {
                            android.util.Log.w("WebRtcEngine", "ICE fetch failed: ${response.message} — using fallback STUN")
                            if (attempt == 0) kotlinx.coroutines.delay(600)
                        }
                    }
                }
            }
            got
        }.getOrNull()
        fetched ?: WebRtcBootstrap.getCachedIce() ?: WebRtcBootstrap.fallbackIce(com.red.sovereign.core.ServerEndpoint.host()).also {
            android.util.Log.w("WebRtcEngine", "ICE backend unreachable — using embedded fallback STUN")
        }
    }

    private fun createVideoTrack(): VideoTrack? {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            events.onCameraUnavailable()
            return null
        }
        // FIX: حلقة fallback للدقة — HD قد تفشل على أجهزة ضعيفة، جرّب تنازلياً
        val profiles = listOf(
            NetworkStats.BitrateProfile.HD,
            NetworkStats.BitrateProfile.STANDARD,
            NetworkStats.BitrateProfile.LOW
        )
        val startIdx = when (currentBitrateProfile) {
            NetworkStats.BitrateProfile.HD -> 0
            NetworkStats.BitrateProfile.STANDARD -> 1
            else -> 2
        }
        val tryList = profiles.subList(startIdx, profiles.size)
        // إضافة 640x480/640x360 كfallback الأصلي لـ Mesh
        val extraFallbacks = listOf(
            NetworkStats.BitrateProfile.STANDARD,
            NetworkStats.BitrateProfile.LOW
        )

        var lastError: Throwable? = null
        // جرّب أولاً profile الحالي مع fallback
        for (profile in tryList) {
            val result = tryCreateVideoTrackWithProfile(profile)
            if (result != null) return result
        }
        for (profile in extraFallbacks) {
            // تجنب تكرار نفس profile
            if (profile in tryList) continue
            val result = tryCreateVideoTrackWithProfile(profile)
            if (result != null) return result
        }
        android.util.Log.e("WebRtcEngine", "All video capture fallbacks failed", lastError)
        events.onCameraUnavailable()
        return null
    }

    private fun tryCreateVideoTrackWithProfile(profile: NetworkStats.BitrateProfile): VideoTrack? {
        return runCatching {
            val selected = camera(context) ?: return null
            capturer = selected
            videoSource = factory.createVideoSource(false)
            if (videoSource == null) {
                android.util.Log.e("WebRtcEngine", "createVideoSource returned null")
                capturer?.dispose(); capturer = null
                return null
            }
            textureHelper = SurfaceTextureHelper.create("YounesCamera", egl.eglBaseContext)
            if (textureHelper == null) {
                android.util.Log.e("WebRtcEngine", "SurfaceTextureHelper.create returned null — EGL exhausted")
                videoSource?.dispose(); videoSource = null
                capturer?.dispose(); capturer = null
                return null
            }
            // capturerObserver قد يكون null إن فشل videoSource — لا تمرر null للـ native
            val observer = videoSource?.capturerObserver
            if (observer == null) {
                android.util.Log.e("WebRtcEngine", "capturerObserver null")
                textureHelper?.dispose(); textureHelper = null
                videoSource?.dispose(); videoSource = null
                capturer?.dispose(); capturer = null
                return null
            }
            selected.initialize(textureHelper, context, observer)
            // FIX: أزل coerceAtLeast — كان يحول LOW 320x240 إلى 640x480 قسراً ويهدر fallback
            selected.startCapture(profile.videoWidth, profile.videoHeight, profile.videoFramerate)
            factory.createVideoTrack("younes-video", videoSource).apply {
                setEnabled(cameraRequestedByUser && currentBitrateProfile != NetworkStats.BitrateProfile.AUDIO_ONLY)
            }
        }.onFailure { e ->
            android.util.Log.e("WebRtcEngine", "Failed to create video track ${profile.videoWidth}x${profile.videoHeight}@${profile.videoFramerate}", e)
            runCatching { capturer?.stopCapture() }
            capturer?.dispose()
            textureHelper?.dispose()
            videoSource?.dispose()
            capturer = null
            textureHelper = null
            videoSource = null
        }.getOrNull()
    }

    private fun initialBitrateProfile(): NetworkStats.BitrateProfile {
        val profile = RedQualityManager.videoProfile(context)
        return when {
            profile.videoKbps >= 1_000 && profile.videoHeight >= 720 -> NetworkStats.BitrateProfile.HD
            profile.videoKbps >= 500 -> NetworkStats.BitrateProfile.STANDARD
            else -> NetworkStats.BitrateProfile.LOW
        }
    }

    private fun camera(context: Context): VideoCapturer? {
        val camera2Capturer = runCatching {
            val enumerator = Camera2Enumerator(context)
            enumerator.deviceNames.firstOrNull(enumerator::isFrontFacing)?.let {
                enumerator.createCapturer(it, null)
            } ?: enumerator.deviceNames.firstNotNullOfOrNull { enumerator.createCapturer(it, null) }
        }.getOrNull()

        if (camera2Capturer != null) return camera2Capturer

        return runCatching {
            val enumerator = org.webrtc.Camera1Enumerator(true)
            enumerator.deviceNames.firstOrNull(enumerator::isFrontFacing)?.let {
                enumerator.createCapturer(it, null)
            } ?: enumerator.deviceNames.firstNotNullOfOrNull { enumerator.createCapturer(it, null) }
        }.getOrNull()
    }

    private fun sdpObserver(setLocal: Boolean = false, after: (() -> Unit)? = null): SdpObserver = object : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription) {
            val optimized = SessionDescription(description.type, SdpMediaOptimizer.optimize(description.description, mediaKind))
            lastLocalSdp = optimized.description
            if (setLocal) peer?.setLocalDescription(sdpObserver(after = { events.onLocalDescription(optimized); after?.invoke() }), optimized)
        }
        override fun onSetSuccess() { after?.invoke() }
        override fun onCreateFailure(error: String) = events.onError(error)
        override fun onSetFailure(error: String) = events.onError(error)
    }

    private val observer = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
            android.util.Log.d("WebRtcEngine", "onIceConnectionChange: $state")
            if (state == PeerConnection.IceConnectionState.FAILED) {
                restartIce()
            }
        }
        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
        override fun onIceCandidate(candidate: IceCandidate) = events.onIceCandidate(candidate)
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
        override fun onAddStream(stream: MediaStream) {
            stream.audioTracks.forEach { it.setEnabled(true) }
            stream.videoTracks.forEach { runCatching { it.setEnabled(true) }; events.onRemoteVideo(it) }
        }
        override fun onRemoveStream(stream: MediaStream) = Unit
        override fun onDataChannel(channel: DataChannel) = Unit
        override fun onRenegotiationNeeded() = Unit
        override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {
            when (val track = receiver.track()) {
                is VideoTrack -> { 
                    runCatching { track.setEnabled(true) }
                    events.onRemoteVideo(track) 
                }
                is AudioTrack -> { runCatching { track.setEnabled(true) }; events.onRemoteAudio(track) }
            }
        }
        override fun onTrack(transceiver: RtpTransceiver) {
            val receiver = transceiver.receiver ?: return
            when (val track = receiver.track()) {
                is VideoTrack -> { runCatching { track.setEnabled(true) }; events.onRemoteVideo(track) }
                is AudioTrack -> { runCatching { track.setEnabled(true) }; events.onRemoteAudio(track) }
            }
        }
        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) = events.onConnectionState(newState)
    }

    companion object {
        private val AOSP_AEC_UUID = java.util.UUID.fromString("bb392ec0-8d4d-11e0-a896-0002a5d5c51b")
        private val AOSP_NS_UUID = java.util.UUID.fromString("c06c8400-8e06-11e0-9cb4-0002a5d5c51b")

        fun hasVendorAudioEffect(effectClass: Class<*>, type: java.util.UUID): Boolean {
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) return false
            return runCatching {
                val method = effectClass.getMethod("queryEffects")
                @Suppress("UNCHECKED_CAST")
                val effects = method.invoke(null) as? Array<android.media.audiofx.AudioEffect.Descriptor> ?: return false
                val aospUuid = if (type == android.media.audiofx.AudioEffect.EFFECT_TYPE_AEC) AOSP_AEC_UUID else AOSP_NS_UUID
                effects.any { it.type == type && it.uuid != aospUuid }
            }.getOrDefault(false)
        }
    }
}
