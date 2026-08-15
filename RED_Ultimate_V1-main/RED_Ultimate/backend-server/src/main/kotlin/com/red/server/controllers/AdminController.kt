package com.red.server.controllers

import com.red.server.audit.AuditService
import com.red.server.auth.ApprovalActionRequest
import com.red.server.auth.RedApprovalService
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.auth.repository.UserDeviceRepository
import com.red.server.auth.toResponse
import com.red.server.services.CoreService
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
@RequestMapping("/api/admin")
class AdminController(
    private val approvalService: RedApprovalService,
    private val coreService: CoreService,
    private val securityService: RedSecurityService,
    private val users: UserAccountRepository,
    private val devices: UserDeviceRepository,
    private val audit: AuditService
) {
    @GetMapping("/users/pending")
    fun getPendingUsers() = ResponseEntity.ok(approvalService.getPendingList())

    @GetMapping("/users/legacy")
    fun getUsersLegacy() = users.findAllByOrderByCreatedAtDesc().map { user ->
        user.toResponse(devices.findAllByUserIdOrderByCreatedAtAsc(user.id))
    }

    @PostMapping("/users/action")
    fun updateUserStatus(
        @RequestBody request: ApprovalActionRequest,
        authentication: Authentication
    ) = ResponseEntity.ok(
        approvalService.processAction(
            userId = request.userId,
            action = request.action,
            reason = request.reason,
            adminId = UUID.fromString(authentication.name)
        )
    )

    @PostMapping("/users/update-status")
    fun legacyUpdateUserStatus(
        @RequestParam userId: String,
        @RequestParam status: String,
        authentication: Authentication
    ) = ResponseEntity.ok(
        approvalService.processAction(
            UUID.fromString(userId),
            com.red.server.auth.model.AccountStatus.valueOf(status.uppercase()),
            adminId = UUID.fromString(authentication.name)
        )
    )

    @GetMapping("/stories/monitor")
    fun monitorStories() = ResponseEntity.ok(coreService.getActiveStoriesCount())

    @PostMapping("/security/wipe")
    fun wipeUser(@RequestParam userId: String, authentication: Authentication): ResponseEntity<Any> {
        val actorId = UUID.fromString(authentication.name)
        val result = securityService.sendWipeSignal(userId, actorId)
        audit.record(actorId, "REMOTE_WIPE_SENT", userId, mapOf("commandId" to result["commandId"]))
        return ResponseEntity.accepted().body(result)
    }

    @PostMapping("/security/kill-switch")
    fun activateKillSwitch(@RequestParam reason: String, authentication: Authentication): ResponseEntity<Any> {
        val actorId = UUID.fromString(authentication.name)
        val result = securityService.activateKillSwitch(reason, actorId)
        audit.record(actorId, "KILL_SWITCH_ACTIVATED", details = mapOf("reason" to reason, "requestedUsers" to result["requestedUsers"]))
        return ResponseEntity.accepted().body(result)
    }
}
