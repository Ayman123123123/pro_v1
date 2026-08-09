package com.red.server.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.red.server.calls.CallHistoryService
import com.red.server.calls.CallRoute
import com.red.server.calls.CallType
import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap

/**
 * Authenticated WebRTC signaling router.
 *
 * The server derives the sender from the authenticated handshake and derives the destination from
 * the authoritative call record for every non-OFFER signal. Client supplied source/target values
 * are therefore never authorization inputs. Every approved device of the callee receives an
 * incoming offer; a sibling device is explicitly told to stop ringing once one device answers.
 */
@Component
class CallWebSocketHandler(
    private val objectMapper: ObjectMapper,
    private val history: CallHistoryService,
    private val accessGuard: ApprovedDeviceSessionGuard
) : TextWebSocketHandler() {
    private val sessions = ConcurrentHashMap<String, ConcurrentHashMap<String, WebSocketSession>>()
    private val frameLimiter = WebSocketRateLimiter(maxMessages = 90, windowMillis = 60_000)

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        if (!frameLimiter.tryAcquire(session.id) || !accessGuard.isStillAuthorized(
                session.attributes["accountId"] as? String,
                session.attributes["deviceId"] as? String
            )
        ) {
            session.close(org.springframework.web.socket.CloseStatus.POLICY_VIOLATION)
            return
        }
        val source = session.attributes["userId"] as? String ?: error("Authenticated RED ID is missing")
        val signal = runCatching { objectMapper.readValue(message.payload, IncomingCallSignal::class.java) }.getOrElse {
            session.close(org.springframework.web.socket.CloseStatus.BAD_DATA)
            return
        }
        val type = signal.type.uppercase()

        val (callId, target, mode) = when (type) {
            "OFFER" -> {
                require(signal.targetUserId.isNotBlank() && signal.targetUserId != source) { "A target RED ID is required" }
                val call = history.start(
                    initiator = source,
                    target = signal.targetUserId,
                    targetLabel = signal.targetUserId,
                    type = CallType.valueOf(signal.mode.uppercase()),
                    route = CallRoute.RED,
                    requestedId = signal.callId
                )
                Triple(call.id, call.targetId, call.type.name)
            }
            "ANSWER", "END", "ICE", "HOLD", "RESUME", "REJECT" -> {
                val call = history.authorizeSignal(requireCallId(signal), source, type)
                val expectedTarget = history.peerFor(call, source)
                require(signal.targetUserId == expectedTarget) { "Signal target does not match the call participant" }
                Triple(call.id, expectedTarget, call.type.name)
            }
            else -> throw IllegalArgumentException("Unsupported call signal type")
        }

        val outbound = OutgoingCallSignal(callId, source, target, type, mode, signal.payload)
        val targetSessions = openSessions(target)
        if (targetSessions.isNotEmpty()) {
            targetSessions.forEach { send(it, outbound) }
            send(session, mapOf("type" to "ACK", "callId" to callId))

            // A target may have more than one approved device. When one answers/rejects/ends, the
            // other target devices must stop ringing instead of retaining a stale incoming call.
            if (type == "ANSWER" || type == "REJECT" || type == "END") {
                val siblingEvent = if (type == "ANSWER") "ANSWERED_ELSEWHERE" else "CALL_CLOSED_ELSEWHERE"
                openSessions(source)
                    .filter { it.id != session.id }
                    .forEach { sibling -> send(sibling, mapOf("type" to siblingEvent, "callId" to callId)) }
            }
        } else {
            if (type == "OFFER") history.markMissed(callId, source)
            send(session, mapOf("type" to "UNAVAILABLE", "callId" to callId))
        }
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val redId = session.attributes["userId"] as? String ?: return
        sessions.computeIfAbsent(redId) { ConcurrentHashMap() }[session.id] = session
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: org.springframework.web.socket.CloseStatus) {
        frameLimiter.remove(session.id)
        val redId = session.attributes["userId"] as? String ?: return
        sessions[redId]?.let { userSessions ->
            userSessions.remove(session.id)
            if (userSessions.isEmpty()) sessions.remove(redId, userSessions)
        }
    }

    private fun openSessions(redId: String): List<WebSocketSession> =
        sessions[redId]?.values?.filter(WebSocketSession::isOpen).orEmpty()

    private fun send(session: WebSocketSession, payload: Any) {
        synchronized(session) {
            if (session.isOpen) session.sendMessage(TextMessage(objectMapper.writeValueAsString(payload)))
        }
    }

    private fun requireCallId(signal: IncomingCallSignal) =
        requireNotNull(signal.callId?.takeIf(String::isNotBlank)) { "callId is required" }
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
