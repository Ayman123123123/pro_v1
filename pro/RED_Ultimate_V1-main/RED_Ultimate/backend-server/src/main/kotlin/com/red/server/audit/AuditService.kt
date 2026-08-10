package com.red.server.audit

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AuditService(private val events: AuditRepository, private val json: ObjectMapper) {
    fun record(actorId: UUID?, action: String, targetId: String? = null, details: Map<String, Any?> = emptyMap()) {
        require(action.matches(Regex("^[A-Z0-9_]{3,80}$")))
        events.save(AuditEvent(actorId = actorId, action = action, targetId = targetId, detailsJson = json.writeValueAsString(details)))
    }

    fun recent() = events.findTop200ByOrderByCreatedAtDesc().map {
        AuditEventResponse(it.id, it.actorId, it.action, it.targetId, json.readTree(it.detailsJson), it.createdAt)
    }
}

data class AuditEventResponse(
    val id: UUID,
    val actorId: UUID?,
    val action: String,
    val targetId: String?,
    val details: com.fasterxml.jackson.databind.JsonNode,
    val createdAt: java.time.Instant
)
