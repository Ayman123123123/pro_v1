package com.red.server.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap

/**
 * WebRTC signaling for live broadcasts with ownership verification.
 * - Only the broadcaster who created the stream via REST (/api/livestream/create)
 *   can claim BROADCASTER role via WebSocket. Any other authenticated user
 *   attempting BROADCASTER is rejected with 403-style error and session closed.
 * - Broadcaster OFFER is broadcast to ALL viewers (true 1-to-many), not firstOnly.
 * - Viewers ANSWER/ICE go to broadcaster.
 * - CHAT, REACTION, RAISE_HAND, APPROVE_COHOST are relayed with permission checks.
 */
@Component
class LiveStreamWebSocketHandler(
    private val objectMapper: ObjectMapper,
    private val liveStreamService: com.red.server.calls.LiveStreamService
) : TextWebSocketHandler() {
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
                val requestedRole = if ((signal.payload["role"] ?: "viewer").toString().equals("broadcaster", ignoreCase = true)) Role.BROADCASTER else Role.VIEWER
                // Ownership verification for broadcaster
                if (requestedRole == Role.BROADCASTER) {
                    val record = liveStreamService.getStreamRecord(signal.roomId)
                    if (record == null) {
                        val err = objectMapper.writeValueAsString(mapOf(
                            "type" to "ERROR",
                            "roomId" to signal.roomId,
                            "payload" to mapOf("code" to "STREAM_NOT_FOUND", "message" to "Live stream not found. Create via REST first.")
                        ))
                        runCatching { session.sendMessage(TextMessage(err)) }
                        runCatching { session.close() }
                        return
                    }
                    if (record.broadcasterId != userId) {
                        val err = objectMapper.writeValueAsString(mapOf(
                            "type" to "ERROR",
                            "roomId" to signal.roomId,
                            "payload" to mapOf("code" to "NOT_OWNER", "message" to "Only stream owner can broadcast")
                        ))
                        runCatching { session.sendMessage(TextMessage(err)) }
                        runCatching { session.close() }
                        return
                    }
                    // Owner verified
                    sessionToStream[session.id] = signal.roomId
                    sessionRole[session.id] = Role.BROADCASTER
                    broadcasters[signal.roomId]?.let { old ->
                        if (old.id != session.id) runCatching { old.close() }
                    }
                    broadcasters[signal.roomId] = session
                } else {
                    // Viewer path — ensure stream exists (allow viewer before broadcaster join for UX?)
                    sessionToStream[session.id] = signal.roomId
                    sessionRole[session.id] = Role.VIEWER
                    viewers.computeIfAbsent(signal.roomId) { ConcurrentHashMap.newKeySet() }.add(session)
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
            }
            "OFFER" -> {
                val role = sessionRole[session.id] ?: return
                if (role == Role.BROADCASTER) {
                    // Broadcast OFFER to all viewers (true live)
                    val vs = viewers[signal.roomId] ?: return
                    val outbound = objectMapper.writeValueAsString(mapOf(
                        "type" to "OFFER",
                        "roomId" to signal.roomId,
                        "userId" to userId,
                        "payload" to signal.payload
                    ))
                    vs.filter { it.isOpen }.forEach { runCatching { it.sendMessage(TextMessage(outbound)) } }
                } else {
                    // Viewer should not send OFFER in live mode, but relay to broadcaster for co-host case
                    val broadcaster = broadcasters[signal.roomId] ?: return
                    if (broadcaster.isOpen) {
                        val outbound = objectMapper.writeValueAsString(mapOf(
                            "type" to "OFFER",
                            "roomId" to signal.roomId,
                            "userId" to userId,
                            "payload" to signal.payload
                        ))
                        runCatching { broadcaster.sendMessage(TextMessage(outbound)) }
                    }
                }
            }
            "ANSWER", "ICE" -> {
                val role = sessionRole[session.id] ?: return
                if (role == Role.VIEWER) {
                    val broadcaster = broadcasters[signal.roomId] ?: return
                    if (broadcaster.isOpen) {
                        val outbound = objectMapper.writeValueAsString(mapOf(
                            "type" to signal.type.uppercase(),
                            "roomId" to signal.roomId,
                            "userId" to userId,
                            "payload" to signal.payload
                        ))
                        runCatching { broadcaster.sendMessage(TextMessage(outbound)) }
                    }
                } else {
                    // Broadcaster ANSWER/ICE to specific viewer if target specified, otherwise to all
                    val targetId = signal.payload["targetUserId"]?.toString()
                    val vs = viewers[signal.roomId] ?: return
                    val outbound = objectMapper.writeValueAsString(mapOf(
                        "type" to signal.type.uppercase(),
                        "roomId" to signal.roomId,
                        "userId" to userId,
                        "payload" to signal.payload
                    ))
                    if (targetId != null) {
                        vs.filter { (it.attributes["userId"] as? String) == targetId && it.isOpen }
                            .forEach { runCatching { it.sendMessage(TextMessage(outbound)) } }
                    } else {
                        vs.filter { it.isOpen }.forEach { runCatching { it.sendMessage(TextMessage(outbound)) } }
                    }
                }
            }
            "CHAT", "REACTION", "RAISE_HAND" -> {
                // Broadcast to whole stream except sender
                val allSessions = mutableListOf<WebSocketSession>()
                broadcasters[signal.roomId]?.let { allSessions.add(it) }
                viewers[signal.roomId]?.let { allSessions.addAll(it) }
                val outbound = objectMapper.writeValueAsString(mapOf(
                    "type" to signal.type.uppercase(),
                    "roomId" to signal.roomId,
                    "userId" to userId,
                    "payload" to signal.payload
                ))
                allSessions.filter { it.id != session.id && it.isOpen }
                    .forEach { runCatching { it.sendMessage(TextMessage(outbound)) } }
            }
            "APPROVE_COHOST" -> {
                // Only broadcaster can approve
                val role = sessionRole[session.id] ?: return
                if (role != Role.BROADCASTER) return
                val allSessions = mutableListOf<WebSocketSession>()
                broadcasters[signal.roomId]?.let { allSessions.add(it) }
                viewers[signal.roomId]?.let { allSessions.addAll(it) }
                val outbound = objectMapper.writeValueAsString(mapOf(
                    "type" to "APPROVE_COHOST",
                    "roomId" to signal.roomId,
                    "userId" to userId,
                    "payload" to signal.payload
                ))
                allSessions.filter { it.isOpen }.forEach { runCatching { it.sendMessage(TextMessage(outbound)) } }
            }
            "LEAVE" -> removeSession(session, signal.roomId, userId)
            else -> {
                // For any other type, throw to surface contract violation, but allow known chat types
                throw IllegalArgumentException("Unsupported live signal type: ${signal.type}")
            }
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
            Role.BROADCASTER -> {
                broadcasters.remove(streamId, session)
                // When broadcaster leaves, notify all viewers that stream ended
                val vs = viewers[streamId]
                val endMsg = objectMapper.writeValueAsString(mapOf(
                    "type" to "STREAM_ENDED",
                    "roomId" to streamId,
                    "payload" to mapOf("userId" to userId)
                ))
                vs?.forEach { runCatching { it.sendMessage(TextMessage(endMsg)) } }
            }
            Role.VIEWER -> {
                viewers[streamId]?.remove(session)
                if (viewers[streamId]?.isEmpty() == true) viewers.remove(streamId)
            }
        }
        // Notify remaining side
        val target = if (role == Role.BROADCASTER) viewers[streamId] else setOfNotNull(broadcasters[streamId])
        val leaveMsg = objectMapper.writeValueAsString(mapOf(
            "type" to "PARTICIPANT_LEFT",
            "roomId" to streamId,
            "payload" to mapOf("userId" to userId, "role" to role.name)
        ))
        target?.forEach { runCatching { it.sendMessage(TextMessage(leaveMsg)) } }
    }

    companion object {
        private val STREAM_ID = Regex("^[A-Za-z0-9_-]{8,128}$")
    }
}
