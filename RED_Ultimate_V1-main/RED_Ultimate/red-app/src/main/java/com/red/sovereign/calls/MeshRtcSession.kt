package com.red.sovereign.calls

import android.content.Context
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.Collections
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
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    interface Events {
        fun onLocalDescription(peerId: String, description: SessionDescription)
        fun onIceCandidate(peerId: String, candidate: IceCandidate)
        /** Remote audio track — impl must enable track + emit to active-speaker UI (speakingPeers StateFlow + speaker indicator). */
        fun onRemoteAudio(peerId: String, track: AudioTrack) {
            android.util.Log.d("MeshRtcSession", "onRemoteAudio default peer=$peerId track=${track.id()} — override should update speakingPeers UI")
            runCatching { track.setEnabled(true) }
        }
        /** توافق قديم بلا peerId — يُحوَّل للنسخة الجديدة بمعرف فارغ. */
        fun onRemoteAudio(track: AudioTrack) {
            onRemoteAudio("", track)
        }
        fun onRemoteVideo(peerId: String, track: VideoTrack)
        fun onConnectionState(peerId: String, state: PeerConnection.PeerConnectionState)
        fun onNetworkStats(stats: NetworkStats)
        fun onError(message: String)
        /** مستوى صوت نظير (0..1) من إحصاءات inbound-rtp — لإبراز المتكلم الحقيقي. impl must emit to speakingPeers StateFlow. */
        fun onPeerAudioLevel(peerId: String, level: Float) {
            if (peerId.isBlank()) return
            android.util.Log.d("MeshRtcSession", "onPeerAudioLevel default peer=$peerId level=$level — override should update speaker indicator")
        }
        /** الكاميرا غير متاحة (إذن مرفوض/فشل فتح) — يُعلم المستخدم إن لزم. */
        fun onCameraUnavailable() {
            android.util.Log.w("MeshRtcSession", "Camera unavailable - continuing audio-only")
        }
    }

    private val egl = WebRtcBootstrap.sharedEgl
    val eglContext: EglBase.Context get() = egl.eglBaseContext

    // نفس الاختيار التلقائي عتاد/برمجي كما في WebRtcEngine (لا تعطيل ثابت).
    private val hwAecMesh = WebRtcEngine.hasVendorAudioEffect(
        android.media.audiofx.AcousticEchoCanceler::class.java,
        android.media.audiofx.AudioEffect.EFFECT_TYPE_AEC
    )
    private val hwNsMesh = WebRtcEngine.hasVendorAudioEffect(
        android.media.audiofx.NoiseSuppressor::class.java,
        android.media.audiofx.AudioEffect.EFFECT_TYPE_NS
    )

    // P0: مصدر VOICE_COMMUNICATION الصريح (كان الافتراضي MIC يلتقط ضجيجاً + بلا error callbacks)
    private val audioDevice = JavaAudioDeviceModule.builder(context)
        .setUseHardwareAcousticEchoCanceler(hwAecMesh)
        .setUseHardwareNoiseSuppressor(hwNsMesh)
        .setAudioSource(android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION)
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
    private var micEnabled = true
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
            // P0: parity مع WebRtcEngine (كانت ناقصة _2/DA/TypingNoise فيرتفع الصدى جماعياً)
            if (!hwAecMesh) {
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation2", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googDAEchoCancellation", "true"))
            }
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl2", "true"))
            if (!hwNsMesh) {
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression2", "true"))
            }
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googTypingNoiseDetection", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("stereo", if (kind.stereoAudio) "true" else "false"))
        }
        audioSource = factory.createAudioSource(audioConstraints)
        localAudio = factory.createAudioTrack("younes-mesh-audio", audioSource).apply { setEnabled(micEnabled) }
        if (kind.wantsVideo) {
            localVideoTrack = createVideoTrack()
        }
        return ApiResult.Success(200, Unit)
    }

    fun attachPeer(peerId: String): Boolean {
        if (peerId.isBlank() || peerId == localUserId) return false
        if (peers.containsKey(peerId)) {
            // RED LEGENDARY FIX: لو النظير موجود لكن بدون مسارات (بث مباشر - فيديو/صوت لا يصل)، أعد إضافة المسارات
            val existing = peers[peerId]
            if (existing != null) {
                ensureTracksForPeer(existing)
            }
            return true
        }
        if (!MeshNegotiation.canAttach(peers.size, false)) {
            events.onError("MESH_PEER_LIMIT")
            return false
        }
        val config = PeerConnection.RTCConfiguration(iceServers).apply {
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
        val slot = PeerSlot(peerId)
        val pc = factory.createPeerConnection(config, slot.observer) ?: return false
        slot.peer = pc
        // RED LEGENDARY FIX 2026: بث مباشر أفضل من تيك توك ويوتيوب - تأكد من إضافة الصوت والفيديو حتى لو أحدهما null
        // الصوت أساسي، الفيديو اختياري حسب الإذن
        localAudio?.let { 
            try { pc.addTrack(it, listOf("younes-mesh")) } catch (e: Exception) { android.util.Log.w("MeshRtcSession", "Failed to add audio to $peerId: ${e.message}") }
        }
        localVideoTrack?.let { 
            try { pc.addTrack(it, listOf("younes-mesh")) } catch (e: Exception) { android.util.Log.w("MeshRtcSession", "Failed to add video to $peerId: ${e.message}") }
        }
        // لو لا يوجد صوت ولا فيديو (فشل إنشاء المسارات)، حاول إعادة الإنشاء
        if (localAudio == null && mediaKind != CallMediaKind.LIVE) {
            android.util.Log.w("MeshRtcSession", "No local audio for $peerId - attempting audio retry")
            retryAudio()
            localAudio?.let { try { pc.addTrack(it, listOf("younes-mesh")) } catch (_: Exception) {} }
        }
        applyCodecPreferences(pc, mediaKind)
        peers[peerId] = slot
        android.util.Log.i("MeshRtcSession", "attachPeer success peer=$peerId kind=$mediaKind audio=${localAudio != null} video=${localVideoTrack != null} totalPeers=${peers.size}")
        return true
    }
    
    private fun ensureTracksForPeer(slot: PeerSlot) {
        val pc = slot.peer ?: return
        val senders = pc.senders
        val hasAudio = senders.any { it.track()?.kind() == "audio" }
        val hasVideo = senders.any { it.track()?.kind() == "video" }
        if (!hasAudio) {
            localAudio?.let { 
                try { pc.addTrack(it, listOf("younes-mesh")); android.util.Log.d("MeshRtcSession", "Re-added audio to ${slot.peerId}") } catch (_: Exception) {}
            }
        }
        if (!hasVideo && mediaKind.wantsVideo) {
            localVideoTrack?.let { 
                try { pc.addTrack(it, listOf("younes-mesh")); android.util.Log.d("MeshRtcSession", "Re-added video to ${slot.peerId}") } catch (_: Exception) {}
            }
        }
    }

    private fun applyCodecPreferences(pc: PeerConnection, kind: CallMediaKind) {
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

    fun detachPeer(peerId: String) {
        peers.remove(peerId)?.release()
    }

    /**
     * تصفير حالة العرض الصادر لنظير — يُستخدم عند قبول عرض وارد منه عمداً
     * (مضيف مشارك معتمد في البث) حتى لا يُتجاهل عرضه بسبب glare سابق.
     */
    fun resetOfferState(peerId: String) {
        peers[peerId]?.haveLocalOffer = false
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
        if (fromUserId.isBlank()) return
        var slot = peers[fromUserId]
        if (slot == null) {
            if (attachPeer(fromUserId)) {
                slot = peers[fromUserId]
            }
        }
        slot?.let {
            if (it.remoteReady) {
                it.peer?.addIceCandidate(candidate)
            } else {
                synchronized(it.pendingIce) {
                    it.pendingIce.add(candidate)
                }
            }
        }
    }

    fun setMicrophoneEnabled(enabled: Boolean) {
        micEnabled = enabled
        localAudio?.setEnabled(enabled)
    }

    fun setCameraEnabled(enabled: Boolean) {
        cameraRequested = enabled
        if (enabled && mediaKind.wantsVideo && localVideoTrack == null) {
            scope.launch {
                val track = createVideoTrack()
                if (track != null) {
                    localVideoTrack = track
                    peers.forEach { (_, slot) -> slot.peer?.addTrack(track, listOf("younes-mesh")) }
                    peers.keys.forEach { offerTo(it) }
                }
            }
        } else {
            localVideoTrack?.setEnabled(enabled && mediaKind.wantsVideo)
        }
    }

    fun switchCamera() {
        (capturer as? org.webrtc.CameraVideoCapturer)?.switchCamera(null)
    }

    // ── Screen Share للـ Mesh (لكل الأقران) ──
    private var screenHelper: ScreenShareHelper? = null
    private var screenTrack: VideoTrack? = null
    var isScreenSharing: Boolean = false; private set

    fun startScreenShare(permissionData: android.content.Intent): VideoTrack? {
        if (isScreenSharing) return screenTrack
        runCatching { capturer?.stopCapture() }
        val helper = ScreenShareHelper(context, egl.eglBaseContext, factory)
        val track = helper.start(permissionData, 1280, 720, 24) ?: return null
        screenHelper = helper
        screenTrack = track
        isScreenSharing = true
        localVideoTrack = track
        // استبدل المسار لكل الأقران
        peers.values.forEach { slot ->
            slot.peer?.let { pc ->
                pc.senders.firstOrNull { it.track()?.kind() == "video" }?.let { pc.removeTrack(it) }
                pc.addTrack(track, listOf("younes-mesh"))
            }
        }
        peers.keys.forEach { offerTo(it) }
        android.util.Log.d("MeshRtcSession", "startScreenShare success track=${track.id()} peers=${peers.size}")
        return track
    }

    fun stopScreenShare(): VideoTrack? {
        if (!isScreenSharing) return null
        runCatching { screenHelper?.stop() }
        screenHelper = null
        screenTrack?.dispose()
        screenTrack = null
        isScreenSharing = false
        val newTrack = if (mediaKind.wantsVideo && cameraRequested) createVideoTrack() else null
        localVideoTrack = newTrack
        peers.values.forEach { slot ->
            slot.peer?.let { pc ->
                pc.senders.firstOrNull { it.track()?.kind() == "video" }?.let { pc.removeTrack(it) }
                if (newTrack != null) pc.addTrack(newTrack, listOf("younes-mesh"))
            }
        }
        peers.keys.forEach { offerTo(it) }
        android.util.Log.d("MeshRtcSession", "stopScreenShare reverted camera=${newTrack?.id()}")
        return newTrack
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
        val entries = peers.entries.toList()
        entries.firstOrNull()?.value?.peer?.getStats { report ->
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
        // مستوى الصوت لكل نظير — إبراز المتكلم الحقيقي
        entries.forEach { (peerId, slot) ->
            runCatching {
                slot.peer?.getStats { report ->
                    var level = 0f
                    report.statsMap.values.forEach { stat ->
                        if (stat.type == "inbound-rtp" && stat.members["kind"] == "audio") {
                            val v = (stat.members["audioLevel"] as? Number)?.toFloat() ?: 0f
                            if (v > level) level = v
                        }
                    }
                    events.onPeerAudioLevel(peerId, level)
                }
            }
        }
    }

    fun release() {
        scope.cancel()
        runCatching { screenHelper?.stop() }; screenHelper = null; screenTrack = null
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
        // DO NOT call egl.release() here: egl is WebRtcBootstrap.sharedEgl (shared singleton).
        capturer = null
        textureHelper = null
        localAudio = null
        localVideoTrack = null
        audioSource = null
        videoSource = null
    }

    private fun offerAnswerConstraints() = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (mediaKind.wantsVideo) "true" else "false"))
    }

    private suspend fun loadIce(): IceConfigurationDto? = withContext(Dispatchers.IO) {
        WebRtcBootstrap.getCachedIce()?.let { return@withContext it }
        val tokens = TokenStore(context)
        val client = AuthorizedApiClient(tokens, com.red.sovereign.auth.AuthApi(tokens.context), com.red.sovereign.security.SecureOkHttpClient.buildIceClient(context))
        val json = Json { ignoreUnknownKeys = true }
        val delays = longArrayOf(400, 1200, 3000)
        repeat(3) { attempt ->
            when (val response = client.request("GET", "/api/calls/ice-servers")) {
                is ApiResult.Success -> runCatching { json.decodeFromString<IceConfigurationDto>(response.value) }.getOrNull()?.let {
                    WebRtcBootstrap.setCachedIce(it)
                    return@withContext it
                }
                is ApiResult.Error -> android.util.Log.w("MeshRtcSession", "loadIce attempt ${attempt + 1} failed: ${response.message}")
            }
            if (attempt < 2) kotlinx.coroutines.delay(delays[attempt] + (0..300).random())
        }
        WebRtcBootstrap.getCachedIce() ?: WebRtcBootstrap.fallbackIce(com.red.sovereign.core.ServerEndpoint.host()).also {
            android.util.Log.w("MeshRtcSession", "ICE backend unreachable — using embedded fallback STUN (broadcaster)")
        }
    }

    private fun createVideoTrack(): VideoTrack? {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            android.util.Log.w("MeshRtcSession", "createVideoTrack: CAMERA permission not granted")
            events.onCameraUnavailable()
            return null
        }
        return runCatching {
            val selected = camera(context) ?: run {
                android.util.Log.e("MeshRtcSession", "createVideoTrack: failed to create capturer")
                events.onCameraUnavailable()
                return null
            }
            capturer = selected
            videoSource = factory.createVideoSource(false)
            textureHelper = SurfaceTextureHelper.create("YounesMeshCamera", egl.eglBaseContext)
            selected.initialize(textureHelper, context, videoSource?.capturerObserver)
            var started = false
            val attempts = listOf(Triple(640, 480, 24), Triple(640, 360, 24), Triple(320, 240, 15))
            for ((w, h, fps) in attempts) {
                try {
                    selected.startCapture(w, h, fps)
                    started = true
                    android.util.Log.d("MeshRtcSession", "createVideoTrack: startCapture success ${w}x${h}@${fps}")
                    break
                } catch (e: Exception) {
                    android.util.Log.w("MeshRtcSession", "createVideoTrack: startCapture ${w}x${h} failed: ${e.message}")
                    runCatching { selected.stopCapture() }
                }
            }
            if (!started) {
                android.util.Log.e("MeshRtcSession", "createVideoTrack: all startCapture attempts failed")
                events.onCameraUnavailable()
                return null
            }
            factory.createVideoTrack("younes-mesh-video", videoSource).apply { setEnabled(cameraRequested) }
        }.onFailure {
            android.util.Log.e("MeshRtcSession", "createVideoTrack: exception ${it.message}", it)
            events.onCameraUnavailable()
            runCatching { capturer?.stopCapture() }
            capturer?.dispose()
            textureHelper?.dispose()
            videoSource?.dispose()
            capturer = null
            textureHelper = null
            videoSource = null
        }.getOrNull()
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

    fun retryCamera(): Boolean {
        cameraRequested = true
        if (localVideoTrack != null) return true
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            events.onCameraUnavailable()
            return false
        }
        val track = createVideoTrack() ?: return false
        localVideoTrack = track
        peers.forEach { (_, slot) -> slot.peer?.addTrack(track, listOf("younes-mesh")) }
        peers.forEach { (peerId, _) -> offerTo(peerId) }
        return true
    }

    fun retryAudio(): Boolean {
        if (localAudio != null) return true
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            android.util.Log.w("MeshRtcSession", "retryAudio: RECORD_AUDIO not granted")
            return false
        }
        return runCatching {
            audioSource?.dispose()
            val constraints = MediaConstraints().apply {
                if (!hwAecMesh) mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                if (!hwNsMesh) mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            }
            val src = factory.createAudioSource(constraints)
            audioSource = src
            val track = factory.createAudioTrack("younes-mesh-audio", src).apply { setEnabled(micEnabled) }
            localAudio = track
            peers.forEach { (_, slot) -> slot.peer?.addTrack(track, listOf("younes-mesh")) }
            peers.forEach { (peerId, _) -> offerTo(peerId) }
            android.util.Log.i("MeshRtcSession", "retryAudio: track added + renegotiated for ${peers.size} peers")
            true
        }.getOrDefault(false)
    }

    private inner class PeerSlot(val peerId: String) {
        var peer: PeerConnection? = null
        var haveLocalOffer = false
        var remoteReady = false
        val pendingIce: MutableList<IceCandidate> = Collections.synchronizedList(mutableListOf())

        fun setRemote(description: SessionDescription, after: (() -> Unit)? = null) {
            peer?.setRemoteDescription(sdpObserver(after = {
                remoteReady = true
                val list = synchronized(pendingIce) {
                    val tmp = ArrayList(pendingIce)
                    pendingIce.clear()
                    tmp
                }
                list.forEach { peer?.addIceCandidate(it) }
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
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                android.util.Log.d("MeshRtcSession", "Peer $peerId ICE state changed: $state")
                if (state == PeerConnection.IceConnectionState.FAILED) {
                    val constraints = MediaConstraints().apply {
                        mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
                    }
                    haveLocalOffer = true
                    peer?.createOffer(sdpObserver(setLocal = true), constraints)
                }
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
            override fun onIceCandidate(candidate: IceCandidate) = events.onIceCandidate(peerId, candidate)
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
            override fun onAddStream(stream: MediaStream) {
                stream.audioTracks.forEach { it.setEnabled(true); events.onRemoteAudio(peerId, it) }
                stream.videoTracks.forEach { it.setEnabled(true); events.onRemoteVideo(peerId, it) }
            }
            override fun onRemoveStream(stream: MediaStream) = Unit
            override fun onDataChannel(channel: DataChannel) = Unit
            override fun onRenegotiationNeeded() = Unit
            override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {
                when (val track = receiver.track()) {
                    is VideoTrack -> events.onRemoteVideo(peerId, track)
                    is AudioTrack -> { track.setEnabled(true); events.onRemoteAudio(peerId, track) }
                }
            }
            override fun onTrack(transceiver: RtpTransceiver) {
                val receiver = transceiver.receiver ?: return
                when (val track = receiver.track()) {
                    is VideoTrack -> events.onRemoteVideo(peerId, track)
                    is AudioTrack -> { track.setEnabled(true); events.onRemoteAudio(peerId, track) }
                }
            }
            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
                events.onConnectionState(peerId, newState)
            }
        }

        fun release() {
            peer?.close()
            peer?.dispose()
            peer = null
            synchronized(pendingIce) {
                pendingIce.clear()
            }
        }
    }
}
