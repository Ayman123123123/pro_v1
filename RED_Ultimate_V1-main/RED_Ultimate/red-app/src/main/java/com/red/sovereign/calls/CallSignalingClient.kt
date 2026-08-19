package com.red.sovereign.calls

import android.content.Context
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.core.ServerEndpoint
import com.red.sovereign.security.SecureOkHttpClient
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * عميل إشارات /ws/calls — متوافق مع YounesCallService و GroupCallService.
 */
class CallSignalingClient(
    private val context: Context,
    private val tokens: TokenStore,
    private val listener: Listener
) {
    interface Listener {
        fun onSignal(signal: CallSignal)
        fun onConnected()
        fun onDisconnected()
        fun onError(message: String)
    }

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val http: OkHttpClient = SecureOkHttpClient.buildWebSocketClient(context)
    private val pendingSignals = PendingCallSignalQueue()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: WebSocket? = null
    @Volatile private var connected = false

    fun connect() {
        if (socket != null) return
        val token = tokens.accessToken ?: return listener.onError("UNAUTHORIZED")
        val url = ServerEndpoint.url()
            .replaceFirst("http://", "ws://")
            .replaceFirst("https://", "wss://") + "/ws/calls"
        socket = http.newWebSocket(
            Request.Builder().url(url).header("Authorization", "Bearer $token").build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    connected = true
                    pendingSignals.flush(webSocket::send)
                    listener.onConnected()
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    runCatching { json.decodeFromString<CallSignal>(text) }
                        .onSuccess(listener::onSignal)
                        .onFailure { listener.onError("INVALID_CALL_SIGNAL") }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    socket = null
                    connected = false
                    listener.onDisconnected()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    socket = null
                    connected = false
                    if (response?.code == 401) {
                        // رمز الوصول منتهٍ — حدّثه ثم أعد الاتصال بدل
                        // الدوران اللانهائي على رمز مرفوض في reconnect().
                        refreshTokenThenConnect()
                        return
                    }
                    listener.onDisconnected()
                }
            }
        )
    }

    /** تحديث رمز الوصول عبر refresh token ثم إعادة الاتصال. */
    private fun refreshTokenThenConnect() {
        val refresh = tokens.refreshToken ?: run { listener.onError("UNAUTHORIZED"); return }
        scope.launch {
            when (val result = com.red.sovereign.auth.AuthApi(context).refresh(refresh)) {
                is com.red.sovereign.auth.ApiResult.Success -> {
                    tokens.updateTokens(result.value)
                    socket = null
                    connect()
                }
                is com.red.sovereign.auth.ApiResult.Error -> listener.onError("UNAUTHORIZED")
            }
        }
    }

    fun reconnect() {
        runCatching { socket?.cancel() }
        socket = null
        connected = false
        connect()
    }

    fun isConnected(): Boolean = connected

    fun send(signal: CallSignal) {
        val encoded = json.encodeToString(signal)
        if (socket?.send(encoded) == true) return
        pendingSignals.enqueue(encoded)
        connect()
    }

    fun sendGroupCallInvite(groupCallId: String, inviteeIds: List<String>, isVideo: Boolean, hostName: String = "") {
        send(
            CallSignal(
                callId = groupCallId,
                callType = if (isVideo) CallType.GROUP_CALL_VIDEO.name else CallType.GROUP_CALL_VOICE.name,
                targetUserId = "",
                type = CallSignal.GROUP_CALL_INVITE,
                mode = if (isVideo) "VIDEO" else "VOICE",
                groupCallId = groupCallId,
                inviteeIds = inviteeIds,
                payload = if (hostName.isNotBlank()) mapOf("hostName" to hostName) else emptyMap()
            )
        )
    }

    fun sendGroupCallResponse(groupCallId: String, accepted: Boolean) {
        send(
            CallSignal(
                callId = groupCallId,
                type = if (accepted) CallSignal.GROUP_CALL_ACCEPT else CallSignal.GROUP_CALL_DECLINE,
                groupCallId = groupCallId
            )
        )
    }

    fun sendGroupCallEnd(groupCallId: String) {
        send(CallSignal(callId = groupCallId, type = CallSignal.GROUP_CALL_END, groupCallId = groupCallId))
    }

    fun sendReaction(callId: String?, targetUserId: String, emoji: String) {
        send(
            CallSignal(
                callId = callId,
                targetUserId = targetUserId,
                type = CallSignal.CALL_REACTION,
                payload = mapOf("emoji" to emoji)
            )
        )
    }

    fun sendRaiseHand(callId: String?, targetUserId: String) {
        send(CallSignal(callId = callId, targetUserId = targetUserId, type = CallSignal.CALL_RAISE_HAND))
    }

    fun close() {
        pendingSignals.clear()
        socket?.close(1000, "call service stopped")
        socket = null
    }
}
