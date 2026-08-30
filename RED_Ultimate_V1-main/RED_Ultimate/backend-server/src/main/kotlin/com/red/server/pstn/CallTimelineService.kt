package com.red.server.pstn

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * خطّ مراحل المكالمة (Call Timeline).
 *
 * يسجّل كل انتقال في حياة مكالمة PSTN مع طابعه الزمني وبياناته، ليُقرأ عند
 * التشخيص: أين توقّفت المكالمة، وكم مكثت في كل مرحلة.
 *
 * ## عطلان أُصلحا هنا
 *
 * ### 1. `stage_data` نصّ في عمود `jsonb` ⇒ كل كتابة تفشل
 *
 * العمود `jsonb`، و`jdbc.update(...)` بوسيط `String` يُرسِل `setString` أي
 * `text`. وPostgreSQL لا يُحوّل `text` إلى `jsonb` ضمنيًّا في `INSERT`:
 *
 *     42804: column "stage_data" is of type jsonb but expression is of type text
 *     HINT: You will need to rewrite or cast the expression.
 *
 * فكانت **كل** مرحلة تحمل بيانات تسقط، والاستثناء يُبتلَع في `catch` مع
 * `log.warn` فقط — والمتحكّم يُعيد `success:true` لأنه لا يرى شيئًا. النتيجة:
 * `pstn_call_timeline` فارغ (0 صفوف) بينما الواجهة تُبلّغ نجاحًا.
 *
 * الإصلاح: `?::jsonb` صريح. وثبت عمليًّا أن `PREPARE` يقبل الجملة بلا
 * التحويل (لأن نوع المحرف يُستنتج من العمود) لكن **التنفيذ** بوسيط نصّي
 * يفشل — لذلك لا يُمسك هذا العيب إلا باختبار تنفيذٍ حقيقي.
 *
 * ### 2. الكتابة الصامتة تُخفي العطل
 *
 * `catch { log.warn }` يعني أن جدولًا فارغًا يبدو سليمًا. الآن يُسجَّل
 * الاستثناء كاملًا (`log.warn(msg, e)`) ليظهر سبب PostgreSQL في السجل،
 * و[recordStage] تُعيد `Boolean` ليعرف المنادي إن كُتب الصفّ فعلًا — فلا
 * يُبلّغ العميل نجاحًا لم يحدث.
 *
 * ## القراءة لا تُبتلَع خطؤها
 *
 * `getTimeline` كانت تُعيد `emptyList()` عند أي خطأ، فيُقرأ «لا مراحل» —
 * وهو تأكيدٌ كاذب. الآن يُسجَّل الخطأ ويُعاد رميه ليصل إلى المعالج العام،
 * لأن جدولًا مفقودًا أو عمودًا مُعاد التسمية عطلٌ يجب أن يُرى.
 */
@Service
class CallTimelineService(
    private val jdbc: JdbcTemplate,
    private val objectMapper: ObjectMapper
) {
    companion object {
        private val log = LoggerFactory.getLogger(CallTimelineService::class.java)

        /**
         * مراحل [PstnCallProgressTracker] هي مصدر الحقيقة الحيّ، ومراحل هذا
         * الخطّ هي تمثيلها المُخزَّن. الاسمان يختلفان في المرحلة الأولى فقط:
         * المتتبِّع يسمّيها `INVITING` (أُرسل INVITE إلى Asterisk) والمخطَّط
         * يسمّيها `DIALING` (قيد الاتصال) — وهما الحدث نفسه.
         *
         * الربط صريح لا بالاسم: `valueOf` كان سيرمي على `INVITING`.
         */
        fun fromProgress(stage: PstnCallProgressTracker.Stage): Stage = when (stage) {
            PstnCallProgressTracker.Stage.INVITING -> Stage.DIALING
            PstnCallProgressTracker.Stage.RINGING -> Stage.RINGING
            PstnCallProgressTracker.Stage.BRIDGING -> Stage.BRIDGING
            PstnCallProgressTracker.Stage.ACTIVE -> Stage.ACTIVE
            PstnCallProgressTracker.Stage.ENDED -> Stage.ENDED
        }
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
     * تسجيل مرحلة جديدة في خطّ المكالمة.
     *
     * @return `true` إن كُتب الصفّ فعلًا. الإرجاع ليس تجميلًا: المتحكّم كان
     *   يُبلّغ `success:true` بينما الكتابة تفشل صامتةً.
     */
    fun recordStage(
        callId: String,
        stage: Stage,
        data: Map<String, Any?> = emptyMap()
    ): Boolean {
        return try {
            // `?::jsonb` إلزامي — العمود jsonb والوسيط نصّ. بلا التحويل
            // تفشل الجملة بـ42804 عند التنفيذ لا عند التحضير.
            val rows = jdbc.update(
                """
                    INSERT INTO pstn_call_timeline
                    (call_id, stage, stage_data, started_at)
                    VALUES (?, ?, ?::jsonb, ?)
                """,
                callId,
                stage.name,
                if (data.isEmpty()) null else objectMapper.writeValueAsString(data),
                java.sql.Timestamp.from(Instant.now())
            )
            log.debug("Recorded stage {} for call {}", stage.name, callId)
            rows > 0
        } catch (e: Exception) {
            // الأثر الكامل: بلا الاستثناء كان سبب PostgreSQL يختفي فيبدو
            // الجدول الفارغ سليمًا.
            log.warn("Failed to record timeline stage {} for call {}", stage.name, callId, e)
            false
        }
    }

    /**
     * إنهاء مرحلة (تسجيل وقت النهاية).
     *
     * @return `true` إن حُدِّث صفٌّ. `false` يعني أن المرحلة لم تُفتح أصلًا
     *   أو أُنهيت سابقًا — وكلاهما حالة مشروعة لا خطأ.
     */
    fun endStage(callId: String, stage: Stage): Boolean {
        return try {
            jdbc.update(
                """
                    UPDATE pstn_call_timeline
                    SET ended_at = ?
                    WHERE call_id = ? AND stage = ? AND ended_at IS NULL
                """,
                java.sql.Timestamp.from(Instant.now()),
                callId,
                stage.name
            ) > 0
        } catch (e: Exception) {
            log.warn("Failed to end timeline stage {} for call {}", stage.name, callId, e)
            false
        }
    }

    /**
     * خطّ مكالمة كامل مرتَّبًا زمنيًّا.
     *
     * لا يُبتلَع خطأ القراءة: `emptyList()` عند عطلٍ في المخطَّط يُقرأ «لا
     * مراحل لهذه المكالمة»، وهو تأكيدٌ كاذب يُخفي جدولًا مفقودًا.
     *
     * `stage_data` يُفكَّك إلى خريطة حقيقية: `queryForList` يُعيد عمود `jsonb`
     * كـ`PGobject`، فيُسلسِله Jackson بخصائص المُشغِّل لا بمحتواه:
     *
     *     "stage_data": { "null": false, "type": "jsonb", "value": "{\"note\":\"x\"}" }
     *
     * أي أن العميل يستقبل نصًّا مُغلَّفًا داخل غلاف تنفيذي بدل JSON — فلا
     * يستطيع قراءة `stage_data.note` إلا بتحليل نصّ داخل كائن.
     */
    fun getTimeline(callId: String): List<Map<String, Any?>> =
        jdbc.query(
            """
                SELECT
                    stage,
                    stage_data,
                    started_at,
                    ended_at,
                    EXTRACT(EPOCH FROM (COALESCE(ended_at, NOW()) - started_at))::int AS duration_seconds
                FROM pstn_call_timeline
                WHERE call_id = ?
                ORDER BY started_at ASC
            """,
            { rs, _ ->
                mapOf(
                    "stage" to rs.getString("stage"),
                    "stageData" to parseStageData(rs.getString("stage_data")),
                    "startedAt" to rs.getTimestamp("started_at")?.toInstant()?.toString(),
                    "endedAt" to rs.getTimestamp("ended_at")?.toInstant()?.toString(),
                    "durationSeconds" to rs.getInt("duration_seconds"),
                )
            },
            callId,
        )

    /**
     * `jsonb` نصًّا → خريطة. `null` تبقى `null` لا خريطة فارغة: غياب البيانات
     * ليس بياناتٍ فارغة.
     */
    private fun parseStageData(raw: String?): Map<String, Any?>? =
        raw?.takeIf { it.isNotBlank() }?.let { json ->
            runCatching {
                @Suppress("UNCHECKED_CAST")
                objectMapper.readValue(json, Map::class.java) as Map<String, Any?>
            }.getOrElse {
                // صفٌّ كُتب قبل التحويل الصريح قد يحمل نصًّا غير JSON.
                log.warn("Malformed stage_data JSON in timeline: {}", it.message)
                null
            }
        }

    /**
     * آخر مرحلة مسجَّلة لمكالمة.
     *
     * `null` يعني «لا خطّ لهذه المكالمة» — لا يُخفى خطأ قراءة تحته: الاستثناء
     * كان يُبتلَع بلا سجلّ إطلاقًا (`catch { null }`).
     */
    fun getLastStage(callId: String): Stage? =
        try {
            val result = jdbc.queryForList(
                """
                    SELECT stage FROM pstn_call_timeline
                    WHERE call_id = ?
                    ORDER BY started_at DESC
                    LIMIT 1
                """,
                String::class.java,
                callId
            ).firstOrNull()
            result?.let { name -> Stage.entries.find { it.name == name } }
        } catch (e: Exception) {
            log.warn("Failed to read last timeline stage for call {}", callId, e)
            null
        }
}
