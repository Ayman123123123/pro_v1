package com.red.server.auth

import com.red.server.auth.security.JwtService
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.util.UUID

@RestController
@RequestMapping("/api/devices/{deviceId}/prekeys")
class OneTimePreKeyController(
    private val service: OneTimePreKeyService,
    private val jwt: JwtService,
    // nullable افتراضيًا للتوافق مع البناء اليدوي في الاختبارات — يحقن Spring القيمة في الإنتاج.
    private val limits: RateLimitService? = null
) {
    @PostMapping
    fun upload(
        @PathVariable deviceId: UUID,
        @RequestBody request: PreKeyUploadRequest,
        authentication: Authentication
    ): PreKeyStockResponse {
        // الرفع يكتب في DB (حتى 100 مفتاح/نداء): 30/ساعة بهوية المتصل ضد تضخيم التخزين.
        limits?.check("prekeys-upload", authentication.name, 30L, Duration.ofHours(1))
        requireAuthenticatedDevice(authentication, deviceId)
        return service.upload(UUID.fromString(authentication.name), deviceId, request)
    }

    @GetMapping("/stock")
    fun stock(@PathVariable deviceId: UUID, authentication: Authentication): PreKeyStockResponse {
        limits?.check("prekeys-stock", authentication.name, 120L, Duration.ofMinutes(10))
        requireAuthenticatedDevice(authentication, deviceId)
        return service.stock(UUID.fromString(authentication.name), deviceId)
    }

    private fun requireAuthenticatedDevice(authentication: Authentication, requested: UUID) {
        // الهوية من المصادقة حصرًا: الجهاز من details (وضعه الفلتر بلا إعادة
        // تحليل)، والسقوط على تحليل credentials انتقاليًا للتوافق.
        val bound = (authentication.details as? String)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: (authentication.credentials as? String)
                ?.let { runCatching { jwt.deviceId(it) }.getOrNull() }
            ?: throw IllegalArgumentException("Device token required")
        require(bound == requested) { "A device may only manage its own pre-keys" }
    }
}
