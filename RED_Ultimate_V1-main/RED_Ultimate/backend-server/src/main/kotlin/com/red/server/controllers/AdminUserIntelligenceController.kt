package com.red.server.controllers

import com.red.server.audit.AuditService
import com.red.server.services.AdminUserIntelligenceService
import com.red.server.services.TemporaryPasswordRequest
import com.red.server.services.RedSecurityService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/admin/users")
class AdminUserIntelligenceController(
    private val intelligence: AdminUserIntelligenceService,
    private val security: RedSecurityService,
    private val audit: AuditService
) {
    @GetMapping("/{userId}/overview")
    fun overview(@PathVariable userId: UUID) = intelligence.overview(userId)

    @PostMapping("/{userId}/temporary-password")
    fun temporaryPassword(
        @PathVariable userId: UUID,
        @RequestBody request: TemporaryPasswordRequest,
        authentication: Authentication
    ): ResponseEntity<Void> {
        val actor = UUID.fromString(authentication.name)
        intelligence.setTemporaryPassword(userId, request.temporaryPassword, actor)
        audit.record(actor, "TEMPORARY_PASSWORD_SET", userId.toString())
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{userId}/remote-app-wipe")
    fun remoteAppWipe(@PathVariable userId: UUID, authentication: Authentication): ResponseEntity<Any> {
        val actor = UUID.fromString(authentication.name)
        val result = security.sendWipeSignal(userId.toString(), actor)
        audit.record(actor, "REMOTE_APP_WIPE_REQUESTED", userId.toString(), mapOf("commandId" to result["commandId"]))
        return ResponseEntity.accepted().body(result)
    }
}
