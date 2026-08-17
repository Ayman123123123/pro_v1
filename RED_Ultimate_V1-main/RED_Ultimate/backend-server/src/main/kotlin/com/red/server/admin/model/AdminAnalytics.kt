package com.red.server.admin.model

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * 📊 System Analytics - إحصائيات يومية للنظام
 */
@Entity
@Table(name = "system_analytics")
class SystemAnalytics(
    @Id var id: UUID = UUID.randomUUID(),

    @Column(name = "stat_date", nullable = false, unique = true)
    var statDate: LocalDate = LocalDate.now(),

    // Users
    @Column(name = "total_users", nullable = false) var totalUsers: Int = 0,
    @Column(name = "new_users", nullable = false) var newUsers: Int = 0,
    @Column(name = "active_users_dau", nullable = false) var activeUsersDau: Int = 0,
    @Column(name = "active_users_mau", nullable = false) var activeUsersMau: Int = 0,
    @Column(name = "pending_approvals", nullable = false) var pendingApprovals: Int = 0,
    @Column(name = "banned_users", nullable = false) var bannedUsers: Int = 0,

    // Messages
    @Column(name = "messages_sent", nullable = false) var messagesSent: Int = 0,
    @Column(name = "messages_delivered", nullable = false) var messagesDelivered: Int = 0,
    @Column(name = "messages_read", nullable = false) var messagesRead: Int = 0,
    @Column(name = "voice_messages", nullable = false) var voiceMessages: Int = 0,
    @Column(name = "media_uploads", nullable = false) var mediaUploads: Int = 0,
    @Column(name = "media_bytes_uploaded", nullable = false) var mediaBytesUploaded: Long = 0,

    // Calls
    @Column(name = "calls_total", nullable = false) var callsTotal: Int = 0,
    @Column(name = "calls_audio", nullable = false) var callsAudio: Int = 0,
    @Column(name = "calls_video", nullable = false) var callsVideo: Int = 0,
    @Column(name = "calls_conference", nullable = false) var callsConference: Int = 0,
    @Column(name = "calls_live", nullable = false) var callsLive: Int = 0,
    @Column(name = "calls_pstn", nullable = false) var callsPstn: Int = 0,
    @Column(name = "calls_duration_seconds", nullable = false) var callsDurationSeconds: Long = 0,
    @Column(name = "calls_failed", nullable = false) var callsFailed: Int = 0,
    @Column(name = "calls_missed", nullable = false) var callsMissed: Int = 0,

    // DINSTAR
    @Column(name = "dinstar_active_ports", nullable = false) var dinstarActivePorts: Int = 0,
    @Column(name = "dinstar_total_calls", nullable = false) var dinstarTotalCalls: Int = 0,
    @Column(name = "dinstar_total_duration_seconds", nullable = false) var dinstarTotalDurationSeconds: Long = 0,
    @Column(name = "dinstar_balance_remaining", nullable = false) var dinstarBalanceRemaining: BigDecimal = BigDecimal.ZERO,

    // Groups
    @Column(name = "groups_created", nullable = false) var groupsCreated: Int = 0,
    @Column(name = "groups_active", nullable = false) var groupsActive: Int = 0,

    // Stories & Posts
    @Column(name = "stories_posted", nullable = false) var storiesPosted: Int = 0,
    @Column(name = "stories_viewed", nullable = false) var storiesViewed: Int = 0,
    @Column(name = "posts_created", nullable = false) var postsCreated: Int = 0,
    @Column(name = "posts_reactions", nullable = false) var postsReactions: Int = 0,

    // Storage
    @Column(name = "storage_used_bytes", nullable = false) var storageUsedBytes: Long = 0,
    @Column(name = "media_objects_count", nullable = false) var mediaObjectsCount: Int = 0,

    // Security
    @Column(name = "security_alerts", nullable = false) var securityAlerts: Int = 0,
    @Column(name = "blocked_attempts", nullable = false) var blockedAttempts: Int = 0,

    // Timestamps
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now()
)

/**
 * 💚 System Health - صحة مكونات النظام
 */
@Entity
@Table(name = "system_health")
class SystemHealth(
    @Id var id: UUID = UUID.randomUUID(),

    @Column(nullable = false, length = 50)
    var component: String = "",

    @Column(nullable = false, length = 20)
    var status: String = "HEALTHY",

    @Column(name = "cpu_usage") var cpuUsage: Float? = null,
    @Column(name = "memory_usage") var memoryUsage: Float? = null,
    @Column(name = "disk_usage") var diskUsage: Float? = null,
    @Column(name = "active_connections") var activeConnections: Int? = null,
    @Column(name = "requests_per_second") var requestsPerSecond: Float? = null,
    @Column(name = "average_response_ms") var averageResponseMs: Float? = null,
    @Column(name = "error_rate") var errorRate: Float? = null,

    @Column(columnDefinition = "TEXT")
    var details: String? = null,

    @Column(name = "last_check_at", nullable = false)
    var lastCheckAt: Instant = Instant.now(),

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
)
