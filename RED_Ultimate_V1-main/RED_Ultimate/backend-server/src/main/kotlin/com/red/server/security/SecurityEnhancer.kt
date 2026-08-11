package com.red.server.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Backend security interceptor: response headers, simple in-memory rate limiting and validators. */
@Component
class SecurityEnhancer(
    @Value("\${red.trust-x-forwarded-for:false}")
    private val trustXForwardedFor: Boolean = false
) : HandlerInterceptor {
    private val rateLimiter = ConcurrentHashMap<String, RateLimitEntry>()

    data class RateLimitEntry(
        var count: Int = 0,
        var lastReset: Long = System.currentTimeMillis()
    )

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
        val key = "rate:${getClientIp(request)}"
        val now = System.currentTimeMillis()
        val entry = rateLimiter.compute(key) { _, current ->
            val value = current ?: RateLimitEntry()
            if (now - value.lastReset >= WINDOW_MS) {
                value.count = 1
                value.lastReset = now
            } else {
                value.count += 1
            }
            value
        } ?: return true
        return entry.count <= MAX_REQUESTS_PER_MINUTE
    }

    private fun validateRequest(request: HttpServletRequest) {
        val query = request.queryString.orEmpty().lowercase()
        val suspicious = listOf("<script", "javascript:", "data:", "blob:")
        if (suspicious.any(query::contains)) {
            // Hook for structured security audit logging.
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
        val entry = rateLimiter["lockout:$ip"] ?: return false
        val now = System.currentTimeMillis()
        if (now - entry.lastReset >= LOCKOUT_DURATION_MS) {
            rateLimiter.remove("lockout:$ip")
            return false
        }
        return entry.count >= MAX_FAILED_ATTEMPTS
    }

    fun recordFailedAttempt(ip: String) {
        val now = System.currentTimeMillis()
        rateLimiter.compute("lockout:$ip") { _, current ->
            val entry = current ?: RateLimitEntry(lastReset = now)
            if (now - entry.lastReset >= LOCKOUT_DURATION_MS) {
                entry.count = 1
                entry.lastReset = now
            } else {
                entry.count += 1
            }
            entry
        }
    }

    fun clearFailedAttempts(ip: String) {
        rateLimiter.remove("lockout:$ip")
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
        const val WINDOW_MS = 60_000L
        const val LOCKOUT_DURATION_MS = 300_000L
    }
}
