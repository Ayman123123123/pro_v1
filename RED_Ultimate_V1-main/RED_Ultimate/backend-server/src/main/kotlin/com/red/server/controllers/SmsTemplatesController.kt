package com.red.server.controllers

import com.red.server.audit.AuditService
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * وحدة التحكم في قوالب SMS — إدارة قوالب الرسائل الجاهزة للإرسال.
 * 
 * Endpoints:
 * GET    /api/admin/dinstar/sms/templates          — قائمة القوالب
 * POST   /api/admin/dinstar/sms/templates          — إنشاء قالب
 * PUT    /api/admin/dinstar/sms/templates/{id}     — تحديث قالب
 * DELETE /api/admin/dinstar/sms/templates/{id}     — حذف قالب
 * POST   /api/admin/dinstar/sms/schedule           — جدولة رسالة
 * GET    /api/admin/dinstar/sms/scheduled          — قائمة الرسائل المجدوَلَة
 */
@RestController
@RequestMapping("/api/admin/dinstar/sms")
class SmsTemplatesController(
    private val jdbc: JdbcTemplate,
    private val audit: AuditService
) {

    @GetMapping("/templates")
    fun listTemplates(): List<Map<String, Any?>> {
        return jdbc.query(
            """SELECT id, name, text, encoding, category, variables_json, usage_count, created_at, updated_at
               FROM sms_templates ORDER BY usage_count DESC, created_at DESC"""
        ) { rs, _ ->
            mapOf(
                "id" to rs.getString("id"),
                "name" to rs.getString("name"),
                "text" to rs.getString("text"),
                "encoding" to rs.getString("encoding"),
                "category" to rs.getString("category"),
                "variables" to parseVariables(rs.getString("variables_json")),
                "usageCount" to rs.getInt("usage_count"),
                "createdAt" to rs.getTimestamp("created_at")?.toInstant()?.toString()
            )
        }
    }

    @PostMapping("/templates")
    fun createTemplate(
        @RequestBody body: Map<String, Any?>,
        authentication: Authentication
    ): Map<String, Any> {
        val actorId = UUID.fromString(authentication.name)
        val id = UUID.randomUUID().toString()
        val name = (body["name"] as? String)?.trim() ?: throw IllegalArgumentException("name is required")
        val text = (body["text"] as? String) ?: throw IllegalArgumentException("text is required")
        val encoding = (body["encoding"] as? String) ?: "gsm-7bit"
        val category = (body["category"] as? String) ?: "custom"
        val variables = extractVariables(text)

        jdbc.update(
            """INSERT INTO sms_templates (id, name, text, encoding, category, variables_json, usage_count, created_at, updated_at)
               VALUES (?, ?, ?, ?, ?, ?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)""",
            id, name, text, encoding, category, variables.joinToString(",")
        )

        audit.record(actorId, "DINSTAR_SMS_TEMPLATE_CREATE", id, mapOf("name" to name, "category" to category))

        return mapOf("id" to id, "name" to name, "created" to true)
    }

    @PutMapping("/templates/{id}")
    fun updateTemplate(
        @PathVariable id: String,
        @RequestBody body: Map<String, Any?>,
        authentication: Authentication
    ): Map<String, Any> {
        val actorId = UUID.fromString(authentication.name)
        val name = (body["name"] as? String)?.trim() ?: throw IllegalArgumentException("name is required")
        val text = (body["text"] as? String) ?: throw IllegalArgumentException("text is required")
        val encoding = (body["encoding"] as? String) ?: "gsm-7bit"
        val category = (body["category"] as? String) ?: "custom"
        val variables = extractVariables(text)

        jdbc.update(
            """UPDATE sms_templates SET name=?, text=?, encoding=?, category=?, variables_json=?, updated_at=CURRENT_TIMESTAMP
               WHERE id=?""",
            name, text, encoding, category, variables.joinToString(","), id
        )

        audit.record(actorId, "DINSTAR_SMS_TEMPLATE_UPDATE", id, mapOf("name" to name))

        return mapOf("id" to id, "updated" to true)
    }

    @DeleteMapping("/templates/{id}")
    fun deleteTemplate(
        @PathVariable id: String,
        authentication: Authentication
    ): Map<String, Any> {
        val actorId = UUID.fromString(authentication.name)
        jdbc.update("DELETE FROM sms_templates WHERE id=?", id)
        audit.record(actorId, "DINSTAR_SMS_TEMPLATE_DELETE", id)
        return mapOf("id" to id, "deleted" to true)
    }

    @PostMapping("/schedule")
    fun scheduleSms(
        @RequestBody body: Map<String, Any?>,
        authentication: Authentication
    ): Map<String, Any> {
        val actorId = UUID.fromString(authentication.name)
        val id = UUID.randomUUID().toString()
        val templateId = (body["templateId"] as? String) ?: throw IllegalArgumentException("templateId is required")
        val scheduledAt = (body["scheduledAt"] as? String) ?: throw IllegalArgumentException("scheduledAt is required")
        @Suppress("UNCHECKED_CAST")
        val recipients = (body["recipients"] as? List<String>) ?: throw IllegalArgumentException("recipients is required")
        val gatewayHost = body["gatewayHost"] as? String

        jdbc.update(
            """INSERT INTO scheduled_sms (id, template_id, recipients_json, gateway_host, variables_json, scheduled_at, status, created_at)
               VALUES (?, ?, ?, ?, ?, ?, 'PENDING', CURRENT_TIMESTAMP)""",
            id, templateId, recipients.joinToString(","), gatewayHost, "{}", Timestamp.from(Instant.parse(scheduledAt))
        )

        audit.record(actorId, "DINSTAR_SMS_SCHEDULE", id, mapOf("templateId" to templateId, "recipientCount" to recipients.size))

        return mapOf("id" to id, "scheduled" to true, "scheduledAt" to scheduledAt)
    }

    @GetMapping("/scheduled")
    fun listScheduled(): List<Map<String, Any?>> {
        return jdbc.query(
            """SELECT s.id, s.template_id, t.name template_name, s.recipients_json, s.gateway_host,
                      s.scheduled_at, s.status, s.variables_json, s.created_at
               FROM scheduled_sms s
               LEFT JOIN sms_templates t ON t.id = s.template_id
               ORDER BY s.scheduled_at ASC"""
        ) { rs, _ ->
            mapOf(
                "id" to rs.getString("id"),
                "templateId" to rs.getString("template_id"),
                "templateName" to rs.getString("template_name") ?: "—",
                "recipients" to (rs.getString("recipients_json")?.split(",") ?: emptyList()),
                "gatewayHost" to rs.getString("gateway_host"),
                "scheduledAt" to rs.getTimestamp("scheduled_at")?.toInstant()?.toString(),
                "status" to rs.getString("status"),
                "createdAt" to rs.getTimestamp("created_at")?.toInstant()?.toString()
            )
        }
    }

    /** استخراج المتغيرات {{var}} من النص. */
    private fun extractVariables(text: String): List<String> {
        val regex = Regex("""\{\{(\w+)\}\}""")
        return regex.findAll(text).map { it.groupValues[1] }.distinct().toList()
    }

    private fun parseVariables(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return json.split(",").filter { it.isNotBlank() }
    }
}
