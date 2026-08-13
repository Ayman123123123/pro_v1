package com.red.server.controllers

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.*

/**
 * وحدة التحكم في تحليل سجل المكالمات CDR — إحصائيات ورسوم بيانية.
 * 
 * Endpoints:
 * GET /api/admin/dinstar/cdr/analysis — بيانات التحليل التفصيلية
 */
@RestController
@RequestMapping("/api/admin/dinstar/cdr")
class CdrAnalysisController(
    private val jdbc: JdbcTemplate
) {

    @GetMapping("/analysis")
    fun analysis(): List<Map<String, Any?>> {
        // قراءة المكالمات من gateway_port_snapshots + gateway_route_decisions
        // ملاحظة: CDR الفعلي يأتي من البوابة عبر hardware.queryCdr()
        // هنا نعرض سجل قرارات التوجيه كمصدر أولي للتحليل
        return jdbc.query(
            """SELECT d.id, d.gateway_id, g.host gateway_host, d.port_index,
                      d.destination_prefix, d.matched_operator, d.score,
                      d.reason, d.outcome, d.created_at
               FROM gateway_route_decisions d
               LEFT JOIN telecom_gateways g ON g.id = d.gateway_id
               ORDER BY d.created_at DESC
               LIMIT 500"""
        ) { rs, _ ->
            mapOf(
                "id" to rs.getString("id"),
                "gatewayHost" to (rs.getString("gateway_host") ?: "—"),
                "portIndex" to rs.getInt("port_index"),
                "direction" to "OUTBOUND",
                "number" to (rs.getString("destination_prefix") ?: "—"),
                "startTime" to rs.getTimestamp("created_at")?.toInstant()?.toString(),
                "duration" to 0, // CDR الفعلي يُجلب من البوابة
                "status" to mapOutcomeToStatus(rs.getString("outcome")),
                "operator" to (rs.getString("matched_operator") ?: "—"),
                "score" to (rs.getObject("score") as? Double) ?: 0.0
            )
        }
    }

    @GetMapping("/summary")
    fun summary(): Map<String, Any> {
        val total = jdbc.queryForObject("SELECT COUNT(*) FROM gateway_route_decisions", Int::class.java) ?: 0
        val selected = jdbc.queryForObject("SELECT COUNT(*) FROM gateway_route_decisions WHERE outcome='SELECTED'", Int::class.java) ?: 0
        val rejectedNoSignal = jdbc.queryForObject("SELECT COUNT(*) FROM gateway_route_decisions WHERE outcome='REJECTED_NO_SIGNAL'", Int::class.java) ?: 0
        val rejectedBusy = jdbc.queryForObject("SELECT COUNT(*) FROM gateway_route_decisions WHERE outcome='REJECTED_BUSY'", Int::class.java) ?: 0
        val rejectedOffline = jdbc.queryForObject("SELECT COUNT(*) FROM gateway_route_decisions WHERE outcome='REJECTED_OFFLINE'", Int::class.java) ?: 0

        return mapOf(
            "total" to total,
            "selected" to selected,
            "rejectedNoSignal" to rejectedNoSignal,
            "rejectedBusy" to rejectedBusy,
            "rejectedOffline" to rejectedOffline,
            "selectionRate" to if (total > 0) Math.round(selected.toDouble() / total * 100) else 0
        )
    }

    private fun mapOutcomeToStatus(outcome: String?): String = when (outcome?.uppercase()) {
        "SELECTED" -> "COMPLETED"
        "REJECTED_NO_SIGNAL" -> "FAILED"
        "REJECTED_BUSY" -> "BUSY"
        "REJECTED_OFFLINE" -> "REJECTED"
        "FALLBACK" -> "COMPLETED"
        else -> "UNKNOWN"
    }
}
