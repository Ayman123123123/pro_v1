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

/** Authenticated WebRTC signaling router. The server injects sourceUserId from JWT. */
@Component
class CallWebSocketHandler(
    private val objectMapper: ObjectMapper,
    private val history: CallHistoryService
) : TextWebSocketHandler() {
    private val sessions = ConcurrentHashMap<String, MutableList<WebSocketSession>>()

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val source = session.attributes["userId"] as? String ?: error("Authenticated RED ID is missing")
        val signal = objectMapper.readValue(message.payload, IncomingCallSignal::class.java)
        require(signal.targetUserId.isNotBlank()) { "targetUserId is required" }
        require(signal.targetUserId != source) { "Cannot call the same RED ID" }
        val type = signal.type.uppercase()
        val callId = when (type) {
            "OFFER" -> history.start(source, signal.targetUserId, signal.targetUserId,
                CallType.valueOf(signal.mode.uppercase()), CallRoute.RED, signal.callId).id
            "ANSWER" -> requireCallId(signal).also(history::answer)
            "END" -> requireCallId(signal).also { history.end(it) }
            "ICE", "HOLD", "RESUME" -> requireCallId(signal)
            "REJECT" -> requireCallId(signal).also { history.end(it) }
            else -> throw IllegalArgumentException("Unsupported call signal type")
        }

        val outbound = OutgoingCallSignal(callId, source, signal.targetUserId, type, signal.mode.uppercase(), signal.payload)
        val targets = sessions[signal.targetUserId]?.filter(WebSocketSession::isOpen) ?: emptyList()
        if (targets.isNotEmpty()) {
            val json = objectMapper.writeValueAsString(outbound)
            targets.forEach { it.sendMessage(TextMessage(json)) }
            // Notify caller that at least one device is ringing
            session.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf("type" to "ACK", "callId" to callId))))
            // If ANSWER/REJECT from one device, cancel ringing on other devices of same user
            if (type in setOf("ANSWER", "REJECT", "END")) {
                val cancelType = if (type == "ANSWER") "CANCELLED" else type
                val cancelMsg = objectMapper.writeValueAsString(mapOf("type" to cancelType, "callId" to callId, "sourceUserId" to source))
                targets.filter { it.id != session.id }.forEach { runCatching { it.sendMessage(TextMessage(cancelMsg)) } }
            }
        } else {
            if (type == "OFFER") history.missed(callId)
            session.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf("type" to "UNAVAILABLE", "callId" to callId))))
        }
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val redId = session.attributes["userId"] as? String ?: return
        sessions.compute(redId) { _, list ->
            val l = list ?: mutableListOf()
            l.removeIf { !it.isOpen }
            l.add(session)
            l
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: org.springframework.web.socket.CloseStatus) {
        val redId = session.attributes["userId"] as? String
        if (redId != null) {
            sessions.computeIfPresent(redId) { _, list ->
                list.removeIf { it.id == session.id }
                if (list.isEmpty()) null else list
            }
        } else {
            sessions.entries.removeIf { entry -> entry.value.any { it.id == session.id } }
        }
    }

    private fun requireCallId(signal: IncomingCallSignal) = requireNotNull(signal.callId?.takeIf(String::isNotBlank)) { "callId is required" }
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
