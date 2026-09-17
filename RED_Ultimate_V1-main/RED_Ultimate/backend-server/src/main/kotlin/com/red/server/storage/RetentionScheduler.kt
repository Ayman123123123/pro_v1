package com.red.server.storage

import com.red.server.calls.CallTelemetryRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Bounded, observable data lifecycle cleanup. This is safer than one massive
 * DELETE: each run removes a capped batch, preserving database responsiveness.
 * Legal-hold exports must be performed before changing retention values.
 *
 * Config source of truth: application.yml `red.retention.*` (see `red.retention`
 * block). Legacy `system_settings` keys (`retention.*_days`) are deprecated —
 * COMMENT only, do not read them here and do not change production values.
 */
@Component
class RetentionScheduler(
    private val jdbc: JdbcTemplate,
    private val telemetry: CallTelemetryRepository,
    @Value("\${red.retention.audit-days:365}") private val auditDays: Long,
    @Value("\${red.retention.health-days:90}") private val healthDays: Long,
    @Value("\${red.retention.telemetry-days:90}") private val telemetryDays: Long,
    @Value("\${red.retention.notifications-days:90}") private val notificationsDays: Long,
    @Value("\${red.retention.call-history-days:365}") private val callHistoryDays: Long,
    @Value("\${red.retention.backup-days:180}") private val backupDays: Long,
    @Value("\${red.retention.batch-size:10000}") private val batchSize: Int
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 25 3 * * *", zone = "UTC")
    @Transactional
    fun pruneExpiredOperationalData() {
        val safeBatch = batchSize.coerceIn(100, 50_000)
        val audit = deleteBatch("admin_audit_log", "created_at", Instant.now().minus(auditDays.coerceAtLeast(30), ChronoUnit.DAYS), safeBatch)
        val health = deleteBatch("system_health", "last_check_at", Instant.now().minus(healthDays.coerceAtLeast(7), ChronoUnit.DAYS), safeBatch)
        val telemetryDeleted = telemetry.deleteByReceivedAtBefore(Instant.now().minus(telemetryDays.coerceAtLeast(7), ChronoUnit.DAYS))
        val notifications = deleteBatch("user_notifications", "created_at", Instant.now().minus(notificationsDays.coerceAtLeast(7), ChronoUnit.DAYS), safeBatch)
        val callHistory = deleteBatch("call_history", "started_at", Instant.now().minus(callHistoryDays.coerceAtLeast(30), ChronoUnit.DAYS), safeBatch)
        val backups = deleteBatch("backup_history", "started_at", Instant.now().minus(backupDays.coerceAtLeast(30), ChronoUnit.DAYS), safeBatch)
        log.info(
            "Retention completed: adminAudit={}, health={}, telemetry={}, notifications={}, callHistory={}, backups={}",
            audit, health, telemetryDeleted, notifications, callHistory, backups
        )
    }

    /**
     * حذف مُقيَّد بدفعة.
     *
     * `Timestamp` لا `Instant`: مُشغِّل PostgreSQL لا يستنتج نوع SQL لـ
     * `java.time.Instant` فيرمي «Can't infer the SQL type…» عند التنفيذ.
     * الجملة تُصرَّف بلا شكوى، ويسقط التنظيف كاملًا في أول تشغيل مجدول —
     * صامتًا لأن المُجدوِل يبتلع الاستثناء.
     */
    private fun deleteBatch(table: String, column: String, cutoff: Instant, limit: Int): Int = jdbc.update(
        """DELETE FROM $table WHERE id IN (
              SELECT id FROM $table WHERE $column < ? ORDER BY $column ASC LIMIT ?
            )""",
        java.sql.Timestamp.from(cutoff), limit
    )
}
