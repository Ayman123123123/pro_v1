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
    private val loadBalancer: DinstarLoadBalancer
) {
    @PostMapping("/calls")
    fun dial(@RequestBody request: PstnCallRequest, authentication: Authentication): ResponseEntity<PstnCallResponse> {
        val result = calls.dial(UUID.fromString(authentication.name), request.number)
        return ResponseEntity.ok(result)
    }

    /**
     * إنهاء مكالمة — يُحرّر المنفذ في Load Balancer.
     *
     * ⚠️ الأمان: لا يُحرَّر إلا المنفذ المرتبط فعلياً بمكالمة المستخدم نفسه
     * (يُربط في لحظة النداء). منعاً لأي مستخدم من تحرير منافذ الغير أو
     * إعادة ضبط عدّادات الاستخدام عبر الأسطول.
     */
    @PostMapping("/calls/{callId}/hangup")
    fun hangup(@PathVariable callId: String, @RequestBody body: Map<String, Int>?, authentication: Authentication): ResponseEntity<Map<String, Any>> {
        val userId = UUID.fromString(authentication.name)
        val bound = calls.resolveActivePort(userId)
            ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to "NO_ACTIVE_PSTN_CALL"))

        val requestedPort = body?.get("port") ?: -1
        if (requestedPort >= 0 && requestedPort != bound.second) {
            // منفذ مقدَّم لا يخصّ هذه المكالمة — نرفض بدل تحرير منفذ اعتباطي.
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to "PORT_MISMATCH"))
        }

        loadBalancer.releasePort(bound.first, bound.second)
        calls.clearActive(userId)
        return ResponseEntity.ok(mapOf("status" to "HUNG_UP", "callId" to callId, "port" to bound.second))
    }

    /**
     * حالة المكالمات النشطة عبر PSTN
     */
    @GetMapping("/status")
    fun status(): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(mapOf("active" to true, "route" to "Asterisk→PJSIP→DINSTAR"))
    }
}

data class PstnCallRequest(val number: String)
