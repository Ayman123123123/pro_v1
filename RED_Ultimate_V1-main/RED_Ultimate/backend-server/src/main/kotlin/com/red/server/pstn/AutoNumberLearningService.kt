package com.red.server.pstn

import com.red.server.services.DinstarFleetService
import com.red.server.services.DinstarHardwareService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

/**
 * خدمة الأتمتة لتعلّم أرقام الشرائح.
 *
 * قد يرسل التعلّم SMS أو ينشئ مكالمة بحسب إعداد البوابة والمشغل، ولذلك يبقى
 * **معطلاً افتراضيًا**. يُفعّل فقط بعد تفويض صريح ومحدد للتكلفة والنطاق عبر
 * `RED_DINSTAR_NUMBER_LEARNING_AUTO_ENABLED=true`.
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
    private val hardware: DinstarHardwareService
) {
    companion object {
        private val log = LoggerFactory.getLogger(AutoNumberLearningService::class.java)
        private const val CHECK_INTERVAL_MINUTES = 60L
    }

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

                // التركيز على سبأفون لأنها الأكثر تطلباً لهذه الميزة
                if (operator == "Sabafon" && (number.isNullOrBlank() || number == "null")) {
                    log.info("Port {} on {} (Sabafon) is missing number. Triggering learning...", index, gw.host)
                    val success = hardware.triggerNumberLearning(index, gw.host)
                    if (success) {
                        log.info("Successfully triggered learning for port {} on {}", index, gw.host)
                    } else {
                        log.warn("Failed to trigger learning for port {} on {}", index, gw.host)
                    }
                }
            }
        }
    }
}
