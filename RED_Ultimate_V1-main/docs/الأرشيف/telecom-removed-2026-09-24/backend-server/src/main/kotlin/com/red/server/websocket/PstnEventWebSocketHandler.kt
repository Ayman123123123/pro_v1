package com.red.server.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.red.server.pstn.DinstarEventListener
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.LinkedHashMap

/**
 * ✨ YOUNES PSTN/SMS Event WebSocket — يبث أحداث المكالمات والرسائل
 * للكيان المُسجَّل على `/ws/pstn`.
 *
 * يُستَخدم مصادقة JWT نفسها (JwtHandshakeInterceptor تُعرّف `accountId`).
 *
 * الأحداث الصادرة:
 * - `SMS_RECEIVED`   {id, number, content, time, port}
 * - `SMS_STATUS`     {id, number, status}
 * - `PSTN_CALL_EVENT` {callId, event: DIALING|RINGING|ANSWERED|ENDED, number, cause, port}
 * - `PSTN_INCOMING`  {number, caller, port}
 *
 * الأحداث الواردة (من التطبيق):
 * - `PSTN_ACCEPT`    {channel} — قبول مكالمة واردة
 * - `PSTN_REJECT`    {channel} — رفض مكالمة واردة
 */
@Component
class PstnEventWebSocketHandler(
    private val objectMapper: ObjectMapper,
    @Lazy private val dinstarEvents: DinstarEventListener
) : TextWebSocketHandler() {
    companion object {
        private val log = LoggerFactory.getLogger(PstnEventWebSocketHandler::class.java)
        private const val PENDING_TTL_SECONDS = 120L
        private const val MAX_PENDING_PER_USER = 10
    }

    /** accountId (UUID string) → جلسات مفتوحة. */
    private val sessions = ConcurrentHashMap<String, CopyOnWriteArrayList<WebSocketSession>>()

    /** Mailbox for offline PSTN events — mirrors CallWebSocketHandler pending queue. TTL 120s, max 10 per user. */
    private val pendingPstn = ConcurrentHashMap<String, MutableList<String>>()
    private val pendingPstnExpiry = ConcurrentHashMap<String, MutableList<Long>>()

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
        drainPending(accountId, session)
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
            return
        }
        // Parse incoming PSTN control messages
        try {
            val json = objectMapper.readValue(message.payload, Map::class.java)
            val type = json["type"] as? String ?: return
            val data = json["data"] as? Map<String, Any> ?: emptyMap()
            when (type) {
                "PSTN_ACCEPT" -> handleAccept(session, data)
                "PSTN_REJECT" -> handleReject(session, data)
                else -> log.debug("Unknown PSTN message type: {}", type)
            }
        } catch (e: Exception) {
            log.warn("Failed to parse PSTN message: {}", e.message)
        }
    }

    private fun handleAccept(session: WebSocketSession, data: Map<String, Any>) {
        val accountId = session.attributes["accountId"] as? String ?: run {
            log.warn("PSTN_ACCEPT without authenticated account")
            return
        }
        val channel = data["channel"] as? String ?: run {
            log.warn("PSTN_ACCEPT missing channel")
            return
        }
        log.info("Received PSTN_ACCEPT for channel {} from account {}", channel, accountId)
        runCatching { dinstarEvents.acceptIncomingCall(channel, accountId) }
            .onFailure { e -> log.error("Failed to accept incoming call: {}", e.message) }
    }

    private fun handleReject(session: WebSocketSession, data: Map<String, Any>) {
        val accountId = session.attributes["accountId"] as? String ?: run {
            log.warn("PSTN_REJECT without authenticated account")
            return
        }
        val channel = data["channel"] as? String ?: run {
            log.warn("PSTN_REJECT missing channel")
            return
        }
        log.info("Received PSTN_REJECT for channel {} from account {}", channel, accountId)
        runCatching { dinstarEvents.rejectIncomingCall(channel, accountId) }
            .onFailure { e -> log.error("Failed to reject incoming call: {}", e.message) }
    }

    private fun send(accountId: String, type: String, data: Map<String, @Suppress("unused") Any?>) {
        val payload = runCatching { objectMapper.writeValueAsString(mapOf("type" to type, "data" to data)) }
            .getOrElse { return }
        val list = sessions[accountId]
        if (list == null || list.none { it.isOpen }) {
            enqueuePending(accountId, payload)
            return
        }
        list.removeIf { !it.isOpen }
        if (list.isEmpty()) {
            enqueuePending(accountId, payload)
            return
        }
        list.forEach { session ->
            runCatching {
                synchronized(session) {
                    if (session.isOpen) session.sendMessage(TextMessage(payload))
                }
            }.onFailure { log.warn("Failed to push PSTN event to {}: {}", accountId, it.message) }
        }
    }

    private fun enqueuePending(accountId: String, payload: String) {
        val now = System.currentTimeMillis()
        val expiry = now + PENDING_TTL_SECONDS * 1000
        val q = pendingPstn.computeIfAbsent(accountId) { CopyOnWriteArrayList() }
        val expQ = pendingPstnExpiry.computeIfAbsent(accountId) { CopyOnWriteArrayList() }
        synchronized(q) {
            synchronized(expQ) {
                // purge expired
                var i = 0
                while (i < expQ.size) {
                    if (expQ[i] < now) {
                        expQ.removeAt(i)
                        q.removeAt(i)
                    } else i++
                }
                if (q.size >= MAX_PENDING_PER_USER) {
                    q.removeAt(0)
                    expQ.removeAt(0)
                }
                q.add(payload)
                expQ.add(expiry)
                log.debug("Queued PSTN event for offline {} (queue={})", accountId, q.size)
            }
        }
    }

    private fun drainPending(accountId: String, session: WebSocketSession) {
        val q = pendingPstn.remove(accountId) ?: return
        val expQ = pendingPstnExpiry.remove(accountId)
        val now = System.currentTimeMillis()
        val toSend = mutableListOf<String>()
        synchronized(q) {
            for (idx in q.indices) {
                val exp = expQ?.getOrNull(idx) ?: (now + PENDING_TTL_SECONDS * 1000)
                if (exp > now) toSend.add(q[idx])
            }
        }
        if (toSend.isEmpty()) return
        log.info("Draining {} pending PSTN events for {}", toSend.size, accountId)
        toSend.forEach { json ->
            runCatching {
                synchronized(session) {
                    if (session.isOpen) session.sendMessage(TextMessage(json))
                }
            }.onFailure { log.warn("Failed to drain PSTN event to {}: {}", accountId, it.message) }
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