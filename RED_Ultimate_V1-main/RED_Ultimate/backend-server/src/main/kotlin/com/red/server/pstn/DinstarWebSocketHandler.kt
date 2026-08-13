package com.red.server.pstn

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import com.fasterxml.jackson.databind.ObjectMapper
import java.util.concurrent.CopyOnWriteArraySet

/**
 * معالج WebSocket لأحداث DINSTAR المباشرة
 * يرسل تحديثات الحالة والأحداث للعملاء المتصلين
 */
@Component
class DinstarWebSocketHandler(
    private val objectMapper: ObjectMapper
) : TextWebSocketHandler() {
    
    private val log = LoggerFactory.getLogger(DinstarWebSocketHandler::class.java)
    private val sessions = CopyOnWriteArraySet<WebSocketSession>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        sessions.add(session)
        log.info("WebSocket connection established: ${session.id}")
        log.info("Total WebSocket connections: ${sessions.size}")
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        sessions.remove(session)
        log.info("WebSocket connection closed: ${session.id}, status: $status")
        log.info("Total WebSocket connections: ${sessions.size}")
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        log.debug("Received WebSocket message from ${session.id}: ${message.payload}")
    }

    /**
     * بث تحديث حالة المنفذ لجميع العملاء المتصلين
     */
    fun broadcastPortStatus(gatewayId: String, port: Int, status: Map<String, Any?>) {
        val message = mapOf(
            "type" to "PORT_STATUS",
            "gatewayId" to gatewayId,
            "port" to port,
            "data" to status,
            "timestamp" to System.currentTimeMillis()
        )
        broadcastMessage(message)
    }

    /**
     * بث حالة الجهاز (CPU, Memory, etc)
     */
    fun broadcastDeviceStatus(gatewayId: String, status: Map<String, Any?>) {
        val message = mapOf(
            "type" to "DEVICE_STATUS",
            "gatewayId" to gatewayId,
            "data" to status,
            "timestamp" to System.currentTimeMillis()
        )
        broadcastMessage(message)
    }

    /**
     * بث استجابة USSD
     */
    fun broadcastUssdResponse(gatewayId: String, port: Int, response: Map<String, Any?>) {
        val message = mapOf(
            "type" to "USSD_RESPONSE",
            "gatewayId" to gatewayId,
            "port" to port,
            "data" to response,
            "timestamp" to System.currentTimeMillis()
        )
        broadcastMessage(message)
    }

    /**
     * بث تحكم بالمنفذ (power, call forward, etc)
     */
    fun broadcastPortControl(gatewayId: String, port: Int, control: Map<String, Any?>) {
        val message = mapOf(
            "type" to "PORT_CONTROL",
            "gatewayId" to gatewayId,
            "port" to port,
            "data" to control,
            "timestamp" to System.currentTimeMillis()
        )
        broadcastMessage(message)
    }

    /**
     * بث سجل مكالمة جديد (CDR)
     */
    fun broadcastNewCdr(gatewayId: String, cdr: Map<String, Any?>) {
        val message = mapOf(
            "type" to "NEW_CDR",
            "gatewayId" to gatewayId,
            "data" to cdr,
            "timestamp" to System.currentTimeMillis()
        )
        broadcastMessage(message)
    }

    /**
     * بث رسالة SMS واردة
     */
    fun broadcastIncomingSms(gatewayId: String, port: Int, sms: Map<String, Any?>) {
        val message = mapOf(
            "type" to "INCOMING_SMS",
            "gatewayId" to gatewayId,
            "port" to port,
            "data" to sms,
            "timestamp" to System.currentTimeMillis()
        )
        broadcastMessage(message)
    }

    /**
     * بث تنبيه جديد
     */
    fun broadcastAlert(gatewayId: String, alert: Map<String, Any?>) {
        val message = mapOf(
            "type" to "ALERT",
            "gatewayId" to gatewayId,
            "data" to alert,
            "timestamp" to System.currentTimeMillis()
        )
        broadcastMessage(message)
    }

    /**
     * بث رسالة مخصصة
     */
    fun broadcastMessage(message: Map<String, Any?>) {
        if (sessions.isEmpty()) {
            log.debug("No WebSocket sessions to broadcast to")
            return
        }

        try {
            val jsonMessage = objectMapper.writeValueAsString(message)
            val textMessage = TextMessage(jsonMessage)

            val failedSessions = mutableListOf<WebSocketSession>()
            
            sessions.forEach { session ->
                try {
                    if (session.isOpen) {
                        session.sendMessage(textMessage)
                    } else {
                        failedSessions.add(session)
                    }
                } catch (e: Exception) {
                    log.error("Error sending WebSocket message to session ${session.id}", e)
                    failedSessions.add(session)
                }
            }

            // إزالة الجلسات الفاشلة
            failedSessions.forEach { session ->
                sessions.remove(session)
                log.warn("Removed failed WebSocket session: ${session.id}")
            }

            log.debug("Broadcast message to ${sessions.size} WebSocket sessions: ${message["type"]}")
        } catch (e: Exception) {
            log.error("Error broadcasting WebSocket message", e)
        }
    }

    /**
     * الحصول على عدد الجلسات النشطة
     */
    fun getActiveSessionCount(): Int {
        return sessions.size
    }

    /**
     * إغلاق جميع الجلسات
     */
    fun closeAllSessions() {
        sessions.forEach { session ->
            try {
                if (session.isOpen) {
                    session.close(CloseStatus.GOING_AWAY)
                }
            } catch (e: Exception) {
                log.error("Error closing WebSocket session ${session.id}", e)
            }
        }
        sessions.clear()
        log.info("All WebSocket sessions closed")
    }
}
