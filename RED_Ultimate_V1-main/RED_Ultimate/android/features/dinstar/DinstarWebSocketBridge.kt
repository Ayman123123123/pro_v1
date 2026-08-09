package com.red.features.dinstar

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 📡 YOUNES Dinstar WebSocket Bridge — اتصال حي بالباكند
 * 
 * يتصل بـ WebSocket endpoint في الباكند ويستقبل:
 * - DINSTAR_PORT_STATUS → تحديث حالة منفذ فوري
 * - DINSTAR_CDR → سجل مكالمة جديد
 * - DINSTAR_SMS → SMS وارد
 * - DINSTAR_USSD → رد USSD
 * - DINSTAR_EXCEPTION → حدث استثناء (call_fail, sim_removed)
 * 
 * الميزات:
 * - Reconnect تلقائي مع Exponential Backoff
 * - Heartbeat (ping/pong) كل 30ث
 * - SharedFlow للأحداث (multicast لعدة مستمعين)
 * - SupervisorJob لعزل الأعطال
 */
class DinstarWebSocketBridge(
    private val backendUrl: String = "http://192.168.1.50:8080"
) {
    companion object {
        private const val TAG = "RED.DinstarWS"
        private const val WS_PATH = "/ws/dinstar"
        private const val PING_INTERVAL_MS = 30_000L
        private const val MAX_RECONNECT_ATTEMPTS = 10
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MINUTES)  // WebSocket: no read timeout
        .pingInterval(PING_INTERVAL_MS, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val isConnected = AtomicBoolean(false)
    private val reconnectAttempts = AtomicInteger(0)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ─── SharedFlow — أحداث حية ───
    private val _wsEvents = MutableSharedFlow<DinstarWsEvent>(
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val wsEvents: SharedFlow<DinstarWsEvent> = _wsEvents.asSharedFlow()

    // ─── StateFlows ───
    private val _connectionState = MutableStateFlow(WsConnectionState.DISCONNECTED)
    val connectionState: StateFlow<WsConnectionState> = _connectionState.asStateFlow()

    enum class WsConnectionState(val labelAr: String) {
        CONNECTED("متصل"), CONNECTING("جاري الاتصال"), DISCONNECTED("غير متصل"), FAILED("فشل")
    }

    fun connect(token: String? = null) {
        if (isConnected.get()) return
        _connectionState.value = WsConnectionState.CONNECTING

        val wsScheme = if (backendUrl.startsWith("https")) "wss" else "ws"
        val baseUrl = backendUrl.removePrefix("https://").removePrefix("http://")
        val wsUrl = "$wsScheme://$baseUrl$WS_PATH${if (token != null) "?token=$token" else ""}"

        val requestBuilder = Request.Builder().url(wsUrl)
        if (token != null) requestBuilder.header("Authorization", "Bearer $token")
        val request = requestBuilder.build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected.set(true)
                reconnectAttempts.set(0)
                _connectionState.value = WsConnectionState.CONNECTED
                Log.i(TAG, "✅ WebSocket CONNECTED to $wsUrl")
                _wsEvents.tryEmit(DinstarWsEvent.Connected)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "WS message: ${text.take(200)}")
                scope.launch {
                    parseAndEmit(text)
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                Log.d(TAG, "WS binary message (${bytes.size} bytes)")
                // Binary protobuf — future: parse with protobuf schema
                _wsEvents.tryEmit(DinstarWsEvent.BinaryMessage(bytes.toByteArray()))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                isConnected.set(false)
                _connectionState.value = WsConnectionState.DISCONNECTED
                Log.i(TAG, "WebSocket closing: $code $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected.set(false)
                _connectionState.value = WsConnectionState.DISCONNECTED
                Log.i(TAG, "WebSocket closed: $code $reason")
                scheduleReconnect(token)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected.set(false)
                _connectionState.value = WsConnectionState.FAILED
                Log.e(TAG, "❌ WebSocket failure: ${t.message}")
                _wsEvents.tryEmit(DinstarWsEvent.Error(t.message ?: "Unknown error"))
                scheduleReconnect(token)
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        isConnected.set(false)
        _connectionState.value = WsConnectionState.DISCONNECTED
    }

    fun send(message: String): Boolean {
        return webSocket?.send(message) ?: false
    }

    private fun scheduleReconnect(token: String?) {
        val attempt = reconnectAttempts.incrementAndGet()
        if (attempt > MAX_RECONNECT_ATTEMPTS) {
            Log.w(TAG, "Max reconnect attempts ($MAX_RECONNECT_ATTEMPTS) reached")
            return
        }
        val delayMs = minOf(1000L * (1L shl minOf(attempt - 1, 6)), 60_000L) // 1s, 2s, 4s, 8s... max 60s
        Log.i(TAG, "Reconnecting in ${delayMs}ms (attempt $attempt)")
        scope.launch {
            delay(delayMs)
            connect(token)
        }
    }

    private suspend fun parseAndEmit(text: String) {
        runCatching {
            val json = org.json.JSONObject(text)
            val type = json.optString("type", "")
            val payload = json.optJSONObject("payload")?.toString() ?: text

            when (type) {
                "DINSTAR_PORT_STATUS" -> {
                    val port = json.optInt("port", -1)
                    val callState = json.optString("callState", "")
                    val signal = json.optInt("signal", 0)
                    _wsEvents.emit(DinstarWsEvent.PortStatusChanged(port, callState, signal))
                }
                "DINSTAR_CDR" -> {
                    _wsEvents.emit(DinstarWsEvent.CdrReceived(payload))
                }
                "DINSTAR_SMS" -> {
                    val port = json.optInt("port", -1)
                    val number = json.optString("number", "")
                    val smsText = json.optString("text", "")
                    _wsEvents.emit(DinstarWsEvent.IncomingSms(port, number, smsText))
                }
                "DINSTAR_USSD" -> {
                    val port = json.optInt("port", -1)
                    val ussdText = json.optString("text", "")
                    _wsEvents.emit(DinstarWsEvent.UssdReply(port, ussdText))
                }
                "DINSTAR_EXCEPTION" -> {
                    val port = json.optInt("port", -1)
                    val exceptionType = json.optString("exception_type", "")
                    val action = json.optString("action", "")
                    _wsEvents.emit(DinstarWsEvent.ExceptionEvent(port, exceptionType, action))
                }
                "HEARTBEAT" -> {
                    _wsEvents.emit(DinstarWsEvent.Heartbeat)
                }
                else -> {
                    _wsEvents.emit(DinstarWsEvent.UnknownMessage(type, text))
                }
            }
        }.onFailure { Log.w(TAG, "Failed to parse WS message: ${it.message}") }
    }

    fun destroy() {
        disconnect()
        scope.cancel()
        client.dispatcher.executorService.shutdown()
    }
}

// ━━━ أحداث WebSocket ━━━

sealed class DinstarWsEvent {
    object Connected : DinstarWsEvent()
    object Heartbeat : DinstarWsEvent()
    data class PortStatusChanged(val port: Int, val callState: String, val signal: Int) : DinstarWsEvent()
    data class CdrReceived(val payload: String) : DinstarWsEvent()
    data class IncomingSms(val port: Int, val number: String, val text: String) : DinstarWsEvent()
    data class UssdReply(val port: Int, val text: String) : DinstarWsEvent()
    data class ExceptionEvent(val port: Int, val type: String, val action: String) : DinstarWsEvent()
    data class Error(val message: String) : DinstarWsEvent()
    data class BinaryMessage(val bytes: ByteArray) : DinstarWsEvent()
    data class UnknownMessage(val type: String, val raw: String) : DinstarWsEvent()
}
