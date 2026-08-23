package com.red.server.pstn

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * مدخل داخلي لأحداث PSTN من Asterisk dialplan.
 *
 * ## لماذا هذا الموجود أصلاً؟
 * مكتبة asterisk-java **تُسقط أحداث UserEvent المخصصة بصمت** (فشل تحويل
 * الحزمة المخصصة إلى كائن) — ثبت ذلك حياً: NewExten/Dial/Hangup تصل،
 * وUserEvent لا يصل أبداً، فكان الوارد ميته منذ البداية.
 *
 * الحل: dialplan ينادي هذا الـendpoint مباشرة عبر System+curl داخل شبكة
 * Docker الداخلية — مستقل تماماً عن أي محلل أحداث.
 *
 * ## الأمان
 * - الـendpoint غير منشور للخارج (nginx لا يمرر /api/internal)
 * - يتطلب ترويسة X-Internal-Secret مطابقة لـ PSTN_INTERNAL_SECRET
 */
@RestController
@RequestMapping("/api/internal/pstn")
class InternalPstnController(
    private val listener: DinstarEventListener,
    @Value("\${pstn.internal-secret:red-internal-pstn-secret}") private val internalSecret: String
) {
    companion object {
        private val log = LoggerFactory.getLogger(InternalPstnController::class.java)
    }

    data class IncomingPayload(
        val caller: String?,
        val called: String?,
        val channel: String?,
        val gatewayHost: String?
    )

    @PostMapping("/incoming")
    fun incoming(
        @RequestHeader(value = "X-Internal-Secret", required = false) secret: String?,
        @RequestBody payload: IncomingPayload
    ): ResponseEntity<Map<String, Any?>> {
        if (internalSecret.isBlank() || secret != internalSecret) {
            log.warn("internal.pstn unauthorized attempt")
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "BAD_SECRET"))
        }
        val caller = payload.caller?.takeIf { it.isNotBlank() } ?: return ResponseEntity.badRequest()
            .body(mapOf("error" to "caller required"))
        val called = payload.called?.takeIf { it.isNotBlank() }
        val channel = payload.channel?.takeIf { it.isNotBlank() } ?: return ResponseEntity.badRequest()
            .body(mapOf("error" to "channel required"))
        val accepted = listener.handleExternalIncoming(
            caller = caller,
            called = called,
            channel = channel,
            gatewayHost = payload.gatewayHost
        )
        return ResponseEntity.ok(mapOf("accepted" to accepted))
    }
}
