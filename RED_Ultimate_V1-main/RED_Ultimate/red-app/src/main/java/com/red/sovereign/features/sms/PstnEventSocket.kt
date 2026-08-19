package com.red.sovereign.features.sms

import com.red.sovereign.auth.TokenStore
import com.red.sovereign.core.ServerEndpoint
import com.red.sovereign.security.SecureOkHttpClient
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * عميل WebSocket لأحداث PSTN/SMS الحية عبر `/ws/pstn`.
 *
 * يبث الخادم:
 * - SMS_RECEIVED  {id, number, content, time, port}
 * - SMS_STATUS    {id, number, status}
 * - PSTN_CALL_EVENT {callId, event, number, cause, port, redId}
 * - PSTN_INCOMING {number, caller, port}
 *
 * المعامل: Authorization: Bearer <accessToken> (يطابقه JwtHandshakeInterceptor).
 */
class PstnEventSocket(
    private val tokens: TokenStore,
    private val onEnvelope: (PstnWsEnvelope) -> Unit = {},
    private val onState: (Boolean) -> Unit = {}
) {
    private val client: OkHttpClient = SecureOkHttpClient.buildWebSocketClient(tokens.context)
    private var socket: WebSocket? = null
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    @Volatile private var active = false

    fun connect() {
        if (active) return
        val token = tokens.accessToken ?: return
        val wsBase = ServerEndpoint.url().replaceFirst("http://", "ws://").replaceFirst("https://", "wss://")
        val request = Request.Builder().url(wsBase.trimEnd('/') + "/ws/pstn")
            .header("Authorization", "Bearer $token")
            .build()
        active = true
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { onState(true) }
            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { json.decodeFromString<PstnWsEnvelope>(text) }.onSuccess(onEnvelope)
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { active = false; onState(false) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { active = false; onState(false) }
        })
    }

    fun disconnect() {
        active = false
        runCatching { socket?.close(1000, "app") }
        socket = null
    }

    fun isActive() = active
}
