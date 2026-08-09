package com.red.server.admin.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.red.server.admin.model.*
import com.red.server.admin.repository.*
import com.red.server.auth.model.AccountRole
import com.red.server.auth.model.AccountStatus
import com.red.server.auth.model.UserAccount
import com.red.server.auth.repository.UserAccountRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * 🛡️ Admin Service - خدمات الإدارة الشاملة
 */
@Service
class AdminService(
    private val auditLog: AdminAuditLogRepository,
    private val analytics: SystemAnalyticsRepository,
    private val health: SystemHealthRepository,
    private val adminSessions: AdminSessionRepository,
    private val featureFlags: FeatureFlagRepository,
    private val userReports: UserReportRepository,
    private val announcements: SystemAnnouncementRepository,
    private val backups: BackupHistoryRepository,
    private val users: UserAccountRepository,
    private val json: ObjectMapper
) {
    // ━━━━━━━━━━━━━━━━ Audit Log ━━━━━━━━━━━━━━━━
    fun recordAudit(
        adminId: UUID?,
        adminUsername: String?,
        action: String,
        category: String,
        targetType: String? = null,
        targetId: String? = null,
        description: String? = null,
        metadata: Map<String, Any?> = emptyMap(),
        ipAddress: String? = null,
        userAgent: String? = null,
        severity: String = "INFO"
    ) {
        auditLog.save(AdminAuditLog(
            adminId = adminId,
            adminUsername = adminUsername,
            action = action,
            category = category,
            targetType = targetType,
            targetId = targetId,
            description = description,
            metadata = if (metadata.isEmpty()) null else json.writeValueAsString(metadata),
            ipAddress = ipAddress,
            userAgent = userAgent,
            severity = severity
        ))
    }

    fun getAuditLog(pageable: Pageable): Page<AdminAuditLog> =
        auditLog.findAll(pageable).let { PageImpl(it.content, pageable, it.totalElements) }

    fun getAuditByAdmin(adminId: UUID, pageable: Pageable): Page<AdminAuditLog> =
        auditLog.findByAdminIdOrderByCreatedAtDesc(adminId, pageable)

    fun getAuditByAction(action: String, pageable: Pageable): Page<AdminAuditLog> =
        auditLog.findByActionOrderByCreatedAtDesc(action, pageable)

    fun getAuditByCategory(category: String, pageable: Pageable): Page<AdminAuditLog> =
        auditLog.findByCategoryOrderByCreatedAtDesc(category, pageable)

    fun getRecentCritical(): List<AdminAuditLog> =
        auditLog.findRecentCritical(Instant.now().minusSeconds(86400))

    // ━━━━━━━━━━━━━━━━ Analytics ━━━━━━━━━━━━━━━━
    fun getAnalytics(start: java.time.LocalDate, end: java.time.LocalDate): List<SystemAnalytics> =
        analytics.findByStatDateBetweenOrderByStatDateDesc(start, end)

    fun getRecentAnalytics(): List<SystemAnalytics> = analytics.findTop30ByOrderByStatDateDesc()

    /**
     * حساب الإحصائيات الحالية من الـ DB
     */
    @Transactional
    fun calculateCurrentAnalytics(): Map<String, Any> {
        val totalUsers = users.count()
        val pendingUsers = users.findAllByStatusOrderByCreatedAtAsc(AccountStatus.PENDING).size
        val bannedUsers = users.findAllByStatusOrderByCreatedAtAsc(AccountStatus.BANNED).size
        val approvedUsers = users.findAllByStatusOrderByCreatedAtAsc(AccountStatus.APPROVED).size
        val newUsers24h = users.findAll().count {
            it.createdAt.isAfter(Instant.now().minusSeconds(86400))
        }

        return mapOf(
            "totalUsers" to totalUsers,
            "approvedUsers" to approvedUsers,
            "pendingUsers" to pendingUsers,
            "bannedUsers" to bannedUsers,
            "newUsers24h" to newUsers24h,
            "approvalRate" to if (totalUsers > 0) (approvedUsers.toDouble() / totalUsers * 100) else 0.0
        )
    }

    // ━━━━━━━━━━━━━━━━ System Health ━━━━━━━━━━━━━━━━
    fun getRecentHealth(): List<SystemHealth> = health.findRecentHealthChecks(Instant.now().minusSeconds(300))
    fun getDegradedComponents(): List<SystemHealth> = health.findByStatusOrderByLastCheckAtDesc("DEGRADED") +
        health.findByStatusOrderByLastCheckAtDesc("DOWN")

    fun recordHealth(
        component: String,
        status: String,
        cpuUsage: Float? = null,
        memoryUsage: Float? = null,
        diskUsage: Float? = null,
        activeConnections: Int? = null,
        requestsPerSecond: Float? = null,
        averageResponseMs: Float? = null,
        errorRate: Float? = null,
        details: Map<String, Any?> = emptyMap()
    ) {
        health.save(SystemHealth(
            component = component,
            status = status,
            cpuUsage = cpuUsage,
            memoryUsage = memoryUsage,
            diskUsage = diskUsage,
            activeConnections = activeConnections,
            requestsPerSecond = requestsPerSecond,
            averageResponseMs = averageResponseMs,
            errorRate = errorRate,
            details = if (details.isEmpty()) null else json.writeValueAsString(details)
        ))
    }

    // ━━━━━━━━━━━━━━━━ Admin Sessions ━━━━━━━━━━━━━━━━
    fun getActiveSessions(adminId: UUID): List<AdminSession> =
        adminSessions.findActiveSessionsForAdmin(adminId)

    fun getAllActiveSessions(): List<AdminSession> =
        adminSessions.findByIsActiveAndExpiresAtBefore(true, Instant.now().plusSeconds(86400))

    @Transactional
    fun terminateSession(sessionId: UUID, reason: String) {
        adminSessions.findById(sessionId).ifPresent { session ->
            session.isActive = false
            session.terminatedAt = Instant.now()
            session.terminationReason = reason
            adminSessions.save(session)
        }
    }

    @Transactional
    fun cleanupExpiredSessions(): Int {
        val expired = adminSessions.findByIsActiveAndExpiresAtBefore(true, Instant.now())
        expired.forEach {
            it.isActive = false
            it.terminatedAt = Instant.now()
            it.terminationReason = "EXPIRED"
        }
        adminSessions.saveAll(expired)
        return expired.size
    }

    // ━━━━━━━━━━━━━━━━ Feature Flags ━━━━━━━━━━━━━━━━
    fun getFeatureFlags(): List<FeatureFlag> = featureFlags.findAll()
    fun getFeatureFlag(name: String): FeatureFlag? = featureFlags.findByFlagName(name)
    fun getActiveFeatureFlags(): List<FeatureFlag> = featureFlags.findActiveFlags(Instant.now())

    fun isFeatureEnabled(name: String, userId: UUID? = null): Boolean {
        val flag = featureFlags.findByFlagName(name) ?: return false
        if (!flag.enabled) return false
        if (flag.expiresAt != null && flag.expiresAt.isBefore(Instant.now())) return false
        if (userId != null && flag.targetUserIds != null) {
            val targets = flag.targetUserIds!!.split(",").map { it.trim() }
            if (targets.contains(userId.toString())) return true
        }
        return flag.rolloutPercentage >= 100 ||
            (flag.rolloutPercentage > 0 && (userId?.hashCode()?.toLong()?.rem(100)?.toInt() ?: 0) < flag.rolloutPercentage)
    }

    @Transactional
    fun updateFeatureFlag(name: String, adminId: UUID, updates: Map<String, Any?>): FeatureFlag? {
        val flag = featureFlags.findByFlagName(name) ?: return null
        updates["enabled"]?.let { flag.enabled = it as Boolean }
        updates["rolloutPercentage"]?.let { flag.rolloutPercentage = it as Int }
        updates["config"]?.let { flag.config = json.writeValueAsString(it) }
        updates["description"]?.let { flag.description = it as? String }
        flag.updatedBy = adminId
        flag.updatedAt = Instant.now()
        return featureFlags.save(flag)
    }

    // ━━━━━━━━━━━━━━━━ User Reports ━━━━━━━━━━━━━━━━
    fun getReports(status: String? = null, pageable: Pageable): Page<UserReport> {
        return if (status != null) userReports.findByStatusOrderByCreatedAtDesc(status, pageable)
        else userReports.findAll(pageable).let { PageImpl(it.content, pageable, it.totalElements) }
    }

    fun getPendingReports(pageable: Pageable): Page<UserReport> =
        userReports.findByStatusOrderByCreatedAtDesc("PENDING", pageable)

    fun getReportsForAdmin(adminId: UUID, status: String = "PENDING", pageable: Pageable): Page<UserReport> =
        userReports.findByAssignedAdminIdAndStatusOrderByCreatedAtDesc(adminId, status, pageable)

    @Transactional
    fun resolveReport(reportId: UUID, adminId: UUID, resolution: String, notes: String? = null): UserReport? {
        val report = userReports.findById(reportId).orElse(null) ?: return null
        report.status = "RESOLVED"
        report.resolution = resolution
        report.adminNotes = notes
        report.resolvedAt = Instant.now()
        report.assignedAdminId = adminId
        return userReports.save(report)
    }

    @Transactional
    fun dismissReport(reportId: UUID, adminId: UUID, notes: String? = null): UserReport? {
        val report = userReports.findById(reportId).orElse(null) ?: return null
        report.status = "DISMISSED"
        report.adminNotes = notes
        report.resolvedAt = Instant.now()
        report.assignedAdminId = adminId
        return userReports.save(report)
    }

    @Transactional
    fun assignReport(reportId: UUID, adminId: UUID): UserReport? {
        val report = userReports.findById(reportId).orElse(null) ?: return null
        report.assignedAdminId = adminId
        return userReports.save(report)
    }

    fun countPendingReports(): Long = userReports.countPending()

    // ━━━━━━━━━━━━━━━━ Announcements ━━━━━━━━━━━━━━━━
    fun getAnnouncements(published: Boolean? = null): List<SystemAnnouncement> {
        return if (published != null) announcements.findByIsPublishedOrderByPriorityDescShowFromDesc(published)
        else announcements.findAll()
    }

    fun getActiveAnnouncements(): List<SystemAnnouncement> =
        announcements.findActiveAnnouncements(Instant.now())

    @Transactional
    fun createAnnouncement(
        title: String,
        body: String,
        type: String,
        targetAudience: String,
        priority: Int,
        isDismissible: Boolean,
        adminId: UUID,
        showFrom: Instant = Instant.now(),
        showUntil: Instant? = null
    ): SystemAnnouncement {
        return announcements.save(SystemAnnouncement(
            title = title, body = body, type = type,
            targetAudience = targetAudience, priority = priority,
            isDismissible = isDismissible, showFrom = showFrom, showUntil = showUntil,
            createdBy = adminId
        ))
    }

    @Transactional
    fun publishAnnouncement(id: UUID, adminId: UUID): SystemAnnouncement? {
        val ann = announcements.findById(id).orElse(null) ?: return null
        ann.isPublished = true
        ann.publishedBy = adminId
        ann.publishedAt = Instant.now()
        ann.updatedAt = Instant.now()
        return announcements.save(ann)
    }

    @Transactional
    fun deleteAnnouncement(id: UUID): Boolean {
        return if (announcements.existsById(id)) {
            announcements.deleteById(id); true
        } else false
    }

    // ━━━━━━━━━━━━━━━━ Backups ━━━━━━━━━━━━━━━━
    fun getBackups(pageable: Pageable): Page<BackupHistory> = backups.findAll(pageable).let { PageImpl(it.content, pageable, it.totalElements) }
    fun getRecentBackups(): List<BackupHistory> = backups.findTop20ByOrderByStartedAtDesc()

    @Transactional
    fun startBackup(backupType: String, adminId: UUID, notes: String? = null): BackupHistory {
        return backups.save(BackupHistory(
            backupType = backupType,
            storageLocation = "/backups/${backupType.lowercase()}/${UUID.randomUUID()}.bak",
            sizeBytes = 0,
            status = "IN_PROGRESS",
            triggeredBy = "MANUAL",
            initiatedBy = adminId,
            notes = notes
        ))
    }

    @Transactional
    fun completeBackup(backupId: UUID, sizeBytes: Long, checksum: String): BackupHistory? {
        val backup = backups.findById(backupId).orElse(null) ?: return null
        backup.status = "COMPLETED"
        backup.sizeBytes = sizeBytes
        backup.checksum = checksum
        backup.completedAt = Instant.now()
        return backups.save(backup)
    }

    @Transactional
    fun restoreBackup(backupId: UUID, confirmCode: String): Boolean {
        if (confirmCode != "RESTORE_CONFIRM") return false
        val backup = backups.findById(backupId).orElse(null) ?: return false
        backup.lastRestoredAt = Instant.now()
        backup.restoreCount += 1
        backups.save(backup)
        return true
    }

    @Transactional
    fun deleteBackup(backupId: UUID): Boolean {
        return if (backups.existsById(backupId)) {
            backups.deleteById(backupId); true
        } else false
    }
}

/**
 * PageImpl helper
 */
private class PageImpl<T>(content: List<T>, pageable: Pageable, total: Long) :
    org.springframework.data.domain.PageImpl<T>(content, pageable, total)
