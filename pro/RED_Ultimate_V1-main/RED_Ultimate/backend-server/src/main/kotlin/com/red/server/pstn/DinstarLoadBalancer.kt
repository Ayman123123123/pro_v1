package com.red.server.pstn

import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicInteger

@Service
class DinstarLoadBalancer {
    private val nextSlot = AtomicInteger(1)

    /**
     * اختيار الشريحة التالية (1 إلى 8) لضمان توزيع المكالمات بالتساوي
     * وتجنب حظر الشرائح من قبل شركات الاتصال (اليمن موبايل/سبأفون)
     */
    fun getOptimalSlot(): Int {
        val slot = nextSlot.getAndIncrement()
        if (slot > 8) {
            nextSlot.set(1)
            return 1
        }
        return slot
    }
}
