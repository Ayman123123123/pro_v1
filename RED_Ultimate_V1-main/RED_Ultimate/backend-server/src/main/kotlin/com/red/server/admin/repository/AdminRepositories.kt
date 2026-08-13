package com.red.server.admin.repository

import com.red.server.admin.model.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

/**
 * 🛡️ Admin Audit Log Repository
 */
interface AdminAuditLogRepository : JpaRepository<AdminAuditLog, UUID> {
    @Query("SELECT a FROM AdminAuditLog a WHERE a.adminId = :adminId ORDER BY a.createdAt DESC")
    fun findByAdminIdOrderByCreatedAtDesc(@Param("adminId") adminId: UUID, pageable: Pageable): Page<AdminAuditLog>

    @Query("SELECT a FROM AdminAuditLog a WHERE a.action = :action ORDER BY a.createdAt DESC")
    fun findByActionOrderByCreatedAtDesc(@Param("action") action: String, pageable: Pageable): Page<AdminAuditLog>

    @Query("SELECT a FROM AdminAuditLog a WHERE a.category = :category ORDER BY a.createdAt DESC")
    fun findByCategoryOrderByCreatedAtDesc(@Param("category") category: String, pageable: Pageable): Page<AdminAuditLog>

    @Query("SELECT a FROM AdminAuditLog a WHERE a.severity = :severity ORDER BY a.createdAt DESC")
    fun findBySeverityOrderByCreatedAtDesc(@Param("severity") severity: String, pageable: Pageable): Page<AdminAuditLog>

    @Query("SELECT a FROM AdminAuditLog a WHERE a.createdAt BETWEEN :start AND :end ORDER BY a.createdAt DESC")
    fun findByCreatedAtBetweenOrderByCreatedAtDesc(@Param("start") start: Instant, @Param("end") end: Instant, pageable: Pageable): Page<AdminAuditLog>

    @Query("SELECT a FROM AdminAuditLog a WHERE a.targetType = :targetType AND a.targetId = :targetId ORDER BY a.createdAt DESC")
    fun findByTargetTypeAndTargetIdOrderByCreatedAtDesc(@Param("targetType") targetType: String, @Param("targetId") targetId: String): List<AdminAuditLog>

    @Query("SELECT a FROM AdminAuditLog a WHERE a.severity = 'CRITICAL' AND a.createdAt > :since ORDER BY a.createdAt DESC")
    fun findRecentCritical(@Param("since") since: Instant): List<AdminAuditLog>

    @Query("SELECT COUNT(a) FROM AdminAuditLog a WHERE a.adminId = :adminId AND a.createdAt > :since")
    fun countByAdminSince(@Param("adminId") adminId: UUID, @Param("since") since: Instant): Long
}

/**
 * 📊 System Analytics Repository
 */
interface SystemAnalyticsRepository : JpaRepository<SystemAnalytics, UUID> {
    fun findByStatDateOrderByStatDateDesc(statDate: java.time.LocalDate): SystemAnalytics?
    fun findByStatDateBetweenOrderByStatDateDesc(start: java.time.LocalDate, end: java.time.LocalDate): List<SystemAnalytics>
    fun findTop30ByOrderByStatDateDesc(): List<SystemAnalytics>
}

/**
 * 💚 System Health Repository
 */
interface SystemHealthRepository : JpaRepository<SystemHealth, UUID> {
    fun findByComponentOrderByLastCheckAtDesc(component: String): List<SystemHealth>
    fun findByStatusOrderByLastCheckAtDesc(status: String): List<SystemHealth>

    @Query("SELECT h FROM SystemHealth h WHERE h.lastCheckAt > :since")
    fun findRecentHealthChecks(@Param("since") since: Instant): List<SystemHealth>
}

/**
 * 🖥️ Admin Session Repository
 */
interface AdminSessionRepository : JpaRepository<AdminSession, UUID> {
    fun findByAdminIdAndIsActiveOrderByLastActiveAtDesc(adminId: UUID, isActive: Boolean): List<AdminSession>
    fun findBySessionTokenHash(sessionTokenHash: String): AdminSession?
    fun findByIsActiveAndExpiresAtBefore(isActive: Boolean, expiresAt: Instant): List<AdminSession>

    @Query("SELECT s FROM AdminSession s WHERE s.isActive = TRUE AND s.adminId = :adminId ORDER BY s.lastActiveAt DESC")
    fun findActiveSessionsForAdmin(@Param("adminId") adminId: UUID): List<AdminSession>
}

/**
 * 🚩 Feature Flag Repository
 */
interface FeatureFlagRepository : JpaRepository<FeatureFlag, UUID> {
    fun findByFlagName(flagName: String): FeatureFlag?

    @Query("SELECT f FROM FeatureFlag f WHERE f.enabled = TRUE AND (f.expiresAt IS NULL OR f.expiresAt > :now)")
    fun findActiveFlags(@Param("now") now: Instant): List<FeatureFlag>
}

/**
 * 🚨 User Report Repository
 */
interface UserReportRepository : JpaRepository<UserReport, UUID> {
    @Query("SELECT r FROM UserReport r WHERE r.status = :status ORDER BY r.createdAt DESC")
    fun findByStatusOrderByCreatedAtDesc(@Param("status") status: String, pageable: Pageable): Page<UserReport>

    @Query("SELECT r FROM UserReport r WHERE r.assignedAdminId = :assignedAdminId AND r.status = :status ORDER BY r.createdAt DESC")
    fun findByAssignedAdminIdAndStatusOrderByCreatedAtDesc(@Param("assignedAdminId") assignedAdminId: UUID, @Param("status") status: String, pageable: Pageable): Page<UserReport>

    @Query("SELECT r FROM UserReport r WHERE r.category = :category ORDER BY r.createdAt DESC")
    fun findByCategoryOrderByCreatedAtDesc(@Param("category") category: String, pageable: Pageable): Page<UserReport>

    @Query("SELECT r FROM UserReport r WHERE r.reporterId = :reporterId ORDER BY r.createdAt DESC")
    fun findByReporterIdOrderByCreatedAtDesc(@Param("reporterId") reporterId: UUID, pageable: Pageable): Page<UserReport>

    @Query("SELECT r FROM UserReport r WHERE r.targetUserId = :targetUserId ORDER BY r.createdAt DESC")
    fun findByTargetUserIdOrderByCreatedAtDesc(@Param("targetUserId") targetUserId: UUID, pageable: Pageable): Page<UserReport>

    @Query("SELECT COUNT(r) FROM UserReport r WHERE r.status = 'PENDING'")
    fun countPending(): Long
}

/**
 * 📢 System Announcement Repository
 */
interface SystemAnnouncementRepository : JpaRepository<SystemAnnouncement, UUID> {
    fun findByIsPublishedOrderByPriorityDescShowFromDesc(isPublished: Boolean): List<SystemAnnouncement>

    @Query("SELECT a FROM SystemAnnouncement a WHERE a.isPublished = TRUE AND a.showFrom <= :now AND (a.showUntil IS NULL OR a.showUntil > :now) ORDER BY a.priority DESC, a.showFrom DESC")
    fun findActiveAnnouncements(@Param("now") now: Instant): List<SystemAnnouncement>
}

/**
 * 💾 Backup History Repository
 */
interface BackupHistoryRepository : JpaRepository<BackupHistory, UUID> {
    fun findByStatusOrderByStartedAtDesc(status: String, pageable: Pageable): Page<BackupHistory>
    fun findByBackupTypeOrderByStartedAtDesc(backupType: String, pageable: Pageable): Page<BackupHistory>
    fun findTop20ByOrderByStartedAtDesc(): List<BackupHistory>
}
