package com.red.server.admin

import com.fasterxml.jackson.databind.ObjectMapper
import com.red.server.admin.model.*
import com.red.server.admin.repository.*
import com.red.server.admin.service.AdminService
import com.red.server.auth.model.AccountRole
import com.red.server.auth.model.AccountStatus
import com.red.server.auth.model.UserAccount
import com.red.server.auth.repository.UserAccountRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.time.Instant
import java.util.UUID

/**
 * اختبارات Admin Service
 */
class AdminServiceTest {
    private lateinit var auditLog: AdminAuditLogRepository
    private lateinit var analytics: SystemAnalyticsRepository
    private lateinit var health: SystemHealthRepository
    private lateinit var adminSessions: AdminSessionRepository
    private lateinit var featureFlags: FeatureFlagRepository
    private lateinit var userReports: UserReportRepository
    private lateinit var announcements: SystemAnnouncementRepository
    private lateinit var backups: BackupHistoryRepository
    private lateinit var users: UserAccountRepository
    private lateinit var json: ObjectMapper
    private lateinit var service: AdminService

    @BeforeEach
    fun setup() {
        auditLog = mock()
        analytics = mock()
        health = mock()
        adminSessions = mock()
        featureFlags = mock()
        userReports = mock()
        announcements = mock()
        backups = mock()
        users = mock()
        json = ObjectMapper()
        service = AdminService(
            auditLog, analytics, health, adminSessions, featureFlags,
            userReports, announcements, backups, users, json
        )
    }

    // ━━━━━━━━━━━━━━━━ Audit Log Tests ━━━━━━━━━━━━━━━━

    @Test
    fun `recordAudit saves audit log entry`() {
        val adminId = UUID.randomUUID()
        service.recordAudit(
            adminId = adminId,
            adminUsername = "admin",
            action = "USER_APPROVED",
            category = "USER",
            targetType = "USER",
            targetId = "user-123",
            description = "Test"
        )
        verify(auditLog).save(any())
    }

    @Test
    fun `getRecentCritical queries recent critical entries`() {
        whenever(auditLog.findRecentCritical(any())).thenReturn(emptyList())
        val result = service.getRecentCritical()
        assertNotNull(result)
        assertEquals(0, result.size)
    }

    // ━━━━━━━━━━━━━━━━ Analytics Tests ━━━━━━━━━━━━━━━━

    @Test
    fun `calculateCurrentAnalytics returns correct counts`() {
        whenever(users.count()).thenReturn(100L)
        whenever(users.findAllByStatusOrderByCreatedAtAsc(AccountStatus.PENDING)).thenReturn(listOf(mock(), mock()))
        whenever(users.findAllByStatusOrderByCreatedAtAsc(AccountStatus.BANNED)).thenReturn(listOf(mock()))
        whenever(users.findAllByStatusOrderByCreatedAtAsc(AccountStatus.APPROVED)).thenReturn(List(95) { mock() })
        whenever(users.findAll()).thenReturn(List(100) { createMockUser(it) })

        val result = service.calculateCurrentAnalytics()
        assertEquals(100L, result["totalUsers"])
        assertEquals(95L, result["approvedUsers"])
        assertEquals(2L, result["pendingUsers"])
        assertEquals(1L, result["bannedUsers"])
    }

    private fun createMockUser(idx: Int): UserAccount {
        val user = mock<UserAccount>()
        whenever(user.createdAt).thenReturn(Instant.now().minusSeconds(idx.toLong() * 3600))
        return user
    }

    // ━━━━━━━━━━━━━━━━ Health Tests ━━━━━━━━━━━━━━━━

    @Test
    fun `recordHealth saves health entry`() {
        service.recordHealth(
            component = "DATABASE",
            status = "HEALTHY",
            cpuUsage = 25.0f,
            memoryUsage = 50.0f
        )
        verify(health).save(any())
    }

    // ━━━━━━━━━━━━━━━━ Sessions Tests ━━━━━━━━━━━━━━━━

    @Test
    fun `terminateSession marks session inactive`() {
        val sessionId = UUID.randomUUID()
        val session = AdminSession(id = sessionId, isActive = true)
        whenever(adminSessions.findById(sessionId)).thenReturn(Optional.of(session))

        service.terminateSession(sessionId, "SECURITY")
        assertFalse(session.isActive)
        assertEquals("SECURITY", session.terminationReason)
        verify(adminSessions).save(session)
    }

    @Test
    fun `cleanupExpiredSessions processes all expired`() {
        val expired = listOf(
            AdminSession(isActive = true),
            AdminSession(isActive = true)
        )
        whenever(adminSessions.findByIsActiveAndExpiresAtBefore(eq(true), any())).thenReturn(expired)
        whenever(adminSessions.saveAll(any<List<AdminSession>>())).thenReturn(expired)

        val count = service.cleanupExpiredSessions()
        assertEquals(2, count)
        expired.forEach {
            assertFalse(it.isActive)
            assertEquals("EXPIRED", it.terminationReason)
        }
    }

    // ━━━━━━━━━━━━━━━━ Feature Flags Tests ━━━━━━━━━━━━━━━━

    @Test
    fun `isFeatureEnabled returns false for non-existent flag`() {
        whenever(featureFlags.findByFlagName("UNKNOWN")).thenReturn(null)
        val result = service.isFeatureEnabled("UNKNOWN")
        assertFalse(result)
    }

    @Test
    fun `isFeatureEnabled returns true for enabled flag at 100 percent`() {
        val flag = FeatureFlag(flagName = "TEST", enabled = true, rolloutPercentage = 100)
        whenever(featureFlags.findByFlagName("TEST")).thenReturn(flag)
        assertTrue(service.isFeatureEnabled("TEST"))
    }

    @Test
    fun `isFeatureEnabled returns false for disabled flag`() {
        val flag = FeatureFlag(flagName = "TEST", enabled = false, rolloutPercentage = 100)
        whenever(featureFlags.findByFlagName("TEST")).thenReturn(flag)
        assertFalse(service.isFeatureEnabled("TEST"))
    }

    @Test
    fun `isFeatureEnabled returns false for expired flag`() {
        val flag = FeatureFlag(flagName = "TEST", enabled = true, rolloutPercentage = 100,
            expiresAt = Instant.now().minusSeconds(3600))
        whenever(featureFlags.findByFlagName("TEST")).thenReturn(flag)
        assertFalse(service.isFeatureEnabled("TEST"))
    }

    // ━━━━━━━━━━━━━━━━ Reports Tests ━━━━━━━━━━━━━━━━

    @Test
    fun `resolveReport sets status and timestamp`() {
        val reportId = UUID.randomUUID()
        val adminId = UUID.randomUUID()
        val report = UserReport(id = reportId, status = "PENDING")
        whenever(userReports.findById(reportId)).thenReturn(Optional.of(report))

        val result = service.resolveReport(reportId, adminId, "WARNING_ISSUED", "Test note")
        assertNotNull(result)
        assertEquals("RESOLVED", result!!.status)
        assertEquals("WARNING_ISSUED", result.resolution)
        assertEquals("Test note", result.adminNotes)
        assertNotNull(result.resolvedAt)
    }

    @Test
    fun `dismissReport sets status to DISMISSED`() {
        val reportId = UUID.randomUUID()
        val adminId = UUID.randomUUID()
        val report = UserReport(id = reportId, status = "PENDING")
        whenever(userReports.findById(reportId)).thenReturn(Optional.of(report))

        val result = service.dismissReport(reportId, adminId, "False alarm")
        assertNotNull(result)
        assertEquals("DISMISSED", result!!.status)
    }

    @Test
    fun `countPendingReports returns correct count`() {
        whenever(userReports.countPending()).thenReturn(42L)
        assertEquals(42L, service.countPendingReports())
    }

    // ━━━━━━━━━━━━━━━━ Announcements Tests ━━━━━━━━━━━━━━━━

    @Test
    fun `createAnnouncement saves new announcement`() {
        val adminId = UUID.randomUUID()
        val saved = service.createAnnouncement(
            title = "Test", body = "Test body", type = "INFO",
            targetAudience = "ALL", priority = 1, isDismissible = true,
            adminId = adminId
        )
        assertNotNull(saved.id)
        assertEquals("Test", saved.title)
        assertFalse(saved.isPublished)
    }

    @Test
    fun `publishAnnouncement marks as published`() {
        val id = UUID.randomUUID()
        val adminId = UUID.randomUUID()
        val ann = SystemAnnouncement(id = id, title = "Test")
        whenever(announcements.findById(id)).thenReturn(Optional.of(ann))

        val result = service.publishAnnouncement(id, adminId)
        assertNotNull(result)
        assertTrue(result!!.isPublished)
        assertNotNull(result.publishedAt)
    }

    @Test
    fun `deleteAnnouncement returns true for existing`() {
        val id = UUID.randomUUID()
        whenever(announcements.existsById(id)).thenReturn(true)
        assertTrue(service.deleteAnnouncement(id))
        verify(announcements).deleteById(id)
    }

    @Test
    fun `deleteAnnouncement returns false for non-existing`() {
        val id = UUID.randomUUID()
        whenever(announcements.existsById(id)).thenReturn(false)
        assertFalse(service.deleteAnnouncement(id))
    }

    // ━━━━━━━━━━━━━━━━ Backups Tests ━━━━━━━━━━━━━━━━

    @Test
    fun `web service refuses to pretend it performed a host backup or restore`() {
        val error = assertThrows<UnsupportedOperationException> {
            service.startBackup("FULL", UUID.randomUUID(), "Test")
        }
        assertEquals("BACKUP_OPERATOR_WORKFLOW_REQUIRED", error.message)
        assertThrows<UnsupportedOperationException> {
            service.completeBackup(UUID.randomUUID(), 1024L, "abc123")
        }
        val restore = assertThrows<UnsupportedOperationException> {
            service.restoreBackup(UUID.randomUUID(), "RESTORE_CONFIRM")
        }
        assertEquals("RESTORE_OPERATOR_WORKFLOW_REQUIRED", restore.message)
    }

}
