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
        val userId = UUID.fromString(authentication.name)
        val user = calls.getUser(userId)
        val isAdmin = authentication.authorities.any { it.authority == "ROLE_ADMIN" }
        // الادمن له مسار حرّ عبر /api/admin/dinstar/calls بلا حد يومي، لكنه قد
        // ينادي هذا المسار من لوحة DinstarControl القديمة. الحد 0 كان يُسقطه
        // حتى بعد تفعيل pstn_enabled (حالة 85204: enabled=true و limit=0).
        if (!isAdmin && (!user.pstnEnabled || user.pstnDailyLimit <= 0)) {
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
