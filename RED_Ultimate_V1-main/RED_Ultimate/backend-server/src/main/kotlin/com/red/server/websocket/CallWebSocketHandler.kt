package com.red.server.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.red.server.calls.CallHistoryService
import com.red.server.calls.CallRoute
import com.red.server.calls.CallType
import com.red.server.services.NotificationService
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** Authenticated WebRTC signaling router with multi-device ringing. */
@Component
class CallWebSocketHandler(
    private val objectMapper: ObjectMapper,
    private val history: CallHistoryService,
    private val notifications: NotificationService
) : TextWebSocketHandler() {
    private val sessions = ConcurrentHashMap<String, CopyOnWriteArrayList<WebSocketSession>>()

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
        val targets = sessions[signal.targetUserId]?.filter(WebSocketSession::isOpen).orEmpty()
        if (targets.isEmpty()) {
            if (type == "OFFER") {
                // Dispatch High-Priority FCM / Sovereign Push to wake up the callee device for incoming call
                notifications.sendVoipPushNotification(signal.targetUserId, source, callId, signal.mode)
            }
            session.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf("type" to "RINGING_PUSH_SENT", "callId" to callId))))
            return
        }

        val json = objectMapper.writeValueAsString(outbound)
        targets.forEach { target -> runCatching { target.sendMessage(TextMessage(json)) } }
        session.sendMessage(TextMessage(objectMapper.writeValueAsString(mapOf("type" to "ACK", "callId" to callId))))

        // Once one device answers/rejects/ends, stop the ringing state on the user's other devices.
        if (type in setOf("ANSWER", "REJECT", "END")) {
            val cancelType = if (type == "ANSWER") "CANCELLED" else type
            val cancel = objectMapper.writeValueAsString(mapOf("type" to cancelType, "callId" to callId, "sourceUserId" to source))
            targets.filter { it.id != session.id }.forEach { target -> runCatching { target.sendMessage(TextMessage(cancel)) } }
        }
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val redId = session.attributes["userId"] as? String ?: return
        val list = sessions.computeIfAbsent(redId) { CopyOnWriteArrayList() }
        list.removeIf { !it.isOpen }
        list.add(session)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val redId = session.attributes["userId"] as? String
        if (redId == null) {
            sessions.values.forEach { it.removeIf { candidate -> candidate.id == session.id } }
        } else {
            sessions.computeIfPresent(redId) { _, list ->
                list.removeIf { it.id == session.id }
                list.takeIf { it.isNotEmpty() }
            }
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
