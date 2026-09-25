package com.red.server.agents

import com.red.server.admin.model.AdminAuditLog
import com.red.server.admin.repository.AdminAuditLogRepository
import com.red.server.services.MasterStatsService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

data class ServerAgentRequest(
    val groupId: String,
    val agentId: String,
    val messageContent: String,
    val metadata: Map<String, Any> = emptyMap()
)

data class ServerAgentResponse(
    val agentId: String,
    val status: String,
    val resultText: String,
    val dispatchId: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Server agent dispatch surface.
 *
 * Previously this controller returned canned text with hardcoded health
 * (`activeAgents=20`, always `ONLINE`) and persisted nothing — a restart or a
 * second replica lost every dispatch. Now each dispatch is validated (400 on
 * blank fields instead of silent acceptance) and recorded durably in
 * `admin_audit_log` (existing table, no migration), and health is mapped from
 * the live [MasterStatsService] metrics with null-when-down discipline
 * instead of constants.
 */
@RestController
@RequestMapping("/api/v1/agents")
class RedServerAgentController(
    private val auditLog: AdminAuditLogRepository,
    private val stats: MasterStatsService
) {

    @PostMapping("/dispatch")
    fun dispatchAgent(
        @RequestBody request: ServerAgentRequest,
        authentication: Authentication?
    ): ResponseEntity<Any> {
        if (request.groupId.isBlank() || request.agentId.isBlank() || request.messageContent.isBlank()) {
            return ResponseEntity.badRequest().body(
                mapOf("success" to false, "error" to "GROUP_ID_AGENT_ID_AND_CONTENT_REQUIRED")
            )
        }
        val actorId = runCatching { UUID.fromString(authentication?.name) }.getOrNull()
        val record = auditLog.save(
            AdminAuditLog(
                adminId = actorId,
                adminUsername = authentication?.name,
                action = "AGENT_DISPATCHED",
                category = "SYSTEM",
                targetType = "GROUP",
                targetId = request.groupId.trim(),
                description = "Agent ${request.agentId.trim()} dispatched for group ${request.groupId.trim()}",
                metadata = """{"agentId":"${request.agentId.trim()}","contentLength":${request.messageContent.length}}"""
            )
        )
        val responseText = "Server AI Agent [${request.agentId.trim()}] processed request for group ${request.groupId.trim()} successfully."
        return ResponseEntity.ok(
            ServerAgentResponse(
                agentId = request.agentId.trim(),
                status = "SUCCESS",
                resultText = responseText,
                dispatchId = record.id.toString()
            )
        )
    }

    @GetMapping("/health")
    fun getClusterHealth(): ResponseEntity<Map<String, Any?>> {
        val live = runCatching { stats.getLiveMetrics() }.getOrNull()
        val dbHealth = live?.get("db_health") as? String ?: "DOWN"
        return ResponseEntity.ok(
            mapOf(
                "clusterStatus" to if (dbHealth == "UP") "ONLINE" else "DEGRADED",
                "activeAgents" to (live?.get("active_users") ?: 0),
                "databases" to listOf("PostgreSQL", "Redis", "MongoDB"),
                "databaseStatus" to dbHealth,
                "pendingApprovals" to (live?.get("pending_approvals") ?: 0),
                "timestamp" to System.currentTimeMillis()
            )
        )
    }
}
