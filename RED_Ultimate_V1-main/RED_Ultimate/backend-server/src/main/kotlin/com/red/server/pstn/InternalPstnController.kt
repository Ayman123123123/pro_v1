package com.red.server.pstn

import jakarta.servlet.http.HttpServletRequest
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
 * - يتم التحقق من IP المصدر (اختياري)
 */
@RestController
@RequestMapping("/api/internal/pstn")
class InternalPstnController(
    private val listener: DinstarEventListener,
    // لا قيمة افتراضية: سرّ معلن في المستودع = أي مضيف على الشبكة يحقن أحداث CDR/مكالمات فائتة.
    @Value("\${pstn.internal-secret:}") private val internalSecret: String,
    @Value("\${pstn.internal-allowed-ips:}") private val allowedIps: String
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
        @RequestBody payload: IncomingPayload,
        request: HttpServletRequest
    ): ResponseEntity<Map<String, Any?>> {
        val clientIp = request.remoteAddr
        log.debug("Internal PSTN attempt from IP: {}", clientIp)

        if (internalSecret.isBlank() || secret != internalSecret) {
            log.warn("internal.pstn unauthorized attempt: wrong secret from IP={}", clientIp)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "BAD_SECRET"))
        }

        // IP validation (if configured)
        if (allowedIps.isNotBlank()) {
            val allowedList = allowedIps.split(",").map { it.trim() }
            if (clientIp !in allowedList && "0.0.0.0" !in allowedList) {
                log.warn("internal.pstn unauthorized attempt: IP {} not in allowed list", clientIp)
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to "IP_NOT_ALLOWED"))
            }
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
