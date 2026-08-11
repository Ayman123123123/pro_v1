package com.red.sovereign.calls

import android.content.Context
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.core.ServerEndpoint
import com.red.sovereign.security.SecureOkHttpClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

@Serializable
data class CallSignal(
    val callId: String? = null,
    val targetUserId: String = "",
    val sourceUserId: String? = null,
    val type: String,
    val mode: String = "VOICE",
    val payload: Map<String, String> = emptyMap()
)

class CallSignalingClient(private val context: Context, private val tokens: TokenStore, private val listener: Listener) {
    interface Listener { fun onSignal(signal: CallSignal); fun onConnected(); fun onDisconnected(); fun onError(message: String) }
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val http: OkHttpClient = SecureOkHttpClient.buildWebSocketClient(context)
    private var socket: WebSocket? = null

    fun connect() {
        if (socket != null) return
        val token = tokens.accessToken ?: return listener.onError("UNAUTHORIZED")
        val url = ServerEndpoint.url().replaceFirst("http://", "ws://").replaceFirst("https://", "wss://") + "/ws/calls"
        socket = http.newWebSocket(Request.Builder().url(url).header("Authorization", "Bearer $token").build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) = listener.onConnected()
            override fun onMessage(webSocket: WebSocket, text: String) { runCatching { json.decodeFromString<CallSignal>(text) }.onSuccess(listener::onSignal).onFailure { listener.onError("INVALID_CALL_SIGNAL") } }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { socket = null; listener.onDisconnected() }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { socket = null; listener.onError(t.message ?: "CALL_SIGNALING_FAILED") }
        })
    }

    fun send(signal: CallSignal) { check(socket?.send(json.encodeToString(signal)) == true) { "Call signaling is not connected" } }
    fun close() { socket?.close(1000, "call service stopped"); socket = null }
}
