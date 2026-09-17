package com.red.server.security

import com.red.server.database.RedisManager
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletRequestWrapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.util.ContentCachingRequestWrapper
import java.nio.charset.StandardCharsets
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
    @Value("\${red.security.allowed-origins:http://localhost,http://127.0.0.1}")
    private val configuredAllowedOrigins: String = "http://localhost,http://127.0.0.1",
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
        validateRequest(request).let { valid ->
            if (!valid) {
                response.status = HttpStatus.FORBIDDEN.value()
                response.contentType = "application/json"
                response.writer.write(
                    """{"error":"XSS_DETECTED","message":"Request blocked due to suspicious content."}"""
                )
                return false
            }
        }
        // Defense-in-depth for the admin HttpOnly cookie flow (red_admin_refresh):
        // SameSite=Strict + double-submit CSRF are primary; a mismatched Origin/Referer
        // is a fallback signal only when the cookie is actually present. Missing
        // Origin+Referer is allowed so non-browser callers without those headers keep working.
        if (!checkAdminCookieOrigin(request)) {
            log.warn(
                "Admin cookie Origin/Referer BLOCKED: ip={} uri={} origin={} referer={} time={}",
                getClientIp(request), request.requestURI, request.getHeader("Origin"), request.getHeader("Referer"), Instant.now()
            )
            response.status = HttpStatus.FORBIDDEN.value()
            response.contentType = "application/json"
            response.writer.write(
                """{"error":"ORIGIN_BLOCKED","message":"Request blocked due to origin mismatch."}"""
            )
            return false
        }
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

    private fun validateRequest(request: HttpServletRequest): Boolean {
        val query = request.queryString.orEmpty().lowercase()
        val uri = request.requestURI.lowercase()
        val suspicious = listOf("<script", "javascript:", "data:", "blob:", "onerror=", "onload=", "eval(", "expression(")
        fun block(source: String, pattern: String): Boolean {
            log.warn(
                "XSS attempt BLOCKED: ip={} uri={} source={} pattern={} time={}",
                getClientIp(request), request.requestURI, source, pattern, Instant.now()
            )
            return false
        }
        fun containsSuspicious(text: String): String? =
            suspicious.firstOrNull(text::contains)
        // URI + query (as before).
        containsSuspicious("$query $uri")?.let { return block("uri-query", it) }
        // Form/urlencoded bodies surface via parameterMap without consuming the stream.
        runCatching {
            request.parameterMap.values.flatMap { it.asList() }.forEach { value ->
                containsSuspicious(value.lowercase())?.let { return block("param", it) }
            }
        }
        // JSON/text bodies: only when SecurityConfig wrapped the request in a
        // ContentCachingRequestWrapper (small bodies ≤32KB, never multipart), so
        // reading the cache replays downstream instead of consuming the stream.
        containsSuspicious(readCachedBody(request))?.let { return block("body", it) }
        return true
    }

    private fun readCachedBody(request: HttpServletRequest): String {
        val wrapper = findCachingWrapper(request) ?: return ""
        val contentType = request.contentType?.lowercase().orEmpty()
        if (contentType.contains("multipart")) return ""
        val inspectable = contentType.contains("json") ||
            contentType.contains("text") ||
            contentType.contains("xml") ||
            contentType.contains("urlencoded")
        if (!inspectable) return ""
        val length = request.contentLengthLong
        if (length <= 0 || length > MAX_BODY_INSPECT_BYTES) return ""
        // Populate the cache; downstream @RequestBody still works because the
        // wrapper replays cached bytes on subsequent getInputStream() calls.
        runCatching { wrapper.inputStream.readAllBytes() }
        val bytes = runCatching { wrapper.contentAsByteArray }.getOrNull() ?: return ""
        if (bytes.isEmpty()) return ""
        val capped = bytes.copyOf(minOf(bytes.size, MAX_BODY_INSPECT_BYTES))
        return String(capped, StandardCharsets.UTF_8).lowercase()
    }

    private fun findCachingWrapper(request: HttpServletRequest): ContentCachingRequestWrapper? {
        var current: HttpServletRequest = request
        var hops = 0
        while (current is HttpServletRequestWrapper && hops++ < 8) {
            if (current is ContentCachingRequestWrapper) return current
            current = current.request as? HttpServletRequest ?: return null
        }
        return null
    }

    private fun checkAdminCookieOrigin(request: HttpServletRequest): Boolean {
        val hasAdminCookie = request.cookies?.any { it.name == ADMIN_REFRESH_COOKIE && it.value.isNotBlank() } == true
        if (!hasAdminCookie) return true
        val allowed = allowedOrigins()
        if (allowed.isEmpty()) return true
        request.getHeader("Origin")?.trim()?.takeIf { it.isNotEmpty() }?.let { origin ->
            return allowed.any { originMatches(it, origin) }
        }
        request.getHeader("Referer")?.trim()?.takeIf { it.isNotEmpty() }?.let { referer ->
            return allowed.any { refererMatches(it, referer) }
        }
        // No Origin and no Referer (curl, mobile, same-origin navigation): allow.
        return true
    }

    private fun allowedOrigins(): List<String> =
        configuredAllowedOrigins.split(',').map { it.trim().trimEnd('/').lowercase() }.filter { it.isNotEmpty() }

    private fun originMatches(pattern: String, origin: String): Boolean {
        val o = origin.trim().trimEnd('/').lowercase()
        if (!pattern.contains('*')) return o == pattern
        return Regex("^" + Regex.escape(pattern).replace("\\*", ".*") + "$").matches(o)
    }

    private fun refererMatches(pattern: String, referer: String): Boolean {
        val refererOrigin = extractOrigin(referer) ?: return false
        return originMatches(pattern, refererOrigin)
    }

    private fun extractOrigin(referer: String): String? {
        val lower = referer.trim().lowercase()
        val schemeEnd = lower.indexOf("://").takeIf { it >= 0 } ?: return null
        val pathStart = lower.indexOf('/', schemeEnd + 3)
        return if (pathStart >= 0) lower.substring(0, pathStart) else lower
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
        /** Must stay in sync with SecurityConfig.BODY_CACHE_LIMIT_BYTES (wrapper cache cap). */
        const val MAX_BODY_INSPECT_BYTES = 32 * 1024
        /** Same name as AuthController.ADMIN_REFRESH_COOKIE (private there); kept literal to avoid coupling. */
        private const val ADMIN_REFRESH_COOKIE = "red_admin_refresh"
    }
}
