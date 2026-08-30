package com.red.server.services

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

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

    @Scheduled(fixedDelayString = "\${red.dinstar.cdr.ingest-interval-ms:300000}", initialDelay = 60_000)
    fun ingest() {
        val gateways = fleet.listGateways(onlyEnabled = true)
        if (gateways.isEmpty()) return

        for (gw in gateways) {
            try {
                ingestFrom(gw)
            } catch (e: Exception) {
                // WARN لا DEBUG: خطأ مخطَّط (عمود مفقود، خرق قيد) كان يختفي
                // على DEBUG فبقي الجدول فارغًا شهورًا بلا أثر في السجل.
                log.warn("CDR ingest failed for {}: {}", gw.host, e.message)
            }
        }
    }

    private fun ingestFrom(gateway: DinstarFleetService.Gateway) {
        // نافذة آخر 24 ساعة — كافية لالتقاط ما فات دون تكرار كل السجل.
        val since = Instant.now().minusSeconds(24 * 3600)
        val timeAfter = DinstarTime.format(since)

        val records = hardware.getCdrRecords(gateway, timeAfter = timeAfter)
        if (records.isEmpty()) {
            log.debug("CDR ingest: no records from {}", gateway.host)
            return
        }

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
            val rows = jdbc.update(
                DinstarApiContract.Cdr.INSERT_SQL,
                gateway.id.toString(), port, java.sql.Timestamp.from(start),
                answer?.let { java.sql.Timestamp.from(it) },
                duration, DinstarApiContract.Cdr.ringSeconds(start, answer),
                direction, status, src, dst,
                reason, gsmCode, codec,
                DinstarApiContract.Cdr.endTime(start, answer, DinstarApiContract.Cdr.ringSeconds(start, answer), duration),
                rawJson
            )
            if (rows > 0) inserted++
        }

        if (inserted > 0) log.info("CDR ingest: {} new record(s) from {}", inserted, gateway.host)
        else log.debug("CDR ingest: {} record(s) from {} (all duplicates)", records.size, gateway.host)
    }
}
