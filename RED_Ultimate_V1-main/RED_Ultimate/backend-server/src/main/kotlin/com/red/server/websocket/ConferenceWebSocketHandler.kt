package com.red.server.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap

/**
 * WebRTC mesh-style signaling for small group conferences.
 * Maintains a map of roomId -> set of sessions, and routes OFFER/ANSWER/ICE/LEAVE
 * messages to all peers in the same room.
 *
 * For larger conferences (>4 peers) the protocol is the same shape but the backend
 * should proxy/relay to media-sfu; the Android clients speak this same JSON shape.
 */
@Component
class ConferenceWebSocketHandler(private val objectMapper: ObjectMapper) : TextWebSocketHandler() {
    private val rooms = ConcurrentHashMap<String, MutableSet<WebSocketSession>>()
    private val sessionToRoom = ConcurrentHashMap<String, String>()

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val userId = session.attributes["userId"] as? String ?: error("Authenticated RED ID is missing")
        val signal = objectMapper.readValue(message.payload, IncomingConferenceSignal::class.java)
        require(signal.roomId.isNotBlank()) { "roomId is required" }
        require(signal.roomId.matches(ROOM_ID)) { "Invalid roomId" }

        when (signal.type.uppercase()) {
            "JOIN" -> handleJoin(session, userId, signal)
            "OFFER", "ANSWER", "ICE", "PRODUCE", "CONSUME" -> relay(session, signal)
            "LEAVE" -> handleLeave(session, signal)
            else -> throw IllegalArgumentException("Unsupported conference signal type: ${signal.type}")
        }
    }

    private fun handleJoin(session: WebSocketSession, userId: String, signal: IncomingConferenceSignal) {
        val room = rooms.computeIfAbsent(signal.roomId) { ConcurrentHashMap.newKeySet() }
        synchronized(room) { room.add(session) }
        sessionToRoom[session.id] = signal.roomId
        // Notify existing peers
        val joinMsg = objectMapper.writeValueAsString(mapOf(
            "type" to "PARTICIPANT_JOINED",
            "roomId" to signal.roomId,
            "payload" to mapOf("userId" to userId, "hasVideo" to (signal.payload["hasVideo"] ?: "false"), "hasAudio" to (signal.payload["hasAudio"] ?: "true"))
        ))
        room.filter { it.id != session.id }.forEach { runCatching { it.sendMessage(TextMessage(joinMsg)) } }
        // Send room state to newcomer
        val peers = room.filter { it.id != session.id }.mapNotNull { it.attributes["userId"] as? String }
        val stateMsg = objectMapper.writeValueAsString(mapOf(
            "type" to "ROOM_STATE",
            "roomId" to signal.roomId,
            "payload" to peers.withIndex().associate { (i, p) -> "user_$i" to p } +
                peers.associate { p -> "${p}_audio" to "true" } +
                peers.associate { p -> "${p}_video" to "true" }
        ))
        session.sendMessage(TextMessage(stateMsg))
    }

    private fun relay(session: WebSocketSession, signal: IncomingConferenceSignal) {
        val room = rooms[signal.roomId] ?: return
        val outbound = objectMapper.writeValueAsString(mapOf(
            "type" to signal.type.uppercase(),
            "roomId" to signal.roomId,
            "userId" to (session.attributes["userId"] as? String ?: ""),
            "payload" to signal.payload
        ))
        room.filter { it.id != session.id && it.isOpen }.forEach { runCatching { it.sendMessage(TextMessage(outbound)) } }
    }

    private fun handleLeave(session: WebSocketSession, signal: IncomingConferenceSignal) {
        val room = rooms[signal.roomId] ?: return
        synchronized(room) { room.remove(session) }
        sessionToRoom.remove(session.id)
        val userId = session.attributes["userId"] as? String ?: return
        val leaveMsg = objectMapper.writeValueAsString(mapOf(
            "type" to "PARTICIPANT_LEFT",
            "roomId" to signal.roomId,
            "payload" to mapOf("userId" to userId)
        ))
        room.forEach { runCatching { it.sendMessage(TextMessage(leaveMsg)) } }
        if (room.isEmpty()) rooms.remove(signal.roomId)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: org.springframework.web.socket.CloseStatus) {
        val roomId = sessionToRoom.remove(session.id) ?: return
        val room = rooms[roomId] ?: return
        synchronized(room) { room.remove(session) }
        val userId = session.attributes["userId"] as? String ?: return
        val leaveMsg = objectMapper.writeValueAsString(mapOf(
            "type" to "PARTICIPANT_LEFT",
            "roomId" to roomId,
            "payload" to mapOf("userId" to userId)
        ))
        room.forEach { runCatching { it.sendMessage(TextMessage(leaveMsg)) } }
        if (room.isEmpty()) rooms.remove(roomId)
    }

    companion object {
        private val ROOM_ID = Regex("^[A-Za-z0-9_-]{8,128}$")
    }
}

data class IncomingConferenceSignal(
    val type: String,
    val roomId: String = "",
    val payload: Map<String, Any?> = emptyMap()
)
