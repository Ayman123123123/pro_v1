package com.red.sovereign.calls

import android.content.Context
import android.util.Log
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.core.ServerEndpoint
import com.red.sovereign.security.SecureOkHttpClient
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
 *   GROUP_SCREEN_SHARE_START / GROUP_SCREEN_SHARE_STOP — مشاركة الشاشة
 *   CALL_REACTION      — إيموجي أثناء المكالمة الخاصة
 *   CALL_RAISE_HAND    — رفع يد في المكالمة الخاصة/الجماعية
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
    private val http: OkHttpClient = SecureOkHttpClient.buildWebSocketClient(context).newBuilder()
        .pingInterval(20, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private var socket: WebSocket? = null
    private val pendingSignals = PendingCallSignalQueue()
    private val epoch = SignalingSocketEpoch()

    @Volatile
    private var connected = false

    // Exponential backoff reconnection
    private var reconnectAttempt = 0
    private var reconnectJob: kotlinx.coroutines.Job? = null
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    companion object {
        private const val TAG = "REDCall"
        private const val MAX_RECONNECT_ATTEMPTS = 10
        private const val BASE_RECONNECT_DELAY_MS = 1000L
        private const val MAX_RECONNECT_DELAY_MS = 30000L
    }

    fun isConnected(): Boolean = connected

    fun connect() {
        if (socket != null && connected) {
            Log.d(TAG, "connect(): already connected")
            return
        }

        epoch.invalidate()
        val currentEpoch = epoch.begin()

        if (socket != null) {
            val oldSocket = socket
            socket = null
            runCatching { oldSocket?.close(1000, "reconnect") }
        }

        val token = tokens.accessToken
        if (token == null) {
            Log.w(TAG, "connect(): no access token")
            listener.onError("UNAUTHORIZED")
            return
        }

        val url = ServerEndpoint.url().replaceFirst("http://", "ws://").replaceFirst("https://", "wss://") + "/ws/calls"
        Log.d(TAG, "connect(): connecting to $url")

        socket = http.newWebSocket(
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (!epoch.isCurrent(currentEpoch) || webSocket !== socket) return
                    connected = true
                    reconnectAttempt = 0
                    reconnectJob?.cancel()
                    reconnectJob = null
                    Log.d(TAG, "onOpen: signaling connected, flushing ${pendingSignals.size()} queued signals")
                    pendingSignals.flush { signalJson ->
                        runCatching { webSocket.send(signalJson) }.getOrDefault(false)
                    }
                    Log.d(TAG, "onOpen: signaling connected")
                    listener.onConnected()
                    PendingOfferPoller.pollNow(context)
                    PendingOfferPoller.schedule(context)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (!epoch.isCurrent(currentEpoch) || webSocket !== socket) return
                    runCatching { json.decodeFromString<CallSignal>(text) }
                        .onSuccess { signal ->
                            Log.d(TAG, "onMessage: type=${signal.type} from=${signal.sourceUserId} target=${signal.targetUserId} callId=${signal.callId}")
                            listener.onSignal(signal)
                        }
                        .onFailure {
                            Log.w(TAG, "onMessage: decode failed, ignoring frame: ${it.message}")
                        }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (!epoch.isCurrent(currentEpoch) || webSocket !== socket) return
                    connected = false
                    socket = null
                    Log.w(TAG, "onClosed: code=$code reason=$reason")
                    listener.onDisconnected()
                    scheduleReconnect()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (!epoch.isCurrent(currentEpoch) || webSocket !== socket) return
                    connected = false
                    socket = null
                    Log.e(TAG, "onFailure: ${t.javaClass.simpleName}: ${t.message}")
                    listener.onDisconnected()
                    scheduleReconnect()
                }
            }
        )
    }

    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        if (reconnectAttempt >= MAX_RECONNECT_ATTEMPTS) {
            Log.e(TAG, "Max reconnect attempts reached, giving up")
            listener.onError("MAX_RECONNECT_ATTEMPTS_REACHED")
            return
        }
        reconnectAttempt++
        val delay = minOf(BASE_RECONNECT_DELAY_MS * (1L shl (reconnectAttempt - 1)), MAX_RECONNECT_DELAY_MS)
        val jitter = (delay * 0.1 * (0..100).random()).toLong()
        val totalDelay = delay + jitter
        Log.d(TAG, "Scheduling reconnect attempt $reconnectAttempt in ${totalDelay}ms")
        reconnectJob = scope.launch {
            kotlinx.coroutines.delay(totalDelay)
            if (!connected) connect()
        }
    }

    fun reconnect() {
        Log.d(TAG, "reconnect()")
        epoch.invalidate()
        connected = false
        val oldSocket = socket
        socket = null
        runCatching { oldSocket?.cancel() }
        reconnectAttempt = 0
        reconnectJob?.cancel()
        reconnectJob = null
        connect()
    }

    fun send(signal: CallSignal) {
        val signalJson = runCatching { json.encodeToString(signal) }.getOrNull()
        if (signalJson == null) {
            Log.e(TAG, "send: serialization failed for type=${signal.type}")
            return
        }

        val currentSocket = socket
        val ok = if (connected && currentSocket != null) {
            runCatching { currentSocket.send(signalJson) }.getOrDefault(false)
        } else false

        Log.d(TAG, "send: type=${signal.type} target=${signal.targetUserId} callId=${signal.callId} ok=$ok")
        if (!ok) {
            pendingSignals.enqueue(signalJson)
            if (!connected) runCatching { connect() }
        }
    }

    /** إرسال دعوة مكالمة جماعية لقائمة من الأصدقاء */
    fun sendGroupCallInvite(groupCallId: String, inviteeIds: List<String>, isVideo: Boolean, hostName: String = "", sourceGroupId: String = "") {
        send(CallSignal(
            callId = groupCallId,
            targetUserId = "",
            type = CallSignal.GROUP_CALL_INVITE,
            mode = if (isVideo) "VIDEO" else "VOICE",
            groupCallId = groupCallId,
            inviteeIds = inviteeIds,
            payload = buildMap {
                if (hostName.isNotBlank()) put("hostName", hostName)
                if (sourceGroupId.isNotBlank()) put("sourceGroupId", sourceGroupId)
            }
        ))
    }

    /** الرد على دعوة مكالمة جماعية */
    fun sendGroupCallResponse(groupCallId: String, accepted: Boolean) {
        send(CallSignal(
            callId = groupCallId,
            type = if (accepted) CallSignal.GROUP_CALL_ACCEPT else CallSignal.GROUP_CALL_DECLINE,
            groupCallId = groupCallId
        ))
    }

    /** إنهاء المكالمة الجماعية من طرف المضيف */
    fun sendGroupCallEnd(groupCallId: String) {
        send(CallSignal(callId = groupCallId, type = CallSignal.GROUP_CALL_END, groupCallId = groupCallId))
    }

    /** مغادرة عضو غير مضيف — تُحدَّث حالة العضو عند البقية (كانت تغادر بصمت فتخلد zombie) */
    fun sendGroupCallLeave(groupCallId: String, memberUserId: String) {
        send(CallSignal(
            callId = groupCallId,
            type = CallSignal.GROUP_CALL_STATUS,
            groupCallId = groupCallId,
            memberStatus = "left",
            payload = mapOf("memberId" to memberUserId)
        ))
    }

    /** بدء مشاركة الشاشة في المكالمة الجماعية */
    fun sendScreenShareStart(groupCallId: String, presenterUserId: String) {
        send(CallSignal(
            callId = groupCallId,
            targetUserId = presenterUserId,
            type = CallSignal.GROUP_SCREEN_SHARE_START,
            groupCallId = groupCallId
        ))
    }

    /** إيقاف مشاركة الشاشة في المكالمة الجماعية */
    fun sendScreenShareStop(groupCallId: String, presenterUserId: String) {
        send(CallSignal(
            callId = groupCallId,
            targetUserId = presenterUserId,
            type = CallSignal.GROUP_SCREEN_SHARE_STOP,
            groupCallId = groupCallId
        ))
    }

    /** إرسال رد فعل (إيموجي) أثناء المكالمة */
    fun sendReaction(callId: String?, targetUserId: String, emoji: String) {
        send(CallSignal(callId = callId, targetUserId = targetUserId, type = CallSignal.CALL_REACTION, payload = mapOf("emoji" to emoji)))
    }

    /** طلب الكلام (رفع يد) أثناء المكالمة */
    fun sendRaiseHand(callId: String?, targetUserId: String) {
        send(CallSignal(callId = callId, targetUserId = targetUserId, type = CallSignal.CALL_RAISE_HAND))
    }

    fun close() {
        Log.d(TAG, "close()")
        epoch.invalidate()
        connected = false
        val oldSocket = socket
        socket = null
        runCatching { oldSocket?.close(1000, "call service stopped") }
        pendingSignals.clear()
    }

    companion object {
        private const val TAG = "REDCall"
    }
}
