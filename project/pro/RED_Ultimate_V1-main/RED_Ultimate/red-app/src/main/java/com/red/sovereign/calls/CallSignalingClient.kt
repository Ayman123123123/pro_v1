package com.red.sovereign.calls

import com.red.sovereign.auth.TokenStore
import com.red.sovereign.core.ServerEndpoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Authenticated signaling channel to `/ws/calls`.
 *
 * Hardening:
 *  - Exponential backoff reconnect with jitter (1s..15s) on transient failures.
 *  - Distinguishes an intentional close from a dropped link so onDisconnected
 *    is only surfaced when the call is actually lost.
 *  - Surfaces ACK / ERROR / UNAVAILABLE / BUSY so the service can react.
 *  - Heart-beats keep intermediaries from killing idle sockets.
 */
class CallSignalingClient(
    private val tokens: TokenStore,
    private val listener: Listener
) {
    interface Listener {
        fun onSignal(signal: CallSignal)
        fun onConnected()
        fun onDisconnected(permanent: Boolean)
        fun onError(message: String)
        /** Fired when an outgoing OFFER was rejected because the peer is offline/busy. */
        fun onCallUnavailable(reason: String) {}
    }

    @Serializable
    data class CallSignal(
        val callId: String? = null,
        val targetUserId: String = "",
        val sourceUserId: String? = null,
        val type: String,
        val mode: String = "VOICE",
        val payload: Map<String, String> = emptyMap()
    )

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    private val http = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: WebSocket? = null
    private var reconnectJob: Job? = null

    private val connected = AtomicBoolean(false)
    private val manuallyClosed = AtomicBoolean(false)
    private val attempt = AtomicInteger(0)

    @Synchronized
    fun connect() {
        if (manuallyClosed.get()) manuallyClosed.set(false)
        if (socket != null) return
        val token = tokens.accessToken
        if (token.isNullOrBlank()) {
            listener.onError("UNAUTHORIZED")
            return
        }
        val url = ServerEndpoint.url()
            .replaceFirst("http://", "ws://")
            .replaceFirst("https://", "wss://") + "/ws/calls"

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()

        socket = http.newWebSocket(request, internalListener)
    }

    fun send(signal: CallSignal) {
        val s = socket
        if (s == null || !connected.get()) {
            // Buffer the most recent signal so it can be sent right after reconnect.
            pendingSignal = signal
            scheduleReconnect(immediate = true)
            return
        }
        val enqueued = s.send(json.encodeToString(signal))
        if (!enqueued) {
            pendingSignal = signal
            scheduleReconnect(immediate = true)
        }
    }

    fun close() {
        manuallyClosed.set(true)
        reconnectJob?.cancel()
        reconnectJob = null
        attempt.set(0)
        runCatching { socket?.close(1000, "client shutdown") }
        socket = null
        connected.set(false)
    }

    private var pendingSignal: CallSignal? = null

    private val internalListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            connected.set(true)
            attempt.set(0)
            listener.onConnected()
            val pending = pendingSignal
            if (pending != null) {
                pendingSignal = null
                webSocket.send(json.encodeToString(pending))
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            // Server control envelopes: {"type":"ACK"}, {"type":"ERROR","error":"..."},
            // {"type":"UNAVAILABLE",...}, {"type":"BUSY",...}
            if (text.startsWith("{")) {
                val type = Regex("\"type\"\\s*:\\s*\"([A-Z_]+)\"").find(text)?.groupValues?.get(1)
                when (type) {
                    "ACK" -> return
                    "ERROR" -> {
                        val error = Regex("\"error\"\\s*:\\s*\"([^\"]+)\"")
                            .find(text)?.groupValues?.get(1) ?: "SIGNALING_ERROR"
                        listener.onError(error)
                        return
                    }
                    "UNAVAILABLE" -> { listener.onCallUnavailable("UNAVAILABLE"); return }
                    "BUSY" -> { listener.onCallUnavailable("BUSY"); return }
                }
            }
            runCatching { json.decodeFromString<CallSignal>(text) }
                .onSuccess(listener::onSignal)
                .onFailure { listener.onError("INVALID_CALL_SIGNAL") }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            connected.set(false)
            socket = null
            if (!manuallyClosed.get()) scheduleReconnect(immediate = false)
            else listener.onDisconnected(permanent = true)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            connected.set(false)
            socket = null
            listener.onError(t.message ?: "CALL_SIGNALING_FAILED")
            if (!manuallyClosed.get()) scheduleReconnect(immediate = false)
        }
    }

    private fun scheduleReconnect(immediate: Boolean) {
        if (manuallyClosed.get()) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            if (!immediate) {
                val n = attempt.incrementAndGet().coerceAtMost(5)
                val base = (1L shl (n - 1)) * 1000L // 1,2,4,8,16s
                val jitter = (0..1000L).random()
                delay(base.coerceAtMost(15_000L) + jitter)
            }
            if (manuallyClosed.get()) return@launch
            socket = null
            connect()
        }
    }
}
