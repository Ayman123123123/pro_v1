package com.red.server.admin.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * 🛡️ Admin Audit Log - سجل شامل لكل عمليات الإدارة
 */
@Entity
@Table(name = "admin_audit_log")
class AdminAuditLog(
    @Id var id: UUID = UUID.randomUUID(),

    @Column(name = "admin_id")
    var adminId: UUID? = null,

    @Column(name = "admin_username", length = 100)
    var adminUsername: String? = null,

    @Column(nullable = false, length = 50)
    var action: String = "",

    @Column(nullable = false, length = 30)
    var category: String = "",

    @Column(name = "target_type", length = 30)
    var targetType: String? = null,

    @Column(name = "target_id", length = 100)
    var targetId: String? = null,

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Column(columnDefinition = "TEXT")
    var metadata: String? = null,

    @Column(name = "ip_address", columnDefinition = "VARCHAR(45)")
    var ipAddress: String? = null,

    @Column(name = "user_agent", columnDefinition = "TEXT")
    var userAgent: String? = null,

    @Column(nullable = false, length = 10)
    var severity: String = "INFO",

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
)
