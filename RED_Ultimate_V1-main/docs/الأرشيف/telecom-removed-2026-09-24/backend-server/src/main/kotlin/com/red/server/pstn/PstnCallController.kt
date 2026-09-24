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
    private val calls: PstnCallService
) {
    @PostMapping("/calls")
    fun dial(@RequestBody request: PstnCallRequest, authentication: Authentication): ResponseEntity<Any> {
        return ResponseEntity.status(HttpStatus.GONE).body(
            mapOf(
                "error" to "PSTN_LEGACY_NO_MEDIA",
                "message" to "Use /api/pstn/bridge with a SIP/WebRTC client for two-way audio"
            )
        )
    }

    /**
     * إنهاء مكالمة هاتف يمني — لا يقبل منفذًا من العميل.
     *
     * كان المنفذ يُقرأ من جسم الطلب — أي يختاره العميل — ويُحرَّر بلا
     * تحديد بوابة، فيُحرِّر منفذًا يحمل الرقم نفسه في بوابة أخرى؛ ولم
     * يكن هناك تحقق من الهوية أصلًا. الآن يحتفظ الخادم بالبوابة
     * والمنفذ اللذين خصصهما، وتتحقق الخدمة من ملكية سجل المكالمة قبل
     * التحرير، وتُنهي قيد المتتبِّع، وتُسقِط ساق GSM عبر AMI في الوقت نفسه.
     */
    @PostMapping("/calls/{callId}/hangup")
    fun hangup(@PathVariable callId: String, authentication: Authentication): ResponseEntity<PstnHangupResponse> =
        ResponseEntity.ok(calls.hangup(UUID.fromString(authentication.name), callId))

    /**
     * الرد على مكالمة PSTN واردة — POST /api/pstn/calls/{callId}/answer (Task 8).
     * يتحقق من الملكية ثم يُسجَّل الرد في سجل المكالمات.
     */
    @PostMapping("/calls/{callId}/answer")
    fun answer(@PathVariable callId: String, authentication: Authentication): ResponseEntity<Map<String, Any>> =
        ResponseEntity.ok(calls.answer(UUID.fromString(authentication.name), callId))

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
 * @param slotIndex منفذ/شريحة محددة يطلبها العميل (اختياري، قديم — استخدم port).
 *                 null = الاختيار التلقائي الذكي عبر موزّع الأحمال.
 * @param port الاسم الجديد لحقل المنفذ القسري (Task 7) — يُفضَّل على slotIndex.
 * @param gateway مضيف البوابة القسرية (Task 7) — إلزامي عملياً مع أسطول
 *               متعدد الأجهزة حيث يتكرر رقم المنفذ على كل جهاز.
 */
data class PstnCallRequest(
    val number: String,
    val slotIndex: Int? = null,
    val port: Int? = null,
    val gateway: String? = null
)
