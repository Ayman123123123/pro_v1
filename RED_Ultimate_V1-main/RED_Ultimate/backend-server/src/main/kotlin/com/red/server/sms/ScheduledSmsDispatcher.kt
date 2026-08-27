package com.red.server.sms

import com.fasterxml.jackson.databind.ObjectMapper
import com.red.server.services.DinstarApiContract
import com.red.server.services.DinstarHardwareService
import com.red.server.services.DinstarFleetService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * مُجدوِل الرسائل المجدولة.
 *
 * كان `POST /api/admin/dinstar/sms/schedule` يكتب في `scheduled_sms`
 * ويُعيد `PENDING`، لكن لا شيء كان يقرأ الجدول — فالرسائل المجدولة
 * تبقى معلّقة إلى الأبد. هذه الخدمة تفحص كل دقيقة ما حان وقتها،
 * تُرسلها عبر البوابة، وتُحدّث حالتها.
 *
 * الحالات: PENDING → SENT (أُرسلت للبوابة) أو FAILED أو CANCELLED.
 */
@Component
@ConditionalOnProperty(
    prefix = "red.dinstar",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class ScheduledSmsDispatcher(
    private val jdbc: JdbcTemplate,
    private val hardware: DinstarHardwareService,
    private val fleet: DinstarFleetService,
    private val mapper: ObjectMapper,
    @Value("\${red.dinstar.sms.scheduled-batch-size:10}") private val batchSize: Int
) {
    private val log = LoggerFactory.getLogger(ScheduledSmsDispatcher::class.java)

    @Scheduled(fixedDelayString = "\${red.dinstar.sms.scheduled-interval-ms:60000}", initialDelay = 60_000)
    fun dispatchDue() {
        val due = jdbc.query(
            """SELECT id, template_id, recipients_json, gateway_host, variables_json, scheduled_at
               FROM scheduled_sms
               WHERE status = 'PENDING' AND scheduled_at <= CURRENT_TIMESTAMP
               ORDER BY scheduled_at ASC LIMIT ?""",
            { rs, _ ->
                mapOf(
                    "id" to rs.getString("id"),
                    "templateId" to rs.getString("template_id"),
                    "recipients" to (rs.getString("recipients_json") ?: ""),
                    "gatewayHost" to rs.getString("gateway_host"),
                    "variables" to (rs.getString("variables_json") ?: "{}"),
                    "scheduledAt" to rs.getTimestamp("scheduled_at")?.toInstant()?.toString()
                )
            },
            batchSize
        )
        if (due.isEmpty()) return

        log.info("Scheduled SMS dispatcher: {} message(s) due", due.size)

        for (row in due) {
            val id = row["id"] as String
            try {
                dispatchOne(id, row)
            } catch (e: Exception) {
                log.warn("Scheduled SMS $id failed: {}", e.message)
                jdbc.update(
                    "UPDATE scheduled_sms SET status = 'FAILED', error_text = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    e.message?.take(300), id
                )
            }
        }
    }

    private fun dispatchOne(id: String, row: Map<String, Any?>) {
        val templateId = row["templateId"] as String
        val recipientsRaw = row["recipients"] as? String ?: ""
        val recipients = recipientsRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (recipients.isEmpty()) {
            jdbc.update("UPDATE scheduled_sms SET status = 'FAILED' WHERE id = ?", id)
            return
        }

        val template = jdbc.query(
            "SELECT text FROM sms_templates WHERE id = ?",
            { rs, _ -> rs.getString("text") },
            templateId
        ).firstOrNull()

        if (template.isNullOrBlank()) {
            jdbc.update("UPDATE scheduled_sms SET status = 'FAILED' WHERE id = ?", id)
            return
        }

        // تعويض المتغيرات {{var}} إن وجدت.
        val variablesJson = row["variables"] as? String ?: "{}"
        @Suppress("UNCHECKED_CAST")
        val variables: Map<String, String> = try {
            mapper.readValue(variablesJson, Map::class.java) as Map<String, String>
        } catch (_: Exception) { emptyMap() }

        var text: String = template ?: return
        variables.forEach { (k, v) -> if (k != null && v != null) text = text.replace("{{$k}}", v) }

        val gatewayHost = row["gatewayHost"] as? String

        // إرسال دفعي: كل مستلم بمعرّفه الخاص.
        val recipientsForApi = recipients.map { mapOf(DinstarApiContract.Sms.REQ_NUMBER to it) }
        val contract = com.red.server.services.DinstarSmsContract(jdbc)
        val prepared = contract.prepare(recipientsForApi)

        val response = hardware.sendSms(text, prepared.recipients, null, com.red.server.services.GsmAlphabet.detectEncoding(text), gatewayHost)
        val accepted = com.red.server.services.DinstarSmsContract.isAccepted(response)

        if (accepted) {
            jdbc.update(
                "UPDATE scheduled_sms SET status = 'SENT', sent_at = CURRENT_TIMESTAMP WHERE id = ?",
                id
            )
            log.info("Scheduled SMS $id sent to {} recipients", recipients.size)
        } else {
            jdbc.update(
                "UPDATE scheduled_sms SET status = 'FAILED' WHERE id = ?",
                id
            )
            log.warn("Scheduled SMS $id rejected: {}", com.red.server.services.DinstarApiContract.errorMessage(response))
        }
    }
}
