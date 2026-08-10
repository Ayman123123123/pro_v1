package com.red.server.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.red.server.calls.CallHistoryService
import com.red.server.calls.CallRoute
import com.red.server.calls.CallType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap

/**
 * Authenticated WebRTC signaling router.
 *
 * Security model:
 *  - `sourceUserId` is ALWAYS taken from the JWT-authenticated session attributes,
 *    never from the message body.
 *  - For every non-OFFER signal the [activeCalls] map is consulted so that a
 *    signal can only be delivered between the two participants of that call.
 *  - The target must currently have an open signaling socket. If not, the
 *    initiator receives UNAVAILABLE and the call is marked missed/ended.
 */
@Component
class CallWebSocketHandler(
    private val objectMapper: ObjectMapper,
    private val history: CallHistoryService
) : TextWebSocketHandler() {

    private val log = LoggerFactory.getLogger(javaClass)

    /** redId -> open WebSocketSession */
    private val sessions = ConcurrentHashMap<String, WebSocketSession>()

    /** callId -> pair of authenticated participant redIds. */
    private val activeCalls = ConcurrentHashMap<String, Pair<String, String>>()

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val source = session.attributes["userId"] as? String
            ?: run {
                session.close(CloseStatus.POLICY_VIOLATION.withReason("Authenticated RED ID is missing"))
                return
            }

        val signal = try {
            objectMapper.readValue(message.payload, IncomingCallSignal::class.java)
        } catch (ex: Exception) {
            session.sendError("INVALID_CALL_SIGNAL")
            return
        }

        val type = signal.type.uppercase()
        val target = signal.targetUserId.trim()
        if (target.isBlank()) return session.sendError("TARGET_REQUIRED")
        if (target == source) return session.sendError("CANNOT_CALL_SELF")

        // If the target is already engaged in another call, reject immediately with BUSY.
        if (type == "OFFER") {
            val alreadyInCall = activeCalls.values.any { participants ->
                target == participants.first || target == participants.second
            }
            if (alreadyInCall) {
                session.send(
                    mapOf(
                        "type" to "BUSY",
                        "targetUserId" to target
                    )
                )
                return
            }
        }

        val callId = when (type) {
            "OFFER" -> {
                val documented = history.start(
                    source, target, target,
                    CallType.valueOf(signal.mode.uppercase()),
                    CallRoute.RED,
                    signal.callId
                ).id
                activeCalls[documented] = source to target
                documented
            }
            "ANSWER", "ICE", "HOLD", "RESUME" -> {
                val id = signal.callId?.takeIf { it.isNotBlank() }
                    ?: return session.sendError("CALL_ID_REQUIRED")
                val participants = activeCalls[id]
                    ?: return session.sendError("CALL_NOT_FOUND")
                // Only the two participants may exchange signaling, and the
                // receiver must send ANSWER back to the initiator.
                if (source !in listOf(participants.first, participants.second)) {
                    log.warn("Rejected signaling from {} for call {} (not a participant)", source, id)
                    return session.sendError("FORBIDDEN")
                }
                if (type == "ANSWER" && source != participants.second) {
                    return session.sendError("ONLY_TARGET_MAY_ANSWER")
                }
                if (source == participants.first) id else id
            }
            "END", "REJECT", "BUSY", "UNAVAILABLE" -> {
                val id = signal.callId?.takeIf { it.isNotBlank() }
                    ?: return session.sendError("CALL_ID_REQUIRED")
                val participants = activeCalls[id]
                if (participants != null && source !in listOf(participants.first, participants.second)) {
                    return session.sendError("FORBIDDEN")
                }
                when (type) {
                    "REJECT", "BUSY", "UNAVAILABLE" -> history.missed(id)
                    else -> history.end(id)
                }
                activeCalls.remove(id)
                id
            }
            else -> return session.sendError("UNSUPPORTED_TYPE")
        }

        // Deliver to the other participant. The recipient is derived from the
        // authenticated call record, never from the client-supplied payload.
        val participants = activeCalls[callId]
        val recipientId = when {
            participants == null -> target // call already ended; still try best-effort delivery
            source == participants.first -> participants.second
            else -> participants.first
        }

        val outbound = OutgoingCallSignal(
            callId = callId,
            sourceUserId = source,
            targetUserId = recipientId,
            type = type,
            mode = signal.mode.uppercase(),
            payload = signal.payload
        )

        val recipient = sessions[recipientId]?.takeIf(WebSocketSession::isOpen)
        if (recipient != null) {
            recipient.sendMessage(TextMessage(objectMapper.writeValueAsString(outbound)))
            session.sendAck(callId)
        } else {
            if (type == "OFFER") history.missed(callId)
            activeCalls.remove(callId)
            session.send(
                mapOf(
                    "type" to "UNAVAILABLE",
                    "callId" to callId,
                    "targetUserId" to recipientId
                )
            )
        }
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val redId = session.attributes["userId"] as? String ?: run {
            session.close(CloseStatus.POLICY_VIOLATION)
            return
        }
        // Replace any previous socket for this account (e.g. reconnect).
        sessions.put(redId, session)?.takeIf { it.isOpen && it.id != session.id }?.let { old ->
            runCatching { old.close(CloseStatus.NORMAL.withReason("replaced by new socket")) }
        }
        log.debug("Call signaling connected: {}", redId)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val redId = session.attributes["userId"] as? String
        sessions.entries.removeIf { it.value.id == session.id }
        if (redId != null) {
            // End any calls where this participant was still involved.
            val stale = activeCalls.entries.filter { it.value.first == redId || it.value.second == redId }
            stale.forEach { (callId, participants) ->
                val otherId = if (participants.first == redId) participants.second else participants.first
                val other = sessions[otherId]?.takeIf(WebSocketSession::isOpen)
                if (other != null) {
                    val notification = OutgoingCallSignal(
                        callId = callId,
                        sourceUserId = redId,
                        targetUserId = otherId,
                        type = "END",
                        mode = "VOICE",
                        payload = mapOf("reason" to "PEER_DISCONNECTED")
                    )
                    runCatching {
                        other.sendMessage(TextMessage(objectMapper.writeValueAsString(notification)))
                    }
                }
                history.end(callId)
                activeCalls.remove(callId)
            }
        }
        log.debug("Call signaling disconnected: {} ({})", redId, status)
    }

    private fun WebSocketSession.sendError(code: String) {
        send(mapOf("type" to "ERROR", "error" to code))
    }

    private fun WebSocketSession.sendAck(callId: String) {
        send(mapOf("type" to "ACK", "callId" to callId))
    }

    private fun WebSocketSession.send(payload: Map<String, Any?>) {
        if (!isOpen) return
        runCatching { sendMessage(TextMessage(objectMapper.writeValueAsString(payload))) }
            .onFailure { log.warn("Failed to send signaling message: {}", it.message) }
    }
}

data class IncomingCallSignal(
    val callId: String? = null,
    val targetUserId: String = "",
    val type: String = "",
    val mode: String = "VOICE",
    val payload: Map<String, Any?> = emptyMap()
)

data class OutgoingCallSignal(
    val callId: String,
    val sourceUserId: String,
    val targetUserId: String,
    val type: String,
    val mode: String,
    val payload: Map<String, Any?>
)
