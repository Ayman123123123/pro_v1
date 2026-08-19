package com.red.server.pstn

import com.red.server.calls.CallHistoryController
import org.springframework.beans.factory.annotation.Value
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
    @Value("\${red.dinstar.enabled:false}") private val dinstarEnabled: Boolean = false
) {
    @PostMapping("/calls")
    fun dial(@RequestBody request: PstnCallRequest, authentication: Authentication): ResponseEntity<PstnCallResponse> {
        val result = calls.dial(UUID.fromString(authentication.name), request.number)
        return ResponseEntity.ok(result)
    }

    /**
     * إنهاء مكالمة — لا يقبل منفذًا من العميل؛ الخادم يحتفظ بالبوابة والمنفذ
     * اللذين خصصهما للمكالمة ويتحقق من صاحبها قبل التحرير.
     */
    @PostMapping("/calls/{callId}/hangup")
    fun hangup(@PathVariable callId: String, authentication: Authentication): ResponseEntity<PstnHangupResponse> =
        ResponseEntity.ok(calls.hangup(UUID.fromString(authentication.name), callId))

    /**
     * حالة المكالمات النشطة عبر PSTN
     */
    @GetMapping("/status")
    fun status(): ResponseEntity<Map<String, Any>> = ResponseEntity.ok(
        mapOf(
            "active" to dinstarEnabled,
            "status" to if (dinstarEnabled) "CONFIGURED" else "HARDWARE_DISABLED",
            "route" to "Asterisk→PJSIP→DINSTAR"
        )
    )
}

data class PstnCallRequest(val number: String)
