package com.red.server.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.red.server.calls.ConferenceRoomService
import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap

/**
 * WebRTC mesh-style signaling + stage management for conferences & audio spaces.
 * Supports: JOIN, OFFER, ANSWER, ICE, PRODUCE, CONSUME, LEAVE,
 * plus stage control: RAISE_HAND, APPROVE_SPEAKER, DEMOTE_LISTENER,
 * GRANT_COHOST, REVOKE_COHOST, KICK_USER, MUTE_USER, REACTION, PIN_MESSAGE.
 *
 * Roles: HOST (first joiner), CO_HOST, SPEAKER, LISTENER.
 * Permission: only HOST or CO_HOST can manage stage (approve/demote/grant/kick/mute/pin).
 * Any participant can RAISE_HAND and REACTION.
 * For larger conferences (>4) the protocol proxies to media-sfu; Android speaks same shape.
 */
@Component
class ConferenceWebSocketHandler(
    private val objectMapper: ObjectMapper,
    private val roomService: ConferenceRoomService
) : TextWebSocketHandler() {
    private val rooms = ConcurrentHashMap<String, MutableSet<WebSocketSession>>()
    private val sessionToRoom = ConcurrentHashMap<String, String>()
    private val roomRoles = ConcurrentHashMap<String, ConcurrentHashMap<String, String>>() // roomId -> userId -> role
    private val roomHosts = ConcurrentHashMap<String, String>() // roomId -> host userId

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val userId = session.attributes["userId"] as? String ?: error("Authenticated RED ID is missing")
        val accountId = session.attributes["accountId"] as? String ?: error("Authenticated account ID is missing")
        val signal = objectMapper.readValue(message.payload, IncomingConferenceSignal::class.java)
        require(signal.roomId.isNotBlank()) { "roomId is required" }
        require(signal.roomId.matches(ROOM_ID)) { "Invalid roomId" }
        val type = signal.type.uppercase()
        if (type != "JOIN") {
            require(sessionToRoom[session.id] == signal.roomId) { "Join this room before sending signaling frames" }
        }

        when (type) {
            "JOIN" -> handleJoin(session, userId, accountId, signal)
            "OFFER", "ANSWER", "ICE", "PRODUCE", "CONSUME" -> relay(session, signal)
            "RAISE_HAND", "REACTION" -> relay(session, signal) // anyone can
            "APPROVE_SPEAKER", "DEMOTE_LISTENER", "GRANT_COHOST", "REVOKE_COHOST",
            "KICK_USER", "MUTE_USER", "PIN_MESSAGE" -> handleStageManagement(session, userId, signal)
            "LEAVE" -> handleLeave(session, signal)
            else -> throw IllegalArgumentException("Unsupported conference signal type: ${signal.type}")
        }
    }

    private fun handleJoin(
        session: WebSocketSession,
        userId: String,
        accountId: String,
        signal: IncomingConferenceSignal
    ) {
        val record = roomService.getRoom(signal.roomId)
            ?: throw NoSuchElementException("Conference room not found; create or join it through REST first")
        val isHost = record.hostId == accountId
        require(isHost || roomService.isParticipant(signal.roomId, accountId)) {
            "Join the conference through REST before opening signaling"
        }
        sessionToRoom[session.id]?.let { existing ->
            require(existing == signal.roomId) { "A signaling socket can join only one room" }
        }

        val room = rooms.computeIfAbsent(signal.roomId) { ConcurrentHashMap.newKeySet() }
        val roles = roomRoles.computeIfAbsent(signal.roomId) { ConcurrentHashMap() }
        synchronized(room) { room.add(session) }
        sessionToRoom[session.id] = signal.roomId

        if (record.hostRedId.isNotBlank()) roomHosts.putIfAbsent(signal.roomId, record.hostRedId)
        roles[userId] = when {
            isHost -> "HOST"
            record.isSpace -> roles[userId] ?: "LISTENER"
            else -> roles[userId] ?: "SPEAKER"
        }
        if (isHost) roomHosts[signal.roomId] = userId
        val myRole = roles[userId] ?: "LISTENER"

        // Notify existing peers
        val joinMsg = objectMapper.writeValueAsString(mapOf(
            "type" to "PARTICIPANT_JOINED",
            "roomId" to signal.roomId,
            "payload" to mapOf(
                "userId" to userId,
                "role" to myRole,
                "isHost" to isHost.toString(),
                "hasVideo" to (signal.payload["hasVideo"] ?: "false"),
                "hasAudio" to (signal.payload["hasAudio"] ?: "true")
            )
        ))
        room.filter { it.id != session.id }.forEach { runCatching { it.sendMessage(TextMessage(joinMsg)) } }

        // Send room state to newcomer — includes roles
        val peers = room.filter { it.id != session.id }.mapNotNull { it.attributes["userId"] as? String }
        val statePayload = mutableMapOf<String, String>()
        peers.forEachIndexed { i, p -> statePayload["user_$i"] = p }
        peers.forEach { p ->
            statePayload["${p}_audio"] = "true"
            statePayload["${p}_video"] = "true"
            statePayload["${p}_role"] = roles[p] ?: "LISTENER"
        }
        statePayload["host"] = roomHosts[signal.roomId] ?: userId
        statePayload["self_role"] = myRole
        val stateMsg = objectMapper.writeValueAsString(mapOf(
            "type" to "ROOM_STATE",
            "roomId" to signal.roomId,
            "payload" to statePayload
        ))
        session.sendMessage(TextMessage(stateMsg))
    }

    private fun relay(session: WebSocketSession, signal: IncomingConferenceSignal) {
        val room = rooms[signal.roomId] ?: return
        val source = session.attributes["userId"] as? String ?: ""
        val targetId = signal.payload["targetUserId"]?.toString()?.takeIf { it.isNotBlank() }
        val outbound = objectMapper.writeValueAsString(mapOf(
            "type" to signal.type.uppercase(),
            "roomId" to signal.roomId,
            "userId" to source,
            "payload" to signal.payload
        ))
        val recipients = if (targetId != null) {
            room.filter { it.isOpen && (it.attributes["userId"] as? String) == targetId }
        } else {
            room.filter { it.id != session.id && it.isOpen }
        }
        recipients.forEach { runCatching { it.sendMessage(TextMessage(outbound)) } }
    }

    private fun handleStageManagement(session: WebSocketSession, userId: String, signal: IncomingConferenceSignal) {
        val roomId = signal.roomId
        val roles = roomRoles[roomId] ?: return
        val senderRole = roles[userId] ?: "LISTENER"
        val isPrivileged = senderRole == "HOST" || senderRole == "CO_HOST"
        val type = signal.type.uppercase()

        // Only HOST/CO_HOST can do management actions except RAISE_HAND/REACTION already routed
        if (type in setOf("APPROVE_SPEAKER","DEMOTE_LISTENER","GRANT_COHOST","REVOKE_COHOST","KICK_USER","MUTE_USER","PIN_MESSAGE")) {
            if (!isPrivileged) {
                // silently reject but inform sender
                val err = objectMapper.writeValueAsString(mapOf(
                    "type" to "ERROR",
                    "roomId" to roomId,
                    "payload" to mapOf("code" to "FORBIDDEN", "message" to "Only host/co-host can $type")
                ))
                runCatching { session.sendMessage(TextMessage(err)) }
                return
            }
        }

        // Apply role changes atomically
        val targetId = signal.payload["targetUserId"]?.toString()
        if (targetId != null) {
            when (type) {
                "APPROVE_SPEAKER" -> roles[targetId] = "SPEAKER"
                "DEMOTE_LISTENER" -> roles[targetId] = "LISTENER"
                "GRANT_COHOST" -> roles[targetId] = "CO_HOST"
                "REVOKE_COHOST" -> {
                    if (roles[targetId] == "CO_HOST") roles[targetId] = "SPEAKER"
                }
                "KICK_USER" -> roles.remove(targetId)
            }
        }

        // Relay to whole room (including sender for UI sync), or exclude kicker for KICK
        val room = rooms[roomId] ?: return
        val outbound = objectMapper.writeValueAsString(mapOf(
            "type" to type,
            "roomId" to roomId,
            "userId" to userId,
            "payload" to if (type == "KICK_USER" && targetId != null) {
                signal.payload + mapOf("kicked" to true)
            } else signal.payload
        ))
        val recipients = if (type == "KICK_USER" && targetId != null) {
            // inform everyone including kicked user so they can leave gracefully
            room.filter { it.isOpen }
        } else {
            room.filter { it.isOpen }
        }
        recipients.forEach { runCatching { it.sendMessage(TextMessage(outbound)) } }

        // If kick, remove session(s) of target
        if (type == "KICK_USER" && targetId != null) {
            room.filter { (it.attributes["userId"] as? String) == targetId }.forEach {
                runCatching { it.close() }
            }
        }
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
        // Host authority comes from ConferenceRoomService/REST ownership. A socket
        // disconnect must never promote the first remaining listener to host.
        if (room.none { (it.attributes["userId"] as? String) == userId }) {
            roomRoles[signal.roomId]?.remove(userId)
        }
        if (room.isEmpty()) {
            rooms.remove(signal.roomId)
            roomRoles.remove(signal.roomId)
            roomHosts.remove(signal.roomId)
        }
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
        if (room.none { (it.attributes["userId"] as? String) == userId }) {
            roomRoles[roomId]?.remove(userId)
        }
        if (room.isEmpty()) {
            rooms.remove(roomId)
            roomRoles.remove(roomId)
            roomHosts.remove(roomId)
        }
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
