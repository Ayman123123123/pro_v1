package com.red.server.api.admin

import com.red.server.auth.ApprovalActionRequest
import com.red.server.auth.RedApprovalService
import com.red.server.infrastructure.dinstar.DinstarMasterClient
import com.red.server.services.MasterStatsService
import com.red.server.services.RedSecurityService
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/master/v1")
class RedMasterController(
    private val statsService: MasterStatsService,
    private val approvalService: RedApprovalService,
    private val dinstarClient: DinstarMasterClient,
    private val securityService: RedSecurityService,
    private val jdbc: JdbcTemplate
) {
    @GetMapping("/stats/realtime")
    fun getGlobalStats() = ResponseEntity.ok(statsService.getLiveMetrics())

    @GetMapping("/auth/pending")
    fun listPending() = ResponseEntity.ok(approvalService.getPendingList())

    @PostMapping("/auth/action")
    fun handleUserAction(
        @RequestBody request: ApprovalActionRequest,
        authentication: Authentication
    ) = ResponseEntity.ok(
        approvalService.processAction(
            request.userId,
            request.action,
            request.reason,
            UUID.fromString(authentication.name)
        )
    )

    @GetMapping("/hardware/dinstar/slots")
    fun getSlots() = ResponseEntity.ok(dinstarClient.getPortsRealtimeStatus())

    @PostMapping("/security/wipe")
    fun initiateWipe(@RequestParam userId: String) =
        ResponseEntity.ok(securityService.sendWipeSignal(userId))

    @GetMapping("/media/active-calls")
    fun getActiveCalls() = ResponseEntity.ok(statsService.getVoipMetrics())

    /** سجل المكالمات الموحّد للوحة الأدمن (RED + DINSTAR) من Postgres. */
    @GetMapping("/calls/history")
    fun getCallHistory(
        @RequestParam(defaultValue = "100") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(required = false) status: String?
    ): ResponseEntity<Any> {
        val safeLimit = limit.coerceIn(1, 500)
        val safeOffset = offset.coerceAtLeast(0)
        val where = status?.takeIf { it.isNotBlank() }?.let { " WHERE status = ?" } ?: ""
        val total = jdbc.queryForObject(
            "SELECT count(*) FROM call_history$where",
            Int::class.java,
            *if (where.isBlank()) arrayOf() else arrayOf(status!!)
        ) ?: 0
        val rows = jdbc.queryForList(
            """
            SELECT id, caller_id, callee_id, callee_phone, call_type, call_route, direction, status,
                   started_at, answered_at, ended_at, duration_ms
            FROM call_history$where
            ORDER BY started_at DESC
            LIMIT ? OFFSET ?
            """.trimIndent(),
            *if (where.isBlank()) arrayOf<Any>(safeLimit, safeOffset) else arrayOf<Any>(status!!, safeLimit, safeOffset)
        )
        return ResponseEntity.ok(mapOf("total" to total, "calls" to rows))
    }
}
