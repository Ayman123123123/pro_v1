package com.red.server.pstn

import com.fasterxml.jackson.databind.ObjectMapper
import com.red.server.services.DinstarFleetService
import com.red.server.services.DinstarHardwareService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

/**
 * 📞 YOUNES PSTN Analytics & Timeline Controller
 *
 * ⚠️ مسارات المكالمات (`POST /calls`, `/calls/{id}/hangup`, `GET /status`)
 * تعيش في [PstnCallController] وحده — كان تعريفها هنا أيضًا يُنتج
 * `duplicate mapping` فيمنع Spring من الإقلاع كليًا.
 *
 * هذا المتحكم مسؤول فقط عن: التحليلات، الخط الزمني، الصحة والمقاييس.
 */
@RestController
@RequestMapping("/api/pstn")
class PstnController(
    private val enhancedManager: EnhancedPstnManager,
    private val timelineService: CallTimelineService,
    private val analyticsService: PstnAnalyticsService,
    private val memoryManager: MemoryManagementService,
    private val fleet: DinstarFleetService,
    private val hardware: DinstarHardwareService,
    private val reservations: PersistentReservationService
) {
    companion object {
        private val log = LoggerFactory.getLogger(PstnController::class.java)
    }

    // ── Analytics Endpoints ────────────────────────────────────────────────

    @GetMapping("/analytics/summary")
    fun getSummary(
        @RequestParam(defaultValue = "7") days: Int,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(analyticsService.getPstnSummary(days))
    }

    @GetMapping("/analytics/routing")
    fun getRoutingStats(
        @RequestParam startDate: String,
        @RequestParam endDate: String,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val start = Instant.parse(startDate)
        val end = Instant.parse(endDate)
        return ResponseEntity.ok(analyticsService.getRoutingStats(start, end))
    }

    @GetMapping("/analytics/cdr")
    fun getCdrStats(
        @RequestParam(defaultValue = "7") days: Int,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(analyticsService.getCdrStats(days))
    }

    @GetMapping("/analytics/gateways")
    fun getGatewayStats(authentication: Authentication): ResponseEntity<List<Map<String, Any?>>> {
        return ResponseEntity.ok(analyticsService.getGatewayStats())
    }

    // ── Timeline Endpoints ─────────────────────────────────────────────────

    @GetMapping("/calls/{callId}/timeline")
    fun getCallTimeline(
        @PathVariable callId: String,
        authentication: Authentication
    ): ResponseEntity<List<Map<String, Any?>>> {
        val timeline = timelineService.getTimeline(callId)
        return ResponseEntity.ok(timeline)
    }

    @PostMapping("/calls/{callId}/timeline")
    fun recordTimelineStage(
        @PathVariable callId: String,
        @RequestBody request: Map<String, String>,
        authentication: Authentication
    ): ResponseEntity<Any> {
        val stage = request["stage"] ?: return ResponseEntity.badRequest().body(mapOf("error" to "stage required"))
        @Suppress("UNCHECKED_CAST")
        val data: Map<String, Any?> = request["data"]?.let {
            runCatching { ObjectMapper().readValue(it, Map::class.java) as Map<String, Any?> }.getOrDefault(emptyMap())
        } ?: emptyMap()

        CallTimelineService.Stage.entries.find { it.name == stage.uppercase() }
            ?.let { timelineService.recordStage(callId, it, data) }

        return ResponseEntity.ok(mapOf("success" to true))
    }

    // ── Ports Status (Task 6: حالة المنافذ للـ UI) ────────────────────────

    /**
     * حالة جميع منافذ Dinstar للـ UI — GET /api/pstn/ports/status.
     *
     * يعيد لكل منفذ: gatewayId, gatewayHost, portIndex, status
     * (REGISTERED/IDLE/BUSY/OFFLINE), signalDbm, signalPercent, signalUsable,
     * operator, simNumber (آخر 4 أرقام), isReserved.
     *
     * يُستخدم في DialPadScreen لمنتقي المنفذ اليدوي/الذكي.
     */
    @GetMapping("/ports/status")
    fun portsStatus(authentication: Authentication): ResponseEntity<List<Map<String, Any?>>> {
        val gateways = runCatching { fleet.routableGateways() }.getOrElse { emptyList() }
        val out = mutableListOf<Map<String, Any?>>()
        if (gateways.isEmpty()) {
            // مسار البوابة الواحدة القديم — حتى لا ينكسر نشر بلا أسطول مسجّل
            val ports = runCatching { hardware.getHardwareStatus() }.getOrElse { emptyList() }
            ports.forEach { out += portRow(null, "configured", it) }
        } else {
            gateways.forEach { gw ->
                val ports = runCatching { hardware.getHardwareStatus(gw) }.getOrElse { e ->
                    log.warn("ports/status: gateway {} unreachable: {}", gw.host, e.message)
                    emptyList()
                }
                ports.forEach { out += portRow(gw.id, gw.host, it) }
            }
        }
        return ResponseEntity.ok(out)
    }

    private fun portRow(gatewayId: UUID?, gatewayHost: String, port: Map<String, Any?>): Map<String, Any?> {
        val index = (port["index"] as? Number)?.toInt() ?: -1
        val rawStatus = port["status"]?.toString().orEmpty()
        val callState = port["callState"]?.toString().orEmpty()
        val usable = port["signalUsable"] as? Boolean ?: false
        val registered = rawStatus.equals("REGISTERED", true) ||
            rawStatus.equals("REGISTER_OK", true) ||
            rawStatus.equals("Mobile Registered", true)
        val busy = callState.equals("ACTIVE", true) || callState.equals("DIALING", true)
        val status = when {
            busy -> "BUSY"
            !registered -> "OFFLINE"
            usable -> "IDLE"
            else -> "REGISTERED"
        }
        val sim = port["number"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
            ?: port["simNumber"]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        val reserved = runCatching { reservations.isPortReserved(gatewayId, index) }.getOrDefault(false)
        return mapOf(
            "gatewayId" to gatewayId?.toString(),
            "gatewayHost" to gatewayHost,
            "portIndex" to index,
            "status" to status,
            "signalDbm" to (port["signalDbm"] as? Number)?.toInt(),
            "signalPercent" to ((port["signal"] as? Number)?.toInt() ?: 0),
            "signalUsable" to usable,
            "operator" to port["operator"]?.toString(),
            "simNumber" to (sim?.takeLast(4)?.let { "****$it" } ?: sim),
            "isReserved" to reserved
        )
    }

    // ── Health & Metrics ───────────────────────────────────────────────────

    @GetMapping("/health")
    fun healthCheck(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(mapOf(
            "enhancedManager" to enhancedManager.getMetrics(),
            "memoryManager" to memoryManager.getCleanupStats(),
            "timestamp" to Instant.now().toString()
        ))
    }

    @GetMapping("/metrics")
    fun metrics(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(mapOf(
            "pstn" to enhancedManager.getMetrics(),
            "memory" to memoryManager.getCleanupStats(),
            "timestamp" to Instant.now().toString()
        ))
    }

    /**
     * التحقق من ربط المستخدمين بالشرائح (للتدقيق)
     */
    @GetMapping("/users/pstn-binding")
    fun getPstnUserBindings(authentication: Authentication): ResponseEntity<List<Map<String, Any?>>> {
        // القائمة الحقيقية متاحة عبر /api/admin/dinstar/bindings (PstnBindingController)
        return ResponseEntity.ok(emptyList())
    }
}
