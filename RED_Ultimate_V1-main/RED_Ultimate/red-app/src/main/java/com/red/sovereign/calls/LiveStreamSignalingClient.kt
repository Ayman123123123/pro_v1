package com.red.sovereign.calls

import android.content.Context
import android.util.Log
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.core.ServerEndpoint
import com.red.sovereign.security.SecureOkHttpClient
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

object FlexibleStringMapSerializer : KSerializer<Map<String, String>> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexibleStringMap", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Map<String, String>) {
        encoder.encodeSerializableValue(kotlinx.serialization.serializer(), value)
    }

    override fun deserialize(decoder: Decoder): Map<String, String> {
        if (decoder !is JsonDecoder) return emptyMap()
        val jsonElement = decoder.decodeJsonElement()
        if (jsonElement !is JsonObject) return emptyMap()
        return jsonElement.mapValues { (_, element) ->
            if (element is JsonPrimitive) element.content else element.toString()
        }
    }
}

/**
 * WebSocket client for live broadcast signaling.
 * Connects to /ws/livestream and exchanges OFFER/ANSWER/ICE/LEAVE with a single broadcaster.
 */
@Serializable
data class LiveStreamSignal(
    val type: String,
    val roomId: String = "",
    val userId: String = "",
    @Serializable(with = FlexibleStringMapSerializer::class)
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
    private val http: OkHttpClient = SecureOkHttpClient.buildWebSocketClient(context).newBuilder()
        .pingInterval(20, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private var socket: WebSocket? = null
    private val pendingSignals = PendingCallSignalQueue()
    private val epoch = SignalingSocketEpoch()

    @Volatile
    private var connected = false

    @Volatile
    private var activeStreamId: String = ""

    // Exponential backoff reconnection
    private var reconnectAttempt = 0
    private var reconnectJob: kotlinx.coroutines.Job? = null
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    companion object {
        private const val TAG = "LiveSignal"
        private const val MAX_RECONNECT_ATTEMPTS = 10
        private const val BASE_RECONNECT_DELAY_MS = 1000L
        private const val MAX_RECONNECT_DELAY_MS = 30000L
    }

    fun isConnected(): Boolean = connected

    fun reconnect(streamId: String) {
        Log.d(TAG, "reconnect() streamId=$streamId")
        epoch.invalidate()
        connected = false
        val oldSocket = socket
        socket = null
        runCatching { oldSocket?.cancel() }
        reconnectAttempt = 0
        reconnectJob?.cancel()
        reconnectJob = null
        connect(streamId)
    }

    fun connect(streamId: String) {
        if (socket != null && connected && activeStreamId == streamId) {
            Log.d(TAG, "connect(): already connected to $streamId")
            return
        }

        activeStreamId = streamId
        epoch.invalidate()
        val currentEpoch = epoch.begin()

        if (socket != null) {
            val oldSocket = socket
            socket = null
            runCatching { oldSocket?.close(1000, "reconnect") }
        }

        val token = tokens.accessToken
        if (token == null) {
            listener.onError("UNAUTHORIZED")
            return
        }

        val baseUrl = ServerEndpoint.url()
            .replaceFirst("http://", "ws://")
            .replaceFirst("https://", "wss://")
        val url = "$baseUrl/ws/livestream?roomId=$streamId"
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
                    Log.d(TAG, "onOpen: live stream signaling connected, flushing queued signals")
                    pendingSignals.flush { signalJson ->
                        runCatching { webSocket.send(signalJson) }.getOrDefault(false)
                    }
                    listener.onConnected()
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (!epoch.isCurrent(currentEpoch) || webSocket !== socket) return
                    runCatching { json.decodeFromString<LiveStreamSignal>(text) }
                        .onSuccess(listener::onSignal)
                        .onFailure {
                            Log.w(TAG, "onMessage: parse failed, ignoring frame: ${it.message}")
                        }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (!epoch.isCurrent(currentEpoch) || webSocket !== socket) return
                    connected = false
                    socket = null
                    Log.w(TAG, "onClosed: code=$code reason=$reason")
                    listener.onDisconnected()
                    scheduleReconnect(streamId)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (!epoch.isCurrent(currentEpoch) || webSocket !== socket) return
                    connected = false
                    socket = null
                    Log.e(TAG, "onFailure ${t.javaClass.simpleName}: ${t.message}")
                    listener.onDisconnected()
                    scheduleReconnect(streamId)
                }
            }
        )
    }

    private fun scheduleReconnect(streamId: String) {
        reconnectJob?.cancel()
        if (reconnectAttempt >= MAX_RECONNECT_ATTEMPTS) {
            Log.e(TAG, "Max reconnect attempts reached for stream $streamId, giving up")
            listener.onError("MAX_RECONNECT_ATTEMPTS_REACHED")
            return
        }
        reconnectAttempt++
        val delay = minOf(BASE_RECONNECT_DELAY_MS * (1L shl (reconnectAttempt - 1)), MAX_RECONNECT_DELAY_MS)
        val jitter = (delay * 0.1 * (0..100).random()).toLong()
        val totalDelay = delay + jitter
        Log.d(TAG, "Scheduling reconnect attempt $reconnectAttempt for stream $streamId in ${totalDelay}ms")
        reconnectJob = scope.launch {
            kotlinx.coroutines.delay(totalDelay)
            if (!connected) connect(streamId)
        }
    }

    fun send(signal: LiveStreamSignal) {
        val signalJson = runCatching { json.encodeToString(signal) }.getOrNull()
        if (signalJson == null) {
            Log.e(TAG, "send: serialization failed for type=${signal.type}")
            return
        }

        val currentSocket = socket
        val ok = if (connected && currentSocket != null) {
            runCatching { currentSocket.send(signalJson) }.getOrDefault(false)
        } else false

        if (!ok) {
            pendingSignals.enqueue(signalJson)
            val targetStream = signal.roomId.ifBlank { activeStreamId }
            if (!connected && targetStream.isNotBlank()) {
                runCatching { connect(targetStream) }
            }
        }
    }

    fun join(streamId: String, userId: String, role: String, password: String? = null) = send(
        LiveStreamSignal(type = "JOIN", roomId = streamId, userId = userId, payload = buildMap {
            put("role", role)
            if (!password.isNullOrBlank()) put("password", password)
        })
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

    fun sendChatMessage(
        streamId: String,
        userId: String,
        senderName: String,
        text: String,
        replyToId: String? = null,
        chatId: String? = null
    ) = send(
        LiveStreamSignal(
            type = "CHAT",
            roomId = streamId,
            userId = userId,
            payload = buildMap {
                // المعرّف يُرسل ليتبنّاه الخادم ويبثّه للجميع، فيتفق المرسل والمستقبلون
                // والخادم على معرّف واحد. بدونه كان كل طرف يولّد معرّفاً مختلفاً ⇒ الحذف
                // لا يطابق شيئاً، وتكرار لرسالة المرسل بعد تحديث السجل، واقتباس مكسور.
                if (!chatId.isNullOrBlank()) put("id", chatId)
                put("senderName", senderName)
                put("text", text)
                if (!replyToId.isNullOrBlank()) put("replyToId", replyToId)
            }
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

    /**
     * الهدايا معطلة بقرار المنتج ("بدون هدايا"): يُحفظ التوقيع للتوافق مع أي
     * مستدعٍ قديم، لكن الإرسال يتحول لتفاعل مجاني REACTION — لا يُبث حدث GIFT
     * ولا تُخصم عملات. التفاعلات المجانية هي البديل الوحيد.
     */
    @Deprecated(
        "Gifts disabled by product decision — sends a free REACTION instead",
        ReplaceWith("sendReaction(streamId, userId, giftEmoji)")
    )
    fun sendGift(streamId: String, userId: String, senderName: String, giftId: String, giftEmoji: String, costCoins: Int) =
        sendReaction(streamId, userId, giftEmoji.ifBlank { "❤️" })

    /** تثبيت تعليق (المذيع فقط — الخادم يتحقق من الدور ويُرحّل للجميع). */
    fun sendPinMessage(streamId: String, userId: String, messageId: String, senderName: String, text: String) = send(
        LiveStreamSignal(
            type = "PIN_MESSAGE",
            roomId = streamId,
            userId = userId,
            payload = mapOf("messageId" to messageId, "senderName" to senderName, "text" to text)
        )
    )

    /** إلغاء تثبيت التعليق (المذيع فقط). */
    fun sendUnpinMessage(streamId: String, userId: String) = send(
        LiveStreamSignal(
            type = "UNPIN_MESSAGE",
            roomId = streamId,
            userId = userId
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

    fun lowerHand(streamId: String, userId: String) = send(
        LiveStreamSignal(type = "LOWER_HAND", roomId = streamId, userId = userId)
    )

    fun approveCoHost(streamId: String, userId: String, targetUserId: String) = send(
        LiveStreamSignal(
            type = "APPROVE_COHOST",
            roomId = streamId,
            userId = userId,
            payload = mapOf("targetUserId" to targetUserId)
        )
    )

    fun rejectCoHost(streamId: String, userId: String, targetUserId: String) = send(
        LiveStreamSignal(
            type = "REJECT_COHOST",
            roomId = streamId,
            userId = userId,
            payload = mapOf("targetUserId" to targetUserId)
        )
    )

    fun removeCoHost(streamId: String, userId: String, targetUserId: String) = send(
        LiveStreamSignal(
            type = "REMOVE_COHOST",
            roomId = streamId,
            userId = userId,
            payload = mapOf("targetUserId" to targetUserId)
        )
    )

    fun leaveCoHost(streamId: String, userId: String) = send(
        LiveStreamSignal(type = "LEAVE_COHOST", roomId = streamId, userId = userId)
    )

    fun kickViewer(streamId: String, userId: String, targetUserId: String) = send(
        LiveStreamSignal(
            type = "KICK",
            roomId = streamId,
            userId = userId,
            payload = mapOf("targetUserId" to targetUserId)
        )
    )

    fun setSlowMode(streamId: String, userId: String, seconds: Int) = send(
        LiveStreamSignal(
            type = "SLOW_MODE_SET",
            roomId = streamId,
            userId = userId,
            payload = mapOf("seconds" to seconds.toString())
        )
    )

    fun muteViewer(streamId: String, userId: String, targetUserId: String, muted: Boolean) = send(
        LiveStreamSignal(
            type = if (muted) "MUTED" else "UNMUTED",
            roomId = streamId,
            userId = userId,
            payload = mapOf("targetUserId" to targetUserId)
        )
    )

    /** LEGENDARY Phase 6: مشاهد بلا SFU يطلب خدمة mesh احتياطية من المذيع. */
    fun sendViewerNeedsMesh(streamId: String, userId: String) = send(
        LiveStreamSignal(
            type = "VIEWER_NEEDS_MESH",
            roomId = streamId,
            userId = userId,
            payload = emptyMap()
        )
    )

    fun setQuality(streamId: String, userId: String, quality: String) = send(
        LiveStreamSignal(
            type = "SET_QUALITY",
            roomId = streamId,
            userId = userId,
            payload = mapOf("quality" to quality)
        )
    )

    fun close() {
        Log.d(TAG, "close()")
        epoch.invalidate()
        connected = false
        val oldSocket = socket
        socket = null
        runCatching { oldSocket?.close(1000, "livestream ended") }
        pendingSignals.clear()
    }

    companion object {
        private const val TAG = "LiveSignal"
    }
}
