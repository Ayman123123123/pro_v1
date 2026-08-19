package com.red.server.auth

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.util.UUID

@RestControllerAdvice
class AuthExceptionHandler {
    private val log = LoggerFactory.getLogger(AuthExceptionHandler::class.java)
    @ExceptionHandler(RateLimitExceededException::class)
    fun rateLimited(error: RateLimitExceededException): ResponseEntity<Map<String, String>> =
        apiError(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", error)

    @ExceptionHandler(InvalidCredentialsException::class)
    fun invalidCredentials(error: InvalidCredentialsException): ResponseEntity<Map<String, String>> =
        apiError(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", error)

    @ExceptionHandler(InvalidRecoveryCodeException::class)
    fun invalidRecovery(error: InvalidRecoveryCodeException): ResponseEntity<Map<String, String>> =
        apiError(HttpStatus.UNAUTHORIZED, "INVALID_RECOVERY_CODE", error)

    @ExceptionHandler(InvalidRefreshTokenException::class)
    fun invalidRefresh(error: InvalidRefreshTokenException): ResponseEntity<Map<String, String>> =
        apiError(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", error)

    @ExceptionHandler(RefreshTokenReuseException::class)
    fun refreshReuse(error: RefreshTokenReuseException): ResponseEntity<Map<String, String>> =
        apiError(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_REUSE_DETECTED", error)

    @ExceptionHandler(UnsupportedOperationException::class)
    fun notImplemented(error: UnsupportedOperationException): ResponseEntity<Map<String, String>> =
        apiError(HttpStatus.NOT_IMPLEMENTED, "NOT_IMPLEMENTED", error)

    @ExceptionHandler(IllegalArgumentException::class)
    fun badRequest(error: IllegalArgumentException): ResponseEntity<Map<String, String>> =
        apiError(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", error)

    /** جسم JSON مفقود أو تالف. */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun unreadableBody(error: HttpMessageNotReadableException): ResponseEntity<Map<String, String>> =
        apiError(HttpStatus.BAD_REQUEST, "MALFORMED_JSON", error)

    /** فشل Bean Validation لا يكشف رسائل الحقول أو قيمها ضمن العقد العام. */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun invalidBody(error: MethodArgumentNotValidException): ResponseEntity<Map<String, String>> =
        apiError(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", error)

    @ExceptionHandler(NoSuchElementException::class)
    fun notFound(error: NoSuchElementException): ResponseEntity<Map<String, String>> =
        apiError(HttpStatus.NOT_FOUND, "NOT_FOUND", error)

    /** حالة الطلب لا تسمح بالعملية المطلوبة. */
    @ExceptionHandler(IllegalStateException::class)
    fun conflict(error: IllegalStateException): ResponseEntity<Map<String, String>> =
        apiError(HttpStatus.CONFLICT, "CONFLICT", error)

    /** جسم مشوّه بتحويل غير آمن. */
    @ExceptionHandler(ClassCastException::class)
    fun badCast(error: ClassCastException): ResponseEntity<Map<String, String>> =
        apiError(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_BODY", error)

    /** رقم مشوّه ضمن جسم أو معامل الطلب. */
    @ExceptionHandler(NumberFormatException::class)
    fun badNumber(error: NumberFormatException): ResponseEntity<Map<String, String>> =
        apiError(HttpStatus.BAD_REQUEST, "INVALID_NUMBER_FORMAT", error)

    /** مرجع null غير متوقع. */
    @ExceptionHandler(NullPointerException::class)
    fun badNull(error: NullPointerException): ResponseEntity<Map<String, String>> =
        apiError(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", error)

    /** أي استثناء غير متوقع يعاد برمز عام فقط. */
    @ExceptionHandler(Exception::class)
    fun unexpected(error: Exception): ResponseEntity<Map<String, String>> =
        apiError(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", error)

    private fun apiError(
        status: HttpStatus,
        code: String,
        cause: Throwable
    ): ResponseEntity<Map<String, String>> {
        val diagnosticId = UUID.randomUUID().toString()
        log.warn(
            "Request failed [diagnosticId={}, errorCode={}]: {}",
            diagnosticId,
            code,
            cause.message,
            cause
        )
        return ResponseEntity.status(status).body(
            mapOf(
                "error" to code,
                "diagnosticId" to diagnosticId
            )
        )
    }
}
