package com.red.server.api

import com.red.server.auth.RedApprovalService
import com.red.server.services.MasterStatsService
import org.springframework.web.bind.annotation.*
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import java.util.UUID

/**
 * Compat admin surface (legacy `/api/master/admin` paths).
 *
 * Canonical contract: [com.red.server.api.admin.RedMasterController]
 * (`/api/master/v1`). Approval logic is already centralized in
 * [RedApprovalService]; stats here delegate to the complete
 * [MasterStatsService.getLiveMetrics] (users/messages/delivery/health) instead
 * of the thin groups+stories aggregate, so both surfaces report identical
 * numbers. Paths are kept only for deployed-dashboard compatibility.
 */
@Deprecated("Compat delegates — canonical admin contract lives in RedMasterController (/api/master/v1).")
@RestController
@RequestMapping("/api/master/admin")
class AdminMasterController(
    private val approval: RedApprovalService,
    private val stats: MasterStatsService
) {
    // [System A & C] إحصائيات المرور الحية — مفوَّضة للمصدر الموحد الكامل.
    @GetMapping("/system/stats")
    fun getGlobalStats() = ResponseEntity.ok(stats.getLiveMetrics())

    // [Security] إدارة الحسابات والسيادة
    @GetMapping("/users/pending")
    fun getPendingUsers() = ResponseEntity.ok(approval.getPendingList())

    @PostMapping("/users/approve")
    fun approveUser(@RequestParam userId: String, authentication: Authentication) = ResponseEntity.ok(
        approval.processAction(
            UUID.fromString(userId),
            com.red.server.auth.model.AccountStatus.APPROVED,
            adminId = UUID.fromString(authentication.name)
        )
    )
}

