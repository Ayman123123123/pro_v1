package com.red.server.auth

import com.red.server.audit.AuditService
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

data class AdminReportResponse(
    val id: UUID,
    val reporterRedId: String,
    val reportedRedId: String?,
    val category: String,
    val details: String?,
    val status: String,
    val createdAt: Instant
)

@RestController
@RequestMapping("/api/admin/moderation/reports")
class ModerationController(private val jdbc: JdbcTemplate, private val audit: AuditService) {
    @GetMapping
    fun list(@RequestParam(defaultValue = "OPEN") status: String): List<AdminReportResponse> {
        val normalized = status.uppercase()
        require(normalized in STATUSES)
        return jdbc.query(
            """SELECT r.id,reporter.red_id reporter_red_id,reported.red_id reported_red_id,r.category,r.details,r.status,r.created_at
               FROM user_reports r JOIN users reporter ON reporter.id=r.reporter_id
               LEFT JOIN users reported ON reported.id=r.reported_id WHERE r.status=? ORDER BY r.created_at""",
            { rs, _ -> AdminReportResponse(rs.getObject("id", UUID::class.java), rs.getString("reporter_red_id"), rs.getString("reported_red_id"), rs.getString("category"), rs.getString("details"), rs.getString("status"), rs.getTimestamp("created_at").toInstant()) },
            normalized
        )
    }

    @PatchMapping("/{reportId}")
    fun update(@PathVariable reportId: UUID, @RequestParam status: String, authentication: Authentication): AdminReportResponse {
        val normalized = status.uppercase()
        require(normalized in STATUSES - "OPEN")
        val adminId = UUID.fromString(authentication.name)
        val changed = jdbc.update("UPDATE user_reports SET status=?,reviewed_at=CURRENT_TIMESTAMP,reviewed_by=? WHERE id=?", normalized, adminId, reportId)
        require(changed == 1) { "Report not found" }
        audit.record(adminId, "REPORT_$normalized", reportId.toString())
        return list(normalized).first { it.id == reportId }
    }

    companion object { private val STATUSES = setOf("OPEN", "REVIEWING", "RESOLVED", "DISMISSED") }
}
