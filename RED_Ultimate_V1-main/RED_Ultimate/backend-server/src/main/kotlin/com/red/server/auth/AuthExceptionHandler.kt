package com.red.server.auth

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class AuthExceptionHandler {
    @ExceptionHandler(RateLimitExceededException::class)
    fun rateLimited(): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(mapOf("error" to "RATE_LIMITED"))

    @ExceptionHandler(InvalidCredentialsException::class)
    fun invalidCredentials(): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "INVALID_CREDENTIALS"))

    @ExceptionHandler(InvalidRecoveryCodeException::class)
    fun invalidRecovery(): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "INVALID_RECOVERY_CODE"))

    @ExceptionHandler(InvalidRefreshTokenException::class)
    fun invalidRefresh(): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "INVALID_REFRESH_TOKEN"))

    @ExceptionHandler(RefreshTokenReuseException::class)
    fun refreshReuse(): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "REFRESH_TOKEN_REUSE_DETECTED"))

    @ExceptionHandler(IllegalArgumentException::class)
    fun badRequest(error: IllegalArgumentException): ResponseEntity<Map<String, String>> =
        ResponseEntity.badRequest().body(mapOf("error" to (error.message ?: "INVALID_REQUEST")))

    @ExceptionHandler(NoSuchElementException::class)
    fun notFound(error: NoSuchElementException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to (error.message ?: "NOT_FOUND")))

    /** IllegalStateException — حالة غير صالحة (مثل POLL_NOT_ACTIVE / ALREADY_VOTED) → 409 Conflict */
    @ExceptionHandler(IllegalStateException::class)
    fun conflict(error: IllegalStateException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to (error.message ?: "CONFLICT")))

    /** ClassCastException — جسم مشوَّه (تحويل غير آمن) → 400 بدل 500 */
    @ExceptionHandler(ClassCastException::class)
    fun badCast(error: ClassCastException): ResponseEntity<Map<String, String>> =
        ResponseEntity.badRequest().body(mapOf("error" to "INVALID_REQUEST_BODY"))

    /** NumberFormatException — رقم مشوَّه (مثل toInt() على نص) → 400 بدل 500 */
    @ExceptionHandler(NumberFormatException::class)
    fun badNumber(error: NumberFormatException): ResponseEntity<Map<String, String>> =
        ResponseEntity.badRequest().body(mapOf("error" to "INVALID_NUMBER_FORMAT"))

    /** NullPointerException — مرجع null غير متوقع → 400 بدل 500 (لا تسريب stack trace) */
    @ExceptionHandler(NullPointerException::class)
    fun badNull(error: NullPointerException): ResponseEntity<Map<String, String>> =
        ResponseEntity.badRequest().body(mapOf("error" to "INVALID_REQUEST"))
}
