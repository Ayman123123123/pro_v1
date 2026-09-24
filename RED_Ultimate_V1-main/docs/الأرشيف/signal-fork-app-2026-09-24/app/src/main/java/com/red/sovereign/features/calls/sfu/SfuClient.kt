package com.red.sovereign.features.calls.sfu

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// ─── SFU Protocol Messages ────────────────────────────────────────────────

data class SfuRequest(
    val type: String,
    val requestId: String = UUID.randomUUID().toString(),
    val roomId: String? = null,
    val direction: String? = null,
    val transportId: String? = null,
    val dtlsParameters: Any? = null,
    val kind: String? = null,
    val rtpParameters: Any? = null,
    val rtpCapabilities: Any? = null,
    val producerId: String? = null,
    val consumerId: String? = null
)

data class SfuTransportOptions(
    val id: String,
    val iceParameters: Any,
    val iceCandidates: List<Any>,
    val dtlsParameters: Any,
    val sctpParameters: Any? = null
)

// ─── SfuClient ─────────────────────────────────────────────────────────────

/**
 * مكتبة عميل mediasoup SFU.
 *
 * تتولى البروتوكول الكامل:
 * 1. join → الحصول على rtpCapabilities + قائمة Producers الموجودين
 * 2. createTransport (send) → produce (audio + video)
 * 3. createTransport (recv) → consume لكل producer آخر + resumeConsumer
 * 4. leave → تنظيف
 *
 * كل العمليات متزامنة async/await بسبب طبيعة request/response للبروتوكول.
 */
class SfuClient(
    private val sfuUrl: String,      // e.g. wss://host:4000/sfu
    private val sfuToken: String,     // JWT ticket من الخادم
    private val roomId: String
) {
    companion object {
        private const val TAG = "SfuClient"
        private const val REQUEST_TIMEOUT_MS = 10_000L
    }

    interface SfuEventListener {
        fun onNewProducer(peerId: String, producerId: String, kind: String)
        fun onProducerClosed(consumerId: String, producerId: String)
        fun onPeerLeft(peerId: String)
        fun onConnected()
        fun onDisconnected()
        fun onError(message: String)
    }

    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()
    private var ws: WebSocket? = null
    private var eventListener: SfuEventListener? = null

    // Mediasoup capabilities (received after join)
    var rtpCapabilities: String? = null
        private set

    var peerId: String? = null
        private set

    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .build()

    // ── Connection ────────────────────────────────────────────────────

    fun connect(listener: SfuEventListener) {
        eventListener = listener
        val request = Request.Builder()
            .url(sfuUrl)
            .addHeader("Authorization", "Bearer $sfuToken")
            .build()

        ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "SFU WebSocket connected")
                scope.launch { eventListener?.onConnected() }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleMessage(bytes.utf8())
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(TAG, "SFU WS closed: $code $reason")
                scope.launch { eventListener?.onDisconnected() }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "SFU WS error: ${t.message}")
                scope.launch { eventListener?.onError(t.message ?: "SFU connection error") }
            }
        })
    }

    fun disconnect() {
        ws?.close(1000, "Client leave")
        ws = null
        pendingRequests.values.forEach { it.cancel() }
        pendingRequests.clear()
        scope.cancel()
    }

    // ── Protocol Operations ───────────────────────────────────────────

    suspend fun join(): JoinResult = withTimeout(REQUEST_TIMEOUT_MS) {
        val response = sendRequest(mapOf(
            "type" to "join",
            "roomId" to roomId
        ))
        val peerIdVal = response.getString("peerId")
        peerId = peerIdVal
        rtpCapabilities = response.getJSONObject("rtpCapabilities").toString()
        val existingProducers = mutableListOf<ExistingProducer>()
        val arr = response.optJSONArray("existingProducers")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                existingProducers.add(ExistingProducer(
                    peerId = obj.getString("peerId"),
                    producerId = obj.getString("producerId"),
                    kind = obj.getString("kind")
                ))
            }
        }
        JoinResult(peerIdVal, rtpCapabilities!!, existingProducers)
    }

    suspend fun createSendTransport(): SfuTransportResult = withTimeout(REQUEST_TIMEOUT_MS) {
        val response = sendRequest(mapOf("type" to "createTransport", "direction" to "send"))
        SfuTransportResult(
            transportId = response.getJSONObject("transportOptions").getString("id"),
            transportOptionsJson = response.getJSONObject("transportOptions").toString()
        )
    }

    suspend fun createRecvTransport(): SfuTransportResult = withTimeout(REQUEST_TIMEOUT_MS) {
        val response = sendRequest(mapOf("type" to "createTransport", "direction" to "recv"))
        SfuTransportResult(
            transportId = response.getJSONObject("transportOptions").getString("id"),
            transportOptionsJson = response.getJSONObject("transportOptions").toString()
        )
    }

    suspend fun connectTransport(transportId: String, dtlsParametersJson: String) = withTimeout(REQUEST_TIMEOUT_MS) {
        sendRequest(mapOf(
            "type" to "connectTransport",
            "transportId" to transportId,
            "dtlsParameters" to gson.fromJson(dtlsParametersJson, Any::class.java)
        ))
    }

    suspend fun produce(transportId: String, kind: String, rtpParametersJson: String): String = withTimeout(REQUEST_TIMEOUT_MS) {
        val response = sendRequest(mapOf(
            "type" to "produce",
            "transportId" to transportId,
            "kind" to kind,
            "rtpParameters" to gson.fromJson(rtpParametersJson, Any::class.java)
        ))
        response.getString("producerId")
    }

    suspend fun consume(transportId: String, producerId: String, rtpCapabilitiesJson: String): ConsumeResult = withTimeout(REQUEST_TIMEOUT_MS) {
        val response = sendRequest(mapOf(
            "type" to "consume",
            "transportId" to transportId,
            "producerId" to producerId,
            "rtpCapabilities" to gson.fromJson(rtpCapabilitiesJson, Any::class.java)
        ))
        ConsumeResult(
            consumerId = response.getString("consumerId"),
            producerId = response.getString("producerId"),
            kind = response.getString("kind"),
            rtpParametersJson = response.getJSONObject("rtpParameters").toString()
        )
    }

    suspend fun resumeConsumer(consumerId: String) = withTimeout(REQUEST_TIMEOUT_MS) {
        sendRequest(mapOf("type" to "resumeConsumer", "consumerId" to consumerId))
    }

    suspend fun pauseProducer(producerId: String) = withTimeout(REQUEST_TIMEOUT_MS) {
        sendRequest(mapOf("type" to "pauseProducer", "producerId" to producerId))
    }

    suspend fun resumeProducer(producerId: String) = withTimeout(REQUEST_TIMEOUT_MS) {
        sendRequest(mapOf("type" to "resumeProducer", "producerId" to producerId))
    }

    suspend fun leave() {
        try { sendRequest(mapOf("type" to "leave")) } catch (_: Exception) {}
    }

    // ── Private ───────────────────────────────────────────────────────

    private suspend fun sendRequest(body: Map<String, Any?>): JSONObject {
        val requestId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<JSONObject>()
        pendingRequests[requestId] = deferred

        val payload = body.toMutableMap().apply { put("requestId", requestId) }
        val json = gson.toJson(payload)
        ws?.send(json) ?: throw IllegalStateException("SFU WebSocket not connected")

        return deferred.await()
    }

    private fun handleMessage(raw: String) {
        try {
            val json = JSONObject(raw)
            val requestId = json.optString("requestId").takeIf { it.isNotBlank() }

            // If it has a requestId, it's a response to our request
            if (requestId != null) {
                val deferred = pendingRequests.remove(requestId)
                if (deferred != null) {
                    val status = json.optString("status")
                    if (status == "error") {
                        deferred.completeExceptionally(Exception(json.optString("error", "SFU error")))
                    } else {
                        deferred.complete(json)
                    }
                    return
                }
            }

            // Otherwise it's a server-initiated event
            when (val type = json.optString("type")) {
                "newProducer" -> {
                    val peerId = json.optString("peerId")
                    val producerId = json.optString("producerId")
                    val kind = json.optString("kind")
                    scope.launch { eventListener?.onNewProducer(peerId, producerId, kind) }
                }
                "producerClosed" -> {
                    val consumerId = json.optString("consumerId")
                    val producerId = json.optString("producerId")
                    scope.launch { eventListener?.onProducerClosed(consumerId, producerId) }
                }
                "peerLeft" -> {
                    val peerId = json.optString("peerId")
                    scope.launch { eventListener?.onPeerLeft(peerId) }
                }
                else -> Log.d(TAG, "Unhandled SFU event: $type")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse SFU message: ${e.message}")
        }
    }
}

// ─── Result Types ──────────────────────────────────────────────────────────

data class JoinResult(
    val peerId: String,
    val rtpCapabilitiesJson: String,
    val existingProducers: List<ExistingProducer>
)

data class ExistingProducer(val peerId: String, val producerId: String, val kind: String)

data class SfuTransportResult(val transportId: String, val transportOptionsJson: String)

data class ConsumeResult(
    val consumerId: String,
    val producerId: String,
    val kind: String,
    val rtpParametersJson: String
)
