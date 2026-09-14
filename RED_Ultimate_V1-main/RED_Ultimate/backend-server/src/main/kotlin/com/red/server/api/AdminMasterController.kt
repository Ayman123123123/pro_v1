package com.red.server.api

import com.red.server.auth.RedApprovalService
import com.red.server.services.CoreService
import org.springframework.web.bind.annotation.*
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import java.util.UUID

@RestController
@RequestMapping("/api/master/admin")
class AdminMasterController(
    private val approval: RedApprovalService,
    private val core: CoreService
) {
    // [System A & C] إحصائيات المرور الحية
    @GetMapping("/system/stats")
    fun getGlobalStats() = ResponseEntity.ok(core.getAggregatedStats())

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

