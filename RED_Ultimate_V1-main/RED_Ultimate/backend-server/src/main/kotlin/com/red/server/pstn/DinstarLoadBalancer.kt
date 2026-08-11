package com.red.server.pstn

import com.red.server.services.DinstarHardwareService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicIntegerArray

/**
 * 🧠 YOUNES Dinstar Load Balancer — توزيع ذكي للمكالمات عبر شرائح GSM
 *
 * يختار أفضل منفذ SIM للمكالمة التالية مع مراعاة:
 * - مطابقة المشغل (on-net call = تكلفة أقل)
 * - قوة الإشارة
 * - الحالة (مسجل + بدون مكالمة نشطة)
 * - العدل (round-robin لمنع حظر SIM)
 *
 * مشغلو اليمن الصحيحون (Wikipedia + ITU E.164):
 * | البادئة | المشغل                          |
 * |---------|---------------------------------|
 * | 77, 78  | يمن موبايل (Yemen Mobile)      |
 * | 73      | سبأفون (Sabafon)               |
 * | 71      | واي (Y Telecom)                |
 * | 70      | يو / YOU (كانت MTN Yemen)      |
 * | 10      | يمن 4G (Yemen 4G)              |
 */
@Service
class DinstarLoadBalancer(private val hardware: DinstarHardwareService) {
    companion object {
        private val log = LoggerFactory.getLogger(DinstarLoadBalancer::class.java)

        /**
         * بادئات المشغلين اليمنيين — مطابقة حسب أول رقمين بعد +967
         * Source: Wikipedia (Telephone_numbers_in_Yemen) + ITU E.164
         */
        private val OPERATOR_PREFIXES: Map<String, YemenOperatorInfo> = mapOf(
            "77" to YemenOperatorInfo("YemenMobile", "يمن موبايل", 40),
            "78" to YemenOperatorInfo("YemenMobile", "يمن موبايل", 40),
            "73" to YemenOperatorInfo("Sabafon", "سبأفون", 30),
            "71" to YemenOperatorInfo("YTelecom", "واي", 5),
            "70" to YemenOperatorInfo("YOU", "يو", 20),
            "10" to YemenOperatorInfo("Yemen4G", "يمن 4G", 5)
        )

        /** أوزان WFQ */
        private const val W_SIGNAL = 1.0
        private const val W_OPERATOR_MATCH = 35.0
        private const val W_USAGE_PENALTY = 5.0
        private const val W_ROUND_ROBIN = 8.0
    }

    private val nextSlot = AtomicInteger(0)
    private val portUsageCount = AtomicIntegerArray(8)

    data class YemenOperatorInfo(
        val apiName: String,
        val arabicName: String,
        val estimatedSharePercent: Int
    )

    /**
     * تصنيف المشغل حسب بادئة الرقم اليمني
     */
    fun classifyOperator(phoneNumber: String): YemenOperatorInfo? {
        val digits = phoneNumber.filter { it.isDigit() }
        val local = when {
            digits.startsWith("+967") -> digits.removePrefix("+967")
            digits.startsWith("00967") -> digits.removePrefix("00967")
            digits.startsWith("967") -> digits.removePrefix("967")
            digits.startsWith("0") -> digits.removePrefix("0")
            else -> digits
        }
        if (local.length >= 2) {
            val prefix = local.substring(0, 2)
            return OPERATOR_PREFIXES[prefix]
        }
        return null
    }

    /**
     * اختيار الشريحة التالية (0 إلى 7) — round-robin بسيط
     */
    fun getOptimalSlot(): Int {
        return nextSlot.getAndIncrement() % 8
    }

    /**
     * توزيع ذكي: يختار الشريحة ذات أفضل إشارة مسجّلة
     * ويتجنب الشرائح في حالة مكالمة أو غير مسجّلة
     */
    fun getOptimalSlotBySignal(): Int {
        val ports = runCatching { hardware.getHardwareStatus() }.getOrNull() ?: return getOptimalSlot()

        val candidates = ports.filter { port ->
            val status = port["status"]?.toString()
            val callState = port["callState"]?.toString()
            status == "REGISTERED" && callState != "ACTIVE" && callState != "DIALING"
        }

        if (candidates.isEmpty()) {
            log.warn("No registered idle SIM slots found, falling back to round-robin")
            return getOptimalSlot()
        }

        return candidates.maxByOrNull { (it["signal"] as? Number)?.toInt() ?: 0 }
            ?.let { (it["index"] as? Number)?.toInt() ?: getOptimalSlot() }
            ?: getOptimalSlot()
    }

    /**
     * توزيع ذكي متقدم: WFQ (Weighted Fair Queuing)
     *
     * يختار أفضل منفذ بناءً على:
     * 1. مطابقة المشغل — إذا رقم الوجهة على نفس شبكة المشغل = مكالمة on-net = أرخص
     * 2. قوة الإشارة — إشارة أعلى = جودة أفضل
     * 3. العدل — تجنب حصر المكالمات على شريحة واحدة
     * 4. الاستخدام — عقاب الشرائح الأكثر استخداماً
     *
     * @param targetNumber رقم الوجهة اليمني (اختياري — يُحسّن الاختيار إذا مُرر)
     * @return أفضل منفذ (0-7)
     */
    fun getOptimalSlotWfq(targetNumber: String? = null): Int {
        val ports = runCatching { hardware.getHardwareStatus() }.getOrNull() ?: return getOptimalSlot()

        val targetOperator = targetNumber?.let { classifyOperator(it) }

        val candidates = ports.filter { port ->
            val status = port["status"]?.toString()
            val callState = port["callState"]?.toString()
            status == "REGISTERED" && callState != "ACTIVE" && callState != "DIALING"
        }

        if (candidates.isEmpty()) {
            log.warn("WFQ: No available ports, falling back to round-robin")
            return getOptimalSlot()
        }

        val rrSlot = nextSlot.getAndIncrement() % 8
        val scored = candidates.map { port ->
            val portIndex = (port["index"] as? Number)?.toInt() ?: 0
            val signal = (port["signal"] as? Number)?.toInt() ?: 0
            val usage = portUsageCount.get(portIndex.coerceIn(0, 7))

            val portOperatorName = port["operator"]?.toString() ?: ""
            val operatorMatch = if (targetOperator != null &&
                (portOperatorName.contains(targetOperator.apiName, ignoreCase = true) ||
                 portOperatorName.contains(targetOperator.arabicName, ignoreCase = true))) {
                W_OPERATOR_MATCH
            } else 0.0

            val wSignal = signal * W_SIGNAL
            val wUsage = -usage * W_USAGE_PENALTY
            val wRR = if (portIndex == rrSlot) W_ROUND_ROBIN else 0.0
            val totalScore = wSignal + operatorMatch + wUsage + wRR

            portIndex to totalScore
        }

        val (bestPort, bestScore) = scored.maxByOrNull { it.second } ?: (getOptimalSlot() to 0.0)

        portUsageCount.incrementAndGet(bestPort.coerceIn(0, 7))

        log.info("WFQ selected port {} (score={:.1f}) for number={} operator={}",
            bestPort, bestScore, targetNumber, targetOperator?.apiName ?: "unknown")

        return bestPort
    }

    /**
     * تناقص عداد الاستخدام عند انتهاء مكالمة
     */
    fun releasePort(port: Int) {
        if (port in 0..7) {
            portUsageCount.decrementAndGet(port)
        }
    }
}
