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
        /** Network stats — impl must update stats UI (GroupCallRuntime/ConferenceRuntime.networkStats + CallStatsScreen via CallTelemetry/CallQualityManager). */
        fun onNetworkStats(stats: NetworkStats) {
            android.util.Log.d("SfuMediaClient", "onNetworkStats default rtt=${stats.rttMs} loss=${stats.packetLossPercent} — override should update stats UI")
        }
        /** المتكلم الحالي من مراقب مستوى الصوت في SFU ("" = صمت). impl must update speaker highlight (speakingPeers). */
        fun onActiveSpeaker(peerId: String) {
            android.util.Log.d("SfuMediaClient", "onActiveSpeaker default peer=$peerId — override should update speaker highlight")
        }
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
    private var recvEngineKind: CallMediaKind? = null
    private var sendTransport: SfuTransportOptions? = null
    private var recvTransport: SfuTransportOptions? = null
    private var routerCaps: JSONObject? = null

    @Volatile
    private var recvConnected = false

    @Volatile
    private var canProduce = true

    @Volatile
    private var attached = false

    /** المستهلكون المعلّقون/المتفاوض عليهم — الترتيب يحدد أرقام m-sections. */
    private val consumers = linkedMapOf<String, SfuConsumer>()

    /**
     * ربط حتمي consumerId→peerId لمستهلكي الفيديو بترتيب التفاوض (m-sections) —
     * بديل FIFO القديم (pendingVideoPeers: ArrayDeque) الذي كان يفقد الربط عند
     * وصول المسارات بترتيب مختلف أو إبقاء مداخل ميتة بعد producerClosed.
     * كل تعديل/استهلاك يتم حصرياً تحت [mutex].
     */
    private val videoPeerByConsumer = linkedMapOf<String, String>()

    /**
     * مستهلكو الفيديو الذين استُلم لهم مسار في **جيل محرك الاستقبال الحالي**.
     * تُصفَّر عند إعادة إنشاء recvEngine، لأن كل PeerConnection جديد يعيد إطلاق
     * onTrack لكل m-lines من جديد. بدونها كان الربط يُستهلك مرة واحدة فقط
     * (remove) فتُسقط كل إعادة تسليم لاحقة صامتًا ⇒ شاشة سوداء للمشاهد.
     */
    private val deliveredVideoConsumers = linkedSetOf<String>()

    var eglContext: org.webrtc.EglBase.Context? = null
        private set
    var localVideo: VideoTrack? = null
        private set

    suspend fun attach(roomId: String, ticketPath: String? = null): Boolean = withContext(Dispatchers.IO) {
        val ticket = loadTicket(roomId, ticketPath) ?: return@withContext false
        canProduce = ticket.canProduce
        if (!openSocket(ticket.token)) return@withContext false
        // التذكرة مربوطة بالمعرف القانوني (canonical) الذي يعيده الخادم في حقل roomId —
        // الانضمام يجب أن يستخدمه حرفياً وإلا رفض SFU بـ "Ticket not bound to this room".
        // مثال حقيقي: بث stream_X بينما القانوني LIVE_stream_X.
        val joinRoomId = ticket.roomId.takeIf { it.isNotBlank() } ?: roomId
        val joined = request(JSONObject().put("type", "join").put("roomId", joinRoomId)) ?: return@withContext false
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
        val offerSdp = waitLocalOffer(engine) ?: return@withContext false
        // 2) أقسام send-only من منظور الخادم: الخادم يستقبل منا = recvonly
        val sections = buildList {
            SfuSdpFactory.rtpParametersFromLocal(offerSdp, "audio")?.let {
                add(SfuMediaKind("audio", "recvonly", it.codecs.map { c -> c.payloadType }, it.codecs, it.encodings.firstOrNull()?.ssrc, it.rtcp.cname, headerExtensions = it.headerExtensions))
            }
            if (kind.wantsVideo) {
                SfuSdpFactory.rtpParametersFromLocal(offerSdp, "video")?.let {
                    add(SfuMediaKind("video", "recvonly", it.codecs.map { c -> c.payloadType }, it.codecs, it.encodings.firstOrNull()?.ssrc, it.rtcp.cname, headerExtensions = it.headerExtensions))
                }
            }
        }
        if (sections.isEmpty()) return@withContext false
        // 3) عرض بعيد وهمي + إجابة محلية → ICE/DTLS فعليان مع خادم mediasoup
        val fakeOffer = SfuSdpFactory.remoteOffer(
            transport.iceParameters, transport.iceCandidates, transport.dtlsParameters, sections
        )
        engine?.setRemote(SessionDescription(SessionDescription.Type.OFFER, fakeOffer)) { engine?.answer() }
        val answerSdp = waitLocalAnswer(engine) ?: return@withContext false
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

    private var cameraTrackBeforeShare: VideoTrack? = null
    var isScreenSharing: Boolean = false
        private set

    /**
     * SFU screen share: capture + swap the live video sender to screen content.
     * Produces nothing new — the existing "video" producer carries the screen
     * (same SSRC/codecs); peers are told via SCREEN_SHARE_START for labeling.
     */
    suspend fun startScreenShare(intentData: android.content.Intent): Boolean = withContext(Dispatchers.IO) {
        if (isScreenSharing) return@withContext true
        val eng = engine ?: return@withContext false
        val screen = eng.startScreenShare(intentData) ?: return@withContext false
        cameraTrackBeforeShare = eng.localMedia?.videoTrack
        if (!eng.replaceVideoTrack(screen)) {
            runCatching { eng.stopScreenShare() }
            cameraTrackBeforeShare = null
            return@withContext false
        }
        isScreenSharing = true
        true
    }

    suspend fun stopScreenShare(): Boolean = withContext(Dispatchers.IO) {
        if (!isScreenSharing) return@withContext true
        val eng = engine ?: return@withContext false
        eng.replaceVideoTrack(cameraTrackBeforeShare)
        runCatching { eng.stopScreenShare() }
        cameraTrackBeforeShare = null
        isScreenSharing = false
        true
    }

    suspend fun publishScreenTrack(track: VideoTrack): Boolean = withContext(Dispatchers.IO) {
        if (!attached || !canProduce) return@withContext false
        val transport = sendTransport ?: return@withContext false
        val sdp = engine?.lastLocalSdp ?: return@withContext false
        val params = SfuSdpFactory.rtpParametersFromLocal(sdp, "video") ?: return@withContext false
        produce("screen", transport.id, params)
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

    fun setConsumerPreferredLayers(consumerId: String, spatialLayer: Int, temporalLayer: Int) {
        if (consumerId.isBlank()) return
        scope.launch {
            request(
                JSONObject()
                    .put("type", "setConsumerPreferredLayers")
                    .put("consumerId", consumerId)
                    .put("spatialLayer", spatialLayer)
                    .put("temporalLayer", temporalLayer)
            )
        }
    }

    /**
     * Viewer quality ladder (live SET_QUALITY): applies spatial/temporal layers
     * to every video consumer (broadcaster simulcast required, else no-op).
     */
    fun setAllVideoLayers(spatialLayer: Int, temporalLayer: Int) {
        val ids = consumers.filter { it.value.kind == "video" }.keys.toList()
        ids.forEach { setConsumerPreferredLayers(it, spatialLayer, temporalLayer) }
    }

    /**
     * Adaptive bitrate based on network stats (REMB/TWCC).
     * Called from events.onNetworkStats to adjust quality dynamically.
     */
    fun adaptBitrate(stats: NetworkStats) {
        // Map quality to spatial/temporal layers
        val (spatial, temporal) = when (stats.quality) {
            NetworkStats.Quality.EXCELLENT -> 2 to 2  // HD
            NetworkStats.Quality.GOOD -> 1 to 2       // SD
            NetworkStats.Quality.FAIR -> 1 to 1       // LD
            NetworkStats.Quality.POOR -> 0 to 0       // Audio only / lowest
            else -> 1 to 1
        }
        
        if (spatial >= 0) {
            setAllVideoLayers(spatial, temporal)
        }
        
        // Also adjust local producer if we're producing
        producers["video"]?.let { producerId ->
            // Request bitrate adaptation from server
            scope.launch {
                request(
                    JSONObject()
                        .put("type", "setProducerMaxBitrate")
                        .put("producerId", producerId)
                        .put("maxBitrate", stats.availableBitrateKbps * 1000L)
                )
            }
        }
    }

    fun requestKeyFrame(consumerId: String) {
        if (consumerId.isBlank()) return
        scope.launch {
            request(
                JSONObject()
                    .put("type", "requestKeyFrame")
                    .put("consumerId", consumerId)
            )
        }
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
        videoPeerByConsumer.clear()
        deliveredVideoConsumers.clear()
        recvConnected = false
        attached = false
        scope.cancel()
    }

    private suspend fun loadTicket(roomId: String, ticketPath: String? = null): SfuTicketDto? {
        val api = AuthorizedApiClient(tokens)
        val path = ticketPath ?: "/api/sfu/groups/rooms/$roomId/ticket"
        return when (val response = api.request("GET", path)) {
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
                                    videoPeerByConsumer.entries.removeAll { it.value == peer }
                                }
                                events.onPeerLeft(peer)
                            }
                        }
                        "producerClosed" -> {
                            val producerId = body.optString("producerId")
                            scope.launch {
                                val removed = mutex.withLock {
                                    val removedIds = consumers.entries
                                        .filter { it.value.producerId == producerId }
                                        .map { it.key }
                                    var any = false
                                    removedIds.forEach { id ->
                                        if (consumers.remove(id) != null) any = true
                                        videoPeerByConsumer.remove(id)
                                    }
                                    // كناسة دفاعية: أي مفتاح فيديو بلا مستهلك = مسار ميّت
                                    videoPeerByConsumer.keys.removeAll { it !in consumers.keys }
                                    deliveredVideoConsumers.removeAll { it !in consumers.keys }
                                    any
                                }
                                if (removed) negotiateRecv()
                            }
                        }
                        "producerPaused", "producerResumed", "networkDegraded" -> Unit
                        "activeSpeaker" -> events.onActiveSpeaker(body.optString("peerId"))
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    handleSocketTerminated("SFU_SOCKET_CLOSED: $reason", opened)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    handleSocketTerminated(t.message ?: "SFU_SOCKET_FAILED", opened)
                }
            }
        )
        // Hardened: مهلة 8s بدل 4s — مصافحة WS على 4G عالي RTT كانت تفشل كذباً.
        return withTimeoutOrNull(8_000) { opened.await() } == true
    }

    private fun handleSocketTerminated(reason: String, opened: CompletableDeferred<Boolean>) {
        socket = null
        attached = false
        recvConnected = false
        if (!opened.isCompleted) opened.complete(false)
        pending.values.forEach { it.cancel() }
        pending.clear()
        events.onError(reason)
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
                    if (kind == "video") videoPeerByConsumer[consumerId] = peerId
                    negotiateRecvLocked()
                }
            }
        }
    }

    fun subscribeToVideo(producerId: String? = null) {
        scope.launch {
            if (producerId != null) {
                // If a specific producerId is given, just consume it (if not already consumed)
                consumeOne("", producerId, "video")
            } else {
                negotiateRecv()
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
        val needsVideo = consumers.values.any { it.kind == "video" }
        val desiredKind = if (needsVideo) CallMediaKind.CONFERENCE else CallMediaKind.SPACE
        if (recvEngine == null) {
            val created = createRecvEngine(desiredKind)
            if (created !is ApiResult.Success) {
                events.onError("SFU_RECV_ENGINE_FAILED")
                return
            }
            recvEngineKind = desiredKind
        } else if (needsVideo && recvEngineKind == CallMediaKind.SPACE) {
            android.util.Log.w("SfuMediaClient", "recvEngine SPACE->CONFERENCE upgrade (first video arrived)")
            runCatching { recvEngine?.release() }
            recvEngine = null
            val created2 = createRecvEngine(CallMediaKind.CONFERENCE)
            if (created2 !is ApiResult.Success) {
                events.onError("SFU_RECV_ENGINE_RECREATE_FAILED")
                return
            }
            recvEngineKind = CallMediaKind.CONFERENCE
        }
        // أقسام sendonly من منظور الخادم: الخادم يرسل إلينا
        val sections = consumers.values.map { c ->
            SfuMediaKind(
                kind = c.kind,
                direction = "sendonly",
                payloadTypes = c.rtp.codecs.map { it.payloadType },
                codecs = c.rtp.codecs,
                ssrc = c.rtp.encodings.firstOrNull()?.ssrc,
                cname = c.rtp.rtcp.cname,
                msid = c.producerId.ifBlank { c.peerId },
                headerExtensions = c.rtp.headerExtensions
            )
        }
        val fakeOffer = SfuSdpFactory.remoteOffer(
            transport.iceParameters, transport.iceCandidates, transport.dtlsParameters, sections
        )
        recvEngine?.setRemote(SessionDescription(SessionDescription.Type.OFFER, fakeOffer)) { recvEngine?.answer() }
        val answerSdp = waitLocalAnswer(recvEngine) ?: return
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

    /**
     * يجب استدعاؤها تحت [mutex]. تستهلك مفتاح فيديو واحداً لمالك المسار القادم:
     * 1) مطابقة trackId بـ consumerId/producerId (msid) إن أمكن،
     * 2) وإلا أول مفتاح بترتيب التفاوض (m-sections).
     */
    private fun claimVideoPeerLocked(trackId: String): String? {
        if (videoPeerByConsumer.isEmpty()) return null
        // الملاءمة تُفضّل مستهلكاً لم يُسلَّم له مسار في هذا الجيل، لكنها **لا تحذف**
        // الربط. الحذف كان يجعل أول تسليم هو الأخير: أي إعادة تفاوض أو إعادة إنشاء
        // recvEngine (SPACE→CONFERENCE) تُعيد onTrack لكل m-lines فتُسقط المسارات
        // اللاحقة صامتاً ⇒ شاشة سوداء للمشاهد. التنظيف الحقيقي يتم عند
        // producerClosed/peerLeft + الكناسة الدفاعية أعلاه.
        if (trackId.isNotBlank()) {
            val hit = videoPeerByConsumer.keys.firstOrNull { consumerId ->
                consumerId !in deliveredVideoConsumers &&
                    (trackId.contains(consumerId) ||
                        consumers[consumerId]?.producerId?.takeIf { it.isNotBlank() }?.let { trackId.contains(it) } == true)
            }
            if (hit != null) {
                deliveredVideoConsumers.add(hit)
                return videoPeerByConsumer[hit]
            }
        }
        val fresh = videoPeerByConsumer.keys.firstOrNull { it !in deliveredVideoConsumers } ?: return null
        deliveredVideoConsumers.add(fresh)
        return videoPeerByConsumer[fresh]
    }

    /**
     * إعادة ضبط ICE لكلا الناقلين (send/recv) عند تبديل الشبكة WiFi↔4G —
     * يعيد استكشاف المسار عبر الشبكة الجديدة دون قطع المنتجين.
     * إن بقيت الـ PeerConnections في FAILED (تغيّر IP جذري لا يصلحه العرض
     * المحلي) استخدم [rejoin] كfallback كامل.
     */
    fun restartSfuIce() {
        scope.launch {
            sendTransport?.let { t -> request(JSONObject().put("type", "restartIce").put("transportId", t.id)) }
            recvTransport?.let { t -> request(JSONObject().put("type", "restartIce").put("transportId", t.id)) }
            runCatching { engine?.restartIce() }
            runCatching { recvEngine?.restartIce() }
            android.util.Log.d("SfuMediaClient", "restartSfuIce requested (send+recv)")
        }
    }

    fun isSfuAttached(): Boolean = attached

    /**
     * Fallback كامل عند تعذّر الوصول للـ transport بعد تبديل الشبكة:
     * مغادرة + إغلاق المقبس + attach جديد (تذكرة وناقلات جديدة) + publish.
     */
    suspend fun rejoin(roomId: String, kind: CallMediaKind): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            requestFireAndForget(JSONObject().put("type", "leave"))
            runCatching { socket?.close(1000, "sfu rejoin") }
            socket = null
            recvConnected = false
            attached = false
            runCatching { recvEngine?.release() }
            recvEngine = null
            mutex.withLock {
                consumers.clear()
                videoPeerByConsumer.clear()
            }
            producers.clear()
            if (!attach(roomId)) return@withContext false
            publish(kind)
        }.getOrDefault(false)
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
        val eng = engine ?: return ApiResult.Error(500, "ENGINE_NOT_CREATED")
        val result = eng.create(kind)
        if (result is ApiResult.Success) {
            localVideo = eng.localMedia?.videoTrack
            eng.offer()
        }
        return result
    }

    private suspend fun createRecvEngine(kind: CallMediaKind): ApiResult<Unit> {
        recvEngine?.release()
        // جيل محرك جديد: كل m-lines ستُسلَّم من جديد ⇒ اسمح بإعادة التسليم، وإلا
        // بقي المشاهد على مسار ميت (شاشة سوداء) بعد ترقية SPACE→CONFERENCE.
        deliveredVideoConsumers.clear()
        recvEngineKind = kind
        recvEngine = WebRtcEngine(context, object : WebRtcEngine.Events {
            override fun onLocalDescription(description: SessionDescription) = Unit
            override fun onIceCandidate(candidate: org.webrtc.IceCandidate) = Unit
            override fun onRemoteVideo(track: VideoTrack) {
                // الربط عبر videoPeerByConsumer تحت mutex: مطابقة msid/producerId أولاً ثم ترتيب m-sections
                val trackId = runCatching { track.id() }.getOrNull().orEmpty()
                scope.launch {
                    val peer = mutex.withLock { claimVideoPeerLocked(trackId) }
                    if (!peer.isNullOrBlank()) events.onRemoteVideo(peer, track)
                    else android.util.Log.w("SfuMediaClient", "onRemoteVideo unmatched track=$trackId — dropped (no pending video consumer)")
                }
            }
            override fun onConnectionState(state: PeerConnection.PeerConnectionState) = Unit
            override fun onNetworkStats(stats: NetworkStats) = events.onNetworkStats(stats)
            override fun onError(message: String) = events.onError(message)
        })
        // LEGENDARY Phase 6: pure consumers (live viewers) never run createEngine —
        // expose the recv context or remote rendering has no EGL (black screen).
        if (eglContext == null) eglContext = recvEngine?.eglContext
        val recv = recvEngine ?: return ApiResult.Error(500, "RECV_ENGINE_NOT_CREATED")
        return recv.createReceiverOnly(kind)
    }

    private suspend fun waitLocalOffer(eng: WebRtcEngine?): String? {
        repeat(30) {
            val sdp = eng?.lastLocalSdp
            if (!sdp.isNullOrBlank()) return sdp
            delay(50)
        }
        return eng?.lastLocalSdp
    }

    private suspend fun waitLocalAnswer(eng: WebRtcEngine?): String? {
        repeat(40) {
            val sdp = eng?.lastLocalSdp
            if (!sdp.isNullOrBlank() && (sdp.contains("a=setup:active") || sdp.contains("a=setup:passive"))) {
                return sdp
            }
            delay(50)
        }
        return eng?.lastLocalSdp
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
        return try {
            withTimeoutOrNull(5_000) { deferred.await() }
        } finally {
            pending.remove(id)
        }
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
