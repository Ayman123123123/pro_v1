package com.red.sovereign.features.dinstar

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okio.ByteString
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * 📡 YOUNES Dinstar WebSocket Bridge
 */
class DinstarWebSocketBridge(private val backendUrl: String = "http://192.168.1.50:8080") {
    companion object {
        private const val TAG = "RED.DinstarWS"
        private const val WS_PATH = "/ws/dinstar"
        private const val PING_INTERVAL_MS = 30_000L
    }

    private val client = OkHttpClient.Builder()
        .pingInterval(PING_INTERVAL_MS, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val isConnected = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _wsEvents = MutableSharedFlow<DinstarWsEvent>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val wsEvents: SharedFlow<DinstarWsEvent> = _wsEvents.asSharedFlow()

    fun connect(token: String? = null) {
        if (isConnected.get()) return
        val wsUrl = backendUrl.replace("http", "ws").trimEnd('/') + WS_PATH
        val request = Request.Builder().url(wsUrl).apply {
            if (token != null) header("Authorization", "Bearer $token")
        }.build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected.set(true)
                _wsEvents.tryEmit(DinstarWsEvent.Connected)
                Log.i(TAG, "Dinstar WS Connected")
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch { parseAndEmit(text) }
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { isConnected.set(false) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected.set(false)
                _wsEvents.tryEmit(DinstarWsEvent.Error(t.message ?: "WS_FAILURE"))
            }
        })
    }

    fun disconnect() { webSocket?.close(1000, "disconnect"); isConnected.set(false) }

    private suspend fun parseAndEmit(text: String) {
        runCatching {
            val json = org.json.JSONObject(text)
            val type = json.optString("type", "")
            when (type) {
                "DINSTAR_PORT_STATUS" -> _wsEvents.emit(DinstarWsEvent.PortStatusChanged(json.optInt("port"), json.optString("callState"), json.optInt("signal")))
                "DINSTAR_CDR" -> _wsEvents.emit(DinstarWsEvent.CdrReceived(text))
                "DINSTAR_SMS" -> _wsEvents.emit(DinstarWsEvent.IncomingSms(json.optInt("port"), json.optString("number"), json.optString("text")))
                "HEARTBEAT" -> _wsEvents.emit(DinstarWsEvent.Heartbeat)
            }
        }
    }

    fun destroy() { disconnect(); scope.cancel() }
}

sealed class DinstarWsEvent {
    object Connected : DinstarWsEvent()
    object Heartbeat : DinstarWsEvent()
    data class PortStatusChanged(val port: Int, val callState: String, val signal: Int) : DinstarWsEvent()
    data class CdrReceived(val payload: String) : DinstarWsEvent()
    data class IncomingSms(val port: Int, val number: String, val text: String) : DinstarWsEvent()
    data class Error(val message: String) : DinstarWsEvent()
}
