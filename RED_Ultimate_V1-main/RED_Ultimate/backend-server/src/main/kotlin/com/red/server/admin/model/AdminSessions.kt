package com.red.server.admin.model

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * 🖥️ Admin Sessions - جلسات الإدارة النشطة
 */
@Entity
@Table(name = "admin_sessions")
class AdminSession(
    @Id var id: UUID = UUID.randomUUID(),

    @Column(name = "admin_id", nullable = false)
    var adminId: UUID = UUID.randomUUID(),

    @Column(name = "session_token_hash", nullable = false, length = 128)
    var sessionTokenHash: String = "",

    @Column(name = "ip_address", columnDefinition = "INET")
    var ipAddress: String? = null,

    @Column(name = "user_agent", columnDefinition = "TEXT")
    var userAgent: String? = null,

    @Column(length = 100)
    var location: String? = null,

    @Column(name = "device_info", columnDefinition = "JSONB")
    var deviceInfo: String? = null,

    @Column(name = "started_at", nullable = false)
    var startedAt: Instant = Instant.now(),

    @Column(name = "last_active_at", nullable = false)
    var lastActiveAt: Instant = Instant.now(),

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant = Instant.now().plusSeconds(3600),

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "terminated_at")
    var terminatedAt: Instant? = null,

    @Column(name = "termination_reason", length = 50)
    var terminationReason: String? = null
)

/**
 * 🚩 Feature Flags - أعلام الميزات
 */
@Entity
@Table(name = "feature_flags")
class FeatureFlag(
    @Id var id: UUID = UUID.randomUUID(),

    @Column(name = "flag_name", nullable = false, unique = true, length = 50)
    var flagName: String = "",

    @Column(columnDefinition = "TEXT")
    var description: String? = null,

    @Column(nullable = false)
    var enabled: Boolean = false,

    @Column(name = "rollout_percentage", nullable = false)
    var rolloutPercentage: Int = 0,

    @Column(name = "target_user_ids", columnDefinition = "UUID[]")
    var targetUserIds: String? = null,

    @Column(name = "target_groups", columnDefinition = "TEXT[]")
    var targetGroups: String? = null,

    @Column(columnDefinition = "JSONB")
    var config: String? = null,

    @Column(name = "created_by")
    var createdBy: UUID? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_by")
    var updatedBy: UUID? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column(name = "expires_at")
    var expiresAt: Instant? = null
)

/**
 * 🚨 User Reports - بلاغات المستخدمين
 */
@Entity
@Table(name = "user_reports")
class UserReport(
    @Id var id: UUID = UUID.randomUUID(),

    @Column(name = "reporter_id", nullable = false)
    var reporterId: UUID = UUID.randomUUID(),

    @Column(name = "target_user_id")
    var targetUserId: UUID? = null,

    @Column(name = "target_content_type", length = 30)
    var targetContentType: String? = null,

    @Column(name = "target_content_id", length = 100)
    var targetContentId: String? = null,

    @Column(nullable = false, length = 30)
    var category: String = "",

    @Column(columnDefinition = "TEXT")
    var reason: String? = null,

    @Column(columnDefinition = "JSONB")
    var evidence: String? = null,

    @Column(nullable = false, length = 20)
    var status: String = "PENDING",

    @Column(name = "assigned_admin_id")
    var assignedAdminId: UUID? = null,

    @Column(length = 30)
    var resolution: String? = null,

    @Column(name = "admin_notes", columnDefinition = "TEXT")
    var adminNotes: String? = null,

    @Column(name = "resolved_at")
    var resolvedAt: Instant? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
)

/**
 * 📢 System Announcements - إعلانات النظام
 */
@Entity
@Table(name = "system_announcements")
class SystemAnnouncement(
    @Id var id: UUID = UUID.randomUUID(),

    @Column(nullable = false, length = 200)
    var title: String = "",

    @Column(nullable = false, columnDefinition = "TEXT")
    var body: String = "",

    @Column(nullable = false, length = 20)
    var type: String = "INFO",

    @Column(name = "target_audience", nullable = false, length = 20)
    var targetAudience: String = "ALL",

    @Column(name = "target_user_ids", columnDefinition = "UUID[]")
    var targetUserIds: String? = null,

    @Column(nullable = false)
    var priority: Int = 0,

    @Column(name = "is_dismissible", nullable = false)
    var isDismissible: Boolean = true,

    @Column(name = "show_from", nullable = false)
    var showFrom: Instant = Instant.now(),

    @Column(name = "show_until")
    var showUntil: Instant? = null,

    @Column(name = "is_published", nullable = false)
    var isPublished: Boolean = false,

    @Column(name = "published_by")
    var publishedBy: UUID? = null,

    @Column(name = "published_at")
    var publishedAt: Instant? = null,

    @Column(name = "created_by", nullable = false)
    var createdBy: UUID = UUID.randomUUID(),

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
)

/**
 * 💾 Backup History - سجل النسخ الاحتياطية
 */
@Entity
@Table(name = "backup_history")
class BackupHistory(
    @Id var id: UUID = UUID.randomUUID(),

    @Column(name = "backup_type", nullable = false, length = 20)
    var backupType: String = "FULL",

    @Column(name = "storage_location", nullable = false, length = 500)
    var storageLocation: String = "",

    @Column(name = "size_bytes", nullable = false)
    var sizeBytes: Long = 0,

    @Column(nullable = false, length = 20)
    var status: String = "IN_PROGRESS",

    @Column(name = "started_at", nullable = false)
    var startedAt: Instant = Instant.now(),

    @Column(name = "completed_at")
    var completedAt: Instant? = null,

    @Column(length = 128)
    var checksum: String? = null,

    @Column(name = "verified_at")
    var verifiedAt: Instant? = null,

    @Column(name = "verified_by")
    var verifiedBy: UUID? = null,

    @Column(name = "last_restored_at")
    var lastRestoredAt: Instant? = null,

    @Column(name = "restore_count", nullable = false)
    var restoreCount: Int = 0,

    @Column(name = "triggered_by", nullable = false, length = 20)
    var triggeredBy: String = "SCHEDULED",

    @Column(name = "initiated_by")
    var initiatedBy: UUID? = null,

    @Column(columnDefinition = "TEXT")
    var notes: String? = null
)
