package com.red.sovereign.calls

import android.content.Context
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.core.ServerEndpoint
import com.red.sovereign.security.SecureOkHttpClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

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
        fun onSelfRole(role: String) {}
        fun onParticipantLeft(userId: String)
        fun onParticipantJoined(participant: ConferenceParticipant)
    }

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val http: OkHttpClient = SecureOkHttpClient.buildWebSocketClient(context)
    private var socket: WebSocket? = null

    fun reconnect(roomId: String) {
        runCatching { socket?.cancel() }
        socket = null
        connect(roomId)
    }

    fun connect(roomId: String) {
        if (socket != null) return
        val token = tokens.accessToken ?: return listener.onError("UNAUTHORIZED")
        // media-sfu يعمل على منفذ منفصل — نستخدم /ws/conference عبر الـ backend كـ proxy
        val baseUrl = ServerEndpoint.url()
            .replaceFirst("http://", "ws://")
            .replaceFirst("https://", "wss://")
        val url = "$baseUrl/ws/conference?roomId=$roomId"
        socket = http.newWebSocket(
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    listener.onConnected()
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
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
                                            isHost = signal.payload["host"] == entry.value || role == "HOST"
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
                                listener.onParticipantJoined(
                                    ConferenceParticipant(
                                        userId = signal.payload["userId"] ?: "",
                                        displayName = signal.payload["displayName"] ?: "",
                                        hasAudio = signal.payload["hasAudio"] == "true",
                                        hasVideo = signal.payload["hasVideo"] == "true",
                                        isHost = signal.payload["isHost"] == "true"
                                    )
                                )
                            }
                            else -> listener.onSignal(signal)
                        }
                    }.onFailure { listener.onError("INVALID_CONFERENCE_SIGNAL: ${it.message}") }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    socket = null
                    listener.onDisconnected()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    socket = null
                    listener.onDisconnected()
                }
            }
        )
    }

    fun send(signal: ConferenceSignal) {
        socket?.send(json.encodeToString(signal))
            ?: listener.onError("CONFERENCE_NOT_CONNECTED")
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

    fun raiseHand(roomId: String, userId: String) = send(
        ConferenceSignal(
            type = "RAISE_HAND",
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
        socket?.close(1000, "conference ended")
        socket = null
    }

    val isConnected: Boolean get() = socket != null
}
