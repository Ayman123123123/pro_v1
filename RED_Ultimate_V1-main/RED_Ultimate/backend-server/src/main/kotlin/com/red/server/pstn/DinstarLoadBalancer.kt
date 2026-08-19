package com.red.server.pstn

import com.red.server.services.DinstarFleetService
import com.red.server.services.DinstarHardwareService
import com.red.server.services.DinstarSignal
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * موزّع أحمال بوابات DINSTAR — يختار البوابة والمنفذ لكل مكالمة صادرة.
 *
 * ## ما تغيّر ولماذا
 *
 * **1. لم يعد يختار شريحة بلا شبكة.** كان الترتيب يجري على حقل `signal`
 * المحسوب بـ `coerceIn(0,31)/31*100`، والقراءة 99 تعني في 3GPP TS 27.007
 * «غير قابلة للكشف» لا «ممتازة». فكانت الشريحة التي لا تحمل أي تغطية
 * تُقيَّم 100% وتُختار أولًا لكل مكالمة — أي أن أسوأ منفذ كان المفضّل.
 * الآن يُستبعد أي منفذ `signalUsable=false` من الترشيح أصلًا.
 *
 * **2. لم يعد مقيّدًا بثمانية منافذ على جهاز واحد.** كان `AtomicIntegerArray(8)`
 * و`% 8` و`coerceIn(0,7)` تفترض بوابة واحدة بثمانية منافذ. الأسطول الآن
 * يضم عدة أجهزة، وعدد منافذ كل جهاز يأتي من طرازه.
 *
 * **3. عدّاد الاستخدام لم يعد يُفلت.** `releasePort` كان يُنقص العداد بلا
 * حدّ أدنى، فمع أي تحرير مزدوج يهبط إلى قيم سالبة وتصير الشريحة
 * «الأقل استخدامًا» أبدًا فتُختار دائمًا.
 *
 * **4. مطابقة المشغل صارت تعتمد الاسم المُطبَّع.** كانت تقارن نص المشغل
 * الخام بـ `contains`، وهو يفشل حين تُعيد البوابة «MTN» بينما الوجهة
 * مُصنَّفة «YOU» رغم أنهما المشغل نفسه بعد 2021.
 *
 * ## مشغلو اليمن (مصدر: خطة الترقيم اليمنية + ITU E.164)
 * | البادئة | المشغل                       |
 * |---------|------------------------------|
 * | 71      | سبأفون (Sabafon)             |
 * | 73      | يو (YOU — كانت MTN حتى 2021) |
 * | 77, 78  | يمن موبايل (Yemen Mobile)    |
 * | 70      | واي (Y Telecom)              |
 */
@Service
class DinstarLoadBalancer(
    private val hardware: DinstarHardwareService,
    private val fleet: DinstarFleetService,
    private val jdbc: JdbcTemplate
) {
    companion object {
        private val log = LoggerFactory.getLogger(DinstarLoadBalancer::class.java)

        /**
         * بادئات المشغلين حسب خطة الترقيم الوطنية اليمنية (وزارة
         * الاتصالات). البادئة رقمان بعد `+967`.
         *
         * المحمول يبدأ دائمًا بالرقم 7. أما `10` فهي تخصيص **Yemen 4G**
         * لخدمة بيانات ثابتة لاسلكية، وليست شبكة GSM محمولة: بوابة
         * DINSTAR لا تحمل شريحة عليها، فهي مُصنَّفة للعرض والتقارير
         * فقط ومستبعدة من مطابقة «داخل الشبكة» عبر `isMobile`.
         */
        private val OPERATOR_PREFIXES: Map<String, YemenOperatorInfo> = mapOf(
            "70" to YemenOperatorInfo("YTelecom", "واي", isMobile = true),
            "71" to YemenOperatorInfo("Sabafon", "سبأفون", isMobile = true),
            "73" to YemenOperatorInfo("YOU", "يو", isMobile = true),
            "77" to YemenOperatorInfo("YemenMobile", "يمن موبايل", isMobile = true),
            "78" to YemenOperatorInfo("YemenMobile", "يمن موبايل", isMobile = true),
            "10" to YemenOperatorInfo("Yemen4G", "يمن فورجي", isMobile = false)
        )

        /**
         * أوزان الترجيح. الإشارة تُقاس بالـ dBm (سالبة) فتُحوَّل إلى
         * نسبة 0..100 قبل الترجيح حتى تبقى الأوزان قابلة للمقارنة.
         */
        private const val W_SIGNAL = 1.0
        private const val W_OPERATOR_MATCH = 35.0
        private const val W_USAGE_PENALTY = 5.0
        private const val W_ROUND_ROBIN = 8.0
        private const val W_GATEWAY_PRIORITY = 0.5

        /**
         * تصنيف رقم يمني إلى مشغله — دالة نقيّة بلا حالة.
         *
         * تُطبَّع الصيغ الدولية (`+967`، `00967`) وصيغة الصفر المحلية،
         * وتُزال الفواصل والمسافات الشائعة في الإدخال اليدوي.
         */
        @JvmStatic
        fun classifyNumber(phoneNumber: String): YemenOperatorInfo? {
            val digits = phoneNumber.filter { it.isDigit() }
            val local = when {
                digits.startsWith("00967") -> digits.removePrefix("00967")
                digits.startsWith("967") -> digits.removePrefix("967")
                digits.startsWith("0") -> digits.removePrefix("0")
                else -> digits
            }
            // البادئة رقمان؛ أقل من ذلك ليس رقمًا صالحًا فلا يُصنَّف تخمينًا
            if (local.length < 2) return null
            return OPERATOR_PREFIXES[local.substring(0, 2)]
        }
    }

    data class YemenOperatorInfo(
        val apiName: String,
        val arabicName: String,
        /**
         * هل هذا المشغل شبكة محمول يمكن لشريحة في البوابة أن تكون
         * عليها؟ `Yemen4G` خدمة بيانات ثابتة، فمطابقتها «داخل الشبكة»
         * بلا معنى وتمنح المنفذ أفضلية لا يستحقها.
         */
        val isMobile: Boolean = true
    )

    /** اختيار نهائي: أي بوابة وأي منفذ عليها. */
    data class PortSelection(
        val gatewayId: UUID?,
        val gatewayHost: String,
        val pjsipEndpoint: String,
        val portIndex: Int,
        val operator: String?,
        val signalDbm: Int?,
        val score: Double,
        val reason: String
    )

    private val nextSlot = AtomicInteger(0)

    /**
     * الحجز محفوظ في PostgreSQL، لا في JVM؛ لهذا لا تعيد إعادة تشغيل خادم
     * اختيار منفذ مشغول ولا تتضارب عقدتان تعملان بالتوازي.
     */
    private fun gatewayKey(gatewayId: UUID?) = gatewayId?.toString() ?: "legacy-single-gateway"

    private fun usageOf(gatewayId: UUID?, port: Int): Int = jdbc.queryForObject(
        """SELECT COUNT(*) FROM gateway_port_reservations
           WHERE gateway_key = ? AND port_index = ? AND expires_at > CURRENT_TIMESTAMP""",
        Int::class.java,
        gatewayKey(gatewayId),
        port
    ) ?: 0

    private fun reservePort(gatewayId: UUID?, port: Int): Boolean = jdbc.update(
        """INSERT INTO gateway_port_reservations
           (gateway_key, port_index, reservation_id, allocated_at, expires_at)
           VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '15 minutes')
           ON CONFLICT (gateway_key, port_index) DO UPDATE
              SET reservation_id = EXCLUDED.reservation_id,
                  allocated_at = EXCLUDED.allocated_at,
                  expires_at = EXCLUDED.expires_at
            WHERE gateway_port_reservations.expires_at <= CURRENT_TIMESTAMP""",
        gatewayKey(gatewayId),
        port,
        UUID.randomUUID()
    ) == 1

    /**
     * تصنيف المشغل حسب بادئة الرقم اليمني.
     *
     * يفوّض إلى دالة نقيّة في الـ companion حتى تُختبر بلا بناء الخدمة
     * كاملة (تتطلب اتصال قاعدة بيانات وعميل أجهزة).
     */
    fun classifyOperator(phoneNumber: String): YemenOperatorInfo? = classifyNumber(phoneNumber)

    /**
     * الاختيار الأمثل عبر الأسطول كله.
     *
     * @param targetNumber رقم الوجهة — يُحسّن الاختيار بمطابقة المشغل.
     * @return المنفذ المختار، أو `null` إذا لم يوجد أي منفذ صالح.
     *         `null` هنا مقصودة: إخبار المتصل بعدم توفر مسار أصدق من
     *         إعادة منفذ عشوائي ستفشل عليه المكالمة.
     */
    fun selectPort(targetNumber: String? = null): PortSelection? {
        val gateways = fleet.routableGateways()
        val targetOperator = targetNumber?.let { classifyNumber(it) }
        val rr = nextSlot.getAndIncrement()

        data class Candidate(
            val gateway: DinstarFleetService.Gateway?,
            val port: Map<String, Any?>,
            val score: Double,
            val reason: String
        )

        val candidates = mutableListOf<Candidate>()

        // لا بوابات مسجّلة: نعمل بالمسار الأحادي القديم حتى لا تنكسر
        // عمليات النشر التي لم تُسجّل أسطولًا بعد.
        val sources: List<Pair<DinstarFleetService.Gateway?, List<Map<String, Any?>>>> =
            if (gateways.isEmpty()) {
                listOf(null to (runCatching { hardware.getHardwareStatus() }.getOrElse {
                    log.warn("DINSTAR single-gateway status failed: {}", it.message); emptyList()
                }))
            } else {
                gateways.map { gw ->
                    gw to (runCatching { hardware.getHardwareStatus(gw) }.getOrElse { e ->
                        fleet.markFailure(gw.id, e.message ?: "status query failed")
                        log.warn("DINSTAR gateway {} unreachable: {}", gw.host, e.message)
                        emptyList()
                    })
                }
            }

        for ((gw, ports) in sources) {
            if (gw != null && ports.isNotEmpty()) fleet.markHealthy(gw.id)

            for (port in ports) {
                val index = (port["index"] as? Number)?.toInt() ?: continue
                val status = port["status"]?.toString()
                val callState = port["callState"]?.toString()
                val usable = port["signalUsable"] as? Boolean ?: false
                val dbm = (port["signalDbm"] as? Number)?.toInt()

                // شرط أول: مسجّلة على الشبكة
                if (!status.equals("REGISTERED", ignoreCase = true)) {
                    recordDecision(gw?.id, index, targetNumber, null, 0.0, "not registered", "REJECTED_OFFLINE")
                    continue
                }
                // شرط ثانٍ: غير مشغولة
                if (callState.equals("ACTIVE", true) || callState.equals("DIALING", true)) {
                    recordDecision(gw?.id, index, targetNumber, null, 0.0, "busy", "REJECTED_BUSY")
                    continue
                }
                // شرط ثالث: إشارة مقيسة فعلًا وكافية — هذا ما كان مفقودًا
                if (!usable) {
                    recordDecision(gw?.id, index, targetNumber, null, 0.0,
                        "signal not usable (raw=${port["signalRaw"]})", "REJECTED_NO_SIGNAL")
                    continue
                }

                val percent = (port["signal"] as? Number)?.toInt() ?: 0
                val portOperator = port["operator"]?.toString()
                val match = targetOperator != null && operatorsMatch(portOperator, targetOperator)

                val score = percent * W_SIGNAL +
                    (if (match) W_OPERATOR_MATCH else 0.0) +
                    (-usageOf(gw?.id, index) * W_USAGE_PENALTY) +
                    (if (index == rr % maxOf(1, ports.size)) W_ROUND_ROBIN else 0.0) +
                    (-(gw?.routingPriority ?: 0) * W_GATEWAY_PRIORITY)

                candidates += Candidate(
                    gw, port, score,
                    buildString {
                        append("signal=${dbm ?: "?"}dBm/${percent}%")
                        if (match) append(" on-net=${targetOperator.apiName}")
                        append(" usage=${usageOf(gw?.id, index)}")
                    }
                )
            }
        }

        val best = candidates.sortedByDescending { it.score }.firstOrNull { candidate ->
            val port = (candidate.port["index"] as? Number)?.toInt() ?: return@firstOrNull false
            if (reservePort(candidate.gateway?.id, port)) {
                true
            } else {
                recordDecision(candidate.gateway?.id, port, targetNumber, candidate.port["operator"]?.toString(),
                    candidate.score, "reserved by another active call", "REJECTED_BUSY")
                false
            }
        }
        if (best == null) {
            log.error("DINSTAR: no usable unreserved port across {} gateway(s) for target={}",
                sources.size, targetNumber ?: "unknown")
            return null
        }

        val index = (best.port["index"] as? Number)?.toInt() ?: return null

        val selection = PortSelection(
            gatewayId = best.gateway?.id,
            gatewayHost = best.gateway?.host ?: "configured",
            pjsipEndpoint = best.gateway?.pjsipEndpoint ?: "dinstar-gateway",
            portIndex = index,
            operator = best.port["operator"]?.toString(),
            signalDbm = (best.port["signalDbm"] as? Number)?.toInt(),
            score = best.score,
            reason = best.reason
        )

        recordDecision(selection.gatewayId, index, targetNumber, selection.operator,
            best.score, best.reason, "SELECTED")

        // SLF4J يستخدم {} فقط — الصيغة {:.1f} كانت تُطبع حرفيًا بدل الرقم.
        log.info("DINSTAR routing: gateway={} port={} score={} reason={} target={}",
            selection.gatewayHost, index, String.format("%.1f", best.score),
            best.reason, targetNumber ?: "unknown")

        return selection
    }

    /**
     * مطابقة المشغلين بالاسم المُطبَّع. «MTN» و«YOU» المشغل نفسه بعد
     * إعادة التسمية في 2021، والمقارنة النصية الخام كانت تفوّت ذلك
     * فتُهدر ميزة المكالمة داخل الشبكة.
     */
    private fun operatorsMatch(portOperator: String?, target: YemenOperatorInfo): Boolean {
        if (portOperator.isNullOrBlank()) return false
        // شبكة غير محمولة لا تُطابَق: لا توجد شريحة عليها في البوابة
        if (!target.isMobile) return false
        val normalized = normalizeOperator(portOperator)
        return normalized == target.apiName
    }

    private fun normalizeOperator(name: String): String = when {
        name.contains("Sabafon", true) || name.contains("سبأفون") -> "Sabafon"
        name.contains("MTN", true) || name.contains("YOU", true) || name.contains("يو") -> "YOU"
        name.contains("Yemen", true) && name.contains("Mobile", true) -> "YemenMobile"
        name.contains("يمن موبايل") -> "YemenMobile"
        name.contains("Y Telecom", true) || name.contains("HiTel", true) || name.contains("واي") -> "YTelecom"
        else -> name
    }

    /** تحرير حجز منفذ محدد بعد انتهاء المكالمة؛ الحذف المتكرر آمن وidempotent. */
    fun releasePort(gatewayId: UUID?, port: Int) {
        jdbc.update(
            "DELETE FROM gateway_port_reservations WHERE gateway_key = ? AND port_index = ?",
            gatewayKey(gatewayId),
            port
        )
    }

    /** تحرير واسع عند حدث Asterisk لا يحمل معرف البوابة؛ يستعمل فقط لمسار hangup التوافقي. */
    fun releasePort(port: Int) {
        jdbc.update("DELETE FROM gateway_port_reservations WHERE port_index = ?", port)
    }

    /** إزالة الحجوزات منتهية المهلة فقط؛ لا يلمس مكالمة ما زالت ضمن فترة الحجز. */
    fun releaseExpiredReservations(): Int = jdbc.update(
        "DELETE FROM gateway_port_reservations WHERE expires_at <= CURRENT_TIMESTAMP"
    )

    /** تستخدم للإيقاف الإداري فقط؛ لا تستعمل كبديل لمسار hangup. */
    fun releaseAll() {
        jdbc.update("DELETE FROM gateway_port_reservations")
    }

    @Deprecated("استخدم selectPort التي تراعي الأسطول وصلاحية الإشارة",
        ReplaceWith("selectPort(targetNumber)"))
    fun getOptimalSlotWfq(targetNumber: String? = null): Int =
        selectPort(targetNumber)?.portIndex
            ?: throw IllegalStateException("No usable DINSTAR port available")

    private fun recordDecision(
        gatewayId: UUID?, port: Int, target: String?, operator: String?,
        score: Double, reason: String, outcome: String
    ) {
        // لا يُسجَّل رقم الوجهة كاملًا: البادئة تكفي للتحليل ولا تكشف
        // من اتصل بمن في سجل يقرأه المسؤول.
        val prefix = target?.filter { it.isDigit() }?.takeLast(9)?.take(2)
        runCatching {
            jdbc.update(
                """INSERT INTO gateway_route_decisions
                   (id,gateway_id,port_index,destination_prefix,matched_operator,score,reason,outcome)
                   VALUES (?,?,?,?,?,?,?,?)""",
                UUID.randomUUID(), gatewayId, port, prefix, operator, score, reason.take(200), outcome
            )
        }.onFailure { log.debug("route decision not recorded: {}", it.message) }
    }
}
