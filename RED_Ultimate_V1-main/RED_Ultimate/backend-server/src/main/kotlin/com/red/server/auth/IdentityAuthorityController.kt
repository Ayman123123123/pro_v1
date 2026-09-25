package com.red.server.auth

import com.red.server.auth.security.DeviceCertificateService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

@RestController
@RequestMapping("/api/identity")
class IdentityAuthorityController(
    private val certificates: DeviceCertificateService,
    // nullable افتراضيًا للتوافق — يحقن Spring القيمة في الإنتاج.
    private val limits: RateLimitService? = null,
    @Value("\${red.trust-x-forwarded-for:false}") private val trustXForwardedFor: Boolean = false
) {
    @GetMapping("/authority")
    fun authority(request: HttpServletRequest) = run {
        // عام (permitAll) بلا مصادقة: الحد بالـ IP فقط (60/10د) ضد الإغراق —
        // المفتاح العام غير حساس لكن قراءته من القرص/الإعدادات مكلفة.
        limits?.check("identity-authority", clientIp(request), 60L, Duration.ofMinutes(10))
        mapOf(
            "algorithm" to "ECDSA_P256_SHA256",
            "version" to "v1",
            "publicKey" to certificates.authorityPublicKey()
        )
    }

    private fun clientIp(request: HttpServletRequest): String {
        if (trustXForwardedFor) {
            val xff = request.getHeader("X-Forwarded-For")
            if (!xff.isNullOrBlank()) {
                return xff.substringBefore(',').trim().takeIf { it.isNotEmpty() } ?: request.remoteAddr
            }
        }
        return request.remoteAddr ?: "unknown"
    }
}
