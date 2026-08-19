package com.red.server.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.red.server.services.DinstarFleetService
import com.red.server.services.DinstarHardwareService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Component
import org.springframework.web.socket.*
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.atomic.AtomicInteger

/**
 * معالج WebSocket لأحداث DINSTAR الحية.
 * 
 * يُرسل تحديثات حالة المنافذ والأحداث (SMS وارد، مكالمة جديدة، إلخ)
 * للعملاء المتصلين عبر `/ws/dinstar`.
 * 
 * الأحداث المرسلة:
 * - DINSTAR_PORT_STATUS: تحديث حالة منفذ (كل 5 ثوان)
 * - DINSTAR_CDR: سجل مكالمة جديد
 * - DINSTAR_SMS: SMS وارد
 * - DINSTAR_USSD: رد USSD
 * - DINSTAR_EXCEPTION: حدث استثناء (فشل مكالمة، شريحة منزوعة)
 */
@Component
class DinstarWebSocketHandler(
    private val hardware: DinstarHardwareService,
    private val fleet: DinstarFleetService,
    private val mapper: ObjectMapper,
    private val taskScheduler: TaskScheduler
) : TextWebSocketHandler() {

    companion object {
        private val log = LoggerFactory.getLogger(DinstarWebSocketHandler::class.java)
        private const val STATUS_UPDATE_INTERVAL_MS = 5000L
    }

    private val sessions = ConcurrentHashMap<String, WebSocketSession>()
    private val scheduledTasks = ConcurrentHashMap<String, ScheduledFuture<*>>()
    private val portStatusCounter = AtomicInteger(0)

    override fun afterConnectionEstablished(session: WebSocketSession) {
        if (session.attributes["role"] != "ADMIN") {
            log.warn("Rejected non-admin DINSTAR WebSocket session: {}", session.id)
            session.close(CloseStatus.POLICY_VIOLATION)
            return
        }
        val sessionId = session.id
        sessions[sessionId] = session
        log.info("DINSTAR WebSocket connected: {} (total: {})", sessionId, sessions.size)

        // بدء تحديثات حالة المنافذ الدورية
        val task = taskScheduler.scheduleAtFixedRate(
            { sendPortStatusUpdate(session) },
            Duration.ofMillis(STATUS_UPDATE_INTERVAL_MS)
        )
        scheduledTasks[sessionId] = task

        // إرسال حالة أولية فورية
        sendPortStatusUpdate(session)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val sessionId = session.id
        sessions.remove(sessionId)
        scheduledTasks.remove(sessionId)?.cancel(false)
        log.info("DINSTAR WebSocket disconnected: {} ({})", sessionId, status)
    }

    public override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        // لا نعالج رسائل من العميل حاليًا — الاتصالunidirectional للعرض فقط
        log.debug("DINSTAR WebSocket received message from {}: {}", session.id, message.payload)
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        log.error("DINSTAR WebSocket transport error for {}: {}", session.id, exception.message)
        sessions.remove(session.id)
        scheduledTasks.remove(session.id)?.cancel(false)
        session.close(CloseStatus.SERVER_ERROR)
    }

    /**
     * إرسال تحديث حالة المنافذ لجميع العملاء المتصلين.
     */
    fun broadcastPortStatus() {
        if (sessions.isEmpty()) return

        try {
            val statusData = buildPortStatusPayload()
            val json = mapper.writeValueAsString(statusData)
            val message = TextMessage(json)

            sessions.values.forEach { session ->
                try {
                    if (session.isOpen) {
                        session.sendMessage(message)
                    }
                } catch (e: Exception) {
                    log.warn("Failed to send to session {}: {}", session.id, e.message)
                }
            }

            portStatusCounter.incrementAndGet()
        } catch (e: Exception) {
            log.error("Failed to broadcast port status: {}", e.message, e)
        }
    }

    /**
     * إرسال حدث SMS وارد.
     */
    fun broadcastIncomingSms(sms: Map<String, Any?>) {
        broadcastEvent("DINSTAR_SMS", sms)
    }

    /**
     * إرسال حدث CDR جديد.
     */
    fun broadcastCdr(cdr: Map<String, Any?>) {
        broadcastEvent("DINSTAR_CDR", cdr)
    }

    /**
     * إرسال حدث USSD.
     */
    fun broadcastUssd(ussd: Map<String, Any?>) {
        broadcastEvent("DINSTAR_USSD", ussd)
    }

    /**
     * إرسال حدث استثناء (فشل مكالمة، شريحة منزوعة، إلخ).
     */
    fun broadcastException(exception: Map<String, Any?>) {
        broadcastEvent("DINSTAR_EXCEPTION", exception)
    }

    /**
     * بث حالة الجهاز (CPU, Memory, Flash) لجميع العملاء.
     * يُستدعى من DinstarApiService بعد جلب حالة الجهاز.
     */
    fun broadcastDeviceStatus(gatewayId: String, status: Map<String, Any?>) {
        broadcastEvent("DINSTAR_DEVICE_STATUS", mapOf("gatewayId" to gatewayId) + status)
    }

    /**
     * بث رد USSD لجميع العملاء.
     * يُستدعى من DinstarApiService بعد إرسال USSD.
     */
    fun broadcastUssdResponse(gatewayId: String, port: Int, response: Map<String, Any?>) {
        broadcastEvent("DINSTAR_USSD", mapOf(
            "gatewayId" to gatewayId,
            "port" to port,
            "response" to response
        ))
    }

    /**
     * بث تغيير تحكم بالمنفذ (طاقة، تحويل مكالمات) لجميع العملاء.
     * يُستدعى من DinstarApiService بعد تغيير حالة المنفذ.
     */
    fun broadcastPortControl(gatewayId: String, port: Int, control: Map<String, Any?>) {
        broadcastEvent("DINSTAR_PORT_CONTROL", mapOf(
            "gatewayId" to gatewayId,
            "port" to port,
            "control" to control
        ))
    }

    private fun sendPortStatusUpdate(session: WebSocketSession) {
        try {
            if (session.isOpen) {
                val payload = buildPortStatusPayload()
                val json = mapper.writeValueAsString(payload)
                session.sendMessage(TextMessage(json))
            }
        } catch (e: Exception) {
            log.warn("Failed to send port status to session {}: {}", session.id, e.message)
        }
    }

    private fun buildPortStatusPayload(): Map<String, Any?> {
        val gateways = fleet.listGateways(onlyEnabled = true)
        val allPorts = mutableListOf<Map<String, Any?>>()

        gateways.forEach { gateway ->
            try {
                val ports = hardware.getHardwareStatus(gateway)
                fleet.markHealthy(gateway.id)
                ports.forEach { port ->
                    allPorts.add(port + mapOf(
                        "gatewayHost" to gateway.host,
                        "gatewayName" to gateway.name,
                        "gatewayModel" to gateway.model
                    ))
                }
            } catch (e: Exception) {
                fleet.markFailure(gateway.id, e.message ?: "WebSocket status query failed")
                log.warn("Failed to get status for gateway {}: {}", gateway.host, e.message)
            }
        }

        return mapOf(
            "type" to "DINSTAR_PORT_STATUS",
            "timestamp" to System.currentTimeMillis(),
            "data" to mapOf(
                "ports" to allPorts,
                "totalGateways" to gateways.size,
                "totalPorts" to allPorts.size,
                "registered" to allPorts.count { 
                    (it["status"]?.toString() ?: "").equals("REGISTERED", ignoreCase = true) 
                },
                "usable" to allPorts.count { it["signalUsable"] == true }
            )
        )
    }

    private fun broadcastEvent(eventType: String, data: Map<String, Any?>) {
        if (sessions.isEmpty()) return

        try {
            val payload = mapOf(
                "type" to eventType,
                "timestamp" to System.currentTimeMillis(),
                "data" to data
            )
            val json = mapper.writeValueAsString(payload)
            val message = TextMessage(json)

            sessions.values.forEach { session ->
                try {
                    if (session.isOpen) {
                        session.sendMessage(message)
                    }
                } catch (e: Exception) {
                    log.warn("Failed to broadcast {} to session {}: {}", eventType, session.id, e.message)
                }
            }

            log.debug("Broadcast {} to {} sessions", eventType, sessions.size)
        } catch (e: Exception) {
            log.error("Failed to broadcast {}: {}", eventType, e.message, e)
        }
    }
}
