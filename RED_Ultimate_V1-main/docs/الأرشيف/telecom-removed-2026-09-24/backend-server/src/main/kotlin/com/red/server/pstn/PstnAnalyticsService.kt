package com.red.server.pstn

import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * خدمة تحليلات PSTN
 *
 * توفر إحصائيات وتحليلات أداء نظام المكالمات.
 */
@Service
class PstnAnalyticsService(
    private val jdbc: JdbcTemplate
) {
    companion object {
        private val log = LoggerFactory.getLogger(PstnAnalyticsService::class.java)
    }

    /**
     * الحصول على ملخص PSTN لفترة زمنية
     */
    fun getPstnSummary(days: Int = 7): Map<String, Any> {
        val startDate = Instant.now().minus(days.toLong(), ChronoUnit.DAYS)

        return mapOf(
            "period" to "$days days",
            "startDate" to startDate.toString(),
            "endDate" to Instant.now().toString(),
            "stats" to getStatsForPeriod(startDate)
        )
    }

    /**
     * إحصائيات التوجيه
     */
    fun getRoutingStats(startDate: Instant, endDate: Instant): Map<String, Any> {
        val stats = jdbc.queryForList(
            """
                SELECT
                    outcome,
                    COUNT(*) as count,
                    AVG(score) as avg_score,
                    MIN(score) as min_score,
                    MAX(score) as max_score
                FROM gateway_route_decisions
                WHERE created_at BETWEEN ? AND ?
                GROUP BY outcome
                ORDER BY count DESC
            """,
            startDate, endDate
        )

        return mapOf(
            "period" to "${startDate.toEpochMilli()} - ${endDate.toEpochMilli()}",
            "decisions" to stats,
            "totalDecisions" to stats.sumOf { (it["count"] as Number).toInt() }
        )
    }

    /**
     * إحصائيات CDR
     */
    fun getCdrStats(days: Int = 7): Map<String, Any> {
        val startDate = Instant.now().minus(days.toLong(), ChronoUnit.DAYS)

        val stats = jdbc.queryForList(
            """
                SELECT
                    status,
                    direction,
                    COUNT(*) as count,
                    AVG(duration_seconds) as avg_duration,
                    AVG(ring_duration_seconds) as avg_ring_duration
                FROM dinstar_cdr
                WHERE start_time >= ?
                GROUP BY status, direction
                ORDER BY count DESC
            """,
            startDate
        )

        return mapOf(
            "periodDays" to days,
            "cdrStats" to stats,
            "totalCalls" to stats.sumOf { (it["count"] as Number).toInt() }
        )
    }

    /**
     * إحصائيات Gateway
     */
    fun getGatewayStats(): List<Map<String, Any?>> {
        return jdbc.queryForList(
            """
                SELECT
                    g.id,
                    g.name,
                    g.host,
                    g.model,
                    g.health_state,
                    g.consecutive_failures,
                    COUNT(DISTINCT c.id) as active_calls,
                    COUNT(DISTINCT r.id) as total_routes
                FROM telecom_gateways g
                LEFT JOIN pstn_active_calls c ON c.gateway_id = g.id AND c.expires_at > NOW()
                LEFT JOIN gateway_route_decisions r ON r.gateway_id = g.id
                GROUP BY g.id, g.name, g.host, g.model, g.health_state, g.consecutive_failures
                ORDER BY g.routing_priority
            """
        )
    }

    private fun getStatsForPeriod(startDate: Instant): Map<String, Any> {
        val activeCalls: Int = jdbc.queryForObject(
            "SELECT COUNT(*) FROM pstn_active_calls WHERE expires_at > NOW()",
            Int::class.java
        ) ?: 0
        val totalCalls: Int = jdbc.queryForObject(
            "SELECT COUNT(*) FROM dinstar_cdr WHERE start_time >= ?",
            Int::class.java,
            java.sql.Timestamp.from(startDate)
        ) ?: 0
        val successRate: Double = jdbc.queryForObject(
            """
                SELECT
                    CASE WHEN COUNT(*) = 0 THEN 0.0
                    ELSE COUNT(*) FILTER (WHERE status = 'answered')::float / COUNT(*)
                    END
                FROM dinstar_cdr
                WHERE start_time >= ?
            """,
            Double::class.java,
            java.sql.Timestamp.from(startDate)
        ) ?: 0.0
        return mapOf<String, Any>(
            "activeCalls" to activeCalls,
            "totalCalls" to totalCalls,
            "successRate" to successRate
        )
    }
}
