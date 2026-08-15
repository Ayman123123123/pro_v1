package com.red.server.api.admin

import com.red.server.auth.ApprovalActionRequest
import com.red.server.auth.RedApprovalService
import com.red.server.infrastructure.dinstar.DinstarMasterClient
import com.red.server.services.MasterStatsService
import com.red.server.services.RedSecurityService
import org.springframework.http.ResponseEntity
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
    private val securityService: RedSecurityService
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
    fun initiateWipe(@RequestParam userId: String, authentication: Authentication) =
        ResponseEntity.accepted().body(
            securityService.sendWipeSignal(userId, UUID.fromString(authentication.name))
        )

    @GetMapping("/media/active-calls")
    fun getActiveCalls() = ResponseEntity.ok(statsService.getVoipMetrics())
}
