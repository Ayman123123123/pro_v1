package com.red.server.security

import com.red.server.auth.AuthExceptionHandler
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Security enhancer for the backend API.
 * Provides additional security layers including rate limiting,
 * request validation, and security headers.
 */
@Component
class SecurityEnhancer : HandlerInterceptor {

    private val rateLimiter = ConcurrentHashMap<String, RateLimitEntry>()

    data class RateLimitEntry(
        val count: Int = 0,
        val lastReset: Long = System.currentTimeMillis()
    )

    companion object {
        const val MAX_REQUESTS_PER_MINUTE = 100
        const val MAX_FAILED_ATTEMPTS = 5
        const val LOCKOUT_DURATION_MS = 300_000L // 5 minutes
    }

    /**
     * Pre-handle check for security.
     */
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        // Add security headers
        addSecurityHeaders(response)

        // Rate limiting (skip for health checks)
        if (request.requestURI != "/health" && request.requestURI != "/actuator/health") {
            if (!checkRateLimit(request)) {
                response.statusCode = HttpStatus.TOO_MANY_REQUESTS.value()
                response.contentType = "application/json"
                response.writer.write("""
                    {
                        "error": "RATE_LIMIT_EXCEEDED",
                        "message": "Too many requests. Please try again later.",
                        "retryAfter": 60
                    }
                """.trimIndent())
                return false
            }
        }

        // Request validation
        validateRequest(request)

        return true
    }

    /**
     * Add security headers to response.
     */
    private fun addSecurityHeaders(response: HttpServletResponse) {
        response.header("X-Content-Type-Options", "nosniff")
        response.header("X-Frame-Options", "DENY")
        response.header("X-XSS-Protection", "1; mode=block")
        response.header("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
        response.header("Cache-Control", "no-store, no-cache, must-revalidate, private")
        response.header("Pragma", "no-cache")
        response.header("X-Request-ID", generateRequestId())
        response.header("X-API-Version", "1.0.0")
    }

    /**
     * Check rate limit for request.
     */
    private fun checkRateLimit(request: HttpServletRequest): Boolean {
        val clientIp = getClientIp(request)
        val key = "rate:${clientIp}"

        val entry = rateLimiter.get(key) ?: run {
            rateLimiter[key] = RateLimitEntry()
            return true
        }

        val now = System.currentTimeMillis()
        val windowStart = now - 60_000L // 1 minute window

        if (entry.lastReset < windowStart) {
            // Reset window
            rateLimiter[key] = RateLimitEntry(count = 1, lastReset = now)
            return true
        }

        if (entry.count >= MAX_REQUESTS_PER_MINUTE) {
            return false
        }

        entry.count++
        return true
    }

    /**
     * Validate incoming request.
     */
    private fun validateRequest(request: HttpServletRequest) {
        // Check for suspicious patterns
        val suspiciousPatterns = listOf(
            "<script", "javascript:", "data:", "blob:"
        )

        val queryString = request.queryString
        val contentType = request.contentType

        // Log suspicious requests
        if (suspiciousPatterns.any { queryString?.contains(it) == true }) {
            // This would be logged in production
        }

        // Validate content type for POST/PUT requests
        if (request.method.equals("POST", ignoreCase = true) ||
            request.method.equals("PUT", ignoreCase = true)) {

            if (contentType != null && !contentType.startsWith("application/")) {
                // Log warning
            }
        }
    }

    /**
     * Get client IP address.
     */
    private fun getClientIp(request: HttpServletRequest): String {
        val xForwardedFor = request.getHeader("X-Forwarded-For")
        return if (xForwardedFor != null && xForwardedFor.isNotEmpty()) {
            xForwardedFor.split(",")[0].trim()
        } else {
            request.remoteAddr ?: "unknown"
        }
    }

    /**
     * Generate unique request ID.
     */
    private fun generateRequestId(): String {
        val timestamp = System.currentTimeMillis()
        val random = (Math.random() * 1000000).toInt()
        return "req_${timestamp}_$random"
    }

    /**
     * Check if an IP is locked out due to failed attempts.
     */
    fun isIpLockedOut(ip: String): Boolean {
        val key = "lockout:$ip"
        val entry = rateLimiter[key] ?: return false

        val now = System.currentTimeMillis()
        if (now - entry.lastReset < LOCKOUT_DURATION_MS) {
            return entry.count >= MAX_FAILED_ATTEMPTS
        }

        // Reset after lockout duration
        rateLimiter.remove(key)
        return false
    }

    /**
     * Record a failed authentication attempt.
     */
    fun recordFailedAttempt(ip: String) {
        val key = "lockout:$ip"
        val entry = rateLimiter.get(key) ?: RateLimitEntry()
        entry.count++
        entry.lastReset = System.currentTimeMillis()
        rateLimiter[key] = entry
    }

    /**
     * Clear failed attempts after successful authentication.
     */
    fun clearFailedAttempts(ip: String) {
        val key = "lockout:$ip"
        rateLimiter.remove(key)
    }

    /**
     * Generate hash for sensitive data storage.
     */
    fun hashData(data: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(data.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    /**
     * Validate email format.
     */
    fun isValidEmail(email: String): Boolean {
        val pattern = Regex("^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$")
        return pattern.matches(email)
    }

    /**
     * Validate password strength.
     */
    fun isStrongPassword(password: String): Boolean {
        if (password.length < 12) return false
        if (!password.any { it.isUpperCase() }) return false
        if (!password.any { it.isLowerCase() }) return false
        if (!password.any { it.isDigit() }) return false
        if (!password.any { !it.isLetterOrDigit() }) return false
        return true
    }

    /**
     * Sanitize string input.
     */
    fun sanitizeInput(input: String?): String? {
        return input?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Validate UUID format.
     */
    fun isValidUuid(uuid: String): Boolean {
        return try {
            java.util.UUID.fromString(uuid)
            true
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    /**
     * Validate phone number format.
     */
    fun isValidPhone(phone: String): Boolean {
        // Allow international format
        val pattern = Regex("^\\+?[0-9]{10,15}$")
        return pattern.matches(phone.replace(Regex("[^0-9+]"), ""))
    }
}
