package com.red.server.pstn

import com.red.server.services.DinstarHardwareService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicInteger

@Service
class DinstarLoadBalancer(private val hardware: DinstarHardwareService) {
    companion object { private val log = LoggerFactory.getLogger(DinstarLoadBalancer::class.java) }

    private val nextSlot = AtomicInteger(0)

    /**
     * اختيار الشريحة التالية (0 إلى 7) لضمان توزيع المكالمات بالتساوي
     * وتجنب حظر الشرائح من قبل شركات الاتصال (اليمن موبايل/سبأفون)
     * 
     * Uses round-robin with modulo for thread-safe rotation.
     */
    fun getOptimalSlot(): Int {
        val slot = nextSlot.getAndIncrement() % 8
        return slot
    }

    /**
     * توزيع ذكي: يختار الشريحة ذات أفضل إشارة مسجّلة
     * ويتجنب الشرائح في حالة通话 أو غير مسجّلة
     */
    fun getOptimalSlotBySignal(): Int {
        val ports = runCatching { hardware.getHardwareStatus() }.getOrNull() ?: return getOptimalSlot()
        
        val candidates = ports.filter { port ->
            val status = port["status"]?.toString()
            val callState = port["callState"]?.toString()
            status == "REGISTERED" && callState != "ACTIVE"
        }
        
        if (candidates.isEmpty()) {
            log.warn("No registered idle SIM slots found, falling back to round-robin")
            return getOptimalSlot()
        }
        
        return candidates.maxByOrNull { (it["signal"] as? Number)?.toInt() ?: 0 }
            ?.let { (it["index"] as? Number)?.toInt() ?: getOptimalSlot() }
            ?: getOptimalSlot()
    }
}
