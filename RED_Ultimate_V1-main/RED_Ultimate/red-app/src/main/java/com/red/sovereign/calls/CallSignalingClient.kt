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
 * إشارة المكالمة الموحّدة — تشمل 1:1 والمجموعات والمؤتمرات.
 *
 * أنواع [type] المدعومة:
 *   OFFER / ANSWER / ICE / RENEGOTIATE — WebRTC negotiation
 *   END / REJECT / CANCELLED / UNAVAILABLE — lifecycle
 *   HOLD / RESUME — call hold
 *   CONFERENCE_INVITE / LIVE_INVITE — multi-party invites
 *   GROUP_CALL_INVITE  — دعوة مكالمة جماعية (iMO/Zoom style) — يرن لكل مدعو
 *   GROUP_CALL_ACCEPT  — قبول الانضمام للمجموعة
 *   GROUP_CALL_DECLINE — رفض الانضمام
 *   GROUP_CALL_STATUS  — حالة كل مدعو: ringing/joined/declined/no_answer
 *   GROUP_CALL_END     — إنهاء المكالمة الجماعية (من المضيف)
 *   CALL_REACTION      — إيموجي أثناء المكالمة الخاصة
 *   CALL_RAISE_HAND    — رفع يد في المكالمة الخاصة/الجماعية
 */
@Serializable
data class CallSignal(
    val callId: String? = null,
    val targetUserId: String = "",
    val sourceUserId: String? = null,
    val type: String,
    val mode: String = "VOICE",
    val payload: Map<String, String> = emptyMap(),
    // ── حقول المكالمات الجماعية ─────────────────────────
    /** معرف مجموعة/غرفة المكالمة الجماعية */
    val groupCallId: String? = null,
    /** قائمة معرفات المدعوين (لإشارة GROUP_CALL_INVITE) */
    val inviteeIds: List<String> = emptyList(),
    /** حالة المدعو (ringing/joined/declined) لإشارة GROUP_CALL_STATUS */
    val memberStatus: String? = null
)

class CallSignalingClient(private val context: Context, private val tokens: TokenStore, private val listener: Listener) {
    interface Listener { fun onSignal(signal: CallSignal); fun onConnected(); fun onDisconnected(); fun onError(message: String) }
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val http: OkHttpClient = SecureOkHttpClient.buildWebSocketClient(context)
    private val pendingSignals = PendingCallSignalQueue()
    private var socket: WebSocket? = null

    fun connect() {
        if (socket != null) return
        val token = tokens.accessToken ?: return listener.onError("UNAUTHORIZED")
        val url = ServerEndpoint.url().replaceFirst("http://", "ws://").replaceFirst("https://", "wss://") + "/ws/calls"
        socket = http.newWebSocket(Request.Builder().url(url).header("Authorization", "Bearer $token").build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                pendingSignals.flush(webSocket::send)
                listener.onConnected()
            }
            override fun onMessage(webSocket: WebSocket, text: String) { runCatching { json.decodeFromString<CallSignal>(text) }.onSuccess(listener::onSignal).onFailure { listener.onError("INVALID_CALL_SIGNAL") } }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { socket = null; listener.onDisconnected() }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                socket = null
                // Network drops must reconnect the active call, not treat the socket error as a fatal UI failure.
                listener.onDisconnected()
            }
        })
    }

    fun reconnect() {
        runCatching { socket?.cancel() }
        socket = null
        connect()
    }

    /** هل قناة الإشارات مفتوحة الآن — يُستخدم لمعرفة نجاح إعادة الاتصال فعلاً. */
    fun isConnected(): Boolean = socket != null

    fun send(signal: CallSignal) {
        val encoded = json.encodeToString(signal)
        if (socket?.send(encoded) == true) return

        pendingSignals.enqueue(encoded)
        connect()
    }

    /** إرسال دعوة مكالمة جماعية لقائمة من الأصدقاء */
    fun sendGroupCallInvite(groupCallId: String, inviteeIds: List<String>, isVideo: Boolean, hostName: String = "") {
        send(CallSignal(
            callId = groupCallId,
            targetUserId = "",
            type = "GROUP_CALL_INVITE",
            mode = if (isVideo) "VIDEO" else "VOICE",
            groupCallId = groupCallId,
            inviteeIds = inviteeIds,
            payload = if (hostName.isNotBlank()) mapOf("hostName" to hostName) else emptyMap()
        ))
    }

    /** الرد على دعوة مكالمة جماعية */
    fun sendGroupCallResponse(groupCallId: String, accepted: Boolean) {
        send(CallSignal(
            callId = groupCallId,
            type = if (accepted) "GROUP_CALL_ACCEPT" else "GROUP_CALL_DECLINE",
            groupCallId = groupCallId
        ))
    }

    /** إنهاء المكالمة الجماعية من طرف المضيف */
    fun sendGroupCallEnd(groupCallId: String) {
        send(CallSignal(callId = groupCallId, type = "GROUP_CALL_END", groupCallId = groupCallId))
    }

    /** إرسال رد فعل (إيموجي) أثناء المكالمة */
    fun sendReaction(callId: String?, targetUserId: String, emoji: String) {
        send(CallSignal(callId = callId, targetUserId = targetUserId, type = "CALL_REACTION", payload = mapOf("emoji" to emoji)))
    }

    /** طلب الكلام (رفع يد) أثناء المكالمة */
    fun sendRaiseHand(callId: String?, targetUserId: String) {
        send(CallSignal(callId = callId, targetUserId = targetUserId, type = "CALL_RAISE_HAND"))
    }

    fun close() {
        pendingSignals.clear()
        socket?.close(1000, "call service stopped")
        socket = null
    }
}
