package com.red.server.pstn

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * خدمة تتبع مراحل المكالمة (Call Timeline).
 *
 * تسجل كل مرحلة من مراحل المكالمة PSTN مع الطابع الزمني والبيانات المرتبطة.
 * تُستخدم للتحقق من صحة سير المكالمة واستكشاف الأخطاء.
 */
@Service
class CallTimelineService(
    private val jdbc: JdbcTemplate,
    private val objectMapper: ObjectMapper
) {
    companion object {
        private val log = LoggerFactory.getLogger(CallTimelineService::class.java)
    }

    /**
     * مراحل المكالمة.
     *
     * كانت داخل `companion object` فلم تُحلّ من الخارج كـ
     * `CallTimelineService.Stage` (المتحكم كان يفشل في الترجمة). النقل إلى
     * مستوى الصنف يجعل الوصول مباشرًا وواضحًا.
     */
    enum class Stage(val label: String) {
        DIALING("Dialing"),
        RINGING("Ringing"),
        BRIDGING("Bridging"),
        ACTIVE("Active"),
        ENDED("Ended")
    }

    /**
     * تسجيل مرحلة جديدة في timeline المكالمة.
     *
     * @param callId معرف المكالمة
     * @param stage المرحلة (DIALING, RINGING, BRIDGING, ACTIVE, ENDED)
     * @param data بيانات إضافية مرتبطة بالمرحلة (اختياري)
     */
    fun recordStage(
        callId: String,
        stage: Stage,
        data: Map<String, Any?> = emptyMap()
    ) {
        try {
            jdbc.update(
                """
                    INSERT INTO pstn_call_timeline
                    (call_id, stage, stage_data, started_at)
                    VALUES (?, ?, ?, ?)
                """,
                callId,
                stage.name,
                if (data.isEmpty()) null else objectMapper.writeValueAsString(data),
                java.sql.Timestamp.from(Instant.now())
            )
            log.debug("Recorded stage {} for call {}", stage.name, callId)
        } catch (e: Exception) {
            log.warn("Failed to record call timeline: {}", e.message)
        }
    }

    /**
     * إنهاء مرحلة (تسجيل وقت النهاية).
     *
     * @param callId معرف المكالمة
     * @param stage المرحلة لإنهائها
     */
    fun endStage(callId: String, stage: Stage) {
        try {
            jdbc.update(
                """
                    UPDATE pstn_call_timeline
                    SET ended_at = ?
                    WHERE call_id = ? AND stage = ? AND ended_at IS NULL
                """,
                java.sql.Timestamp.from(Instant.now()),
                callId,
                stage.name
            )
        } catch (e: Exception) {
            log.warn("Failed to end stage: {}", e.message)
        }
    }

    /**
     * الحصول على timeline كامل لمكالمة.
     *
     * @param callId معرف المكالمة
     * @return قائمة المراحل مرتبة زمنياً
     */
    fun getTimeline(callId: String): List<Map<String, Any?>> {
        return try {
            jdbc.queryForList(
                """
                    SELECT
                        stage,
                        stage_data,
                        started_at,
                        ended_at
                    FROM pstn_call_timeline
                    WHERE call_id = ?
                    ORDER BY started_at ASC
                """,
                callId
            )
        } catch (e: Exception) {
            log.warn("Failed to get timeline: {}", e.message)
            emptyList()
        }
    }

    /**
     * الحصول على آخر مرحلة لمكالمة (للسرعة).
     *
     * @param callId معرف المكالمة
     * @return المرحلة الأخيرة أو null
     */
    fun getLastStage(callId: String): Stage? {
        return try {
            val result = jdbc.queryForObject(
                """
                    SELECT stage FROM pstn_call_timeline
                    WHERE call_id = ?
                    ORDER BY started_at DESC
                    LIMIT 1
                """,
                String::class.java,
                callId
            )
            Stage.entries.find { it.name == result }
        } catch (e: Exception) {
            null
        }
    }
}
