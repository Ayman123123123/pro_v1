package com.red.server.services

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * ابتلاع سجل المكالمات (CDR) من البوابات.
 *
 * كان `CdrAnalysisController` يقرأ `gateway_route_decisions` (قرارات
 * الموزّع) ويعرضها كأنها سجل مكالمات، مع `duration=0` ثابت. بينما
 * `get_cdr` الحقيقي — بالرغم من وجود كود استدعائه — لم يكن مُجدولًا
 * إطلاقًا، فلا يمتلئ `dinstar_cdr` أبدًا. هذه الخدمة تملأ الفجوة: كل
 * 5 دقائق تسحب آخر السجلات من كل بوابة وتُدخل الجديد منها فقط.
 */
@Component
@ConditionalOnProperty(
    prefix = "red.dinstar",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class CdrIngestScheduler(
    private val fleet: DinstarFleetService,
    private val hardware: DinstarHardwareService,
    private val jdbc: JdbcTemplate,
    private val mapper: com.fasterxml.jackson.databind.ObjectMapper,
    @Value("\${red.dinstar.cdr.ingest-interval-ms:300000}") private val intervalMs: Long
) {
    private val log = LoggerFactory.getLogger(CdrIngestScheduler::class.java)

    /** آخر تحذير انزلاق ساعة لكل بوابة — يمنع تكرار السطر كل خمس دقائق. */
    private val lastSkewWarnAt = ConcurrentHashMap<String, Instant>()

    private companion object {
        /** فوقها يُعَدّ عمر أحدث سجل دليلًا على انزلاق ساعة الجهاز. */
        const val MAX_PLAUSIBLE_CDR_AGE_HOURS = 12L
    }

    @Scheduled(fixedDelayString = "\${red.dinstar.cdr.ingest-interval-ms:300000}", initialDelay = 60_000)
    fun ingest() {
        val gateways = fleet.listGateways(onlyEnabled = true)
        // مرئي دائمًا: مجدول صامت هو عطل صامت — وهذا المشروع يظهر الفشل لا يدفنه.
        log.info("CDR ingest cycle: {} enabled gateway(s)", gateways.size)
        if (gateways.isEmpty()) return

        for (gw in gateways) {
            try {
                ingestFrom(gw)
            } catch (e: Exception) {
                // WARN لا DEBUG: خطأ مخطَّط (عمود مفقود، خرق قيد) كان يختفي
                // على DEBUG فبقي الجدول فارغًا شهورًا بلا أثر في السجل.
                // + full stack: رسالة e.message وحدها (bad SQL grammar) تخفي
                // السبب الحقيقي (Caused by: PSQLException detail) — مؤقت للتشخيص.
                log.warn("CDR ingest failed for {}: {}", gw.host, e.message, e)
            }
        }
    }

    private fun ingestFrom(gateway: DinstarFleetService.Gateway) {
        // ── لا فلتر زمني: ساعة الجهاز ليست مرجعًا موثوقًا ──────────────────
        //
        // كان هذا يرسل `time_after = now-24h` مُنسَّقًا بمنطقة الجهاز. لكن
        // `time_after` يُقارَن بـ**ساعة الجهاز الداخلية**، وهي تنزلق بلا حدّ
        // حين يفشل NTP (الجهاز على شبكة إدارة معزولة بلا مسار إلى
        // pool.ntp.org). القياس الحيّ 2026-09-06:
        //
        //   ساعة الجهاز (HTTP Date) : Sat Sep  5 03:04:01 2026
        //   الزمن الحقيقي (+03)      : Sun Sep  6 04:32:35 2026
        //   الانزلاق                 : 25.5 ساعة إلى الخلف
        //
        // فالنافذة المُرسَلة `2026-09-05 04:32:35` تسبق **كل** سجلات الجهاز
        // (أحدثها `2026-09-05 02:03:57` بساعته) ⇒ يرد بـ`cdr:[]`. وهذا ردّ
        // **ناجح** لا استثناء، فلا يتفعّل التدرّج الاحتياطي في
        // `getCdrRecords` (يعمل على الاستثناء فقط) ⇒ الجدول يبقى فارغًا
        // أبدًا بلا سطر تحذير واحد.
        //
        // الصواب ألّا نبني منطقنا على ساعة لا نضبطها: مخزن CDR في الجهاز
        // محدود الحجم، فسحبه كاملًا كل خمس دقائق رخيص، ومنع التكرار مضمون
        // بالقيد الفريد uq_dinstar_cdr_natural_key (V40) لا بنافذة زمنية.
        // وتبقى الأزمنة المخزَّنة كما يعلنها الجهاز (شفافية لا تلفيق).
        val records = hardware.getCdrRecords(gateway)
        if (records.isEmpty()) {
            log.debug("CDR ingest: no records from {}", gateway.host)
            return
        }
        warnIfClockSkewed(gateway, records)

var inserted = 0
        for (cdr in records) {
            val port = (cdr["port"] as? Number)?.toInt() ?: continue
            val start = DinstarTime.parse(cdr["start_date"]?.toString()) ?: continue
            val answer = DinstarTime.parse(cdr["answer_date"]?.toString())
            val duration = (cdr["duration"] as? Number)?.toInt() ?: 0
            val hangup = cdr["hangup"]?.toString()
            val gsmCode = (cdr["gsm_code"] as? Number)?.toInt()
            val codec = cdr["codec"]?.toString()
            val src = cdr["source_number"]?.toString() ?: ""
            val dst = cdr["destination_number"]?.toString() ?: ""
            val callId = cdr["call_id"]?.toString() ?: UUID.randomUUID().toString()

            // اتجاه الجهاز (`ip->gsm`) يخرق CHECK على العمود، والحالة لا يُصدرها
            // الجهاز أصلًا وعمودها NOT NULL — فتُشتقّان قبل الإدراج. بلا هذا
            // كان كل صف يُرفض ويُبتلَع الاستثناء في catch الأعلى.
            val direction = DinstarApiContract.Cdr.normalizeDirection(cdr["direction"]?.toString())
                ?: run {
                    log.debug("CDR: اتجاه غير معروف {} — تخطّي السجل", cdr["direction"])
                    continue
                }
            val status = DinstarApiContract.Cdr.callOutcome(answer != null, hangup)
            val reason = cdr["reason"]?.toString()
            val rawJson = mapper.writeValueAsString(cdr)

            // التكرار يمنعه القيد الفريد uq_dinstar_cdr_natural_key (V40) لا
            // فحصٌ مسبق: الفحص ثم الإدراج يفتح نافذة سباق بين دورتين.
            // الجملة المشتركة في DinstarApiContract.Cdr.INSERT_SQL — انظر شرحها
            // هناك لسبب وجوب شرط WHERE بعد ON CONFLICT.
            //
            // ⚠️ كل زمن يجب أن يُمرَّر كـ`java.sql.Timestamp`. سائق PostgreSQL
            // يرفض `java.time.Instant` مباشرةً:
            //     Can't infer the SQL type to use for an instance of java.time.Instant
            // وكان `endTime(...)` يُمرَّر كـ`Instant` خامًا، فتفشل **كل** عبارة
            // إدراج بـBadSqlGrammarException ويبقى `dinstar_cdr` فارغًا — وهو
            // العطل الذي حجبه سابقًا الفلتر الزمني (كان الردّ فارغًا فلا يُدرَج
            // شيء أصلًا فلا يظهر الخطأ).
            val ringSec = DinstarApiContract.Cdr.ringSeconds(start, answer)
            val endTs = DinstarApiContract.Cdr.endTime(start, answer, ringSec, duration)
                ?.let { java.sql.Timestamp.from(it) }
            val rows = jdbc.update(
                DinstarApiContract.Cdr.INSERT_SQL,
                gateway.id.toString(), callId, port, java.sql.Timestamp.from(start),
                answer?.let { java.sql.Timestamp.from(it) },
                duration, ringSec,
                direction, status, src, dst,
                reason, gsmCode, codec,
                endTs,
                rawJson
            )
            if (rows > 0) inserted++
        }

        if (inserted > 0) log.info("CDR ingest: {} new record(s) from {}", inserted, gateway.host)
        else log.debug("CDR ingest: {} record(s) from {} (all duplicates)", records.size, gateway.host)
    }

    /**
     * يُنبّه على انزلاق ساعة البوابة مرة كل ساعة على الأكثر.
     *
     * انزلاق الساعة لا يُعطّل الابتلاع بعد إزالة الفلتر الزمني، لكنه يُفسد
     * أزمنة السجلات المخزَّنة وترتيبها. وهو عطل صامت بطبعه: الجهاز يرد 200
     * بمحتوى صحيح البنية وزمن خاطئ. فليُقَل بصوت مسموع مرة واحدة كل ساعة
     * بدل أن يُكتشف بعد أسبوع من تحليل غير مفهوم.
     */
    private fun warnIfClockSkewed(gateway: DinstarFleetService.Gateway, records: List<Map<String, Any?>>) {
        val newest = records.asSequence()
            .mapNotNull { DinstarTime.parse(it["start_date"]?.toString()) }
            .maxOrNull() ?: return
        val skewHours = Duration.between(newest, Instant.now()).toHours()
        if (skewHours < MAX_PLAUSIBLE_CDR_AGE_HOURS) return
        val last = lastSkewWarnAt[gateway.host]
        if (last != null && Duration.between(last, Instant.now()).toMinutes() < 60) return
        lastSkewWarnAt[gateway.host] = Instant.now()
        log.warn(
            "بوابة {}: أحدث سجل مكالمة عمره {} ساعة — ساعة الجهاز منزلقة على الأرجح (NTP لا يصل من شبكة الإدارة). " +
                "الأزمنة المخزَّنة ستبقى بساعة الجهاز. اضبط الوقت من enManageCfg.htm (NTPEnable/TimeZone) أو زوّد مسارًا إلى NTP.",
            gateway.host, skewHours
        )
    }
}
