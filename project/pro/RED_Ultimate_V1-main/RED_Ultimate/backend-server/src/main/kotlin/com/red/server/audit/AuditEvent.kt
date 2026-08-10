package com.red.server.audit

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "audit_events")
class AuditEvent(
    @Id var id: UUID = UUID.randomUUID(),
    @Column(name = "actor_id") var actorId: UUID? = null,
    @Column(nullable = false, length = 80) var action: String = "",
    @Column(name = "target_id", length = 100) var targetId: String? = null,
    @Column(name = "details_json", nullable = false, columnDefinition = "TEXT") var detailsJson: String = "{}",
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()
)
