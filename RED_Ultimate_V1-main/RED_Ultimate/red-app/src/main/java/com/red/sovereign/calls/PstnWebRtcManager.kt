package com.red.sovereign.calls

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
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
    private var egl: EglBase? = EglBase.create()
    private var factory: PeerConnectionFactory? = null
    private var peer: PeerConnection? = null
    private var audioDevice: JavaAudioDeviceModule? = null
    private var sipClient: WebRtcSipClient? = null
    private var callId: String? = null
    private var activeEvents: Events? = null
    private var activeBridge: BridgeResponse? = null
    private var pendingIncomingSdp: String? = null
    private var pendingLocalAnswerSdp: String? = null

    // ── Audio routing & ringback ────────────────────────────────────────
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var outboundRingback: ToneGenerator? = null

    @Volatile
    var state: PstnCallState = PstnCallState.IDLE
        private set(value) {
            field = value
            // إشعار مراقبي الحالة (Compose, Service) بتغير الحالة
            _stateFlow.value = value
        }
    private val _stateFlow = kotlinx.coroutines.flow.MutableStateFlow(PstnCallState.IDLE)
    /** تدفق حالة المكالمة للمراقبة التفاعلية (Compose recomposition) */
    val stateFlow: kotlinx.coroutines.flow.StateFlow<PstnCallState> = _stateFlow
    var localAudioTrack: org.webrtc.AudioTrack? = null; private set
    var lastLocalSdp: String? = null; private set

    /** callId الحقيقي من استجابة الخادم — يُعرض في واجهة المكالمة. */
    val currentCallId: String? get() = callId

    var isMuted: Boolean = false
        set(value) {
            field = value
            localAudioTrack?.setEnabled(!value)
            listenerAudio?.setEnabled(!value)
        }

    var isSpeaker: Boolean = false
        set(value) {
            field = value
            applySpeaker()
        }

    private fun ensureFactory() {
        if (factory != null) return
        if (egl == null) egl = EglBase.create()
        WebRtcBootstrap.ensure(context)
        audioDevice = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(false)
            .setUseHardwareNoiseSuppressor(false)
            .createAudioDeviceModule()
        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDevice)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl!!.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl!!.eglBaseContext))
            .createPeerConnectionFactory()
    }

    private fun buildAudioConstraints(): MediaConstraints = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("googTypingNoiseDetection", "true"))
    }

    private fun configureAudioForActiveCall() {
        try {
            val am = (audioManager ?: context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).also { audioManager = it }
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            // Request audio focus
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener {}
                    .build()
                am.requestAudioFocus(req)
                audioFocusRequest = req
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN)
            }
            applySpeaker()
        } catch (_: Exception) {}
    }

    private fun restoreAudio() {
        try {
            val am = audioManager ?: context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { am?.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am?.abandonAudioFocus(null)
            }
            am?.mode = AudioManager.MODE_NORMAL
            // Leave speakerphone as-is? Reset to earpiece for next call
            am?.isSpeakerphoneOn = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try { am?.clearCommunicationDevice() } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        audioFocusRequest = null
        // keep audioManager reference for isSpeaker toggles
    }

    private fun applySpeaker() {
        try {
            val am = (audioManager ?: context.getSystemService(Context.AUDIO_SERVICE) as AudioManager).also { audioManager = it }
            am.isSpeakerphoneOn = isSpeaker
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val targetType = if (isSpeaker) android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER else android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                am.availableCommunicationDevices.firstOrNull { it.type == targetType }?.let { dev ->
                    am.setCommunicationDevice(dev)
                }
                // Also clear if no device found? Keep current.
            }
        } catch (_: Exception) {
            try {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = isSpeaker
            } catch (_: Exception) {}
        }
    }

    /** Expose audio configuration for UI: ensures MODE_IN_COMMUNICATION and applies speaker. */
    fun ensureAudioSetup() {
        configureAudioForActiveCall()
    }

    private fun startOutboundRingback() {
        stopOutboundRingback()
        try {
            outboundRingback = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 70)
            // TONE_SUP_RINGTONE is the standard PSTN ringback (425Hz cadence). Play until stopped.
            outboundRingback?.startTone(ToneGenerator.TONE_SUP_RINGTONE)
        } catch (e: Exception) {
            android.util.Log.w("PstnWebRtc", "ToneGenerator start failed: ${e.message}")
            outboundRingback = null
        }
    }

    private fun stopOutboundRingback() {
        try {
            outboundRingback?.stopTone()
            outboundRingback?.release()
        } catch (_: Exception) {}
        outboundRingback = null
    }

    private fun onLocalAnswerReady(sdp: String) {
        lastLocalSdp = sdp
        pendingLocalAnswerSdp = sdp
        // If user already pressed Accept before SDP was ready, send 200 OK now (UAS auto-answer)
        if (listenerAutoAnswer && (pendingIncomingSdp != null || state == PstnCallState.INVITING)) {
            val sip = listenerSip
            if (sip != null) {
                val sent = sip.send200OkWithSdp(sdp)
                if (sent) {
                    state = PstnCallState.ACTIVE
                    configureAudioForActiveCall()
                    pendingIncomingSdp = null
                    android.util.Log.i("PstnIncoming", "Auto-sent 200 OK after SDP ready")
                }
            } else {
                // Fallback for non-listener path (outbound manager acting as UAS)
                val sip2 = sipClient
                if (sip2 != null) {
                    val sent2 = sip2.send200OkWithSdp(sdp)
                    if (sent2) {
                        state = PstnCallState.ACTIVE
                        configureAudioForActiveCall()
                        pendingIncomingSdp = null
                    }
                }
            }
        }
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
        activeEvents = events

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

        // Use the callId from the bridge response so the backend (PstnBridgeController)
        // can match this call for hangup and history tracking.
        callId = bridge.callId
        activeBridge = bridge

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

        // Create audio-only track with proper constraints
        val audioConstraints = buildAudioConstraints()
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
                // Start local ringback from INVITE until 180/200
                startOutboundRingback()
            }
            override fun onInviteSent() {
                state = PstnCallState.RINGING
                // Keep ringback playing through RINGING until ACTIVE; start if not already
                if (outboundRingback == null) startOutboundRingback()
                events.onRinging()
            }
            override fun onAnswered() {
                stopOutboundRingback()
                state = PstnCallState.ACTIVE
                configureAudioForActiveCall()
                events.onAnswered(bridge.usedToday, bridge.dailyLimit)
            }
            override fun onIncomingInvite(sdp: String, fromNumber: String) {
                // لا ردّ تلقائي: تُحضَّر الإجابة محليًا ويُترك القرار للمستخدم.
                // 200 OK يُرسَل فقط عند القبول عبر answerIncoming().
                state = PstnCallState.INVITING
                pendingIncomingSdp = sdp
                pendingLocalAnswerSdp = null
                val pcLocal = pc
                val remoteSdp = SessionDescription(SessionDescription.Type.OFFER, sdp)
                pcLocal.setRemoteDescription(object : org.webrtc.SdpObserver {
                    override fun onSetSuccess() {
                        pcLocal.createAnswer(object : org.webrtc.SdpObserver {
                            override fun onCreateSuccess(answerSdp: SessionDescription) {
                                // Store answer and set local description
                                pendingLocalAnswerSdp = answerSdp.description
                                lastLocalSdp = answerSdp.description
                                pcLocal.setLocalDescription(object : org.webrtc.SdpObserver {
                                    override fun onSetSuccess() {
                                        // SDP ready; if auto-answer already requested, send 200 OK now
                                        onLocalAnswerReady(answerSdp.description)
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
                        }, buildAudioConstraints())
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
                stopOutboundRingback()
                restoreAudio()
                state = PstnCallState.ENDED
                events.onHangup(cause)
                release()
            }
            override fun onError(message: String) {
                stopOutboundRingback()
                restoreAudio()
                state = PstnCallState.ERROR
                events.onError(message)
                release()
            }
        })

        sipClient?.register(
            sipCandidates(bridge.sipServer),
            username = bridge.sipUsername,
            password = bridge.sipPassword
        )

        state
    }

    /**
     * قائمة عناوين WSS لتسجيل SIP — يُجرَّب الأول فالأول:
     * 1. العنوان الصادر من الخادم (ASTERISK_WSS_URL في الإعدادات — عام عند الإنتاج).
     * 2. عناوين مشتقّة من عنوان API الذي يستخدمه التطبيق بالفعل:
     *    - http://host:port  → ws://host:8089/ws   (Asterisk مباشرة على الـ LAN)
     *    - https://host:port → wss://host/ws/sip   (nginx العام)
     *      و wss://host:8443/ws/sip كاحتياط.
     */
    private fun sipCandidates(primary: String): List<String> {
        val out = LinkedHashSet<String>()
        out.add(primary)
        val apiBase = runCatching { com.red.sovereign.core.ServerEndpoint.url() }.getOrNull()
        if (!apiBase.isNullOrBlank()) {
            try {
                val uri = java.net.URI(apiBase)
                val host = uri.host ?: return out.toList()
                when (uri.scheme) {
                    "https" -> {
                        out.add("wss://$host/ws/sip")
                        if (uri.port > 0 && uri.port != 443) out.add("wss://$host:${uri.port}/ws/sip")
                        out.add("wss://$host:8443/ws/sip")
                    }
                    "http" -> {
                        out.add("ws://$host:8089/ws")
                    }
                }
            } catch (_: Exception) {
                // العنوان الأساسي يبقى — المشتقات تجريبية فقط.
            }
        }
        return out.toList()
    }

    fun hangup() {
        if (state == PstnCallState.IDLE || state == PstnCallState.ENDED) return
        stopOutboundRingback()
        restoreAudio()
        sipClient?.bye()
        val callId = this.callId
        state = PstnCallState.ENDED
        // تحرير الموارد فورًا بدل انتظار رد BYE من الخادم.
        release()
        if (callId != null) {
            scope.launch {
                runCatching {
                    val api = PstnApi(TokenStore(context))
                    // Use bridge hangup endpoint (sets the right active-call key)
                    api.hangupBridge(callId)
                }
            }
        }
    }

    /**
     * Accept an incoming PSTN call. The answer SDP was prepared when the
     * INVITE arrived; accepting sends 200 OK carrying the answer so the
     * call becomes active on both sides (UAS per RFC 3261). Falls back to
     * waiting for SDP if not yet ready.
     */
    fun answerIncoming(offerSdp: String? = null) {
        if (state == PstnCallState.INVITING) {
            val localSdp = pendingLocalAnswerSdp ?: lastLocalSdp
            if (localSdp != null) {
                val sip = sipClient ?: listenerSip
                val sent = sip?.send200OkWithSdp(localSdp) ?: false
                if (sent) {
                    state = PstnCallState.ACTIVE
                    configureAudioForActiveCall()
                    val bridge = activeBridge
                    val events = activeEvents
                    if (bridge != null && events != null) {
                        events.onAnswered(bridge.usedToday, bridge.dailyLimit)
                    }
                    pendingIncomingSdp = null
                    // Keep pendingLocalAnswerSdp for dialog
                } else {
                    // If send failed (no INVITE stored), try fallback via listenerSip
                    listenerSip?.let { ls ->
                        if (ls.send200OkWithSdp(localSdp)) {
                            state = PstnCallState.ACTIVE
                            configureAudioForActiveCall()
                            pendingIncomingSdp = null
                        }
                    }
                }
            } else {
                // SDP not ready yet — mark auto-answer and onLocalAnswerReady will send 200 OK
                listenerAutoAnswer = true
                // Also try if offerSdp provided and we can create answer synchronously?
                // For now defer; state remains INVITING until SDP ready
            }
        }
    }

    /**
     * Reject an incoming PSTN call: 486 Busy Here + full cleanup.
     */
    fun rejectIncoming() {
        if (state == PstnCallState.INVITING) {
            runCatching<Unit> { sipClient?.rejectIncoming() }
            runCatching<Unit> { listenerSip?.rejectIncoming() }
            pendingIncomingSdp = null
            pendingLocalAnswerSdp = null
            lastLocalSdp = null
            state = PstnCallState.ENDED
            activeEvents?.onHangup("CALL_REJECTED")
            stopOutboundRingback()
            restoreAudio()
            release()
        }
    }

    fun release() {
        stopOutboundRingback()
        restoreAudio()
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
        egl?.release()
        egl = null
        activeEvents = null
        activeBridge = null
        pendingIncomingSdp = null
        // Keep pendingLocalAnswerSdp/lastLocalSdp? Clear on IDLE
        if (state == PstnCallState.IDLE || state == PstnCallState.ENDED) {
            pendingLocalAnswerSdp = null
            lastLocalSdp = null
        }
        callId = null
        state = PstnCallState.IDLE
    }

    private fun createObserver(events: Events) = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
            if (state == PeerConnection.IceConnectionState.DISCONNECTED || state == PeerConnection.IceConnectionState.FAILED) {
                if (this@PstnWebRtcManager.state == PstnCallState.ACTIVE) {
                    this@PstnWebRtcManager.state = PstnCallState.ENDED
                    events.onHangup("ICE_DISCONNECTED")
                    // تحرير كامل للموارد — كان يترك PeerConnection/EglBase
                    // يتسربان بعد كل انقطاع ICE.
                    stopOutboundRingback()
                    restoreAudio()
                    release()
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

    // ════════════════════════════════════════════════════════════════════
    // مستمع المكالمات الواردة — تسجيل دائم لـ red-webrtc-client بحيث يجد
    // AMI Redirect نقطة حية فور قبول المستخدم. منفصل تماماً عن حالة الصادر.
    // ════════════════════════════════════════════════════════════════════

    private var listenerSip: WebRtcSipClient? = null
    private var listenerPeer: PeerConnection? = null
    private var listenerFactory: PeerConnectionFactory? = null
    private var listenerAudio: org.webrtc.AudioTrack? = null
    private var listenerEgl: EglBase? = null
    @Volatile private var listenerAutoAnswer = false
    @Volatile private var listenerActiveCallId: String? = null

    /** ابدأ الاستماع لمكالمة واردة بعينها (يُستدعى لحظة ظهور شاشة الرنين). */
    fun startIncomingListener(callId: String) {
        if (listenerSip != null && listenerActiveCallId == callId) return
        stopIncomingListener()
        listenerActiveCallId = callId
        scope.launch {
            val api = PstnApi(TokenStore(context))
            val bridge = when (val r = api.incomingBridge(callId)) {
                is ApiResult.Success -> r.value
                is ApiResult.Error -> {
                    android.util.Log.w("PstnIncoming", "incoming-bridge failed: ${r.message}")
                    return@launch
                }
            }
            withContext(Dispatchers.Main) { setupListener(bridge) }
        }
    }

    private fun setupListener(bridge: BridgeResponse) {
        if (listenerSip != null) return
        if (listenerEgl == null) listenerEgl = EglBase.create()
        WebRtcBootstrap.ensure(context)
        val adm = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(false)
            .setUseHardwareNoiseSuppressor(false)
            .createAudioDeviceModule()
        listenerFactory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(adm)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(listenerEgl!!.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(listenerEgl!!.eglBaseContext))
            .createPeerConnectionFactory()

        val servers = bridge.iceServers.iceServers.map { dto ->
            PeerConnection.IceServer.builder(dto.urls)
                .setUsername(dto.username.orEmpty())
                .setPassword(dto.credential.orEmpty())
                .createIceServer()
        }
        val config = PeerConnection.RTCConfiguration(servers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        }
        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) = Unit
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
            override fun onIceCandidate(candidate: IceCandidate) { listenerSip?.addIceCandidate(candidate) }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
            override fun onAddStream(stream: MediaStream) = Unit
            override fun onRemoveStream(stream: MediaStream) = Unit
            override fun onDataChannel(channel: DataChannel) = Unit
            override fun onRenegotiationNeeded() = Unit
            override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) = Unit
            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) = Unit
        }
        val pc = listenerFactory!!.createPeerConnection(config, observer) ?: return
        listenerPeer = pc
        val audioConstraints = buildAudioConstraints()
        val src = listenerFactory!!.createAudioSource(audioConstraints)
        listenerAudio = listenerFactory!!.createAudioTrack("pstn-in-audio", src).apply { setEnabled(true) }
        pc.addTrack(listenerAudio, listOf("pstn-in-stream"))

        // Prepare audio for inbound call early (ringing -> communication mode)
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager = am
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            // Request focus early so ringtone/audio routes correctly
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener {}
                    .build()
                am.requestAudioFocus(req)
                audioFocusRequest = req
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            }
            applySpeaker()
        } catch (_: Exception) {}

        listenerSip = WebRtcSipClient(context, pc, object : WebRtcSipClient.Events {
            override fun onRegistered() {
                android.util.Log.i("PstnIncoming", "red-webrtc-client REGISTERED — جاهز للـ Redirect")
            }
            override fun onInviteSent() = Unit
            override fun onAnswered() = Unit
            override fun onBye(cause: String?) {
                restoreAudio()
                stopIncomingListener()
            }
            override fun onError(message: String) {
                android.util.Log.w("PstnIncoming", "listener error: $message")
            }
            override fun onIncomingInvite(sdp: String, fromNumber: String) {
                // حضّر الإجابة؛ إن كان المستخدم قد ضغط قبولاً قبل وصول INVITE
                // (التوقيت الأشيع لأن PSTN_ACCEPT يسبق Redirect) أرسل 200 OK فوراً عند جهوزية SDP.
                state = PstnCallState.INVITING
                pendingIncomingSdp = sdp
                pendingLocalAnswerSdp = null
                prepareAnswer(pc, sdp)
                // If auto-answer already true and SDP becomes ready, onLocalAnswerReady will send 200 OK.
                // If SDP is already ready (rare), the observer will handle.
            }
        })
        listenerSip?.register(
            sipCandidates(bridge.sipServer),
            username = bridge.sipUsername,
            password = bridge.sipPassword
        )
    }

    /** إعداد الإجابة محلياً بدون 200 OK — مشترك بين مسارَي القبول. */
    private fun prepareAnswer(pc: PeerConnection, sdp: String) {
        val remote = SessionDescription(SessionDescription.Type.OFFER, sdp)
        pc.setRemoteDescription(RemoteSdpObserver(pc), remote)
    }

    /** Observer خارجي: عند نجاح setRemote يبني الإجابة عبر [LocalSdpObserver]. */
    private inner class RemoteSdpObserver(private val pc: PeerConnection) : SdpObserver {
        override fun onSetSuccess() {
            pc.createAnswer(LocalSdpObserver(pc), buildAudioConstraints())
        }
        override fun onSetFailure(error: String?) {
            android.util.Log.w("PstnWebRtc", "setRemote failed: $error")
        }
        override fun onCreateSuccess(p0: SessionDescription?) {}
        override fun onCreateFailure(error: String?) {
            android.util.Log.w("PstnWebRtc", "createAnswer failed: $error")
        }
    }

    /** Observer داخلي: يثبّت الإجابة المحلية الجاهزة للإرسال في 200 OK. */
    private inner class LocalSdpObserver(private val pc: PeerConnection) : SdpObserver {
        override fun onCreateSuccess(answer: SessionDescription?) {
            if (answer == null) return
            // Store locally then set as local description
            pendingLocalAnswerSdp = answer.description
            lastLocalSdp = answer.description
            pc.setLocalDescription(object : SdpObserver {
                override fun onSetSuccess() {
                    // Local description is now set; SDP answer is ready to send via 200 OK
                    onLocalAnswerReady(answer.description)
                }
                override fun onSetFailure(error: String?) {
                    android.util.Log.w("PstnWebRtc", "setLocal failed: $error")
                }
                override fun onCreateSuccess(p0: SessionDescription?) = Unit
                override fun onCreateFailure(error: String?) = Unit
            }, answer)
        }
        override fun onSetSuccess() = Unit
        override fun onSetFailure(error: String?) = Unit
        override fun onCreateFailure(error: String?) {
            android.util.Log.w("PstnWebRtc", "createAnswer failed: $error")
        }
    }

    /** المستخدم قبل المكالمة: أرسل 200 OK إن كان INVITE قد وصل و SDP جاهز، وإلا علّم auto-answer. */
    fun acceptIncomingListener() {
        listenerAutoAnswer = true
        val readySdp = pendingLocalAnswerSdp ?: lastLocalSdp
        if (readySdp != null) {
            val sent = listenerSip?.send200OkWithSdp(readySdp) ?: false
            if (sent) {
                state = PstnCallState.ACTIVE
                configureAudioForActiveCall()
                pendingIncomingSdp = null
                android.util.Log.i("PstnIncoming", "acceptIncomingListener sent 200 OK immediately")
            } else {
                // Fallback to sipClient if listenerSip had no INVITE stored
                val sent2 = sipClient?.send200OkWithSdp(readySdp) ?: false
                if (sent2) {
                    state = PstnCallState.ACTIVE
                    configureAudioForActiveCall()
                    pendingIncomingSdp = null
                }
            }
        } else if (pendingIncomingSdp != null || state == PstnCallState.INVITING) {
            // SDP not yet ready — onLocalAnswerReady will auto-send 200 OK
            android.util.Log.i("PstnIncoming", "acceptIncomingListener deferred until SDP ready")
        }
    }

    fun stopIncomingListener() {
        listenerAutoAnswer = false
        listenerActiveCallId = null
        pendingLocalAnswerSdp = null
        runCatching { listenerSip?.dispose() }
        listenerSip = null
        runCatching { listenerPeer?.close() }
        runCatching { listenerPeer?.dispose() }
        listenerPeer = null
        try { listenerAudio?.dispose() } catch (_: Exception) {}
        listenerAudio = null
        runCatching { listenerFactory?.dispose() }
        listenerFactory = null
        runCatching { listenerEgl?.release() }
        listenerEgl = null
        // Do not clear lastLocalSdp/pendingIncomingSdp here if active call still ongoing via main peer
    }

    enum class PstnCallState {
        IDLE, BRIDGING, REGISTERING, INVITING, RINGING, ACTIVE, ENDED, ERROR
    }

    companion object {
        /** نسخة المستمع الوحيدة — تعيش طوال جلسة الدخول وتُعاد استخدامها. */
        @Volatile private var incomingInstance: PstnWebRtcManager? = null
        fun incoming(context: Context): PstnWebRtcManager =
            incomingInstance ?: synchronized(this) {
                incomingInstance ?: PstnWebRtcManager(context.applicationContext).also { incomingInstance = it }
            }
    }
}
