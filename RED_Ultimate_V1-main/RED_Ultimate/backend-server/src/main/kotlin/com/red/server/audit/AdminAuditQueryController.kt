package com.red.server.audit

import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.ZoneOffset

/**
 * استعلامات سجل التدقيق للوحة الإدارة — تُغذي `AuditLog.tsx` مباشرة.
 *
 * كانت اللوحة تستدعي `GET /api/admin/audit` و`/api/admin/security/alerts`
 * و`/api/admin/audit/export` وهي غير موجودة (404 دائم + صفحة تدقيق ميتة)،
 * بينما البيانات حية في جدول `admin_audit_log`. هذا المتحكم يقرأ نفس الجدول
 * (read-only، JdbcTemplate) بشكل صفحات (`content/totalElements/...`) يفهمه
 * `asPage()` في الواجهة. المسارات تحت `/api/admin/` = صلاحية ADMIN أصلاً.
 */
@RestController
@RequestMapping("/api/admin")
class AdminAuditQueryController(private val jdbc: JdbcTemplate) {

    @GetMapping("/audit")
    fun page(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) adminId: String?,
        @RequestParam(required = false) severity: String?
    ): Map<String, Any?> = queryPage(page, size, category, adminId, severity)

    @GetMapping("/security/alerts")
    fun alerts(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) severity: String?
    ): Map<String, Any?> = queryPage(page, size, null, null, severity)

    @GetMapping("/audit/export", produces = ["text/csv"])
    fun export(
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) adminId: String?
    ): ResponseEntity<String> {
        val rows = queryRows(category, adminId, null, 0, 5000)
        val sb = StringBuilder("id,created_at,admin_username,admin_id,action,category,severity,target_type,target_id,ip_address,description\n")
        rows.forEach { r ->
            sb.append(csvRow(r)).append('\n')
        }
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-export.csv\"")
            .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
            .body("\uFEFF" + sb.toString())
    }

    private fun queryPage(page: Int, size: Int, category: String?, adminId: String?, severity: String?): Map<String, Any?> {
        val safePage = page.coerceAtLeast(0)
        val safeSize = size.coerceIn(1, 200)
        val where = StringBuilder("WHERE 1=1")
        val args = mutableListOf<Any?>()
        if (!category.isNullOrBlank()) {
            where.append(" AND category = ?")
            args.add(category.trim().uppercase())
        }
        if (!adminId.isNullOrBlank()) {
            where.append(" AND (admin_id::text = ? OR admin_username ILIKE ?)")
            args.add(adminId.trim())
            args.add("%${adminId.trim()}%")
        }
        if (!severity.isNullOrBlank()) {
            where.append(" AND severity = ?")
            args.add(severity.trim().uppercase())
        }
        val total = jdbc.queryForObject("SELECT COUNT(*) FROM admin_audit_log $where", Long::class.java, *args.toTypedArray()) ?: 0L
        val rows = queryRows(category, adminId, severity, safePage * safeSize, safeSize)
        val totalPages = if (total == 0L) 0 else ((total + safeSize - 1) / safeSize).toInt()
        return mapOf(
            "content" to rows,
            "totalElements" to total,
            "totalPages" to totalPages,
            "number" to safePage,
            "size" to safeSize
        )
    }

    private fun queryRows(category: String?, adminId: String?, severity: String?, offset: Int, limit: Int): List<Map<String, Any?>> {
        val where = StringBuilder("WHERE 1=1")
        val args = mutableListOf<Any?>()
        if (!category.isNullOrBlank()) {
            where.append(" AND category = ?")
            args.add(category.trim().uppercase())
        }
        if (!adminId.isNullOrBlank()) {
            where.append(" AND (admin_id::text = ? OR admin_username ILIKE ?)")
            args.add(adminId.trim())
            args.add("%${adminId.trim()}%")
        }
        if (!severity.isNullOrBlank()) {
            where.append(" AND severity = ?")
            args.add(severity.trim().uppercase())
        }
        args.add(limit)
        args.add(offset)
        return jdbc.queryForList(
            """SELECT id, created_at, admin_id, admin_username, action, category,
               target_type, target_id, description, metadata, ip_address, user_agent,
               severity, target_red_id
               FROM admin_audit_log $where
               ORDER BY created_at DESC LIMIT ? OFFSET ?""",
            *args.toTypedArray()
        ).map { r ->
            mapOf(
                "id" to r["id"]?.toString(),
                "createdAt" to (r["created_at"] as? java.sql.Timestamp)?.toInstant()?.atOffset(ZoneOffset.UTC)?.toString(),
                "adminId" to (r["admin_id"]?.toString() ?: r["admin_username"]?.toString()),
                "adminUsername" to (r["admin_username"]?.toString() ?: r["admin_id"]?.toString() ?: "system"),
                "action" to r["action"]?.toString(),
                "category" to r["category"]?.toString(),
                "targetType" to r["target_type"]?.toString(),
                "targetId" to r["target_id"]?.toString(),
                "description" to r["description"]?.toString(),
                "metadata" to r["metadata"]?.toString(),
                "ipAddress" to r["ip_address"]?.toString(),
                "userAgent" to r["user_agent"]?.toString(),
                "severity" to (r["severity"]?.toString() ?: "INFO"),
                "targetRedId" to r["target_red_id"]?.toString()
            )
        }
    }

    private fun csvRow(r: Map<String, Any?>): String {
        fun esc(v: Any?): String {
            val s = (v?.toString() ?: "").replace("\"", "\"\"")
            return "\"$s\""
        }
        return listOf(
            r["id"], r["createdAt"], r["adminUsername"], r["adminId"], r["action"],
            r["category"], r["severity"], r["targetType"], r["targetId"],
            r["ipAddress"], r["description"]
        ).joinToString(",") { esc(it) }
    }
}
