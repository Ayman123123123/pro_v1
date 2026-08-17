package com.red.server.admin.controller

import com.red.server.admin.model.*
import com.red.server.admin.repository.*
import com.red.server.admin.service.AdminService
import com.red.server.auth.RedApprovalService
import com.red.server.auth.model.AccountRole
import com.red.server.auth.model.AccountStatus
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.auth.repository.searchForAdmin
import com.red.server.auth.UserAccountResponse
import com.red.server.auth.toResponse
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

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
    private val approval: RedApprovalService,
    private val redis: RedisTemplate<String, String>
) {

    @GetMapping("/dashboard/summary")
    fun getDashboardSummary(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(runCatching {
            val analytics = service.calculateCurrentAnalytics()
            mapOf(
                "analytics" to analytics,
                "pendingReports" to service.countPendingReports(),
                "recentCriticalAlerts" to service.getRecentCritical().size,
                "generatedAt" to Instant.now()
            )
        }.getOrElse { mapOf("error" to (it.message ?: "Unknown error")) })
    }

    /** صحة النظام (آخر 5 دقائق) — عقد Dashboard.getSystemHealth */
    @GetMapping("/health")
    fun getHealth(): List<SystemHealth> = service.getRecentHealth()

    /** قياسات لحظية — عقد Dashboard.getRealtimeMetrics */
    @GetMapping("/metrics/realtime")
    fun realtime(): Map<String, Any> {
        val a = service.calculateCurrentAnalytics()
        val online = redis.opsForZSet()?.zCard("red:presence:index") ?: 0L
        val latestByComponent = health.findAll()
            .groupBy { it.component }
            .mapValues { (_, checks) -> checks.maxByOrNull { it.lastCheckAt }!! }
        return mapOf(
            "users" to mapOf(
                "total" to (a["totalUsers"] ?: 0),
                "pending" to (a["pendingUsers"] ?: 0),
                "approved" to (a["approvedUsers"] ?: 0),
                "banned" to (a["bannedUsers"] ?: 0),
                "online" to online
            ),
            "health" to latestByComponent,
            "timestamp" to Instant.now()
        )
    }

    /** سلاسل تحليلات يومية في مدى زمني (افتراضي: آخر 7 أيام) — عقد Dashboard.getSystemAnalytics */
    @GetMapping("/analytics")
    fun analytics(
        @RequestParam(required = false) start: LocalDate? = null,
        @RequestParam(required = false) end: LocalDate? = null
    ): List<SystemAnalytics> {
        val to = end ?: LocalDate.now()
        val from = start ?: to.minusDays(6)
        return analytics.findByStatDateBetweenOrderByStatDateDesc(from, to).sortedBy { it.statDate }
    }

    // ━━━━━━ Admin Sessions ━━━━━━
    @GetMapping("/sessions")
    fun sessions(): List<AdminSession> = service.getAllActiveSessions()

    @PostMapping("/sessions/{sessionId}/terminate")
    fun terminateSession(@PathVariable sessionId: UUID, @RequestBody body: Map<String, String>): ResponseEntity<Map<String, Any>> {
        service.terminateSession(sessionId, body["reason"] ?: "MANUAL")
        return ResponseEntity.ok(mapOf("terminated" to true))
    }

    // ━━━━━━ Announcements ━━━━━━
    @GetMapping("/announcements")
    fun announcements(@RequestParam(required = false) published: Boolean?): List<SystemAnnouncement> =
        service.getAnnouncements(published)

    @PostMapping("/announcements")
    fun createAnnouncement(@RequestBody body: Map<String, Any>, authentication: Authentication): ResponseEntity<Any> {
        val adminId = UUID.fromString(authentication.name)
        val showFrom = (body["showFrom"] as? String)?.let { Instant.parse(it) } ?: Instant.now()
        val showUntil = (body["showUntil"] as? String)?.let { Instant.parse(it) }
        val created = service.createAnnouncement(
            title = requireNotNull(body["title"] as? String) { "title required" },
            body = requireNotNull(body["body"] as? String) { "body required" },
            type = body["type"] as? String ?: "INFO",
            targetAudience = body["targetAudience"] as? String ?: "ALL",
            priority = (body["priority"] as? Number)?.toInt() ?: 0,
            isDismissible = body["isDismissible"] as? Boolean ?: true,
            adminId = adminId,
            showFrom = showFrom,
            showUntil = showUntil
        )
        return ResponseEntity.ok(created)
    }

    @PostMapping("/announcements/{id}/publish")
    fun publishAnnouncement(@PathVariable id: UUID, authentication: Authentication): ResponseEntity<Any> =
        service.publishAnnouncement(id, UUID.fromString(authentication.name))
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()

    @DeleteMapping("/announcements/{id}")
    fun deleteAnnouncement(@PathVariable id: UUID): ResponseEntity<Map<String, Any>> =
        if (service.deleteAnnouncement(id)) ResponseEntity.ok(mapOf("deleted" to true))
        else ResponseEntity.notFound().build()

    // ━━━━━━ Backups ━━━━━━
    @GetMapping("/backups")
    fun backups(@RequestParam(defaultValue = "0") page: Int, @RequestParam(defaultValue = "20") size: Int): ResponseEntity<Map<String, Any>> {
        val safeSize = size.coerceIn(1, 100)
        val paged = service.getBackups(PageRequest.of(page, safeSize))
        return ResponseEntity.ok(mapOf(
            "content" to paged.content,
            "page" to paged.number,
            "size" to paged.size,
            "totalElements" to paged.totalElements,
            "totalPages" to paged.totalPages
        ))
    }

    @PostMapping("/backups")
    fun startBackup(@RequestBody body: Map<String, String>): ResponseEntity<Map<String, Any>> =
        ResponseEntity.status(501).body(mapOf(
            "error" to "BACKUP_OPERATOR_WORKFLOW_REQUIRED",
            "message" to "Backups run on the Docker host via scripts/backup-platform.sh; the web process never holds the Docker socket."
        ))

    @PostMapping("/backups/{backupId}/restore")
    fun restoreBackup(@PathVariable backupId: UUID, @RequestBody body: Map<String, String>): ResponseEntity<Map<String, Any>> =
        ResponseEntity.status(501).body(mapOf(
            "error" to "RESTORE_OPERATOR_WORKFLOW_REQUIRED",
            "message" to "Restores run on the Docker host via scripts/restore-platform.sh."
        ))

    @DeleteMapping("/backups/{backupId}")
    fun deleteBackup(@PathVariable backupId: UUID): ResponseEntity<Map<String, Any>> =
        if (service.deleteBackup(backupId)) ResponseEntity.ok(mapOf("deleted" to true))
        else ResponseEntity.notFound().build()

    // ━━━━━━ Feature Flags ━━━━━━
    @GetMapping("/feature-flags")
    fun featureFlags(): List<FeatureFlag> = service.getFeatureFlags()

    @PutMapping("/feature-flags/{name}")
    fun updateFeatureFlag(@PathVariable name: String, @RequestBody body: Map<String, Any>, authentication: Authentication): ResponseEntity<Any> {
        val updated = service.updateFeatureFlag(name, UUID.fromString(authentication.name), body)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(updated)
    }

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
        val safeSize = size.coerceIn(1, 100)
        val safeSort = sortBy?.takeIf { it in setOf("createdAt", "updatedAt", "username", "displayName", "redId", "status") } ?: "createdAt"
        val direction = if (sortDir?.lowercase() == "asc") Sort.Direction.ASC else Sort.Direction.DESC
        val pageable = PageRequest.of(page, safeSize, direction, safeSort)

        val parsedStatus = status?.trim()?.takeIf { it.isNotEmpty() }?.let {
            runCatching { AccountStatus.valueOf(it.uppercase()) }.getOrNull()
        }
        val parsedRole = role?.trim()?.takeIf { it.isNotEmpty() }?.let {
            runCatching { AccountRole.valueOf(it.uppercase()) }.getOrNull()
        }
        val normalizedSearch = search?.trim()?.takeIf { it.length >= 2 }

        val allUsers = users.searchForAdmin(parsedStatus, parsedRole, normalizedSearch, pageable)

        val dtoContent = allUsers.content.map { it.toResponse(emptyList()) }

        return ResponseEntity.ok(mapOf(
            "content" to dtoContent,
            "page" to page,
            "size" to size,
            "totalElements" to allUsers.totalElements,
            "totalPages" to allUsers.totalPages
        ))
    }

    @PostMapping("/users/{userId}/approve")
    fun approveUser(@PathVariable userId: String, authentication: Authentication): ResponseEntity<*> {
        val updated = approval.processAction(UUID.fromString(userId), AccountStatus.APPROVED, null, UUID.fromString(authentication.name))
        return ResponseEntity.ok(updated)
    }

    @PostMapping("/users/{userId}/ban")
    fun banUser(@PathVariable userId: String, @RequestBody body: Map<String, String>, authentication: Authentication): ResponseEntity<*> {
        val updated = approval.processAction(UUID.fromString(userId), AccountStatus.BANNED, body["reason"], UUID.fromString(authentication.name))
        return ResponseEntity.ok(updated)
    }

    // ━━━━━━ Audit Log ━━━━━━
    @GetMapping("/audit")
    fun getAuditLog(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam(required = false) adminId: String?,
        @RequestParam(required = false) action: String?,
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) severity: String?,
        @RequestParam(required = false) startDate: String?,
        @RequestParam(required = false) endDate: String?
    ): ResponseEntity<Map<String, Any>> {
        val safeSize = size.coerceIn(1, 100)
        val pageable = PageRequest.of(page, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))

        val spec = org.springframework.data.jpa.domain.Specification<com.red.server.admin.model.AdminAuditLog> { root, _, cb ->
            val predicates = mutableListOf<jakarta.persistence.criteria.Predicate>()
            adminId?.trim()?.takeIf { it.isNotEmpty() }?.let {
                runCatching { UUID.fromString(it) }.getOrNull()?.let { uid ->
                    predicates.add(cb.equal(root.get<UUID>("adminId"), uid))
                }
            }
            action?.trim()?.takeIf { it.isNotEmpty() }?.let {
                predicates.add(cb.equal(root.get<String>("action"), it))
            }
            category?.trim()?.takeIf { it.isNotEmpty() }?.let {
                predicates.add(cb.equal(root.get<String>("category"), it))
            }
            severity?.trim()?.takeIf { it.isNotEmpty() }?.let {
                predicates.add(cb.equal(root.get<String>("severity"), it))
            }
            startDate?.trim()?.takeIf { it.isNotEmpty() }?.let {
                runCatching { Instant.parse(it) }.getOrNull()?.let { s ->
                    predicates.add(cb.greaterThanOrEqualTo(root.get<Instant>("createdAt"), s))
                }
            }
            endDate?.trim()?.takeIf { it.isNotEmpty() }?.let {
                runCatching { Instant.parse(it) }.getOrNull()?.let { e ->
                    predicates.add(cb.lessThanOrEqualTo(root.get<Instant>("createdAt"), e))
                }
            }
            if (predicates.isEmpty()) null else cb.and(*predicates.toTypedArray())
        }
        val paged = auditLog.findAll(spec, pageable)

        return ResponseEntity.ok(mapOf(
            "content" to paged.content,
            "page" to page,
            "size" to safeSize,
            "totalElements" to paged.totalElements,
            "totalPages" to paged.totalPages
        ))
    }

    @GetMapping("/security/alerts")
    fun getSecurityAlerts(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam(required = false) severity: String?
    ): ResponseEntity<Map<String, Any>> {
        val safeSize = size.coerceIn(1, 100)
        val pageable = PageRequest.of(page, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))

        val paged = if (severity?.trim()?.isNotEmpty() == true) {
            auditLog.findBySeverityOrderByCreatedAtDesc(severity, pageable)
        } else {
            auditLog.findAll(pageable)
        }

        return ResponseEntity.ok(mapOf(
            "content" to paged.content,
            "page" to page,
            "size" to safeSize,
            "totalElements" to paged.totalElements,
            "totalPages" to paged.totalPages
        ))
    }

    // ━━━━━━ User Reports ━━━━━━
    @GetMapping("/reports")
    fun getReports(
        @RequestParam(required = false) page: Int = 0,
        @RequestParam(required = false) size: Int = 50,
        @RequestParam(required = false) status: String? = null
    ): ResponseEntity<Map<String, Any>> {
        val safeSize = size.coerceIn(1, 100)
        val paged = service.getReports(status, PageRequest.of(page, safeSize))
        return ResponseEntity.ok(mapOf(
            "content" to paged.content,
            "page" to paged.number,
            "size" to paged.size,
            "totalElements" to paged.totalElements,
            "totalPages" to paged.totalPages
        ))
    }

    @PostMapping("/reports/{reportId}/resolve")
    fun resolveReport(
        @PathVariable reportId: UUID,
        @RequestBody body: Map<String, String>,
        authentication: Authentication
    ): ResponseEntity<*> {
        val resolution = body["resolution"] ?: return ResponseEntity.badRequest().body(mapOf("error" to "Resolution required"))
        val adminId = UUID.fromString(authentication.name)
        val updated = service.resolveReport(reportId, adminId, resolution, body["notes"])
        return if (updated != null) ResponseEntity.ok(updated) else ResponseEntity.notFound().build<Any>()
    }

    @PostMapping("/reports/{reportId}/dismiss")
    fun dismissReport(
        @PathVariable reportId: UUID,
        @RequestBody body: Map<String, String>,
        authentication: Authentication
    ): ResponseEntity<*> {
        val adminId = UUID.fromString(authentication.name)
        val updated = service.dismissReport(reportId, adminId, body["notes"])
        return if (updated != null) ResponseEntity.ok(updated) else ResponseEntity.notFound().build<Any>()
    }

    @PostMapping("/reports/{reportId}/assign")
    fun assignReport(
        @PathVariable reportId: UUID,
        authentication: Authentication
    ): ResponseEntity<*> {
        val adminId = UUID.fromString(authentication.name)
        val updated = service.assignReport(reportId, adminId)
        return if (updated != null) ResponseEntity.ok(updated) else ResponseEntity.notFound().build<Any>()
    }
}
