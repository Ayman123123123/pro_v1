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
 */
@Component
class RetentionScheduler(
    private val jdbc: JdbcTemplate,
    private val telemetry: CallTelemetryRepository,
    @Value("\${red.retention.audit-days:365}") private val auditDays: Long,
    @Value("\${red.retention.cdr-days:365}") private val cdrDays: Long,
    @Value("\${red.retention.health-days:90}") private val healthDays: Long,
    @Value("\${red.retention.telemetry-days:90}") private val telemetryDays: Long,
    @Value("\${red.retention.batch-size:10000}") private val batchSize: Int
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 25 3 * * *", zone = "UTC")
    @Transactional
    fun pruneExpiredOperationalData() {
        val safeBatch = batchSize.coerceIn(100, 50_000)
        val audit = deleteBatch("admin_audit_log", "created_at", Instant.now().minus(auditDays.coerceAtLeast(30), ChronoUnit.DAYS), safeBatch)
        val cdr = deleteBatch("dinstar_cdr", "start_time", Instant.now().minus(cdrDays.coerceAtLeast(30), ChronoUnit.DAYS), safeBatch)
        val health = deleteBatch("system_health", "last_check_at", Instant.now().minus(healthDays.coerceAtLeast(7), ChronoUnit.DAYS), safeBatch)
        val telemetryDeleted = telemetry.deleteByReceivedAtBefore(Instant.now().minus(telemetryDays.coerceAtLeast(7), ChronoUnit.DAYS))
        log.info("Retention completed: adminAudit={}, dinstarCdr={}, health={}, telemetry={}", audit, cdr, health, telemetryDeleted)
    }

    private fun deleteBatch(table: String, column: String, cutoff: Instant, limit: Int): Int = jdbc.update(
        """DELETE FROM $table WHERE id IN (
              SELECT id FROM $table WHERE $column < ? ORDER BY $column ASC LIMIT ?
            )""", cutoff, limit
    )
}
