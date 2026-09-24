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
    @Value("\${pstn.internal-secret:red-internal-pstn-secret}") private val internalSecret: String,
    @Value("\${pstn.internal-allowed-ips:}") private val allowedIps: String
) {
    companion object {
        private val log = LoggerFactory.getLogger(InternalPstnController::class.java)

        /** IPv4 CIDR containment without extra dependencies. Non-IPv4 input → false. */
        fun cidrContains(cidr: String, ip: String): Boolean = runCatching {
            val parts = cidr.split("/")
            require(parts.size == 2) { "Not CIDR" }
            val mask = parts[1].toInt()
            require(mask in 0..32) { "Bad mask" }
            val net = toInt(parts[0])
            val addr = toInt(ip)
            if (mask == 0) return true
            (net shr (32 - mask)) == (addr shr (32 - mask))
        }.getOrDefault(false)

        private fun toInt(ipv4: String): Int {
            val o = ipv4.split(".")
            require(o.size == 4) { "Not IPv4" }
            return o.fold(0) { acc, s ->
                val b = s.toInt()
                require(b in 0..255) { "Bad octet" }
                (acc shl 8) or b
            }
        }
    }

    data class IncomingPayload(
        val caller: String?,
        val called: String?,
        val channel: String?,
        val gatewayHost: String?,
        /**
         * فهرس المنفذ الخلوي الحقيقي كما حلّه [dinstar-resolve-port] من
         * ترويسة Via. `null` أو قيمة سالبة = غير معروف، فيعود الخادم إلى
         * الاستخراج من اسم القناة.
         *
         * بلا هذا الحقل كان كل وارد يفقد منفذه: INVITE من البوابة بلا
         * مصادقة فيُطابَق بالعنوان على النظير العام (بلا set_var)، وDocker
         * يترجم العنوان، واسم القناة يحمل عدّاد أستيريكس لا رقم المنفذ.
         */
        val port: Int? = null
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

        // IP validation (if configured). Supports exact IPs AND CIDR ranges
        // (e.g. 172.16.0.0/12). NOTE: exact-string compare alone silently
        // rejects every Docker-bridged caller (e.g. 172.19.0.11 vs the
        // "172.16.0.0/12" entry) — that bug dropped ALL inbound GSM
        // notifications with 403 while the SIP leg waited in silence.
        if (allowedIps.isNotBlank()) {
            val allowedList = allowedIps.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val allowed = allowedList.any { rule ->
                rule == "0.0.0.0" || rule == clientIp || (rule.contains("/") && cidrContains(rule, clientIp))
            }
            if (!allowed) {
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
            gatewayHost = payload.gatewayHost,
            portHint = payload.port?.takeIf { it in 0..31 }
        )
        return ResponseEntity.ok(mapOf("accepted" to accepted))
    }
}
