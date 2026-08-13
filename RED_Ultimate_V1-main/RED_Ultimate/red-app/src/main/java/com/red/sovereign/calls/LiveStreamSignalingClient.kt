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

/**
 * WebSocket client for live broadcast signaling.
 * Connects to /ws/livestream and exchanges OFFER/ANSWER/ICE/LEAVE with a single broadcaster.
 */
@Serializable
data class LiveStreamSignal(
    val type: String,
    val roomId: String = "",
    val userId: String = "",
    val payload: Map<String, String> = emptyMap()
)

class LiveStreamSignalingClient(
    private val context: Context,
    private val tokens: TokenStore,
    private val listener: Listener
) {
    interface Listener {
        fun onConnected()
        fun onDisconnected()
        fun onSignal(signal: LiveStreamSignal)
        fun onError(message: String)
    }

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val http: OkHttpClient = SecureOkHttpClient.buildWebSocketClient(context)
    private var socket: WebSocket? = null

    fun reconnect(streamId: String) {
        runCatching { socket?.cancel() }
        socket = null
        connect(streamId)
    }

    fun connect(streamId: String) {
        if (socket != null) return
        val token = tokens.accessToken ?: return listener.onError("UNAUTHORIZED")
        val baseUrl = ServerEndpoint.url()
            .replaceFirst("http://", "ws://")
            .replaceFirst("https://", "wss://")
        val url = "$baseUrl/ws/livestream?roomId=$streamId"
        socket = http.newWebSocket(
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) = listener.onConnected()
                override fun onMessage(webSocket: WebSocket, text: String) {
                    runCatching { json.decodeFromString<LiveStreamSignal>(text) }
                        .onSuccess(listener::onSignal)
                        .onFailure { listener.onError("INVALID_LIVE_SIGNAL: ${it.message}") }
                }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    socket = null; listener.onDisconnected()
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    socket = null
                    listener.onDisconnected()
                }
            }
        )
    }

    fun send(signal: LiveStreamSignal) {
        socket?.send(json.encodeToString(signal)) ?: listener.onError("LIVESTREAM_NOT_CONNECTED")
    }

    fun join(streamId: String, userId: String, role: String) = send(
        LiveStreamSignal(type = "JOIN", roomId = streamId, userId = userId, payload = mapOf("role" to role))
    )

    fun leave(streamId: String, userId: String) = send(
        LiveStreamSignal(type = "LEAVE", roomId = streamId, userId = userId)
    )

    fun sendIce(streamId: String, userId: String, sdpMid: String, sdpMLineIndex: Int, candidate: String, targetUserId: String = "") = send(
        LiveStreamSignal(
            type = "ICE",
            roomId = streamId,
            userId = userId,
            payload = buildMap {
                put("sdpMid", sdpMid)
                put("sdpMLineIndex", sdpMLineIndex.toString())
                put("candidate", candidate)
                if (targetUserId.isNotBlank()) put("targetUserId", targetUserId)
            }
        )
    )

    fun sendOffer(streamId: String, userId: String, sdp: String, targetUserId: String = "") = send(
        LiveStreamSignal(
            type = "OFFER",
            roomId = streamId,
            userId = userId,
            payload = buildMap {
                put("sdp", sdp)
                if (targetUserId.isNotBlank()) put("targetUserId", targetUserId)
            }
        )
    )

    fun sendAnswer(streamId: String, userId: String, sdp: String, targetUserId: String = "") = send(
        LiveStreamSignal(
            type = "ANSWER",
            roomId = streamId,
            userId = userId,
            payload = buildMap {
                put("sdp", sdp)
                if (targetUserId.isNotBlank()) put("targetUserId", targetUserId)
            }
        )
    )

    fun sendChatMessage(streamId: String, userId: String, senderName: String, text: String) = send(
        LiveStreamSignal(
            type = "CHAT",
            roomId = streamId,
            userId = userId,
            payload = mapOf("senderName" to senderName, "text" to text)
        )
    )

    fun sendReaction(streamId: String, userId: String, emoji: String = "❤️") = send(
        LiveStreamSignal(
            type = "REACTION",
            roomId = streamId,
            userId = userId,
            payload = mapOf("emoji" to emoji)
        )
    )

    fun raiseHand(streamId: String, userId: String, userName: String) = send(
        LiveStreamSignal(
            type = "RAISE_HAND",
            roomId = streamId,
            userId = userId,
            payload = mapOf("userName" to userName)
        )
    )

    fun approveCoHost(streamId: String, userId: String, targetUserId: String) = send(
        LiveStreamSignal(
            type = "APPROVE_COHOST",
            roomId = streamId,
            userId = userId,
            payload = mapOf("targetUserId" to targetUserId)
        )
    )

    fun close() {
        socket?.close(1000, "livestream ended")
        socket = null
    }

    val isConnected: Boolean get() = socket != null
}
