package com.red.sovereign.calls

import android.content.Context
import android.util.Log
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
 * رسالة إشارات المؤتمر — تُرسل وتُستقبل عبر WebSocket مع media-sfu.
 * النوع "JOIN", "PRODUCE", "CONSUME", "ICE", "LEAVE", "ROOM_STATE", "PARTICIPANT_LEFT",
 * "PRODUCER_READY", "CONSUMER_READY", "LIVE_START", "LIVE_STOP"
 */
@Serializable
data class ConferenceSignal(
    val type: String,
    val roomId: String = "",
    val userId: String = "",
    val payload: Map<String, String> = emptyMap()
)

/** مصفوفة تصاريح المشارك في الجلسة حسب دوره */
@Serializable
data class ParticipantPermissions(
    val canPublishAudio: Boolean = false,
    val canPublishVideo: Boolean = false,
    val canManageStage: Boolean = false,
    val canMuteOthers: Boolean = false,
    val canPinMessages: Boolean = false,
    val canKickUsers: Boolean = false
)

/** معلومات وصلاحيات مشارك واحد في المؤتمر / المساحة الصوتية */
@Serializable
data class ConferenceParticipant(
    val userId: String,
    val displayName: String = "",
    val role: String = "LISTENER", // HOST, CO_HOST, SPEAKER, LISTENER
    val permissions: ParticipantPermissions = ParticipantPermissions(),
    val hasVideo: Boolean = false,
    val hasAudio: Boolean = false,
    val isHost: Boolean = false,
    val isMuted: Boolean = false,
    val isSpeaking: Boolean = false,
    val raisedHand: Boolean = false
) {
    fun isHostOrCoHost() = role == "HOST" || role == "CO_HOST" || isHost
    fun canProduceMedia() = role == "HOST" || role == "CO_HOST" || role == "SPEAKER" || isHost
}

class ConferenceSignalingClient(
    private val context: Context,
    private val tokens: TokenStore,
    private val listener: Listener
) {
    interface Listener {
        fun onConnected()
        fun onDisconnected()
        fun onSignal(signal: ConferenceSignal)
        fun onError(message: String)
        fun onRoomState(participants: List<ConferenceParticipant>, selfRole: String = "LISTENER")
        /** Self role change — impl must update role badge UI (ConferenceRuntime.selfRole) + permission gating (mute/unmute others). */
        fun onSelfRole(role: String) {
            Log.d(TAG, "onSelfRole default role=$role — override should update role badge + permission gating")
        }
        fun onParticipantLeft(userId: String)
        fun onParticipantJoined(participant: ConferenceParticipant)
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
    private var activeRoomId: String = ""

    val isConnected: Boolean get() = connected && socket != null

    fun reconnect(roomId: String) {
        Log.d(TAG, "reconnect() roomId=$roomId")
        epoch.invalidate()
        connected = false
        val oldSocket = socket
        socket = null
        runCatching { oldSocket?.cancel() }
        connect(roomId)
    }

    fun connect(roomId: String) {
        if (socket != null && connected && activeRoomId == roomId) {
            Log.d(TAG, "connect(): already connected to room $roomId")
            return
        }

        activeRoomId = roomId
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
        val url = "$baseUrl/ws/conference?roomId=$roomId"
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
                    Log.d(TAG, "onOpen: conference signaling connected, flushing queued signals")
                    pendingSignals.flush { signalJson ->
                        runCatching { webSocket.send(signalJson) }.getOrDefault(false)
                    }
                    listener.onConnected()
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (!epoch.isCurrent(currentEpoch) || webSocket !== socket) return
                    runCatching {
                        val signal = json.decodeFromString<ConferenceSignal>(text)
                        when (signal.type) {
                            "ROOM_STATE" -> {
                                val participants = signal.payload.entries
                                    .filter { it.key.startsWith("user_") }
                                    .map { entry ->
                                        val role = signal.payload["${entry.value}_role"]
                                            ?: if (signal.payload["host"] == entry.value) "HOST" else "LISTENER"
                                        ConferenceParticipant(
                                            userId = entry.value,
                                            role = role,
                                            hasAudio = signal.payload["${entry.value}_audio"] == "true",
                                            hasVideo = signal.payload["${entry.value}_video"] == "true",
                                            isHost = signal.payload["host"] == entry.value || role == "HOST",
                                            isMuted = signal.payload["${entry.value}_muted"] == "true",
                                            raisedHand = signal.payload["${entry.value}_hand"] == "true"
                                        )
                                    }
                                val selfRole = signal.payload["self_role"] ?: "LISTENER"
                                listener.onRoomState(participants, selfRole)
                                listener.onSelfRole(selfRole)
                            }
                            "PARTICIPANT_LEFT" -> {
                                signal.payload["userId"]?.let { listener.onParticipantLeft(it) }
                            }
                            "PARTICIPANT_JOINED" -> {
                                // الخادم يُرسل الدور في الحمولة — كان العميل يُسقطه فيظهر
                                // المنضم جديداً بلقب LISTENER حتى لو كان مضيفاً مشاركاً.
                                val joinedRole = signal.payload["role"]
                                    ?: if (signal.payload["isHost"] == "true") "HOST" else "LISTENER"
                                listener.onParticipantJoined(
                                    ConferenceParticipant(
                                        userId = signal.payload["userId"] ?: "",
                                        displayName = signal.payload["displayName"] ?: "",
                                        role = joinedRole,
                                        hasAudio = signal.payload["hasAudio"] == "true",
                                        hasVideo = signal.payload["hasVideo"] == "true",
                                        isHost = signal.payload["isHost"] == "true" || joinedRole == "HOST"
                                    )
                                )
                            }
                            else -> listener.onSignal(signal)
                        }
                    }.onFailure {
                        Log.w(TAG, "onMessage: parse failed, ignoring frame: ${it.message}")
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (!epoch.isCurrent(currentEpoch) || webSocket !== socket) return
                    connected = false
                    socket = null
                    Log.w(TAG, "onClosed code=$code reason=$reason")
                    listener.onDisconnected()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (!epoch.isCurrent(currentEpoch) || webSocket !== socket) return
                    connected = false
                    socket = null
                    Log.e(TAG, "onFailure ${t.javaClass.simpleName}: ${t.message}")
                    listener.onDisconnected()
                }
            }
        )
    }

    fun send(signal: ConferenceSignal) {
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
            val targetRoom = signal.roomId.ifBlank { activeRoomId }
            if (!connected && targetRoom.isNotBlank()) {
                runCatching { connect(targetRoom) }
            }
        }
    }

    fun join(roomId: String, userId: String, hasVideo: Boolean, speaker: Boolean = hasVideo) = send(
        ConferenceSignal(
            type = "JOIN",
            roomId = roomId,
            userId = userId,
            payload = mapOf(
                "hasVideo" to hasVideo.toString(),
                "hasAudio" to speaker.toString()
            )
        )
    )

    fun leave(roomId: String, userId: String) = send(
        ConferenceSignal(type = "LEAVE", roomId = roomId, userId = userId)
    )

    fun sendIce(roomId: String, userId: String, sdpMid: String, sdpMLineIndex: Int, candidate: String, targetUserId: String = "") = send(
        ConferenceSignal(
            type = "ICE",
            roomId = roomId,
            userId = userId,
            payload = buildMap {
                put("sdpMid", sdpMid)
                put("sdpMLineIndex", sdpMLineIndex.toString())
                put("candidate", candidate)
                if (targetUserId.isNotBlank()) put("targetUserId", targetUserId)
            }
        )
    )

    fun sendOffer(roomId: String, userId: String, sdp: String, targetUserId: String = "") = send(
        ConferenceSignal(
            type = "OFFER",
            roomId = roomId,
            userId = userId,
            payload = buildMap {
                put("sdp", sdp)
                if (targetUserId.isNotBlank()) put("targetUserId", targetUserId)
            }
        )
    )

    fun sendAnswer(roomId: String, userId: String, sdp: String, targetUserId: String = "") = send(
        ConferenceSignal(
            type = "ANSWER",
            roomId = roomId,
            userId = userId,
            payload = buildMap {
                put("sdp", sdp)
                if (targetUserId.isNotBlank()) put("targetUserId", targetUserId)
            }
        )
    )

    /** رفع اليد أو خفضها */
    fun raiseHand(roomId: String, userId: String, lowered: Boolean = false) = send(
        ConferenceSignal(
            type = "RAISE_HAND",
            roomId = roomId,
            userId = userId,
            payload = mapOf("lowered" to lowered.toString())
        )
    )

    /** مشاركة الشاشة: بدء/إيقاف — تُبث للغرفة لتمييز بلاطة العارض. */
    fun sendScreenShare(roomId: String, userId: String, sharing: Boolean) = send(
        ConferenceSignal(
            type = if (sharing) "SCREEN_SHARE_START" else "SCREEN_SHARE_STOP",
            roomId = roomId,
            userId = userId
        )
    )

    /** خفض اليد صراحة */
    fun lowerHand(roomId: String, userId: String) = raiseHand(roomId, userId, lowered = true)

    /** مسح كافة الأيدي المرفوعة (للمضيف) */
    fun clearAllHands(roomId: String, userId: String) = send(
        ConferenceSignal(
            type = "CLEAR_ALL_HANDS",
            roomId = roomId,
            userId = userId
        )
    )

    fun approveSpeaker(roomId: String, userId: String, targetUserId: String) = send(
        ConferenceSignal(
            type = "APPROVE_SPEAKER",
            roomId = roomId,
            userId = userId,
            payload = mapOf("targetUserId" to targetUserId)
        )
    )

    fun demoteListener(roomId: String, userId: String, targetUserId: String) = send(
        ConferenceSignal(
            type = "DEMOTE_LISTENER",
            roomId = roomId,
            userId = userId,
            payload = mapOf("targetUserId" to targetUserId)
        )
    )

    fun grantCoHost(roomId: String, userId: String, targetUserId: String) = send(
        ConferenceSignal(
            type = "GRANT_COHOST",
            roomId = roomId,
            userId = userId,
            payload = mapOf("targetUserId" to targetUserId)
        )
    )

    fun revokeCoHost(roomId: String, userId: String, targetUserId: String) = send(
        ConferenceSignal(
            type = "REVOKE_COHOST",
            roomId = roomId,
            userId = userId,
            payload = mapOf("targetUserId" to targetUserId)
        )
    )

    fun kickUser(roomId: String, userId: String, targetUserId: String) = send(
        ConferenceSignal(
            type = "KICK_USER",
            roomId = roomId,
            userId = userId,
            payload = mapOf("targetUserId" to targetUserId)
        )
    )

    fun muteUser(roomId: String, userId: String, targetUserId: String) = send(
        ConferenceSignal(
            type = "MUTE_USER",
            roomId = roomId,
            userId = userId,
            payload = mapOf("targetUserId" to targetUserId)
        )
    )

    /** LEGENDARY Phase 7: host mutes every participant except self (server persists + relays). */
    fun muteAll(roomId: String, userId: String) = send(
        ConferenceSignal(
            type = "MUTE_ALL",
            roomId = roomId,
            userId = userId,
            payload = emptyMap()
        )
    )

    fun sendReaction(roomId: String, userId: String, emoji: String = "👏") = send(
        ConferenceSignal(
            type = "REACTION",
            roomId = roomId,
            userId = userId,
            payload = mapOf("emoji" to emoji)
        )
    )

    fun pinMessage(roomId: String, userId: String, text: String) = send(
        ConferenceSignal(
            type = "PIN_MESSAGE",
            roomId = roomId,
            userId = userId,
            payload = mapOf("text" to text)
        )
    )

    fun close() {
        Log.d(TAG, "close()")
        epoch.invalidate()
        connected = false
        val oldSocket = socket
        socket = null
        runCatching { oldSocket?.close(1000, "conference ended") }
        pendingSignals.clear()
    }

    companion object {
        private const val TAG = "ConferenceSignaling"
    }
}
