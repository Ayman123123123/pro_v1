package com.red.server.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap

/**
 * WebRTC signaling for live broadcasts.
 * Maintains a registry of (streamId -> broadcaster) plus (streamId -> set of viewers).
 * Viewers receive OFFER from broadcaster; broadcaster receives ANSWER/ICE from each viewer.
 */
@Component
class LiveStreamWebSocketHandler(private val objectMapper: ObjectMapper) : TextWebSocketHandler() {
    private val broadcasters = ConcurrentHashMap<String, WebSocketSession>()
    private val viewers = ConcurrentHashMap<String, MutableSet<WebSocketSession>>()
    private val sessionToStream = ConcurrentHashMap<String, String>()
    private val sessionRole = ConcurrentHashMap<String, Role>()

    enum class Role { BROADCASTER, VIEWER }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val userId = session.attributes["userId"] as? String ?: error("Authenticated RED ID is missing")
        val signal = objectMapper.readValue(message.payload, IncomingConferenceSignal::class.java)
        require(signal.roomId.isNotBlank()) { "streamId is required" }
        require(signal.roomId.matches(STREAM_ID)) { "Invalid streamId" }

        when (signal.type.uppercase()) {
            "JOIN" -> {
                val role = if ((signal.payload["role"] ?: "viewer").toString().equals("broadcaster", ignoreCase = true)) Role.BROADCASTER else Role.VIEWER
                sessionToStream[session.id] = signal.roomId
                sessionRole[session.id] = role
                if (role == Role.BROADCASTER) {
                    broadcasters[signal.roomId] = session
                } else {
                    viewers.computeIfAbsent(signal.roomId) { ConcurrentHashMap.newKeySet() }.add(session)
                }
                // Notify broadcaster that a new viewer joined
                val broadcaster = broadcasters[signal.roomId]
                if (broadcaster != null && broadcaster.isOpen) {
                    val notif = objectMapper.writeValueAsString(mapOf(
                        "type" to "VIEWER_JOINED",
                        "roomId" to signal.roomId,
                        "payload" to mapOf("userId" to userId, "isBroadcaster" to false)
                    ))
                    runCatching { broadcaster.sendMessage(TextMessage(notif)) }
                }
            }
            "OFFER", "ANSWER", "ICE" -> {
                val role = sessionRole[session.id] ?: return
                val target = if (role == Role.BROADCASTER) {
                    // broadcaster -> first viewer
                    viewers[signal.roomId]?.firstOrNull { it.isOpen }
                } else {
                    broadcasters[signal.roomId]
                }
                if (target != null && target.isOpen) {
                    val outbound = objectMapper.writeValueAsString(mapOf(
                        "type" to signal.type.uppercase(),
                        "roomId" to signal.roomId,
                        "userId" to userId,
                        "payload" to signal.payload
                    ))
                    runCatching { target.sendMessage(TextMessage(outbound)) }
                }
            }
            "LEAVE" -> removeSession(session, signal.roomId, userId)
            else -> throw IllegalArgumentException("Unsupported live signal type: ${signal.type}")
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: org.springframework.web.socket.CloseStatus) {
        val streamId = sessionToStream.remove(session.id) ?: return
        val userId = session.attributes["userId"] as? String ?: return
        removeSession(session, streamId, userId)
    }

    private fun removeSession(session: WebSocketSession, streamId: String, userId: String) {
        val role = sessionRole.remove(session.id) ?: return
        when (role) {
            Role.BROADCASTER -> broadcasters.remove(streamId, session)
            Role.VIEWER -> {
                viewers[streamId]?.remove(session)
                if (viewers[streamId]?.isEmpty() == true) viewers.remove(streamId)
            }
        }
        // Notify other side
        val target = if (role == Role.BROADCASTER) viewers[streamId] else broadcasters[streamId]?.let { setOf(it) }
        val leaveMsg = objectMapper.writeValueAsString(mapOf(
            "type" to "PARTICIPANT_LEFT",
            "roomId" to streamId,
            "payload" to mapOf("userId" to userId)
        ))
        target?.forEach { runCatching { it.sendMessage(TextMessage(leaveMsg)) } }
    }

    companion object {
        private val STREAM_ID = Regex("^[A-Za-z0-9_-]{8,128}$")
    }
}
