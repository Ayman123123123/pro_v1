package com.red.server.pstn

import com.red.server.services.DinstarFleetService
import com.red.server.services.DinstarHardwareService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * خدمة الأتمتة لتعلّم أرقام الشرائح.
 *
 * قد يرسل التعلّم SMS أو ينشئ مكالمة بحسب إعداد البوابة والمشغل، ولذلك يبقى
 * **معطلاً افتراضيًا**. يُفعّل فقط بعد تفويض صريح ومحدد للتكلفة والنطاق عبر
 * `RED_DINSTAR_NUMBER_LEARNING_AUTO_ENABLED=true`.
 *
 * ## ما تغيّر ولماذا
 *
 * **1. لا يعيد المحاولة بلا حدّ.** كانت الدورة تُنشئ قاعدة تعلّم لكل منفذ بلا
 * رقم في **كل** ساعة إلى الأبد. قاعدة سبأفون تُرسل SMS إلى `333` بتكلفة
 * 10 YER لكل محاولة، فثمانية منافذ = 80 YER/ساعة = ~57,600 YER شهريًا على
 * منافذ قد لا تدعم الخدمة أصلًا. الآن: مهلة تهدئة لكل منفذ وسقف محاولات.
 *
 * **2. لا يطلب لمنفذ غير مسجّل.** الشريحة غير المسجّلة على الشبكة لا تستطيع
 * إرسال SMS، فالطلب يُهدر محاولة مضمونة الفشل.
 *
 * **3. يستخدم الطريق الموثَّق لسبأفون.** الافتراضي SMS إلى `333` بالنص `MMN`
 * لا نمط Call — وهو ما توثّقه صفحة خدمات سبأفون الرسمية.
 */
@Service
@ConditionalOnProperty(
    prefix = "red.dinstar.number-learning",
    name = ["auto-enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class AutoNumberLearningService(
    private val fleet: DinstarFleetService,
    private val hardware: DinstarHardwareService,
    @Value("\${red.dinstar.number-learning.max-attempts-per-port:3}")
    private val maxAttemptsPerPort: Int,
    @Value("\${red.dinstar.number-learning.cooldown-hours:24}")
    private val cooldownHours: Long
) {
    companion object {
        private val log = LoggerFactory.getLogger(AutoNumberLearningService::class.java)
        private const val CHECK_INTERVAL_MINUTES = 60L
    }

    /** آخر محاولة وعددها لكل (بوابة، منفذ) — يمنع الإنفاق المتكرر. */
    private val attempts = ConcurrentHashMap<String, Attempt>()

    private data class Attempt(val count: Int, val at: Instant)

    /**
     * فحص دوري لكل المنافذ في كافة البوابات.
     */
    @Scheduled(fixedDelay = CHECK_INTERVAL_MINUTES, timeUnit = TimeUnit.MINUTES)
    fun checkAndTriggerLearning() {
        log.info("Starting periodic check for Phone Number Learning...")

        val gateways = fleet.listGateways(onlyEnabled = true)
        for (gw in gateways) {
            val ports = runCatching { hardware.getHardwareStatus(gw) }.getOrNull() ?: continue

            for (port in ports) {
                val index = (port["index"] as? Number)?.toInt() ?: continue
                val operator = port["operator"]?.toString()
                val number = port["number"]?.toString()
                val registered = com.red.server.services.DinstarApiContract.PortInfo
                    .isRegistered(port["status"]?.toString())

                if (operator != "Sabafon") continue
                if (!number.isNullOrBlank() && number != "null") continue

                // شريحة غير مسجّلة لا ترسل SMS — الطلب هدر مضمون
                if (!registered) {
                    log.debug("Port {} on {} not registered — skipping learning", index, gw.host)
                    continue
                }

                val key = "${gw.id}#$index"
                val prior = attempts[key]
                if (prior != null) {
                    if (prior.count >= maxAttemptsPerPort) {
                        log.debug(
                            "Port {} on {} reached the {}-attempt cap — manual review required",
                            index, gw.host, maxAttemptsPerPort
                        )
                        continue
                    }
                    val waited = Duration.between(prior.at, Instant.now()).toHours()
                    if (waited < cooldownHours) {
                        log.debug(
                            "Port {} on {} in cooldown ({}h of {}h)",
                            index, gw.host, waited, cooldownHours
                        )
                        continue
                    }
                }

                log.info(
                    "Port {} on {} (Sabafon) is missing its number — attempt {}/{}",
                    index, gw.host, (prior?.count ?: 0) + 1, maxAttemptsPerPort
                )
                val success = hardware.triggerNumberLearning(index, gw.host)
                attempts[key] = Attempt((prior?.count ?: 0) + 1, Instant.now())
                if (success) {
                    log.info("Successfully triggered learning for port {} on {}", index, gw.host)
                } else {
                    log.warn("Failed to trigger learning for port {} on {}", index, gw.host)
                }
            }
        }
    }

    /** يُصفّر العدّاد بعد تدخّل يدوي أو تغيير شريحة. */
    fun resetAttempts(gatewayId: java.util.UUID, port: Int) {
        attempts.remove("$gatewayId#$port")
    }
}
