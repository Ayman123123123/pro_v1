package com.red.server.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.red.server.services.DinstarEventPublisher
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler

/**
 * Dinstar WebSocket Handler — يبث أحداث البوابات (حالة المنافذ، CDR، SMS، USSD، الأخطاء) للعملاء الإداريين.
 * المسار: /ws/dinstar
 * المصادقة: JWT (JwtHandshakeInterceptor) — أدمن فقط.
 */
@Component
class DinstarWebSocketHandler(
    private val objectMapper: ObjectMapper,
    private val eventPublisher: DinstarEventPublisher
) : TextWebSocketHandler() {

    private val sessions = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.CopyOnWriteArrayList<WebSocketSession>>()

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        // رسائل من العميل (مثلاً HEARTBEAT PING أو اشتراكات)
        val payload = message.payload
        if (payload == "PING") {
            session.sendMessage(TextMessage("PONG"))
        }
    }

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val redId = session.attributes["userId"] as? String ?: return
        val list = sessions.computeIfAbsent(redId) { java.util.concurrent.CopyOnWriteArrayList() }
        list.removeIf { !it.isOpen }
        list.add(session)
        eventPublisher.onClientConnected(redId, session)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val redId = session.attributes["userId"] as? String
        if (redId != null) {
            sessions.computeIfPresent(redId) { _, list ->
                list.removeIf { it.id == session.id }
                list.takeIf { it.isNotEmpty() }
            }
            eventPublisher.onClientDisconnected(redId, session)
        }
    }

    fun broadcastPortStatus(status: Map<String, Any>) {
        broadcast("DINSTAR_PORT_STATUS", status)
    }

    fun broadcastCdr(cdr: Map<String, Any>) {
        broadcast("DINSTAR_CDR", cdr)
    }

    fun broadcastSms(sms: Map<String, Any>) {
        broadcast("DINSTAR_SMS", sms)
    }

    fun broadcastUssd(ussd: Map<String, Any>) {
        broadcast("DINSTAR_USSD", ussd)
    }

    fun broadcastException(exception: Map<String, Any>) {
        broadcast("DINSTAR_EXCEPTION", exception)
    }

    private fun broadcast(type: String, data: Map<String, Any>) {
        val message = objectMapper.writeValueAsString(mapOf("type" to type, "data" to data))
        sessions.values.forEach { list ->
            list.forEach { session ->
                if (session.isOpen) {
                    runCatching { session.sendMessage(TextMessage(message)) }
                }
            }
        }
    }
}