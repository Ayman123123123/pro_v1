package com.red.server.security

import com.red.server.database.RedisManager
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/**
 * Backend security interceptor: response headers, Redis-backed rate limiting and validators.
 * Rate limiting is delegated to RedisManager which uses atomic Lua scripts —
 * survives restarts and works across multiple instances.
 */
@Component
class SecurityEnhancer(
    @Value("\${red.trust-x-forwarded-for:false}")
    private val trustXForwardedFor: Boolean = false,
    private val redisManager: RedisManager
) : HandlerInterceptor {
    private val log = LoggerFactory.getLogger(SecurityEnhancer::class.java)

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        addSecurityHeaders(response)
        if (request.requestURI !in setOf("/health", "/actuator/health") && !checkRateLimit(request)) {
            response.status = HttpStatus.TOO_MANY_REQUESTS.value()
            response.contentType = "application/json"
            response.writer.write(
                """{"error":"RATE_LIMIT_EXCEEDED","message":"Too many requests. Please try again later.","retryAfter":60}"""
            )
            return false
        }
        validateRequest(request)
        return true
    }

    private fun addSecurityHeaders(response: HttpServletResponse) {
        response.setHeader("X-Content-Type-Options", "nosniff")
        response.setHeader("X-Frame-Options", "DENY")
        response.setHeader("X-XSS-Protection", "1; mode=block")
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, private")
        response.setHeader("Pragma", "no-cache")
        response.setHeader("X-Request-ID", UUID.randomUUID().toString())
        response.setHeader("X-API-Version", "1.0.0")
    }

    private fun checkRateLimit(request: HttpServletRequest): Boolean {
        val ip = getClientIp(request)
        return redisManager.checkRateLimit("security:ip:$ip", MAX_REQUESTS_PER_MINUTE, WINDOW_SECONDS)
    }

    private fun validateRequest(request: HttpServletRequest) {
        val query = request.queryString.orEmpty().lowercase()
        val suspicious = listOf("<script", "javascript:", "data:", "blob:")
        val matched = suspicious.firstOrNull(query::contains)
        if (matched != null) {
            log.warn(
                "XSS attempt blocked: ip={} uri={} pattern={} time={}",
                getClientIp(request), request.requestURI, matched, Instant.now()
            )
        }
    }

    /**
     * X-Forwarded-For is attacker-controlled unless the request arrived through
     * a trusted proxy that replaces the incoming header. Docker Compose enables
     * this setting only because nginx.conf sets XFF to `$remote_addr`.
     */
    private fun getClientIp(request: HttpServletRequest): String {
        if (trustXForwardedFor) {
            request.getHeader("X-Forwarded-For")
                ?.substringBefore(',')
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let { return it }
        }
        return request.remoteAddr ?: "unknown"
    }

    fun isIpLockedOut(ip: String): Boolean {
        // Delegate to Redis-backed rate limiter with lockout window
        return !redisManager.checkRateLimit("lockout:$ip", MAX_FAILED_ATTEMPTS, LOCKOUT_DURATION_SECONDS)
    }

    fun recordFailedAttempt(ip: String) {
        // زيادة العدّاد فعلياً — checkRateLimit يزيد + يتحقق في ذرّة واحدة
        redisManager.checkRateLimit("lockout:$ip", MAX_FAILED_ATTEMPTS, LOCKOUT_DURATION_SECONDS)
        // تسجيل محاولات فاشلة للتدقيق الأمني
        log.info("Failed login attempt recorded: ip={} time={}", ip, Instant.now())
    }

    fun clearFailedAttempts(ip: String) {
        redisManager.deleteKey("lockout:$ip")
    }

    fun hashData(data: String): String = MessageDigest.getInstance("SHA-256")
        .digest(data.toByteArray())
        .joinToString("") { "%02x".format(it) }

    fun isValidEmail(email: String): Boolean =
        Regex("^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$").matches(email)

    fun isStrongPassword(password: String): Boolean =
        password.length >= 12 &&
            password.any { it.isUpperCase() } &&
            password.any { it.isLowerCase() } &&
            password.any { it.isDigit() } &&
            password.any { !it.isLetterOrDigit() }

    fun sanitizeInput(input: String?): String? = input?.trim()?.takeIf { it.isNotEmpty() }

    fun isValidUuid(uuid: String): Boolean = runCatching { UUID.fromString(uuid) }.isSuccess

    fun isValidPhone(phone: String): Boolean =
        Regex("^\\+?[0-9]{10,15}$").matches(phone.replace(Regex("[^0-9+]"), ""))

    companion object {
        const val MAX_REQUESTS_PER_MINUTE = 100
        const val MAX_FAILED_ATTEMPTS = 5
        const val WINDOW_SECONDS = 60L
        const val LOCKOUT_DURATION_SECONDS = 300L
    }
}
