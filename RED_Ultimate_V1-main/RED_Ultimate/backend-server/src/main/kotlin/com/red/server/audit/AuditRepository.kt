package com.red.server.audit

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface AuditRepository : JpaRepository<AuditEvent, UUID> {
    @Query("SELECT e FROM AuditEvent e ORDER BY e.createdAt DESC LIMIT 200")
    fun findTop200ByOrderByCreatedAtDesc(): List<AuditEvent>
}
