package com.red.sovereign.features.lan

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.webrtc.AudioTrack
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack
import java.util.UUID

/**
 * P2-LAN — مدير مكالمات نفس الواي فاي (جهاز-لجهاز، بلا إنترنت وبلا خادم).
 *
 * التدفق:
 * - اكتشاف NSD → قائمة أقران → اتصال TCP مباشر (عرض SDP) → WebRTC P2P.
 * - الوسائط DTLS-SRTP. الهوية: redId معلن + شارة تحقق من جهات الاتصال.
 * - تعارض العروض (glare): الأصغر redId مهذب (يطبق الوارد) — نفس قاعدة Mesh.
 * - مهلة رنين 30s وارد / 45s صادر (موائمة CallRingPolicy).
 *
 * حدود معلنة بصدق:
 * - يتطلب نفس الشبكة (نفس /24) وعدم تفعيل عزل العملاء في الراوتر.
 * - مكالمة 1:1 صوت/فيديو فقط في V1 (الجماعية عبر SFU الخادم).
 */
class LanCallManager(
    private val appContext: Context,
    private val myRedId: String,
    private val myName: String,
    /** redIds جهات الاتصال — لشارة التحقق. */
    private val contactRedIds: () -> Set<String> = { emptySet() }
) {
    companion object {
        private const val TAG = "LanCallManager"
        const val RING_TIMEOUT_IN_MS = 30_000L
        const val RING_TIMEOUT_OUT_MS = 45_000L
    }

    enum class LanCallState {
        IDLE, CALLING, RINGING, IN_CALL, ENDED
    }

    data class IncomingLanCall(
        val callId: String,
        val from: LanPeer,
        val media: String
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val link = LanLink(myRedId.trim().uppercase())
    private val signalCrypto = LanSignalCrypto(appContext)
    private var presence: LanPresence? = null
    private var rtc: LanRtcSession? = null
    private var ringJob: Job? = null

    private val _peers = MutableStateFlow<List<LanPeer>>(emptyList())
    val peers: StateFlow<List<LanPeer>> = _peers.asStateFlow()

    private val _state = MutableStateFlow(LanCallState.IDLE)
    val state: StateFlow<LanCallState> = _state.asStateFlow()

    private val _incoming = MutableStateFlow<IncomingLanCall?>(null)
    val incoming: StateFlow<IncomingLanCall?> = _incoming.asStateFlow()

    private val _remoteAudio = MutableStateFlow<AudioTrack?>(null)
    val remoteAudio: StateFlow<AudioTrack?> = _remoteAudio.asStateFlow()

    private val _remoteVideo = MutableStateFlow<VideoTrack?>(null)
    val remoteVideo: StateFlow<VideoTrack?> = _remoteVideo.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /** المكالمة النشطة (صادرة/واردة): النظير + المعرف + هل أنا المتصل. */
    private var activePeer: LanPeer? = null
    private var activeCallId: String = ""
    private var iAmCaller = false
    private var haveLocalOffer = false
    /** dedup العروض المكررة (إعادة إرسال الشبكة) بنفس callId أثناء RINGING. */
    private val ringingDedup = mutableMapOf<String, Long>()
    private val DEDUP_TTL_MS = 30_000L

    var events: Events? = null

    interface Events {
        /** رنين وارد — تعرض الواجهة حوار قبول/رفض. */
        fun onIncomingCall(call: IncomingLanCall)
        fun onCallConnected(peer: LanPeer)
        fun onCallEnded(peer: LanPeer?, reason: String)
    }

    /** بدء الاكتشاف والنشر. */
    fun startDiscovery() {
        val port = link.listen()
        if (port <= 0) {
            _lastError.value = "LAN_PORT_BUSY"
            return
        }
        link.listener = linkListener
        val presence = LanPresence(appContext, myRedId, myName, port).also { this.presence = it }
        scope.launch {
            presence.peersFlow.collect { found ->
                val contacts = runCatching { contactRedIds() }.getOrDefault(emptySet())
                val myIp = myAdvertisedHost()
                _peers.value = found.filter {
                    it.redId != myRedId.trim().uppercase() &&
                        it.port > 0 &&
                        LanNet.isPrivateIpv4(it.host) &&
                        // تفعيل فحص نفس الشبكة الفرعية: تجاهل أقران شبكة مختلفة
                        // (يبقى الاكتشاف يعمل إن تعذر تحديد عنواننا).
                        (myIp.isBlank() || LanNet.sameSubnet(myIp, it.host))
                }.map { p ->
                    if (p.redId in contacts) p.copy(verified = true) else p
                }
            }
        }
        presence.start()
    }

    fun stopDiscovery() {
        runCatching { presence?.stop() }
        presence = null
    }

    fun shutdown() {
        stopDiscovery()
        endCall(notify = false)
        link.stop()
    }

    /** اتصال صادر — يعيد false إن كنا مشغولين. */
    fun call(peer: LanPeer, video: Boolean): Boolean {
        if (_state.value == LanCallState.CALLING ||
            _state.value == LanCallState.IN_CALL ||
            _state.value == LanCallState.RINGING
        ) return false
        activePeer = peer
        activeCallId = UUID.randomUUID().toString()
        iAmCaller = true
        haveLocalOffer = false
        ensureRtc(video)
        rtc?.attach()
        _state.value = LanCallState.CALLING
        armRingTimeout(RING_TIMEOUT_OUT_MS)
        // ننشئ العرض أولا؛ يُرسل عند اكتمال الوصف المحلي (onLocalDescription).
        rtc?.createOffer()
        return true
    }

    /** قبول الوارد. */
    fun accept(video: Boolean) {
        val inc = _incoming.value ?: return
        activePeer = inc.from
        activeCallId = inc.callId
        iAmCaller = false
        haveLocalOffer = false
        _incoming.value = null
        ensureRtc(video)
        rtc?.attach()
        _state.value = LanCallState.IN_CALL
        cancelRingTimeout()
        // الإجابة تُبنى بعد تثبيت العرض الوارد (مخزن مؤقتا في pendingOffer).
        pendingOffer?.let { sdp ->
            pendingOffer = null
            rtc?.handleOffer(sdp)
        }
    }

    /** رفض الوارد. */
    fun decline() {
        val inc = _incoming.value ?: return
        _incoming.value = null
        pendingOffer = null
        link.send(inc.from, LanMsg(type = "BYE", from = myRedId, to = inc.from.redId, callId = inc.callId))
        resetCallState()
    }

    /** إنهاء نشطة/صادرة. */
    fun endCall(notify: Boolean = true) {
        val peer = activePeer
        val callId = activeCallId
        if (notify && peer != null && callId.isNotBlank() &&
            (_state.value == LanCallState.CALLING || _state.value == LanCallState.IN_CALL)
        ) {
            link.send(peer, LanMsg(type = "BYE", from = myRedId, to = peer.redId, callId = callId))
        }
        resetCallState()
        if (peer != null) events?.onCallEnded(peer, "LOCAL_HANGUP")
    }

    fun setMicrophoneEnabled(enabled: Boolean) {
        rtc?.setMicrophoneEnabled(enabled)
    }

    fun setCameraEnabled(enabled: Boolean) {
        rtc?.setCameraEnabled(enabled)
    }

    fun switchCamera() {
        rtc?.switchCamera()
    }

    fun localVideo(): VideoTrack? = rtc?.localVideo

    /** سياق EGL للعرض المحلي/البعيد (SurfaceViewRenderer). */
    fun eglContext(): org.webrtc.EglBase.Context? = rtc?.eglContext

    /** عنواننا المعلن داخل LAN (يُرسل ضمن العرض للرد المباشر). */
    private fun myAdvertisedHost(): String =
        runCatching { LanNet.currentWifiNet(appContext)?.ip }.getOrNull()
            ?: runCatching { LanNet.firstSiteLocalIpv4() }.getOrNull()
            .orEmpty()

    // ── داخلي ──

    private var pendingOffer: String? = null

    private fun ensureRtc(video: Boolean) {
        if (rtc == null) {
            rtc = LanRtcSession(appContext, rtcEvents).also { it.prepare(video) }
        }
    }

    private fun resetCallState() {
        cancelRingTimeout()
        runCatching { rtc?.release() }
        rtc = null
        activePeer = null
        // إبقاء بصمة dedup لفترة TTL بعد انتهاء الرنين لابتلاع إعادة الإرسال المتأخرة
        // (بدل رفضها)، مع تنظيف البصمات المنتهية فقط.
        val now = System.currentTimeMillis()
        ringingDedup.entries.removeAll { now - it.value >= DEDUP_TTL_MS }
        activeCallId = ""
        pendingOffer = null
        haveLocalOffer = false
        _state.value = LanCallState.IDLE
    }

    private fun armRingTimeout(ms: Long) {
        cancelRingTimeout()
        val peer = activePeer
        val callId = activeCallId
        ringJob = scope.launch {
            delay(ms)
            if (activeCallId == callId && callId.isNotBlank()) {
                Log.i(TAG, "ring timeout call=$callId")
                if (peer != null && _state.value == LanCallState.CALLING) {
                    link.send(peer, LanMsg(type = "BYE", from = myRedId, to = peer.redId, callId = callId))
                }
                val endedPeer = activePeer
                resetCallState()
                _lastError.value = "LAN_NO_ANSWER"
                if (endedPeer != null) events?.onCallEnded(endedPeer, "TIMEOUT")
            }
        }
    }

    private fun cancelRingTimeout() {
        ringJob?.cancel()
        ringJob = null
    }

    private val linkListener = object : LanLink.Listener {
        override fun onFrame(msg: LanMsg) {
            if (msg.to.isNotBlank() && msg.to != myRedId.trim().uppercase()) return
            when (msg.type) {
                "OFFER" -> onRemoteOffer(msg)
                "ANSWER" -> onRemoteAnswer(msg)
                "ICE" -> onRemoteIce(msg)
                "BYE" -> onRemoteBye(msg)
                "PING" -> Unit
                "HELLO" -> Unit
                else -> Log.w(TAG, "unknown lan type ${msg.type}")
            }
        }

        override fun onSendError(to: LanPeer, error: LanLink.LanLinkError) {
            _lastError.value = when (error) {
                LanLink.LanLinkError.UNREACHABLE -> "LAN_ISOLATED"
                LanLink.LanLinkError.TIMEOUT -> "LAN_TIMEOUT"
                LanLink.LanLinkError.IO -> "LAN_IO"
            }
            // فشل إرسال العرض الصادر = إنهاء فوري (لا رنين وهمي)
            if (_state.value == LanCallState.CALLING && to.redId == activePeer?.redId) {
                val p = activePeer
                resetCallState()
                if (p != null) events?.onCallEnded(p, "UNREACHABLE")
            }
        }
    }

    private fun onRemoteOffer(msg: LanMsg) {
        if (msg.callId.isBlank()) return
        // dedup: إعادة إرسال نفس العرض أثناء RINGING ليست مكالمة جديدة —
        // تُتجاهل بصمت ولا تُرفض بـ BYE (كانت تقع في فرع "مشغول" وتقتل الرنين).
        if (_state.value == LanCallState.RINGING && msg.callId == activeCallId) {
            Log.i(TAG, "dedup offer ${msg.callId} during RINGING — ignored")
            return
        }
        val seenAt = ringingDedup[msg.callId]
        if (seenAt != null && System.currentTimeMillis() - seenAt < DEDUP_TTL_MS &&
            _state.value == LanCallState.RINGING
        ) {
            Log.i(TAG, "dedup map hit ${msg.callId} — ignored")
            return
        }
        // تفعيل فحص نفس الشبكة الفرعية على مسار الإشارة (دفاع بالعمق مع فلتر الاكتشاف).
        if (msg.host.isNotBlank()) {
            val mine = myAdvertisedHost()
            if (mine.isNotBlank() && !LanNet.sameSubnet(mine, msg.host)) {
                Log.w(TAG, "offer from different subnet ${msg.host} (mine=$mine) — rejected")
                return
            }
        }
        val sdp = unsealSdp(msg)
        if (sdp.isNullOrBlank()) {
            Log.w(TAG, "offer sealed-unreadable from ${msg.from} — ignored")
            return
        }
        val peer = findOrBuildPeer(msg) ?: return
        when (_state.value) {
            LanCallState.IDLE -> {
                activePeer = peer
                activeCallId = msg.callId
                iAmCaller = false
                pendingOffer = sdp
                ringingDedup[msg.callId] = System.currentTimeMillis()
                _incoming.value = IncomingLanCall(msg.callId, peer, msg.media.ifBlank { "voice" })
                _state.value = LanCallState.RINGING
                armRingTimeout(RING_TIMEOUT_IN_MS)
                events?.onIncomingCall(_incoming.value!!)
            }
            LanCallState.CALLING -> {
                // تعارض: الأصغر redId مهذب فيقبل الوارد (قاعدة Mesh نفسها)
                val mine = myRedId.trim().uppercase()
                if (mine < peer.redId) {
                    Log.i(TAG, "glare: yielding to ${peer.redId}")
                    haveLocalOffer = false
                    pendingOffer = null
                    _incoming.value = null
                    activePeer = peer
                    activeCallId = msg.callId
                    iAmCaller = false
                    ensureRtc(msg.media == "video")
                    rtc?.attach()
                    _state.value = LanCallState.IN_CALL
                    cancelRingTimeout()
                    rtc?.handleOffer(sdp)
                } else {
                    Log.i(TAG, "glare: keeping our offer, ignoring ${peer.redId}")
                }
            }
            else -> {
                // مشغول — رفض مهذب
                link.send(peer, LanMsg(type = "BYE", from = myRedId, to = peer.redId, callId = msg.callId))
            }
        }
    }

    private fun onRemoteAnswer(msg: LanMsg) {
        if (msg.callId != activeCallId) return
        val sdp = unsealSdp(msg)
        if (sdp.isNullOrBlank()) return
        if (_state.value != LanCallState.CALLING) return
        cancelRingTimeout()
        rtc?.handleAnswer(sdp)
        _state.value = LanCallState.IN_CALL
        activePeer?.let { events?.onCallConnected(it) }
    }

    private fun onRemoteIce(msg: LanMsg) {
        if (msg.callId != activeCallId) return
        val ice = msg.ice ?: return
        rtc?.handleIce(IceCandidate(ice.sdpMid, ice.sdpMLineIndex, ice.sdp))
    }

    private fun onRemoteBye(msg: LanMsg) {
        if (msg.callId != activeCallId && msg.callId != _incoming.value?.callId) return
        val peer = activePeer ?: _incoming.value?.from
        _incoming.value = null
        resetCallState()
        _state.value = LanCallState.IDLE
        if (peer != null) events?.onCallEnded(peer, "REMOTE_BYE")
    }

    /** ختم SDP بجلسة Signal القائمة (fallback علني موثق عند غيابها). */
    private fun withSeal(peer: LanPeer, base: LanMsg, sdp: String): LanMsg {
        val sealed = runCatching {
            signalCrypto.seal(peer.redId, sdp.toByteArray(Charsets.UTF_8))
        }.getOrNull() ?: return base
        return base.copy(
            sdp = "",
            sealed = true,
            enc = java.util.Base64.getEncoder().encodeToString(sealed.bytes),
            encType = sealed.type,
            encDevice = sealed.deviceId
        )
    }

    /** فتح SDP المختوم، أو النص المباشر — null = تجاهل آمن. */
    private fun unsealSdp(msg: LanMsg): String? {
        if (!msg.sealed) return msg.sdp.takeIf { it.isNotBlank() }
        if (msg.enc.isBlank()) return null
        val bytes = runCatching { java.util.Base64.getDecoder().decode(msg.enc) }.getOrNull()
            ?: return null
        return runCatching { signalCrypto.open(msg.from, msg.encDevice, msg.encType, bytes) }
            .getOrNull()?.toString(Charsets.UTF_8)?.takeIf { it.isNotBlank() }
    }

    private fun findOrBuildPeer(msg: LanMsg): LanPeer? {
        _peers.value.firstOrNull { it.redId == msg.from }?.let { return it }
        // احتياطي: العرض يحمل عنوان المرسل — نبني نظيرا مؤقتا (يُستبدل عند اكتشاف NSD).
        // نتحقق من الشرطين الأمنيين: redId صالح + IP خاص (لا إنترنت).
        if (msg.host.isBlank() || msg.port <= 0) {
            Log.w(TAG, "offer from undiscovered peer ${msg.from} without route — ignored")
            return null
        }
        if (!LanNet.isPrivateIpv4(msg.host)) {
            Log.w(TAG, "offer from non-private host ${msg.host} — rejected")
            return null
        }
        // تفعيل sameSubnet هنا أيضًا: عنوان خارج /24 الخاصة بنا مرفوض
        // (يمنع عروض عبر VPN/شبكة مختلفة رغم كونها private).
        val mine = myAdvertisedHost()
        if (mine.isNotBlank() && !LanNet.sameSubnet(mine, msg.host)) {
            Log.w(TAG, "offer outside our subnet ${msg.host} (mine=$mine) — rejected")
            return null
        }
        val contacts = runCatching { contactRedIds() }.getOrDefault(emptySet())
        return LanPeer(
            redId = msg.from,
            name = msg.name.take(32).ifBlank { msg.from.take(8) },
            host = msg.host,
            port = msg.port,
            verified = msg.from in contacts
        )
    }

    private val rtcEvents = object : LanRtcSession.Events {
        override fun onLocalDescription(description: SessionDescription) {
            val peer = activePeer ?: return
            val callId = activeCallId
            if (callId.isBlank()) return
            when (description.type) {
                SessionDescription.Type.OFFER -> {
                    haveLocalOffer = true
                    link.send(
                        peer,
                        withSeal(
                            peer,
                            LanMsg(
                                type = "OFFER", from = myRedId, to = peer.redId,
                                callId = callId, sdp = description.description,
                                media = if (rtc?.localVideo != null) "video" else "voice",
                                name = myName,
                                host = myAdvertisedHost(),
                                port = link.actualPort()
                            ),
                            description.description
                        )
                    )
                }
                SessionDescription.Type.ANSWER -> {
                    link.send(
                        peer,
                        withSeal(
                            peer,
                            LanMsg(
                                type = "ANSWER", from = myRedId, to = peer.redId,
                                callId = callId, sdp = description.description
                            ),
                            description.description
                        )
                    )
                }
                else -> Unit
            }
        }

        override fun onIceCandidate(candidate: IceCandidate) {
            val peer = activePeer ?: return
            val callId = activeCallId
            if (callId.isBlank()) return
            link.send(
                peer,
                LanMsg(
                    type = "ICE", from = myRedId, to = peer.redId, callId = callId,
                    ice = LanIce(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp)
                )
            )
        }

        override fun onRemoteAudio(track: AudioTrack) {
            _remoteAudio.value = track
        }

        override fun onRemoteVideo(track: VideoTrack) {
            _remoteVideo.value = track
        }

        override fun onConnectionState(state: PeerConnection.PeerConnectionState) {
            if (state == PeerConnection.PeerConnectionState.CONNECTED) {
                cancelRingTimeout()
                if (_state.value == LanCallState.CALLING) {
                    _state.value = LanCallState.IN_CALL
                    activePeer?.let { events?.onCallConnected(it) }
                }
            }
            if (state == PeerConnection.PeerConnectionState.FAILED ||
                state == PeerConnection.PeerConnectionState.DISCONNECTED
            ) {
                val peer = activePeer
                resetCallState()
                _lastError.value = "LAN_PC_FAILED"
                if (peer != null) events?.onCallEnded(peer, state.name)
            }
            if (state == PeerConnection.PeerConnectionState.CLOSED) {
                if (_state.value == LanCallState.IN_CALL) {
                    val peer = activePeer
                    resetCallState()
                    if (peer != null) events?.onCallEnded(peer, "CLOSED")
                }
            }
        }

        override fun onError(message: String) {
            Log.w(TAG, "rtc error: $message")
            _lastError.value = message
        }
    }
}
