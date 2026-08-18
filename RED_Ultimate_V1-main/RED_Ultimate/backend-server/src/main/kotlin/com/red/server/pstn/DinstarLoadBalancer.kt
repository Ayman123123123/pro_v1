package com.red.server.pstn

import com.red.server.services.DinstarFleetService
import com.red.server.services.DinstarHardwareService
import com.red.server.services.DinstarSignal
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
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
    private val jdbc: JdbcTemplate,
    private val redis: RedisTemplate<String, String>
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

    /** عدّاد الاستخدام لكل (بوابة، منفذ) — لا حجم ثابت. */
    private val portUsage = ConcurrentHashMap<String, AtomicInteger>()

    private fun usageKey(gatewayId: UUID?, port: Int) = "${gatewayId ?: "local"}#$port"
    private fun usageOf(gatewayId: UUID?, port: Int): Int =
        portUsage[usageKey(gatewayId, port)]?.get() ?: 0

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
     * @param forcedPort منفذ بعينه يطلبه العميل (اختياري). يُحصر الترشيح فيه
     *        مع بقاء شروط الصلاحية (تسجيل/إشارة/انشغال) مطبقة — طلب منفذ
     *        ميت يُرجع null لا منفذًا بديلًا، كي لا تخرج المكالمة من شريحة
     *        غير التي اختارها المستخدم عمدًا.
     * @return المنفذ المختار، أو `null` إذا لم يوجد أي منفذ صالح.
     *         `null` هنا مقصودة: إخبار المتصل بعدم توفر مسار أصدق من
     *         إعادة منفذ عشوائي ستفشل عليه المكالمة.
     */
    fun selectPort(targetNumber: String? = null, forcedPort: Int? = null): PortSelection? {
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
                // حصر الترشيح على المنفذ المطلوب صراحةً إن وُجد
                if (forcedPort != null && index != forcedPort) continue
                val status = port["status"]?.toString()
                val callState = port["callState"]?.toString()
                val usable = port["signalUsable"] as? Boolean ?: false
                val dbm = (port["signalDbm"] as? Number)?.toInt()

                // شرط أول: مسجّلة على الشبكة.
                // القيمة الخام من الجهاز قد تأتي "REGISTER_OK" (UC2000 عبر
                // get_port_info) أو "REGISTERED"/"Mobile Registered" (نُسخ
                // أخرى) — كلها تعني مسجّلًا فيُقبل.
                if (!status.registeredOnNetwork()) {
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

        val best = candidates.maxByOrNull { it.score }
        if (best == null) {
            log.error("DINSTAR: no usable port across {} gateway(s) for target={}",
                sources.size, targetNumber ?: "unknown")
            return null
        }

        val index = (best.port["index"] as? Number)?.toInt() ?: return null
        portUsage.computeIfAbsent(usageKey(best.gateway?.id, index)) { AtomicInteger(0) }.incrementAndGet()

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
    /**
     * هل حالة المنفذ تعني «مسجّل على الشبكة»؟
     *
     * صيغ الحالة تختلف بين إصدارات UC2000:
     * - "REGISTER_OK" — الصيغة الخام من get_port_info في هذا الجهاز.
     * - "REGISTERED" / "Mobile Registered" — نُسخ أخرى.
     * أي منهما يستحق المرور إلى الترشيح.
     */
    private fun String?.registeredOnNetwork(): Boolean {
        val s = this?.trim().orEmpty()
        return s.equals("REGISTERED", ignoreCase = true) ||
            s.equals("REGISTER_OK", ignoreCase = true) ||
            s.equals("Mobile Registered", ignoreCase = true)
    }

    private fun operatorsMatch(portOperator: String?, target: YemenOperatorInfo): Boolean {
        if (portOperator.isNullOrBlank()) return false
        // شبكة غير محمولة لا تُطابَق: لا توجد شريحة عليها في البوابة
        if (!target.isMobile) return false
        val normalized = normalizeOperator(portOperator)
        return normalized == target.apiName
    }

    private fun normalizeOperator(name: String): String = when {        name.contains("Sabafon", true) || name.contains("سبأفون") -> "Sabafon"
        name.contains("MTN", true) || name.contains("YOU", true) || name.contains("يو") -> "YOU"
        name.contains("Yemen", true) && name.contains("Mobile", true) -> "YemenMobile"
        name.contains("يمن موبايل") -> "YemenMobile"
        name.contains("Y Telecom", true) || name.contains("HiTel", true) || name.contains("واي") -> "YTelecom"
        else -> name
    }

    /** تحرير المنفذ بعد انتهاء المكالمة — بحدّ أدنى صفر. */
    fun releasePort(gatewayId: UUID?, port: Int) {        portUsage[usageKey(gatewayId, port)]?.updateAndGet { current ->
            // بدون هذا الحدّ كان التحرير المزدوج يدفع العدّاد إلى السالب
            // فتبدو الشريحة أبدًا «الأقل استخدامًا» وتُختار دائمًا.
            if (current > 0) current - 1 else 0
        }
        // أيضًا حرر النسخة المحلية القديمة للتوافق
        if (gatewayId != null) {
            portUsage[usageKey(null, port)]?.updateAndGet { if (it > 0) it - 1 else 0 }
        }
    }

    /** تحرير لمن وضع البوابة الواحدة (legacy) — يحرر local وكل البوابات التي تحمل نفس المنفذ */
    fun releasePort(port: Int) {
        // local
        releasePort(null, port)
        // كل البوابات التي لديها نفس المنفذ (for broad cleanup on hangup without gatewayId)
        portUsage.keys.filter { it.endsWith("#$port") }.forEach { key ->
            portUsage[key]?.updateAndGet { if (it > 0) it - 1 else 0 }
        }
    }

    /** تحرير كل المنافذ العالقة — يستخدمها heartbeat لمنع التعليق */
    fun releaseAll() {
        portUsage.forEach { (_, counter) -> counter.set(0) }
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

    /**
     * Check if a user currently has an active PSTN call bound in Redis.
     */
    fun hasActiveCall(userId: UUID): Boolean {
        return redis.opsForValue().get("red:pstn:active:$userId") != null
    }

    /**
     * Resolve the active call binding for a user from the load balancer's
     * internal tracking. Returns (callId, portIndex, gatewayId) or null.
     */
    fun resolveActiveCall(userId: UUID): Triple<String, Int, UUID>? {
        val raw = redis.opsForValue().get("red:pstn:active:$userId") ?: return null
        val parts = raw.split(":")
        if (parts.size != 3) return null
        return try {
            Triple(parts[0], parts[1].toInt(), UUID.fromString(parts[2]))
        } catch (_: Exception) { null }
    }
}
