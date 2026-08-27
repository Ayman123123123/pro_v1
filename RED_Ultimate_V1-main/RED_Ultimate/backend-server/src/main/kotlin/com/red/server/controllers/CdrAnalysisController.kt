package com.red.server.controllers

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.*

/**
 * تحليل سجل المكالمات الفعلي من `dinstar_cdr`.
 *
 * ## ما كان معطوبًا
 *
 * كانت `/analysis` تقرأ `gateway_route_decisions` — وهي **قرارات الموزّع** لا
 * مكالمات: صفٌّ يُكتب عند اختيار منفذ، حتى لو لم تُجرَ مكالمة قطّ. فظهر:
 *
 * - `duration` صفرًا ثابتًا مكتوبًا في الكود (`"duration" to 0`)، فمتوسط المدة
 *   في الواجهة صفر دائمًا.
 * - `direction` مثبَّتًا على `OUTBOUND`، فالمكالمات الواردة لا تُرى أصلًا.
 * - `number` بادئةَ الوجهة (`destination_prefix`) لا الرقم المطلوب.
 * - `status` مُشتقًّا من نتيجة التوجيه (`SELECTED` ⇒ `COMPLETED`)، أي أن مكالمة
 *   اختِير لها منفذ ثم فشلت في الشبكة تُعَدّ ناجحة.
 *
 * السبب الجذري أن `dinstar_cdr` كان فارغًا: `get_cdr` لم يكن مُجدولًا، ثم بعد
 * جدولته كانت كل دورة ابتلاع تسقط لأن `ON CONFLICT` لم يُعِد شرط الفهرس
 * الجزئي. بعد إصلاح ذلك صار الجدول يمتلئ فعلًا، فلا عذر لقراءة بديلٍ مضلِّل.
 *
 * إحصاءات الموزّع نفسها ليست ضائعة: `/api/admin/dinstar/fleet/routing/decisions`
 * تعرضها في موضعها الصحيح.
 */
@RestController
@RequestMapping("/api/admin/dinstar/cdr")
class CdrAnalysisController(
    private val jdbc: JdbcTemplate
) {

    /**
     * سجل المكالمات مع اسم البوابة والمشغّل.
     *
     * المشغّل ليس في `dinstar_cdr` — الجهاز لا يُصدره في `get_cdr` — فيُستخرَج
     * من آخر لقطة للمنفذ الذي حملت المكالمة (`gateway_port_snapshots`).
     * `LEFT JOIN` لا `JOIN`: منفذ بلا لقطة بعد لا يجوز أن يُخفي مكالماته.
     */
    @GetMapping("/analysis")
    fun analysis(
        @RequestParam(defaultValue = "500") limit: Int
    ): List<Map<String, Any?>> {
        val safeLimit = limit.coerceIn(1, 2000)
        return jdbc.query(
            """SELECT c.id, c.port_index, c.direction, c.status,
                      c.caller_number, c.callee_number,
                      c.duration_seconds, c.ring_duration_seconds,
                      c.start_time, c.answer_time,
                      c.hangup_cause, c.codec, c.gsm_code, c.cost_yer,
                      g.host gateway_host, p.operator_name
               FROM dinstar_cdr c
               LEFT JOIN telecom_gateways g ON g.id = c.gateway_id
               LEFT JOIN gateway_port_snapshots p
                      ON p.gateway_id = c.gateway_id AND p.port_index = c.port_index
               ORDER BY c.start_time DESC
               LIMIT ?""",
            arrayOf<Any>(safeLimit)
        ) { rs, _ ->
            val direction = rs.getString("direction") ?: ""
            val caller = rs.getString("caller_number")
            val callee = rs.getString("callee_number")
            // getInt يُعيد 0 عند NULL بلا تمييز، وهو نفس الكذب الذي أزالته V41:
            // «رَنَّ صفر ثانية» بدل «زمن الرنين مجهول». getObject يُبقي NULL.
            val ring = (rs.getObject("ring_duration_seconds") as? Number)?.toInt()
            mapOf(
                "id" to rs.getString("id"),
                "gatewayHost" to (rs.getString("gateway_host") ?: "—"),
                "portIndex" to rs.getInt("port_index"),
                // الواجهة تتوقّع INBOUND/OUTBOUND، والعمود يحمل inbound/outbound.
                "direction" to direction.uppercase(),
                // الرقم المُقابل: في الصادرة المطلوب، وفي الواردة المتّصل.
                "number" to ((if (direction == "inbound") caller else callee) ?: "—"),
                "callerNumber" to caller,
                "calleeNumber" to callee,
                "startTime" to rs.getTimestamp("start_time")?.toInstant()?.toString(),
                "answerTime" to rs.getTimestamp("answer_time")?.toInstant()?.toString(),
                "duration" to rs.getInt("duration_seconds"),
                "ringDuration" to ring,
                "status" to (rs.getString("status") ?: "").uppercase(),
                "hangupCause" to rs.getString("hangup_cause"),
                "codec" to rs.getString("codec"),
                "gsmCode" to rs.getObject("gsm_code"),
                "operator" to (rs.getString("operator_name") ?: "—"),
                "cost" to (rs.getBigDecimal("cost_yer")?.toDouble() ?: 0.0)
            )
        }
    }

    /**
     * ملخّص المكالمات.
     *
     * استعلام واحد بـ`FILTER` لا خمسة استعلامات متتابعة: النسخة السابقة كانت
     * تُصدر خمس رحلات إلى القاعدة قد تقرأ لحظاتٍ مختلفة، فتظهر أرقام لا
     * تتجامع. و`answered` هو معيار النجاح الوحيد — وجود زمن إجابة — لا نتيجة
     * التوجيه.
     */
    @GetMapping("/summary")
    fun summary(): Map<String, Any?> {
        val row = jdbc.queryForMap(
            """SELECT
                 COUNT(*)                                            total,
                 COUNT(*) FILTER (WHERE status = 'answered')          answered,
                 COUNT(*) FILTER (WHERE status = 'no_answer')         no_answer,
                 COUNT(*) FILTER (WHERE status = 'busy')              busy,
                 COUNT(*) FILTER (WHERE status = 'failed')            failed,
                 COUNT(*) FILTER (WHERE status = 'cancelled')         cancelled,
                 COUNT(*) FILTER (WHERE direction = 'inbound')        inbound,
                 COUNT(*) FILTER (WHERE direction = 'outbound')       outbound,
                 COALESCE(SUM(duration_seconds), 0)                   total_seconds,
                 COALESCE(SUM(duration_seconds) FILTER (WHERE status = 'answered'), 0) billable_seconds,
                 COALESCE(SUM(cost_yer), 0)                           total_cost_yer,
                 MIN(start_time)                                      first_call_at,
                 MAX(start_time)                                      last_call_at
               FROM dinstar_cdr"""
        )
        val total = (row["total"] as Number).toLong()
        val answered = (row["answered"] as Number).toLong()
        val billable = (row["billable_seconds"] as Number).toLong()

        return mapOf(
            "total" to total,
            "answered" to answered,
            "noAnswer" to (row["no_answer"] as Number).toLong(),
            "busy" to (row["busy"] as Number).toLong(),
            "failed" to (row["failed"] as Number).toLong(),
            "cancelled" to (row["cancelled"] as Number).toLong(),
            "inbound" to (row["inbound"] as Number).toLong(),
            "outbound" to (row["outbound"] as Number).toLong(),
            "totalSeconds" to (row["total_seconds"] as Number).toLong(),
            "billableSeconds" to billable,
            // متوسط مدة المُجابة فقط: إدخال غير المُجابة يسحب المتوسط إلى الصفر
            // ويُقرأ كأن جودة المكالمات انهارت.
            "avgAnsweredSeconds" to if (answered > 0) billable / answered else 0L,
            "answerRate" to if (total > 0) Math.round(answered.toDouble() / total * 100) else 0L,
            "totalCostYer" to ((row["total_cost_yer"] as? Number)?.toDouble() ?: 0.0),
            "firstCallAt" to (row["first_call_at"] as? java.sql.Timestamp)?.toInstant()?.toString(),
            "lastCallAt" to (row["last_call_at"] as? java.sql.Timestamp)?.toInstant()?.toString()
        )
    }

    /**
     * توزيع يومي للرسم البياني — كان الحساب في المتصفّح على 500 صفًّا فقط،
     * فيُظهر «آخر 30 يومًا» من عيّنة مقطوعة. التجميع في القاعدة يرى كل الصفوف.
     */
    @GetMapping("/daily")
    fun daily(@RequestParam(defaultValue = "30") days: Int): List<Map<String, Any?>> {
        val safeDays = days.coerceIn(1, 365)
        return jdbc.query(
            """SELECT date_trunc('day', start_time)::date AS call_day,
                      COUNT(*)                                        AS calls,
                      COUNT(*) FILTER (WHERE status = 'answered')     AS answered,
                      COALESCE(SUM(duration_seconds), 0)              AS seconds
               FROM dinstar_cdr
               WHERE start_time >= CURRENT_DATE - make_interval(days => ?)
               GROUP BY 1
               ORDER BY 1""",
            arrayOf<Any>(safeDays)
        ) { rs, _ ->
            val calls = rs.getLong("calls")
            val answered = rs.getLong("answered")
            mapOf(
                "date" to rs.getDate("call_day")?.toString(),
                "calls" to calls,
                "succeeded" to answered,
                "failed" to (calls - answered),
                "duration" to rs.getLong("seconds")
            )
        }
    }
}
