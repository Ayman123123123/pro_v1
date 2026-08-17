package com.red.server.auth

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class AuthExceptionHandler {
    private val log = LoggerFactory.getLogger(AuthExceptionHandler::class.java)
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

    @ExceptionHandler(UnsupportedOperationException::class)
    fun notImplemented(error: UnsupportedOperationException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(mapOf("error" to (error.message ?: "NOT_IMPLEMENTED")))

    @ExceptionHandler(IllegalArgumentException::class)
    fun badRequest(error: IllegalArgumentException): ResponseEntity<Map<String, String>> {
        // تسجيل السبب الفعلي بدل بلعه: «INVALID_REQUEST» بلا سبب كانت تجعل
        // فشل التسجيل لغزًا لا يمكن تشخيصه من الطرفين.
        val message = error.message?.takeIf { it.isNotBlank() }
        if (message == null) log.warn("IllegalArgumentException without message", error) else log.info("Bad request: {}", message)
        return ResponseEntity.badRequest().body(mapOf("error" to (message ?: "INVALID_REQUEST")))
    }

    /** جسم JSON مفقود/تالف — كان يسقط في معالج Spring الافتراضي برسالة خام. */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun unreadableBody(error: HttpMessageNotReadableException): ResponseEntity<Map<String, String>> {
        log.warn("Malformed request body: {}", error.message)
        return ResponseEntity.badRequest().body(mapOf("error" to "MALFORMED_JSON"))
    }

    /** فشل Bean Validation (@Valid) — يعيد أول رسالة حقل بدل 500/INVALID_REQUEST. */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun invalidBody(error: MethodArgumentNotValidException): ResponseEntity<Map<String, String>> {
        val first = error.bindingResult.fieldErrors.firstOrNull()
        val message = first?.defaultMessage ?: "VALIDATION_FAILED"
        log.warn("Validation failed on field '{}': {}", first?.field, message)
        return ResponseEntity.badRequest().body(mapOf("error" to message))
    }

    @ExceptionHandler(NoSuchElementException::class)
    fun notFound(error: NoSuchElementException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to (error.message ?: "NOT_FOUND")))

    /** IllegalStateException — حالة غير صالحة (مثل POLL_NOT_ACTIVE / ALREADY_VOTED) → 409 Conflict */
    @ExceptionHandler(IllegalStateException::class)
    fun conflict(error: IllegalStateException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to (error.message ?: "CONFLICT")))

    /** ClassCastException — جسم مشوَّه (تحويل غير آمن) → 400 بدل 500 */
    @ExceptionHandler(ClassCastException::class)
    fun badCast(error: ClassCastException): ResponseEntity<Map<String, String>> {
        log.error("ClassCastException while handling request", error)
        return ResponseEntity.badRequest().body(mapOf("error" to "INVALID_REQUEST_BODY"))
    }

    /** NumberFormatException — رقم مشوَّه (مثل toInt() على نص) → 400 بدل 500 */
    @ExceptionHandler(NumberFormatException::class)
    fun badNumber(error: NumberFormatException): ResponseEntity<Map<String, String>> =
        ResponseEntity.badRequest().body(mapOf("error" to "INVALID_NUMBER_FORMAT"))

    /** NullPointerException — مرجع null غير متوقع → 400 بدل 500 (لا تسريب stack trace) */
    @ExceptionHandler(NullPointerException::class)
    fun badNull(error: NullPointerException): ResponseEntity<Map<String, String>> {
        // سجّل الأثر الكامل: هذا هو السبب الخفي خلف «INVALID_REQUEST» في التسجيل.
        log.error("NullPointerException while handling request", error)
        return ResponseEntity.badRequest().body(mapOf("error" to "INVALID_REQUEST"))
    }

    /** أي استثناء غير متوقع → 500 برسالة عامة بلا تسريب تفاصيل داخلية. */
    @ExceptionHandler(Exception::class)
    fun unexpected(error: Exception): ResponseEntity<Map<String, String>> {
        log.error("Unexpected error while handling request", error)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(mapOf("error" to "INTERNAL_ERROR"))
    }
}
