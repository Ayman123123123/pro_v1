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
    private val sessions = ConcurrentHashMap<String, WebSocketSession>()

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
        val target = sessions[signal.targetUserId]?.takeIf(WebSocketSession::isOpen)
        if (target != null) {
            target.sendMessage(TextMessage(objectMapper.writeValueAsString(outbound)))
            session.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf("type" to "ACK", "callId" to callId))))
        } else {
            if (type == "OFFER") history.missed(callId)
            session.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf("type" to "UNAVAILABLE", "callId" to callId))))
        }
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val redId = session.attributes["userId"] as? String ?: return
        sessions.put(redId, session)?.takeIf { it.isOpen }?.close()
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: org.springframework.web.socket.CloseStatus) {
        sessions.entries.removeIf { it.value.id == session.id }
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
