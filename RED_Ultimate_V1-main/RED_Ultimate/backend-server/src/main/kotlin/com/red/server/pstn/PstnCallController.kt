package com.red.server.pstn

import com.red.server.calls.CallHistoryController
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

/**
 * 📞 YOUNES PSTN Call Controller — نقاط نهاية REST للمكالمات الخطية
 *
 * تدفق المكالمة:
 * 1. POST /api/pstn/calls — بدء مكالمة جديدة
 * 2. POST /api/pstn/calls/{callId}/hangup — إنهاء مكالمة (يُحرّر المنفذ)
 * 3. GET /api/pstn/status — حالة المكالمات النشطة
 */
@RestController
@RequestMapping("/api/pstn")
class PstnCallController(
    private val calls: PstnCallService,
    private val loadBalancer: DinstarLoadBalancer,
    private val pstnManager: PstnManager
) {
    @PostMapping("/calls")
    fun dial(@RequestBody request: PstnCallRequest, authentication: Authentication): ResponseEntity<Any> {
        val userId = UUID.fromString(authentication.name)
        val user = calls.getUser(userId)
        if (!user.pstnEnabled || user.pstnDailyLimit <= 0) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to "PSTN_NOT_ENABLED"))
        }
        if (calls.hasActiveCall(userId)) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to "ALREADY_IN_PSTN_CALL"))
        }
        // الربط الدائم 1:1 — المنفذ يحدده الأدمن فقط، لا يسمح للمستخدم باختياره
        if (request.slotIndex != null) {
            org.slf4j.LoggerFactory.getLogger(javaClass).warn("User {} attempted to specify slotIndex {} — ignored (admin-only binding)", user.redId, request.slotIndex)
        }
        val result = calls.dial(userId, request.number, null)
        return ResponseEntity.ok(result)
    }

    /**
     * إنهاء مكالمة — يُحرّر المنفذ في Load Balancer.
     *
     * ⚠️ الأمان: لا يُحرَّر إلا المنفذ المرتبط فعلياً بمكالمة المستخدم نفسه
     * (يُربط في لحظة النداء). منعاً لأي مستخدم من تحرير منافذ الغير أو
     * إعادة ضبط عدّادات الاستخدام عبر الأسطول.
     * يتم التحقق من callId لمنع التلاعب.
     */
    @PostMapping("/calls/{callId}/hangup")
    fun hangup(@PathVariable callId: String, @RequestBody body: Map<String, Int>?, authentication: Authentication): ResponseEntity<Map<String, Any>> {
        val userId = UUID.fromString(authentication.name)
        val bound = calls.resolveActiveCall(userId)
            ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to "NO_ACTIVE_PSTN_CALL"))

        if (bound.first != callId) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to "CALL_ID_MISMATCH"))
        }

        val requestedPort = body?.get("port") ?: -1
        if (requestedPort >= 0 && requestedPort != bound.second) {
            // منفذ مقدَّم لا يخصّ هذه المكالمة — نرفض بدل تحرير منفذ اعتباطي.
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to "PORT_MISMATCH"))
        }

        loadBalancer.releasePort(bound.third, bound.second)
        // Actually hang up the GSM leg via AMI — without this the GSM side keeps ringing
        runCatching { pstnManager.hangupCall(bound.first) }
        calls.clearActive(userId)
        return ResponseEntity.ok(mapOf("status" to "HUNG_UP", "callId" to callId, "port" to bound.second))
    }

    /**
     * حالة المكالمات النشطة عبر PSTN للمستخدم الحالي.
     * يعيد: مفعّل/معطل، الحد اليومي، المستهلك اليوم، مكالمة نشطة أم لا، المسار.
     */
    @GetMapping("/status")
    fun status(authentication: Authentication): ResponseEntity<Map<String, Any>> {
        val userId = UUID.fromString(authentication.name)
        return ResponseEntity.ok(calls.getPstnStatus(userId))
    }

}

/**
 * @param slotIndex منفذ/شريحة محددة يطلبها العميل (اختياري).
 *                 null = الاختيار التلقائي الذكي عبر موزّع الأحمال.
 */
data class PstnCallRequest(val number: String, val slotIndex: Int? = null)
