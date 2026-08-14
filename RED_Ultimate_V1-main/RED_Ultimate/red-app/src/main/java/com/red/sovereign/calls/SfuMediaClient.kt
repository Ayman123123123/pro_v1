package com.red.sovereign.calls

import android.content.Context
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.core.ServerEndpoint
import com.red.sovereign.security.SecureOkHttpClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Android mediasoup client for group spaces / conferences.
 *
 * Ticket: `GET /api/sfu/groups/rooms/{id}/ticket`
 * Socket: `{backend}/sfu` (nginx → media-sfu)
 * If [attach] returns false, [ConferenceService] keeps the mesh path.
 *
 * How media actually flows (mediasoup ice-lite model):
 * 1. The server owns ICE (ice-lite) — its transport options carry the remote
 *    ICE credentials/candidates and its DTLS fingerprint.
 * 2. We build a FAKE remote offer from those options (send-side: recvonly
 *    sections; consume-side: sendonly sections) and answer it locally, so the
 *    local PeerConnection negotiates with the server's ICE/DTLS for real.
 * 3. We hand our own DTLS fingerprint + role to connectTransport.
 * 4. produce() tells the server our audio/video SSRCs; consume() returns the
 *    server's RTP parameters which we turn into recv-only m-sections.
 */
class SfuMediaClient(
    private val context: Context,
    private val tokens: TokenStore,
    private val events: Events
) {
    interface Events {
        fun onRemoteVideo(peerId: String, track: VideoTrack)
        fun onPeerLeft(peerId: String)
        fun onError(message: String)
        fun onNetworkStats(stats: NetworkStats) {}
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val http = SecureOkHttpClient.buildWebSocketClient(context)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()
    private val producers = ConcurrentHashMap<String, String>()
    private val mutex = Mutex()
    private var socket: WebSocket? = null
    private var engine: WebRtcEngine? = null
    private var recvEngine: WebRtcEngine? = null
    private var sendTransport: SfuTransportOptions? = null
    private var recvTransport: SfuTransportOptions? = null
    private var routerCaps: JSONObject? = null
    private var recvConnected = false
    private var canProduce = true
    private var attached = false

    /** المستهلكون المعلّقون/المتفاوض عليهم — الترتيب يحدد أرقام m-sections. */
    private val consumers = linkedMapOf<String, SfuConsumer>()
    private val pendingVideoPeers = ArrayDeque<String>()
    var eglContext: org.webrtc.EglBase.Context? = null
        private set
    var localVideo: VideoTrack? = null
        private set

    suspend fun attach(roomId: String): Boolean = withContext(Dispatchers.IO) {
        val ticket = loadTicket(roomId) ?: return@withContext false
        canProduce = ticket.canProduce
        if (!openSocket(ticket.token)) return@withContext false
        val joined = request(JSONObject().put("type", "join").put("roomId", roomId)) ?: return@withContext false
        if (joined.optString("status") != "joined") return@withContext false
        routerCaps = joined.optJSONObject("rtpCapabilities")
        sendTransport = createTransport("send") ?: return@withContext false
        recvTransport = createTransport("recv") ?: return@withContext false
        attached = true
        consumeExisting(joined.optJSONArray("existingProducers"))
        true
    }

    suspend fun publish(kind: CallMediaKind): Boolean = withContext(Dispatchers.IO) {
        if (!attached || !canProduce) return@withContext false
        val transport = sendTransport ?: return@withContext false
        if (producers.isNotEmpty()) return@withContext true // نُشر من قبل
        val created = createEngine(kind)
        if (created !is ApiResult.Success) return@withContext false
        // 1) أول offer محلي لمعرفة الكوديكس و SSRCs
        val offerSdp = waitLocalSdp() ?: return@withContext false
        // 2) أقسام send-only من منظور الخادم: الخادم يستقبل منا = recvonly
        val sections = buildList {
            SfuSdpFactory.rtpParametersFromLocal(offerSdp, "audio")?.let {
                add(SfuMediaKind("audio", "recvonly", it.codecs.map { c -> c.payloadType }, it.codecs, it.encodings.firstOrNull()?.ssrc, it.rtcp.cname))
            }
            if (kind.wantsVideo) {
                SfuSdpFactory.rtpParametersFromLocal(offerSdp, "video")?.let {
                    add(SfuMediaKind("video", "recvonly", it.codecs.map { c -> c.payloadType }, it.codecs, it.encodings.firstOrNull()?.ssrc, it.rtcp.cname))
                }
            }
        }
        if (sections.isEmpty()) return@withContext false
        // 3) عرض بعيد وهمي + إجابة محلية → ICE/DTLS فعليان مع خادم mediasoup
        val fakeOffer = SfuSdpFactory.remoteOffer(
            transport.iceParameters, transport.iceCandidates, transport.dtlsParameters, sections
        )
        engine?.setRemote(SessionDescription(SessionDescription.Type.OFFER, fakeOffer)) { engine?.answer() }
        val answerSdp = waitLocalSdp() ?: return@withContext false
        val dtls = SfuSdpFactory.dtlsFromLocalSdp(answerSdp) ?: return@withContext false
        // 4) اربط الـ transport ببصمتنا ودورنا الفعليين
        val connected = connectTransport(transport.id, dtls)
        if (connected != "transportConnected") return@withContext false
        // 5) produce بالمعلمات المستخرجة من إجابة SDP النهائية
        val audioParams = SfuSdpFactory.rtpParametersFromLocal(answerSdp, "audio") ?: return@withContext false
        if (produce("audio", transport.id, audioParams) != true) return@withContext false
        if (kind.wantsVideo) {
            SfuSdpFactory.rtpParametersFromLocal(answerSdp, "video")?.let { produce("video", transport.id, it) }
        }
        true
    }

    fun setMicrophoneEnabled(enabled: Boolean) {
        engine?.setMicrophoneEnabled(enabled)
        toggleProducer("audio", enabled)
    }

    fun setCameraEnabled(enabled: Boolean) {
        engine?.setCameraEnabled(enabled)
        toggleProducer("video", enabled)
    }

    fun switchCamera() = engine?.switchCamera()

    /** إعادة محاولة فتح الكاميرا — تُعيد التفاوض وتحدّث localVideo عند النجاح. */
    fun retryCamera(): Boolean {
        if (engine?.retryCamera() == true) {
            localVideo = engine?.localMedia?.videoTrack
            // إن كان publish السابق صوتياً فقط (فشلت الكاميرا حينها) — انشر الفيديو الآن
            scope.launch { republishVideoIfNeeded() }
            return true
        }
        return false
    }

    /** نشر فيديو على الناقل المتصل إذا لم يُنشر سابقاً (كاميرا فشلت عند الـ publish الأول). */
    private suspend fun republishVideoIfNeeded() = withContext(Dispatchers.IO) {
        if (!attached || !canProduce) return@withContext
        if (producers.containsKey("video")) return@withContext
        val transport = sendTransport ?: return@withContext
        // انتظر حتى يظهر قسم الفيديو في الـ SDP المحلي بعد إعادة التفاوض
        val sdp = runCatching {
            var found: String? = null
            repeat(20) {
                val s = engine?.lastLocalSdp?.takeIf { it.isNotBlank() } ?: return@repeat
                if (SfuSdpFactory.rtpParametersFromLocal(s, "video") != null) { found = s; return@repeat }
                delay(50)
            }
            found
        }.getOrNull() ?: return@withContext
        val params = SfuSdpFactory.rtpParametersFromLocal(sdp, "video") ?: return@withContext
        // الخادم يبث newProducer للمشاركين الآخرين تلقائياً بعد نجاح produce
        produce("video", transport.id, params)
    }
    fun pollStats() {
        engine?.pollStats()
        recvEngine?.pollStats()
    }

    fun release() {
        pending.values.forEach { it.cancel() }
        pending.clear()
        requestFireAndForget(JSONObject().put("type", "leave"))
        socket?.close(1000, "sfu leave")
        socket = null
        engine?.release(); engine = null
        recvEngine?.release(); recvEngine = null
        eglContext = null
        localVideo = null
        producers.clear()
        consumers.clear()
        pendingVideoPeers.clear()
        recvConnected = false
        attached = false
        scope.cancel()
    }

    private suspend fun loadTicket(roomId: String): SfuTicketDto? {
        val api = AuthorizedApiClient(tokens)
        return when (val response = api.request("GET", "/api/sfu/groups/rooms/$roomId/ticket")) {
            is ApiResult.Success -> runCatching { json.decodeFromString<SfuTicketDto>(response.value) }.getOrNull()
            is ApiResult.Error -> null
        }
    }

    private suspend fun openSocket(ticket: String): Boolean {
        val url = ServerEndpoint.url()
            .replaceFirst("http://", "ws://")
            .replaceFirst("https://", "wss://") + "/sfu"
        val opened = CompletableDeferred<Boolean>()
        socket = http.newWebSocket(
            Request.Builder().url(url).header("Authorization", "Bearer $ticket").build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    opened.complete(true)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val body = runCatching { JSONObject(text) }.getOrNull() ?: return
                    val requestId = body.optString("requestId").takeIf { it.isNotBlank() }
                    if (requestId != null) pending.remove(requestId)?.complete(body)
                    when (body.optString("type")) {
                        "newProducer" -> consumeOne(
                            body.optString("peerId"),
                            body.optString("producerId"),
                            body.optString("kind")
                        )
                        "peerLeft" -> {
                            val peer = body.optString("peerId")
                            // التعديلات على consumers/القائمة تتم حصرياً تحت mutex — نفس قفل التفاوض
                            scope.launch {
                                mutex.withLock {
                                    consumers.entries.removeAll { it.value.peerId == peer }
                                    pendingVideoPeers.removeAll { it == peer }
                                }
                                events.onPeerLeft(peer)
                            }
                        }
                        "producerClosed" -> {
                            val producerId = body.optString("producerId")
                            scope.launch {
                                val removed = mutex.withLock {
                                    val any = consumers.entries.removeAll { it.value.producerId == producerId }
                                    // نظّف أي تعيين فيديو معلّق أصبح بلا منتج (مسار ميّت في القائمة)
                                    val videoPeers = consumers.values.filter { it.kind == "video" }.map { it.peerId }.toSet()
                                    pendingVideoPeers.removeAll { it !in videoPeers }
                                    any
                                }
                                if (removed) negotiateRecv()
                            }
                        }
                        "producerPaused", "producerResumed", "networkDegraded" -> Unit
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    socket = null
                    if (!opened.isCompleted) opened.complete(false)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    socket = null
                    if (!opened.isCompleted) opened.complete(false)
                    events.onError(t.message ?: "SFU_SOCKET_FAILED")
                }
            }
        )
        return withTimeoutOrNull(4_000) { opened.await() } == true
    }

    private suspend fun createTransport(direction: String): SfuTransportOptions? {
        val response = request(JSONObject().put("type", "createTransport").put("direction", direction))
            ?: return null
        if (response.optString("status") != "transportCreated") return null
        val options = response.optJSONObject("transportOptions") ?: return null
        return runCatching { json.decodeFromString<SfuTransportOptions>(options.toString()) }.getOrNull()
    }

    private suspend fun connectTransport(transportId: String, dtls: SfuDtlsParameters): String? {
        val fingerprint = dtls.fingerprints.firstOrNull() ?: return null
        val response = request(
            JSONObject()
                .put("type", "connectTransport")
                .put("transportId", transportId)
                .put(
                    "dtlsParameters",
                    JSONObject()
                        .put("role", dtls.role)
                        .put(
                            "fingerprints",
                            JSONArray().put(
                                JSONObject()
                                    .put("algorithm", fingerprint.algorithm)
                                    .put("value", fingerprint.value)
                            )
                        )
                )
        )
        return response?.optString("status")
    }

    private suspend fun produce(kind: String, transportId: String, rtp: SfuRtpParameters): Boolean {
        val payload = JSONObject()
            .put("type", "produce")
            .put("transportId", transportId)
            .put("kind", kind)
            .put("rtpParameters", JSONObject(json.encodeToString(SfuRtpParameters.serializer(), rtp)))
        val response = request(payload) ?: return false
        if (response.optString("status") != "producing") return false
        val id = response.optString("producerId")
        if (id.isBlank()) return false
        producers[kind] = id
        return true
    }

    private fun consumeExisting(list: JSONArray?) {
        if (list == null) return
        for (i in 0 until list.length()) {
            val item = list.optJSONObject(i) ?: continue
            consumeOne(item.optString("peerId"), item.optString("producerId"), item.optString("kind"))
        }
    }

    private fun consumeOne(peerId: String, producerId: String, kind: String) {
        if (producerId.isBlank()) return
        val transport = recvTransport ?: return
        scope.launch {
            runCatching {
                val consume = JSONObject()
                    .put("type", "consume")
                    .put("transportId", transport.id)
                    .put("producerId", producerId)
                routerCaps?.let { consume.put("rtpCapabilities", it) }
                val response = request(consume) ?: return@launch
                if (response.optString("status") != "consuming") return@launch
                val consumerId = response.optString("consumerId")
                val rtp = response.optJSONObject("rtpParameters")
                    ?.let { runCatching { json.decodeFromString<SfuRtpParameters>(it.toString()) }.getOrNull() }
                    ?: return@launch
                mutex.withLock {
                    consumers[consumerId] = SfuConsumer(peerId, producerId, kind, rtp, false)
                    if (kind == "video") pendingVideoPeers.add(peerId)
                    negotiateRecvLocked()
                }
            }
        }
    }

    /**
     * تفاوض/إعادة تفاوض على الـ recv engine بجميع المستهلكين المتراكمين.
     * يجب استدعاؤها داخل [mutex] — الـ wrapper الخارجي يقفل قبله.
     */
    private suspend fun negotiateRecv() {
        mutex.withLock { negotiateRecvLocked() }
    }

    private suspend fun negotiateRecvLocked() {
        val transport = recvTransport ?: return
        if (consumers.isEmpty()) return
        if (recvEngine == null) {
            val kind = if (consumers.values.any { it.kind == "video" }) CallMediaKind.CONFERENCE else CallMediaKind.SPACE
            val created = createRecvEngine(kind)
            if (created !is ApiResult.Success) {
                events.onError("SFU_RECV_ENGINE_FAILED")
                return
            }
        }
        // أقسام sendonly من منظور الخادم: الخادم يرسل إلينا
        val sections = consumers.values.map { c ->
            SfuMediaKind(
                kind = c.kind,
                direction = "sendonly",
                payloadTypes = c.rtp.codecs.map { it.payloadType },
                codecs = c.rtp.codecs,
                ssrc = c.rtp.encodings.firstOrNull()?.ssrc,
                cname = c.rtp.rtcp.cname
            )
        }
        val fakeOffer = SfuSdpFactory.remoteOffer(
            transport.iceParameters, transport.iceCandidates, transport.dtlsParameters, sections
        )
        recvEngine?.setRemote(SessionDescription(SessionDescription.Type.OFFER, fakeOffer)) { recvEngine?.answer() }
        val answerSdp = waitRecvLocalSdp() ?: return
        if (!recvConnected) {
            val dtls = SfuSdpFactory.dtlsFromLocalSdp(answerSdp) ?: return
            val status = connectTransport(transport.id, dtls)
            if (status != "transportConnected") return
            recvConnected = true
        }
        consumers.entries.filter { !it.value.resumed }.forEach { (consumerId, c) ->
            if (request(JSONObject().put("type", "resumeConsumer").put("consumerId", consumerId))?.optString("status") == "consumerResumed") {
                consumers[consumerId] = c.copy(resumed = true)
            }
        }
    }

    private fun toggleProducer(kind: String, enabled: Boolean) {
        val id = producers[kind] ?: return
        requestFireAndForget(
            JSONObject()
                .put("type", if (enabled) "resumeProducer" else "pauseProducer")
                .put("producerId", id)
        )
    }

    private suspend fun createEngine(kind: CallMediaKind): ApiResult<Unit> {
        engine?.release()
        engine = WebRtcEngine(context, object : WebRtcEngine.Events {
            override fun onLocalDescription(description: SessionDescription) = Unit
            override fun onIceCandidate(candidate: org.webrtc.IceCandidate) = Unit
            override fun onRemoteVideo(track: VideoTrack) = Unit
            override fun onConnectionState(state: PeerConnection.PeerConnectionState) = Unit
            override fun onNetworkStats(stats: NetworkStats) = events.onNetworkStats(stats)
            override fun onError(message: String) = events.onError(message)
        })
        eglContext = engine?.eglContext
        val result = engine!!.create(kind)
        if (result is ApiResult.Success) {
            localVideo = engine?.localMedia?.videoTrack
            engine?.offer()
        }
        return result
    }

    private suspend fun createRecvEngine(kind: CallMediaKind): ApiResult<Unit> {
        recvEngine?.release()
        recvEngine = WebRtcEngine(context, object : WebRtcEngine.Events {
            override fun onLocalDescription(description: SessionDescription) = Unit
            override fun onIceCandidate(candidate: org.webrtc.IceCandidate) = Unit
            override fun onRemoteVideo(track: VideoTrack) {
                // تُربط المسارات القادمة بأصحابها حسب ترتيب m-sections المتفاوض عليها
                val peer = pendingVideoPeers.removeFirstOrNull()
                if (peer != null && !peer.isBlank()) events.onRemoteVideo(peer, track)
            }
            override fun onConnectionState(state: PeerConnection.PeerConnectionState) = Unit
            override fun onNetworkStats(stats: NetworkStats) = events.onNetworkStats(stats)
            override fun onError(message: String) = events.onError(message)
        })
        return recvEngine!!.createReceiverOnly(kind)
    }

    private suspend fun waitLocalSdp(): String? {
        repeat(20) {
            engine?.lastLocalSdp?.takeIf { it.isNotBlank() }?.let { return it }
            delay(50)
        }
        return engine?.lastLocalSdp
    }

    private suspend fun waitRecvLocalSdp(): String? {
        repeat(20) {
            recvEngine?.lastLocalSdp?.takeIf { it.isNotBlank() }?.let { return it }
            delay(50)
        }
        return recvEngine?.lastLocalSdp
    }

    private suspend fun request(body: JSONObject): JSONObject? {
        val id = UUID.randomUUID().toString()
        body.put("requestId", id)
        val deferred = CompletableDeferred<JSONObject>()
        pending[id] = deferred
        if (socket?.send(body.toString()) != true) {
            pending.remove(id)
            return null
        }
        return withTimeoutOrNull(5_000) { deferred.await() }
    }

    private fun requestFireAndForget(body: JSONObject) {
        body.put("requestId", UUID.randomUUID().toString())
        socket?.send(body.toString())
    }

    private data class SfuConsumer(
        val peerId: String,
        val producerId: String,
        val kind: String,
        val rtp: SfuRtpParameters,
        val resumed: Boolean
    )
}