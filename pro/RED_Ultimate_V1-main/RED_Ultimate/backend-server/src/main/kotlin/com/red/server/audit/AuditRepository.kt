package com.red.server.audit

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface AuditRepository : JpaRepository<AuditEvent, UUID> {
    fun findTop200ByOrderByCreatedAtDesc(): List<AuditEvent>
}
