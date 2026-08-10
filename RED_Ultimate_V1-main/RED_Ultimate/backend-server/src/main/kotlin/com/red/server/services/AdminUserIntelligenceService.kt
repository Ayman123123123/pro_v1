package com.red.server.services

import com.red.server.audit.AuditEvent
import com.red.server.audit.AuditRepository
import com.red.server.auth.RefreshTokenService
import com.red.server.auth.model.UserAccount
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.auth.repository.UserDeviceRepository
import com.red.server.auth.toResponse
import com.red.server.calls.CallHistoryDocument
import com.red.server.database.MessageDocument
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

data class AdminUserOverview(
    val user: Any,
    val online: Boolean,
    val messagesSent: Long,
    val messagesReceived: Long,
    val messages24h: Long,
    val callsMade: Long,
    val callsReceived: Long,
    val redCalls: Long,
    val pstnCalls: Long,
    val passwordResetRequired: Boolean,
    val remoteWipeStatus: String?,
    val managedDeviceWipeAllowed: Boolean,
    val securityEvents: List<AdminUserSecurityEvent>
)

data class AdminUserSecurityEvent(val action: String, val targetId: String?, val createdAt: Instant)
data class TemporaryPasswordRequest(val temporaryPassword: String)

@Service
class AdminUserIntelligenceService(
    private val users: UserAccountRepository,
    private val devices: UserDeviceRepository,
    private val mongo: MongoTemplate,
    private val redis: StringRedisTemplate,
    private val passwords: PasswordEncoder,
    private val refreshTokens: RefreshTokenService,
    private val auditEvents: AuditRepository
) {
    fun overview(userId: UUID): AdminUserOverview {
        val user = user(userId)
        val since = Instant.now().minus(24, ChronoUnit.HOURS)
        val sender = Criteria.where("senderId").`is`(user.redId)
        val receiver = Criteria.where("receiverId").`is`(user.redId)
        val callSender = Criteria.where("initiatorId").`is`(user.redId)
        val callReceiver = Criteria.where("targetId").`is`(user.redId)
        val cutoff = (System.currentTimeMillis() - 5 * 60_000).toDouble()
        val score = redis.opsForZSet().score("red:presence:index", user.redId) ?: Double.NEGATIVE_INFINITY
        val events = auditEvents.findTop200ByOrderByCreatedAtDesc()
            .filter { it.actorId == user.id || it.targetId == user.id.toString() }
            .take(30)
            .map { AdminUserSecurityEvent(it.action, it.targetId, it.createdAt) }
        return AdminUserOverview(
            user = user.toResponse(devices.findAllByUserIdOrderByCreatedAtAsc(user.id)),
            online = score >= cutoff,
            messagesSent = mongo.count(Query(sender), MessageDocument::class.java),
            messagesReceived = mongo.count(Query(receiver), MessageDocument::class.java),
            messages24h = mongo.count(Query(Criteria().andOperator(Criteria().orOperator(sender, receiver), Criteria.where("createdAt").gte(since))), MessageDocument::class.java),
            callsMade = mongo.count(Query(callSender), CallHistoryDocument::class.java),
            callsReceived = mongo.count(Query(callReceiver), CallHistoryDocument::class.java),
            redCalls = mongo.count(Query(Criteria().andOperator(Criteria().orOperator(callSender, callReceiver), Criteria.where("route").`is`("RED"))), CallHistoryDocument::class.java),
            pstnCalls = mongo.count(Query(Criteria().andOperator(Criteria().orOperator(callSender, callReceiver), Criteria.where("route").`is`("DINSTAR"))), CallHistoryDocument::class.java),
            passwordResetRequired = user.passwordResetRequired,
            remoteWipeStatus = user.remoteWipeStatus,
            managedDeviceWipeAllowed = user.managedDeviceWipeAllowed,
            securityEvents = events
        )
    }

    @Transactional
    fun setTemporaryPassword(userId: UUID, temporaryPassword: String, actorId: UUID) {
        val user = user(userId)
        require(user.role.name != "ADMIN") { "Administrator passwords cannot be changed from this endpoint" }
        require(temporaryPassword.length in 12..128) { "Temporary password must contain 12-128 characters" }
        require(!temporaryPassword.contains(user.username, ignoreCase = true)) { "Temporary password must not contain the username" }
        user.passwordHash = passwords.encode(temporaryPassword)
        user.passwordResetRequired = true
        user.passwordResetIssuedAt = Instant.now()
        user.updatedAt = Instant.now()
        users.save(user)
        refreshTokens.revokeAll(user.id)
    }

    @Transactional
    fun markRemoteWipeAcknowledged(userId: UUID, commandId: String) {
        val user = user(userId)
        if (user.remoteWipeStatus == "REQUESTED") {
            user.remoteWipeStatus = "ACKNOWLEDGED"
            user.updatedAt = Instant.now()
            users.save(user)
        }
    }

    @Transactional
    fun requestRemoteAppWipe(userId: UUID, actorId: UUID): UserAccount {
        val user = user(userId)
        user.remoteWipeStatus = "REQUESTED"
        user.remoteWipeRequestedAt = Instant.now()
        user.updatedAt = Instant.now()
        refreshTokens.revokeAll(user.id)
        return users.save(user)
    }

    private fun user(userId: UUID): UserAccount = users.findById(userId).orElseThrow { NoSuchElementException("User not found") }
}
