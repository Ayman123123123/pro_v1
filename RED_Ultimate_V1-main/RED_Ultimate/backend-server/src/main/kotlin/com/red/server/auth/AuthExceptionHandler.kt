package com.red.server.auth

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.util.UUID

/**
 * معالج أخطاء الـ API الموحّد.
 *
 * دمج قرارين متعارضين كانا في فرعين منفصلين، مع الحفاظ على مكسب كل منهما:
 *
 * 1. الفرع `main` كان يمرّر `error.message` للعميل لأن إرجاع «INVALID_REQUEST»
 *    بلا سبب جعل فشل التسجيل لغزًا لا يمكن تشخيصه. المكسب: رموز النطاق
 *    (POLL_NOT_ACTIVE / ALREADY_VOTED / CSRF_VALIDATION_FAILED) تصل للواجهة.
 *    الخطر: أي رسالة استثناء داخلية تُسرَّب كما هي (مسار SQL، IP بوابة، سر SIP).
 *
 * 2. الفرع `chore/platform-rebuild-baseline` كان يُرجع رمزًا ثابتًا فقط مع
 *    `diagnosticId`. المكسب: صفر تسريب + إمكانية ربط شكوى المستخدم بسجل الخادم.
 *    الخطر: فقدان كل رموز النطاق، فتصبح كل الأخطاء 409/CONFLICT عامية.
 *
 * الحل المدمج: حدّ صريح عند الحدود — يُمرَّر فقط ما يشبه **رمزًا ثابتًا**
 * (SCREAMING_SNAKE_CASE بلا مسافات) لأنه مُعرّف بروتوكول مقصود من المطوّر.
 * أي رسالة حرة (جملة، مسار، استعلام SQL، عنوان شبكة) لا تعبر الحدود أبدًا،
 * بل تُسجَّل كاملة في الخادم مقترنة بـ `diagnosticId` يُعاد للعميل.
 */
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
        apiError(HttpStatus.NOT_IMPLEMENTED, "NOT_IMPLEMENTED", error)

    @ExceptionHandler(IllegalArgumentException::class)
    fun badRequest(error: IllegalArgumentException): ResponseEntity<Map<String, String>> =
        apiError(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", error)

    /** جسم JSON مفقود/تالف — كان يسقط في معالج Spring الافتراضي برسالة خام. */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun unreadableBody(error: HttpMessageNotReadableException): ResponseEntity<Map<String, String>> {
        log.warn("Malformed request body: {}", error.message)
        return ResponseEntity.badRequest().body(mapOf("error" to "MALFORMED_JSON"))
    }

    /**
     * فشل Bean Validation (@Valid) — يعيد أول رسالة حقل.
     * رسائل `@Valid` يكتبها المطوّر في التعليق التوضيحي نفسه ولا تحمل حالة
     * تشغيلية، لذلك تعبر الحدود بأمان وتظل مفيدة للمستخدم.
     */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun invalidBody(error: MethodArgumentNotValidException): ResponseEntity<Map<String, String>> {
        val first = error.bindingResult.fieldErrors.firstOrNull()
        val message = first?.defaultMessage ?: "VALIDATION_FAILED"
        log.warn("Validation failed on field '{}': {}", first?.field, message)
        return ResponseEntity.badRequest().body(mapOf("error" to message))
    }

    @ExceptionHandler(NoSuchElementException::class)
    fun notFound(error: NoSuchElementException): ResponseEntity<Map<String, String>> =
        apiError(HttpStatus.NOT_FOUND, "NOT_FOUND", error)

    /** IllegalStateException — حالة غير صالحة (مثل POLL_NOT_ACTIVE / ALREADY_VOTED) → 409 Conflict */
    @ExceptionHandler(IllegalStateException::class)
    fun conflict(error: IllegalStateException): ResponseEntity<Map<String, String>> =
        apiError(HttpStatus.CONFLICT, "CONFLICT", error)

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
        val diagnosticId = UUID.randomUUID().toString()
        log.error("Unexpected error while handling request [diagnosticId={}]", diagnosticId, error)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(mapOf("error" to "INTERNAL_ERROR", "diagnosticId" to diagnosticId))
    }

    /**
     * يبني جسم خطأ آمنًا: يمرّر رمز النطاق إن كان الاستثناء يحمل رمزًا ثابتًا،
     * وإلا يعيد الرمز العام. في كل الحالات يُسجَّل السبب الكامل في الخادم
     * مقترنًا بـ `diagnosticId` يُعاد للعميل لربط الشكوى بالسجل.
     */
    private fun apiError(
        status: HttpStatus,
        fallbackCode: String,
        cause: Throwable
    ): ResponseEntity<Map<String, String>> {
        val diagnosticId = UUID.randomUUID().toString()
        val raw = cause.message?.takeIf { it.isNotBlank() }
        val code = if (raw != null && isStableDomainCode(raw)) raw else fallbackCode

        if (code == fallbackCode) {
            // رسالة حرة: لا تعبر الحدود. تُسجَّل كاملة هنا فقط.
            log.warn(
                "Request failed [diagnosticId={}, errorCode={}]: {}",
                diagnosticId, code, raw ?: "<no message>", cause
            )
        } else {
            log.info("Request rejected [diagnosticId={}, domainCode={}]", diagnosticId, code)
        }

        return ResponseEntity.status(status).body(
            mapOf("error" to code, "diagnosticId" to diagnosticId)
        )
    }

    private companion object {
        /**
         * رمز نطاق ثابت: حروف لاتينية كبيرة وأرقام وشرطة سفلية فقط، 3..64 خانة.
         * جملة، مسار ملف، استعلام SQL، أو عنوان شبكة لا يطابق هذا النمط أبدًا،
         * لأن كلًّا منها يحتوي مسافة أو حرفًا صغيرًا أو رمزًا.
         */
        private val STABLE_DOMAIN_CODE = Regex("^[A-Z][A-Z0-9_]{2,63}$")

        fun isStableDomainCode(value: String): Boolean = STABLE_DOMAIN_CODE.matches(value)
    }
}
