package com.red.server.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * ✨ YOUNES PSTN/SMS Event WebSocket — يبث أحداث المكالمات والرسائل
 * للكيان المُسجَّل على `/ws/pstn`.
 *
 * يُستَخدم مصادقة JWT نفسها (JwtHandshakeInterceptor تُعرّف `accountId`).
 *
 * الأحداث:
 * - `SMS_RECEIVED`   {id, number, content, time, port}
 * - `SMS_STATUS`     {id, number, status}
 * - `PSTN_CALL_EVENT` {callId, event: DIALING|RINGING|ANSWERED|ENDED, number, cause, port}
 * - `PSTN_INCOMING`  {number, caller, port}
 */
@Component
class PstnEventWebSocketHandler(
    private val objectMapper: ObjectMapper
) : TextWebSocketHandler() {
    companion object {
        private val log = LoggerFactory.getLogger(PstnEventWebSocketHandler::class.java)
    }

    /** accountId (UUID string) → جلسات مفتوحة. */
    private val sessions = ConcurrentHashMap<String, CopyOnWriteArrayList<WebSocketSession>>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val accountId = session.attributes["accountId"] as? String
        if (accountId == null) {
            session.close(CloseStatus.POLICY_VIOLATION)
            return
        }
        val list = sessions.computeIfAbsent(accountId) { CopyOnWriteArrayList() }
        list.removeIf { !it.isOpen }
        list.add(session)
        log.info("PSTN event WS connected: {} (total sessions: {})", accountId, list.size)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val accountId = session.attributes["accountId"] as? String ?: return
        sessions.computeIfPresent(accountId) { _, list ->
            list.removeAll { it.id == session.id }
            list.ifEmpty { null }
        }
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        if (message.payload == "PING") {
            runCatching { session.sendMessage(TextMessage("PONG")) }
        }
    }

    private fun send(accountId: String, type: String, data: Map<String, @Suppress("unused") Any?>) {
        val payload = runCatching { objectMapper.writeValueAsString(mapOf("type" to type, "data" to data)) }
            .getOrElse { return }
        val list = sessions[accountId] ?: return
        list.removeIf { !it.isOpen }
        list.forEach { session ->
            runCatching { session.sendMessage(TextMessage(payload)) }
                .onFailure { log.warn("Failed to push PSTN event to {}: {}", accountId, it.message) }
        }
    }

    // ── بابليشر الأحداث ────────────────────────────────────────────────────

    /** إرسال حدث مكالمة PSTN إلى مستخدم محدد. */
    fun pushPstnCallEvent(accountId: String, callId: String, event: String, data: Map<String, Any?> = emptyMap()) {
        val merged = LinkedHashMap(data)
        merged["callId"] = callId
        merged["event"] = event
        send(accountId, "PSTN_CALL_EVENT", merged)
    }

    /** إرسال إشعار مكالمة واردة للمستخدم. */
    fun pushPstnIncoming(accountId: String, payload: Map<String, Any?>) {
        val data = LinkedHashMap<String, Any?>(payload)
        data["event"] = "INCOMING"
        send(accountId, "PSTN_INCOMING", data)
    }

    /** إرسال حدث SMS جديد (وارد) إلى كل المستخدمين المتصلين. */
    fun broadcastSmsReceived(data: Map<String, Any?>) {
        val payload = runCatching { objectMapper.writeValueAsString(mapOf("type" to "SMS_RECEIVED", "data" to data)) }.getOrElse { return }
        sessions.values.flatMap { it.toList() }.forEach { session ->
            if (session.isOpen) runCatching { session.sendMessage(TextMessage(payload)) }
        }
    }

    /** إرسال تحديث حالة تسليم SMS إلى مالك الرسالة. */
    fun pushSmsStatus(ownerId: String, data: Map<String, Any?>) {
        send(ownerId, "SMS_STATUS", data)
    }
}