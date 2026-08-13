package com.red.server.admin.controller

import com.red.server.admin.model.*
import com.red.server.admin.repository.*
import com.red.server.admin.service.AdminService
import com.red.server.auth.RedApprovalService
import com.red.server.auth.model.AccountRole
import com.red.server.auth.model.AccountStatus
import com.red.server.auth.repository.AdminUserSort
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.auth.repository.searchForAdmin
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

/**
 * 🛡️ Admin V2 Controller - APIs شاملة للوحة الإدارة
 * يغطي: Audit, Analytics, Users, Calls, DINSTAR, Reports, Media, Security, Announcements, Backups
 */
@RestController
@RequestMapping("/api/admin")
class AdminV2Controller(
    private val service: AdminService,
    private val users: UserAccountRepository,
    private val auditLog: AdminAuditLogRepository,
    private val analytics: SystemAnalyticsRepository,
    private val health: SystemHealthRepository,
    private val adminSessions: AdminSessionRepository,
    private val featureFlags: FeatureFlagRepository,
    private val userReports: UserReportRepository,
    private val announcements: SystemAnnouncementRepository,
    private val backups: BackupHistoryRepository,
    private val approval: RedApprovalService
) {
    // ━━━━━━━━━━━━━━━━ 📊 Dashboard & Analytics ━━━━━━━━━━━━━━━━
    @GetMapping("/dashboard/summary")
    fun getDashboardSummary(authentication: Authentication): ResponseEntity<Map<String, Any>> {
        val analytics = runCatching { service.calculateCurrentAnalytics() }
        val pendingReports = runCatching { service.countPendingReports() }
        val recentCritical = runCatching { service.getRecentCritical() }
        val degradedHealth = runCatching { service.getDegradedComponents() }
        val activeBackups = runCatching { service.getRecentBackups().count { it.status == "IN_PROGRESS" } }
        val failures = listOf(
            "analytics" to analytics.exceptionOrNull(),
            "pendingReports" to pendingReports.exceptionOrNull(),
            "recentCritical" to recentCritical.exceptionOrNull(),
            "health" to degradedHealth.exceptionOrNull(),
            "backups" to activeBackups.exceptionOrNull(),
        ).mapNotNull { (name, error) -> error?.let { name to (it.message ?: it.javaClass.simpleName) } }
        return ResponseEntity.ok(
            mapOf(
                "analytics" to analytics.getOrDefault(
                    mapOf(
                        "totalUsers" to 0L, "approvedUsers" to 0L, "pendingUsers" to 0L,
                        "bannedUsers" to 0L, "newUsers24h" to 0L, "approvalRate" to 0.0
                    )
                ),
                "pendingReports" to pendingReports.getOrDefault(0L),
                "recentCriticalAlerts" to (recentCritical.getOrNull()?.size ?: 0),
                "degradedComponents" to (degradedHealth.getOrNull()?.size ?: 0),
                "activeBackups" to activeBackups.getOrDefault(0),
                "generatedAt" to Instant.now(),
                "partial" to failures.isNotEmpty(),
                "errors" to failures.toMap()
            )
        )
    }

    @GetMapping("/analytics")
    fun getAnalytics(
        @RequestParam start: String,
        @RequestParam end: String
    ): ResponseEntity<List<SystemAnalytics>> {
        val startDate = runCatching { java.time.LocalDate.parse(start) }.getOrNull()
            ?: return ResponseEntity.ok(emptyList())
        val endDate = runCatching { java.time.LocalDate.parse(end) }.getOrNull()
            ?: return ResponseEntity.ok(emptyList())
        return ResponseEntity.ok(runCatching { service.getAnalytics(startDate, endDate) }.getOrDefault(emptyList()))
    }

    @GetMapping("/health")
    fun getHealth(): ResponseEntity<List<SystemHealth>> =
        ResponseEntity.ok(runCatching { service.getRecentHealth() }.getOrDefault(emptyList()))

    @GetMapping("/metrics/realtime")
    fun getRealtimeMetrics(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(runCatching {
            val analytics = service.calculateCurrentAnalytics()
            val health = service.getRecentHealth()
            mapOf(
                "users" to analytics,
                "health" to health.associateBy { it.component },
                "timestamp" to Instant.now()
            )
        }.getOrElse {
            mapOf(
                "users" to emptyMap<String, Any>(),
                "health" to emptyMap<String, Any>(),
                "timestamp" to Instant.now(),
                "partial" to true
            )
        })
    }

    // ━━━━━━━━━━━━━━━━ 👥 Users Management ━━━━━━━━━━━━━━━━
    @GetMapping("/users")
    fun getUsers(
        @RequestParam(required = false) page: Int = 0,
        @RequestParam(required = false) size: Int = 50,
        @RequestParam(required = false) status: String? = null,
        @RequestParam(required = false) search: String? = null,
        @RequestParam(required = false) role: String? = null,
        @RequestParam(required = false) sortBy: String? = "createdAt",
        @RequestParam(required = false) sortDir: String? = "desc",
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        require(page >= 0) { "PAGE_MUST_NOT_BE_NEGATIVE" }
        val safeSize = size.coerceIn(1, 100)
        val safeSort = sortBy?.takeIf { it in setOf("createdAt", "updatedAt", "username", "displayName", "redId", "status", "role") } ?: "createdAt"
        val direction = runCatching { Sort.Direction.fromString(sortDir ?: "desc") }.getOrDefault(Sort.Direction.DESC)
        val pageable = AdminUserSort.pageable(page, safeSize, safeSort, direction)
        val parsedStatus = status?.trim()?.takeIf(String::isNotEmpty)?.let {
            runCatching { AccountStatus.valueOf(it.uppercase()) }.getOrElse { throw IllegalArgumentException("INVALID_ACCOUNT_STATUS") }
        }
        val parsedRole = role?.trim()?.takeIf(String::isNotEmpty)?.let {
            runCatching { AccountRole.valueOf(it.uppercase()) }.getOrElse { throw IllegalArgumentException("INVALID_ACCOUNT_ROLE") }
        }
        val normalizedSearch = search?.trim()?.takeIf { it.length >= 2 }?.take(80)
        val allUsers = users.searchForAdmin(parsedStatus, parsedRole, normalizedSearch, pageable)

        val adminId = UUID.fromString(authentication.name)
        service.recordAudit(
            adminId = adminId,
            adminUsername = authentication.principal.toString(),
            action = "USERS_LISTED",
            category = "USER",
            description = "Listed users with filters: status=$status, role=$role, search=$search"
        )

        return ResponseEntity.ok(mapOf(
            "content" to allUsers.content.map { user -> mapOf(
                "id" to user.id,
                "redId" to user.redId,
                "username" to user.username,
                "displayName" to user.displayName,
                "status" to user.status.name,
                "role" to user.role.name,
                "pstnEnabled" to user.pstnEnabled,
                "createdAt" to user.createdAt,
                "approvedAt" to user.approvedAt,
                "lastSeen" to user.lastSeen
            )},
            "page" to page,
            "size" to size,
            "totalElements" to allUsers.totalElements,
            "totalPages" to allUsers.totalPages
        ))
    }

    @GetMapping("/users/{userId}")
    fun getUserDetail(
        @PathVariable userId: String,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any?>> {
        val user = users.findById(UUID.fromString(userId)).orElse(null)
            ?: return ResponseEntity.notFound().build()
        val adminId = UUID.fromString(authentication.name)
        service.recordAudit(
            adminId = adminId,
            adminUsername = authentication.principal.toString(),
            action = "USER_DETAIL_VIEWED",
            category = "USER",
            targetType = "USER",
            targetId = userId
        )
        return ResponseEntity.ok(mapOf(
            "id" to user.id,
            "redId" to user.redId,
            "username" to user.username,
            "displayName" to user.displayName,
            "status" to user.status.name,
            "role" to user.role.name,
            "pstnEnabled" to user.pstnEnabled,
            "pstnDailyLimit" to user.pstnDailyLimit,
            "createdAt" to user.createdAt,
            "approvedAt" to user.approvedAt,
            "approvedBy" to user.approvedBy,
            "rejectionReason" to user.rejectionReason,
            "lastSeen" to user.lastSeen
        ))
    }

    /**
     * كل انتقال لحالة الحساب يمر عبر RedApprovalService: اعتماد الجهاز،
     * إصدار الشهادة، وإبطال الجلسات ليست عمليات اختيارية في الواجهة.
     */
    @PostMapping("/users/{userId}/approve")
    fun approveUser(@PathVariable userId: String, authentication: Authentication): ResponseEntity<Map<String, Any>> =
        accountAction(userId, AccountStatus.APPROVED, null, authentication)

    @PostMapping("/users/{userId}/reject")
    fun rejectUser(
        @PathVariable userId: String,
        @RequestBody(required = false) body: Map<String, String?> = emptyMap(),
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> = accountAction(userId, AccountStatus.REJECTED, body["reason"], authentication)

    @PostMapping("/users/{userId}/ban")
    fun banUser(
        @PathVariable userId: String,
        @RequestBody(required = false) body: Map<String, Any?> = emptyMap(),
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> = accountAction(userId, AccountStatus.BANNED, body["reason"] as? String, authentication)

    @PostMapping("/users/{userId}/unban")
    fun unbanUser(@PathVariable userId: String, authentication: Authentication): ResponseEntity<Map<String, Any>> {
        val adminId = UUID.fromString(authentication.name)
        val targetId = UUID.fromString(userId)
        require(targetId != adminId) { "SELF_ACCOUNT_ACTION_FORBIDDEN" }
        val updated = approval.restoreBannedToPending(targetId, adminId)
        service.recordAudit(
            adminId, authentication.name, "ACCOUNT_UNBAN_TO_PENDING", "USER", "USER", userId,
            "Ban lifted; account is PENDING until a new device is enrolled and approved",
            severity = "WARNING"
        )
        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "status" to updated.status.name,
                "message" to "ACCOUNT_RESTORED_PENDING_REENROLL_REQUIRED",
                "user" to mapOf("id" to updated.id, "status" to updated.status.name)
            )
        )
    }

    @PutMapping("/users/{userId}/role")
    @org.springframework.transaction.annotation.Transactional
    fun promoteUser(
        @PathVariable userId: String,
        @RequestBody body: Map<String, String>,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val adminId = UUID.fromString(authentication.name)
        val target = users.findById(UUID.fromString(userId)).orElse(null) ?: return ResponseEntity.notFound().build()
        // لا توجد طبقة SUPER_ADMIN بعد؛ لذلك لا يمكن لهذه الـ API منح ADMIN أو
        // تعديل دور مسؤول. هذا يمنع تصعيد امتياز ذاتي/أفقي حتى يكتمل RBAC الحقيقي.
        require(target.role != AccountRole.ADMIN) { "ADMIN_ROLE_CHANGE_REQUIRES_SUPER_ADMIN" }
        val requested = body["role"]?.uppercase() ?: "USER"
        require(requested == AccountRole.USER.name) { "ADMIN_PROMOTION_REQUIRES_SUPER_ADMIN" }
        target.role = AccountRole.USER
        users.save(target)
        service.recordAudit(adminId, authentication.name, "USER_ROLE_CONFIRMED", "SECURITY", "USER", userId,
            "Role retained as USER; privileged role changes require SUPER_ADMIN", severity = "WARNING")
        return ResponseEntity.ok(mapOf("success" to true, "role" to target.role.name))
    }

    @DeleteMapping("/users/{userId}")
    fun deleteUser(
        @PathVariable userId: String,
        @RequestParam(required = false) hard: Boolean = false,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        require(!hard) { "HARD_DELETE_DISABLED_USE_RETENTION_WORKFLOW" }
        return accountAction(userId, AccountStatus.BANNED, "ADMIN_SOFT_DELETE", authentication)
    }

    private fun accountAction(userId: String, action: AccountStatus, reason: String?, authentication: Authentication): ResponseEntity<Map<String, Any>> {
        val adminId = UUID.fromString(authentication.name)
        val targetId = UUID.fromString(userId)
        require(targetId != adminId) { "SELF_ACCOUNT_ACTION_FORBIDDEN" }
        val updated = approval.processAction(targetId, action, reason?.trim()?.take(500), adminId)
        service.recordAudit(adminId, authentication.name, "ACCOUNT_${action.name}", "USER", "USER", userId,
            "Account transition completed through the central approval workflow", severity = if (action == AccountStatus.BANNED) "WARNING" else "INFO")
        return ResponseEntity.ok(mapOf("success" to true, "user" to mapOf("id" to updated.id, "status" to updated.status.name)))
    }

    // PSTN authorization is owned by PstnAuthorizationController.

    // ━━━━━━━━━━━━━━━━ 🛡️ Audit Log ━━━━━━━━━━━━━━━━
    @GetMapping("/audit")
    fun getAudit(
        @RequestParam(required = false) page: Int = 0,
        @RequestParam(required = false) size: Int = 50,
        @RequestParam(required = false) adminId: String? = null,
        @RequestParam(required = false) action: String? = null,
        @RequestParam(required = false) category: String? = null,
        @RequestParam(required = false) severity: String? = null
    ): ResponseEntity<*> {
        val pageable = PageRequest.of(page, size, org.springframework.data.jpa.domain.JpaSort.unsafe(Sort.Direction.DESC, "created_at"))
        val result = when {
            adminId != null -> service.getAuditByAdmin(UUID.fromString(adminId), pageable)
            action != null -> service.getAuditByAction(action, pageable)
            category != null -> service.getAuditByCategory(category, pageable)
            severity != null -> auditLog.findBySeverityOrderByCreatedAtDesc(severity, pageable)
            else -> service.getAuditLog(pageable)
        }
        return ResponseEntity.ok(result)
    }

    @GetMapping("/security/alerts")
    fun getSecurityAlerts(
        @RequestParam(required = false) page: Int = 0,
        @RequestParam(required = false) size: Int = 50,
        @RequestParam(required = false) severity: String? = null
    ): ResponseEntity<*> {
        val pageable = PageRequest.of(page, size)
        val result = if (severity != null) {
            auditLog.findBySeverityOrderByCreatedAtDesc(severity, pageable)
        } else {
            auditLog.findAll(pageable)
        }
        return ResponseEntity.ok(result)
    }

    // ━━━━━━━━━━━━━━━━ 🖥️ Admin Sessions ━━━━━━━━━━━━━━━━
    @GetMapping("/sessions")
    fun getAdminSessions(
        @RequestParam(required = false) adminId: String? = null
    ): ResponseEntity<List<AdminSession>> {
        val sessions = if (adminId != null) {
            service.getActiveSessions(UUID.fromString(adminId))
        } else {
            service.getAllActiveSessions()
        }
        return ResponseEntity.ok(sessions)
    }

    @PostMapping("/sessions/{sessionId}/terminate")
    fun terminateSession(
        @PathVariable sessionId: String,
        @RequestBody body: Map<String, String>,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val adminId = UUID.fromString(authentication.name)
        service.terminateSession(UUID.fromString(sessionId), body["reason"] ?: "ADMIN_ACTION")
        service.recordAudit(
            adminId = adminId,
            adminUsername = authentication.principal.toString(),
            action = "SESSION_TERMINATED",
            category = "SECURITY",
            targetType = "SESSION",
            targetId = sessionId,
            severity = "WARNING"
        )
        return ResponseEntity.ok(mapOf("success" to true))
    }

    @PostMapping("/sessions/cleanup")
    fun cleanupSessions(authentication: Authentication): ResponseEntity<Map<String, Any>> {
        val adminId = UUID.fromString(authentication.name)
        val count = service.cleanupExpiredSessions()
        service.recordAudit(
            adminId = adminId,
            adminUsername = authentication.principal.toString(),
            action = "SESSIONS_CLEANED",
            category = "SYSTEM",
            description = "Cleaned up $count expired sessions"
        )
        return ResponseEntity.ok(mapOf("cleanedCount" to count))
    }

    // ━━━━━━━━━━━━━━━━ 🚩 Feature Flags ━━━━━━━━━━━━━━━━
    @GetMapping("/feature-flags")
    fun getFeatureFlags(): ResponseEntity<List<FeatureFlag>> =
        ResponseEntity.ok(service.getFeatureFlags())

    @PutMapping("/feature-flags/{name}")
    fun updateFeatureFlag(
        @PathVariable name: String,
        @RequestBody body: Map<String, Any?>,
        authentication: Authentication
    ): ResponseEntity<FeatureFlag> {
        val adminId = UUID.fromString(authentication.name)
        val updated = service.updateFeatureFlag(name, adminId, body)
        service.recordAudit(
            adminId = adminId,
            adminUsername = authentication.principal.toString(),
            action = "FEATURE_FLAG_UPDATED",
            category = "SYSTEM",
            targetType = "FEATURE_FLAG",
            targetId = name,
            description = "Updated feature flag: ${body.keys}"
        )
        return ResponseEntity.ok(updated ?: FeatureFlag().apply { flagName = "NOT_FOUND" })
    }

    // ━━━━━━━━━━━━━━━━ 🚨 User Reports ━━━━━━━━━━━━━━━━
    @GetMapping("/reports")
    fun getReports(
        @RequestParam(required = false) page: Int = 0,
        @RequestParam(required = false) size: Int = 50,
        @RequestParam(required = false) status: String? = null,
        @RequestParam(required = false) category: String? = null,
        @RequestParam(required = false) assignedToMe: Boolean = false
    ): ResponseEntity<*> {
        val pageable = PageRequest.of(page, size)
        val adminId = try {
            org.springframework.security.core.context.SecurityContextHolder.getContext().authentication?.name?.let { UUID.fromString(it) }
        } catch (_: Exception) { null }
        val result = when {
            assignedToMe && adminId != null && status != null ->
                service.getReportsForAdmin(adminId, status, pageable)
            status != null -> service.getReports(status, pageable)
            else -> service.getReports(null, pageable)
        }
        return ResponseEntity.ok(result)
    }

    @PostMapping("/reports/{reportId}/resolve")
    fun resolveReport(
        @PathVariable reportId: String,
        @RequestBody body: Map<String, String>,
        authentication: Authentication
    ): ResponseEntity<UserReport> {
        val adminId = UUID.fromString(authentication.name)
        val updated = service.resolveReport(
            reportId = UUID.fromString(reportId),
            adminId = adminId,
            resolution = body["resolution"] ?: "NO_ACTION",
            notes = body["notes"]
        )
        service.recordAudit(
            adminId = adminId,
            adminUsername = authentication.principal.toString(),
            action = "REPORT_RESOLVED",
            category = "MODERATION",
            targetType = "REPORT",
            targetId = reportId,
            description = "Resolution: ${body["resolution"]}"
        )
        return ResponseEntity.ok(updated ?: UserReport())
    }

    @PostMapping("/reports/{reportId}/dismiss")
    fun dismissReport(
        @PathVariable reportId: String,
        @RequestBody body: Map<String, String>,
        authentication: Authentication
    ): ResponseEntity<UserReport> {
        val adminId = UUID.fromString(authentication.name)
        val updated = service.dismissReport(
            reportId = UUID.fromString(reportId),
            adminId = adminId,
            notes = body["notes"]
        )
        service.recordAudit(
            adminId = adminId,
            adminUsername = authentication.principal.toString(),
            action = "REPORT_DISMISSED",
            category = "MODERATION",
            targetType = "REPORT",
            targetId = reportId
        )
        return ResponseEntity.ok(updated ?: UserReport())
    }

    @PostMapping("/reports/{reportId}/assign")
    fun assignReport(
        @PathVariable reportId: String,
        @RequestBody body: Map<String, String>,
        authentication: Authentication
    ): ResponseEntity<UserReport> {
        val adminId = UUID.fromString(authentication.name)
        val targetAdmin = body["adminId"]?.let { UUID.fromString(it) } ?: adminId
        val updated = service.assignReport(UUID.fromString(reportId), targetAdmin)
        return ResponseEntity.ok(updated ?: UserReport())
    }

    // ━━━━━━━━━━━━━━━━ 📢 Announcements ━━━━━━━━━━━━━━━━
    @GetMapping("/announcements")
    fun getAnnouncements(
        @RequestParam(required = false) published: Boolean? = null
    ): ResponseEntity<List<SystemAnnouncement>> =
        ResponseEntity.ok(service.getAnnouncements(published))

    @PostMapping("/announcements")
    fun createAnnouncement(
        @RequestBody body: Map<String, Any?>,
        authentication: Authentication
    ): ResponseEntity<SystemAnnouncement> {
        val adminId = UUID.fromString(authentication.name)
        val title = body["title"] as? String
            ?: return ResponseEntity.badRequest().build()
        val annBody = body["body"] as? String
            ?: return ResponseEntity.badRequest().build()
        val ann = service.createAnnouncement(
            title = title,
            body = annBody,
            type = body["type"] as? String ?: "INFO",
            targetAudience = body["targetAudience"] as? String ?: "ALL",
            priority = (body["priority"] as? Number)?.toInt() ?: 0,
            isDismissible = body["isDismissible"] as? Boolean ?: true,
            adminId = adminId
        )
        service.recordAudit(
            adminId = adminId,
            adminUsername = authentication.principal.toString(),
            action = "ANNOUNCEMENT_CREATED",
            category = "SYSTEM",
            targetType = "ANNOUNCEMENT",
            targetId = ann.id.toString(),
            description = ann.title
        )
        return ResponseEntity.ok(ann)
    }

    @PostMapping("/announcements/{id}/publish")
    fun publishAnnouncement(
        @PathVariable id: String,
        authentication: Authentication
    ): ResponseEntity<SystemAnnouncement> {
        val adminId = UUID.fromString(authentication.name)
        val ann = service.publishAnnouncement(UUID.fromString(id), adminId)
        service.recordAudit(
            adminId = adminId,
            adminUsername = authentication.principal.toString(),
            action = "ANNOUNCEMENT_PUBLISHED",
            category = "SYSTEM",
            targetType = "ANNOUNCEMENT",
            targetId = id
        )
        return ResponseEntity.ok(ann ?: SystemAnnouncement())
    }

    @DeleteMapping("/announcements/{id}")
    fun deleteAnnouncement(
        @PathVariable id: String,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val adminId = UUID.fromString(authentication.name)
        val success = service.deleteAnnouncement(UUID.fromString(id))
        if (success) {
            service.recordAudit(
                adminId = adminId,
                adminUsername = authentication.principal.toString(),
                action = "ANNOUNCEMENT_DELETED",
                category = "SYSTEM",
                targetType = "ANNOUNCEMENT",
                targetId = id,
                severity = "WARNING"
            )
        }
        return ResponseEntity.ok(mapOf("success" to success))
    }

    // ━━━━━━━━━━━━━━━━ 💾 Backups ━━━━━━━━━━━━━━━━
    @GetMapping("/backups")
    fun getBackups(
        @RequestParam(required = false) page: Int = 0,
        @RequestParam(required = false) size: Int = 20
    ): ResponseEntity<Map<String, Any>> {
        val pageable = PageRequest.of(page, size)
        val result = service.getBackups(pageable)
        return ResponseEntity.ok(mapOf(
            "content" to result.content,
            "page" to page,
            "size" to size,
            "totalElements" to result.totalElements,
            "totalPages" to result.totalPages
        ))
    }

    @PostMapping("/backups")
    fun createBackup(
        @RequestBody body: Map<String, String>,
        authentication: Authentication
    ): ResponseEntity<BackupHistory> {
        val adminId = UUID.fromString(authentication.name)
        val type = body["type"] ?: "FULL"
        val backup = service.startBackup(type, adminId, body["notes"])
        service.recordAudit(
            adminId = adminId,
            adminUsername = authentication.principal.toString(),
            action = "BACKUP_STARTED",
            category = "SYSTEM",
            targetType = "BACKUP",
            targetId = backup.id.toString(),
            description = "Started $type backup"
        )
        return ResponseEntity.ok(backup)
    }

    @PostMapping("/backups/{backupId}/restore")
    fun restoreBackup(
        @PathVariable backupId: String,
        @RequestBody body: Map<String, String>,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val adminId = UUID.fromString(authentication.name)
        val confirmCode = body["confirmCode"] ?: ""
        val success = service.restoreBackup(UUID.fromString(backupId), confirmCode)
        if (success) {
            service.recordAudit(
                adminId = adminId,
                adminUsername = authentication.principal.toString(),
                action = "BACKUP_RESTORED",
                category = "SYSTEM",
                targetType = "BACKUP",
                targetId = backupId,
                severity = "CRITICAL"
            )
        }
        return ResponseEntity.ok(mapOf("success" to success))
    }

    @DeleteMapping("/backups/{backupId}")
    fun deleteBackup(
        @PathVariable backupId: String,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val adminId = UUID.fromString(authentication.name)
        val success = service.deleteBackup(UUID.fromString(backupId))
        if (success) {
            service.recordAudit(
                adminId = adminId,
                adminUsername = authentication.principal.toString(),
                action = "BACKUP_DELETED",
                category = "SYSTEM",
                targetType = "BACKUP",
                targetId = backupId,
                severity = "WARNING"
            )
        }
        return ResponseEntity.ok(mapOf("success" to success))
    }
}
