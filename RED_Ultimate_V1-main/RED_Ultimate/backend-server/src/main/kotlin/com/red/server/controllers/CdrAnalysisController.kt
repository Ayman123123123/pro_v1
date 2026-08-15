package com.red.server.controllers

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Read-only analytics over the authoritative DINSTAR CDR table. */
@RestController
@RequestMapping("/api/admin/dinstar/cdr")
class CdrAnalysisController(
    private val jdbc: JdbcTemplate
) {
    @GetMapping("/analysis")
    fun analysis(): List<Map<String, Any?>> = jdbc.query(
        """SELECT c.id, c.gateway_id, g.host AS gateway_host, c.port_index,
                  c.direction, c.caller_number, c.callee_number, c.start_time,
                  c.duration_seconds, c.status, c.cost_yer
           FROM dinstar_cdr c
           LEFT JOIN telecom_gateways g ON g.id = c.gateway_id
           ORDER BY c.start_time DESC
           LIMIT 500"""
    ) { rs, _ ->
        val direction = rs.getString("direction")?.uppercase() ?: "UNKNOWN"
        val caller = rs.getString("caller_number").orEmpty()
        val callee = rs.getString("callee_number").orEmpty()
        val peerNumber = if (direction == "INBOUND") caller else callee
        mapOf(
            "id" to rs.getString("id"),
            "gatewayHost" to (rs.getString("gateway_host") ?: "—"),
            "portIndex" to rs.getInt("port_index"),
            "direction" to direction,
            "number" to peerNumber,
            "startTime" to rs.getTimestamp("start_time")?.toInstant()?.toString(),
            "duration" to rs.getInt("duration_seconds"),
            "status" to (rs.getString("status")?.uppercase() ?: "UNKNOWN"),
            "operator" to operatorFor(peerNumber),
            "cost" to rs.getBigDecimal("cost_yer")
        )
    }

    @GetMapping("/summary")
    fun summary(): Map<String, Any> = jdbc.queryForMap(
        """SELECT COUNT(*)::bigint AS total,
                  COUNT(*) FILTER (WHERE status = 'answered')::bigint AS answered,
                  COUNT(*) FILTER (WHERE status = 'no_answer')::bigint AS no_answer,
                  COUNT(*) FILTER (WHERE status = 'busy')::bigint AS busy,
                  COUNT(*) FILTER (WHERE status IN ('failed', 'cancelled'))::bigint AS failed,
                  COALESCE(SUM(duration_seconds), 0)::bigint AS duration_seconds,
                  COALESCE(SUM(cost_yer), 0) AS cost_yer
           FROM dinstar_cdr"""
    )

    private fun operatorFor(number: String): String {
        val local = number.filter(Char::isDigit)
            .removePrefix("00967")
            .removePrefix("967")
            .removePrefix("0")
        return when {
            local.startsWith("77") -> "Sabafon"
            local.startsWith("73") -> "Yemen Mobile"
            local.startsWith("71") -> "YOU"
            local.startsWith("70") -> "Y Telecom"
            local.startsWith("78") -> "Aden Net"
            else -> "غير معروف"
        }
    }
}
