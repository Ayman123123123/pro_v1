package com.red.server.auth

import com.red.server.audit.AuditService
import com.red.server.auth.repository.UserAccountRepository
import org.springframework.security.core.Authentication
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/admin/users/pstn")
class PstnAuthorizationController(private val users: UserAccountRepository, private val audit: AuditService) {
    @PutMapping
    @Transactional
    fun update(@RequestBody request: PstnAuthorizationRequest, authentication: Authentication): UserAccountResponse {
        require(request.dailyLimit in 0..1000) { "dailyLimit must be between 0 and 1000" }
        val user = users.findById(request.userId).orElseThrow { NoSuchElementException("User not found") }
        user.pstnEnabled = request.enabled
        user.pstnDailyLimit = if (request.enabled) request.dailyLimit else 0
        user.updatedAt = Instant.now()
        users.save(user)
        audit.record(UUID.fromString(authentication.name), "PSTN_AUTHORIZATION_CHANGED", user.id.toString(),
            mapOf("redId" to user.redId, "enabled" to user.pstnEnabled, "dailyLimit" to user.pstnDailyLimit))
        return user.toResponse()
    }
}

data class PstnAuthorizationRequest(val userId: UUID, val enabled: Boolean, val dailyLimit: Int)
