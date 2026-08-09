package com.red.server.pstn

import com.red.server.calls.CallHistoryController
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
     * إنهاء مكالمة — يُحرّر المنفذ في Load Balancer
     */
    @PostMapping("/calls/{callId}/hangup")
    fun hangup(@PathVariable callId: String, @RequestBody body: Map<String, Int>?): ResponseEntity<Map<String, Any>> {
        val port = body?.get("port") ?: -1
        if (port in 0..7) {
            loadBalancer.releasePort(port)
        }
        return ResponseEntity.ok(mapOf("status" to "HUNG_UP", "callId" to callId, "port" to port))
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
