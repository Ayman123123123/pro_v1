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
    private var socket: WebSocket? = null
    private var engine: WebRtcEngine? = null
    private var sendTransport: SfuTransportOptions? = null
    private var recvTransport: SfuTransportOptions? = null
    private var routerCaps: JSONObject? = null
    private var recvConnected = false
    private var canProduce = true
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
        consumeExisting(joined.optJSONArray("existingProducers"))
        true
    }

    suspend fun publish(kind: CallMediaKind): Boolean = withContext(Dispatchers.IO) {
        if (!canProduce) return@withContext false
        val transport = sendTransport ?: return@withContext false
        val created = createEngine(kind)
        if (created !is ApiResult.Success) return@withContext false
        val offerSdp = waitLocalSdp() ?: return@withContext false
        val dtls = SfuSdpFactory.dtlsFromLocalSdp(offerSdp) ?: return@withContext false
        val fingerprint = dtls.fingerprints.firstOrNull() ?: return@withContext false
        val connected = request(
            JSONObject()
                .put("type", "connectTransport")
                .put("transportId", transport.id)
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
        if (connected?.optString("status") != "transportConnected") return@withContext false
        SfuSdpFactory.rtpParametersFromLocal(offerSdp, "audio")?.let { produce("audio", transport.id, it) }
        if (kind.wantsVideo) {
            SfuSdpFactory.rtpParametersFromLocal(offerSdp, "video")?.let { produce("video", transport.id, it) }
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
    fun pollStats() = engine?.pollStats()

    fun release() {
        pending.values.forEach { it.cancel() }
        pending.clear()
        requestFireAndForget(JSONObject().put("type", "leave"))
        socket?.close(1000, "sfu leave")
        socket = null
        engine?.release()
        engine = null
        eglContext = null
        localVideo = null
        producers.clear()
        recvConnected = false
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
                        "peerLeft" -> events.onPeerLeft(body.optString("peerId"))
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

    private suspend fun produce(kind: String, transportId: String, rtp: SfuRtpParameters) {
        val payload = JSONObject()
            .put("type", "produce")
            .put("transportId", transportId)
            .put("kind", kind)
            .put("rtpParameters", JSONObject(json.encodeToString(SfuRtpParameters.serializer(), rtp)))
        val response = request(payload)
        val id = response?.optString("producerId").orEmpty()
        if (id.isNotBlank()) producers[kind] = id
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
                ensureRecvConnected()
                val consume = JSONObject()
                    .put("type", "consume")
                    .put("transportId", transport.id)
                    .put("producerId", producerId)
                routerCaps?.let { consume.put("rtpCapabilities", it) }
                val response = request(consume) ?: return@launch
                if (response.optString("status") != "consuming") return@launch
                val consumerId = response.optString("consumerId")
                request(JSONObject().put("type", "resumeConsumer").put("consumerId", consumerId))
            }
        }
    }

    private suspend fun ensureRecvConnected() {
        if (recvConnected) return
        val transport = recvTransport ?: return
        val fingerprint = transport.dtlsParameters.fingerprints.firstOrNull()
        request(
            JSONObject()
                .put("type", "connectTransport")
                .put("transportId", transport.id)
                .put(
                    "dtlsParameters",
                    JSONObject()
                        .put("role", "client")
                        .put(
                            "fingerprints",
                            JSONArray().put(
                                JSONObject()
                                    .put("algorithm", fingerprint?.algorithm ?: "sha-256")
                                    .put("value", fingerprint?.value.orEmpty())
                            )
                        )
                )
        )
        recvConnected = true
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
            override fun onRemoteVideo(track: VideoTrack) = events.onRemoteVideo("", track)
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

    private suspend fun waitLocalSdp(): String? {
        repeat(20) {
            engine?.lastLocalSdp?.takeIf { it.isNotBlank() }?.let { return it }
            delay(50)
        }
        return engine?.lastLocalSdp
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
}
