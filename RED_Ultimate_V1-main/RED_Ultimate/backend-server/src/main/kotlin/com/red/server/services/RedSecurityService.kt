package com.red.server.services

import com.red.server.auth.model.AccountRole
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.websocket.RedMasterHandler
import org.springframework.stereotype.Service
import java.util.UUID

/** Durable application-wipe orchestration; no best-effort Redis-only placeholders. */
@Service
class RedSecurityService(
    private val users: UserAccountRepository,
    private val intelligence: AdminUserIntelligenceService,
    private val messaging: RedMasterHandler
) {
    /** Request and immediately deliver a wipe; offline devices receive it on next /ws/master connection. */
    fun sendWipeSignal(userReference: String, actorId: UUID): Map<String, Any> {
        val user = resolveUser(userReference)
        require(user.role != AccountRole.ADMIN) { "Administrator accounts cannot be remotely wiped" }
        val updated = intelligence.requestRemoteAppWipe(user.id, actorId)
        val commandId = requireNotNull(intelligence.pendingRemoteWipeCommand(updated.id))
        messaging.sendRemoteWipe(updated.redId, commandId, "ADMIN_REQUESTED_REMOTE_APP_WIPE")
        return mapOf(
            "status" to "REQUESTED",
            "userId" to updated.id.toString(),
            "redId" to updated.redId,
            "action" to "REMOTE_APP_WIPE",
            "commandId" to commandId,
            "durable" to true,
            "timestamp" to System.currentTimeMillis()
        )
    }

    /**
     * Emergency application kill switch. It intentionally excludes administrators
     * and creates a durable per-user command rather than publishing to an empty
     * Redis topic.
     */
    fun activateKillSwitch(reason: String, actorId: UUID): Map<String, Any> {
        val cleanReason = reason.trim()
        require(cleanReason.length in 8..200) { "Kill-switch reason must contain 8-200 characters" }
        val targets = users.findAll().filter { it.role != AccountRole.ADMIN }
        var requested = 0
        targets.forEach { user ->
            runCatching {
                val updated = intelligence.requestRemoteAppWipe(user.id, actorId)
                val commandId = requireNotNull(intelligence.pendingRemoteWipeCommand(updated.id))
                messaging.sendRemoteWipe(updated.redId, commandId, "KILL_SWITCH: ${cleanReason.take(160)}")
                requested++
            }
        }
        return mapOf(
            "status" to "ACTIVATED",
            "reason" to cleanReason,
            "requestedUsers" to requested,
            "durable" to true,
            "timestamp" to System.currentTimeMillis()
        )
    }

    private fun resolveUser(reference: String) =
        runCatching { UUID.fromString(reference) }.getOrNull()
            ?.let { users.findById(it).orElse(null) }
            ?: users.findByRedId(reference)
            ?: throw NoSuchElementException("User not found")
}
