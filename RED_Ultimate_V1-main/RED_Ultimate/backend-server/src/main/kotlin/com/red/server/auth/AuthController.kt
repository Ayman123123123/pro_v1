package com.red.server.auth

import com.red.server.auth.model.AccountStatus
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseCookie
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.security.core.Authentication
import jakarta.validation.Valid
import java.time.Instant
import java.util.UUID
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val registration: RegistrationService,
    private val recovery: RecoveryService,
    private val limits: RateLimitService,
    private val users: com.red.server.auth.repository.UserAccountRepository,
    private val media: com.red.server.media.MediaService,
    @Value("\${red.trust-x-forwarded-for:false}") private val trustXForwardedFor: Boolean = false,
    @Value("\${red.admin-cookie.secure:true}") private val adminCookieSecure: Boolean = true
) {

    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest, servlet: HttpServletRequest): ResponseEntity<AuthResponse> {
        limits.check("register", clientIp(servlet), 5, Duration.ofHours(1))
        return ResponseEntity.status(HttpStatus.CREATED).body(registration.register(request))
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest, servlet: HttpServletRequest, httpResponse: HttpServletResponse): ResponseEntity<AuthResponse> {
        val rateIdentity = "${clientIp(servlet)}:${request.username}"
        limits.check("login", rateIdentity, 10, Duration.ofMinutes(15))
        val response = registration.login(request)
        limits.reset("login", rateIdentity)
        val status = when (response.status) {
            AccountStatus.APPROVED -> HttpStatus.OK
            AccountStatus.PENDING -> HttpStatus.LOCKED
            AccountStatus.REJECTED, AccountStatus.SUSPENDED, AccountStatus.BANNED -> HttpStatus.FORBIDDEN
        }
        return if (isAdminWebRequest(servlet, response)) {
            writeAdminCookies(httpResponse, requireNotNull(response.refreshToken))
            // The long-lived secret must never enter JavaScript for the admin SPA.
            ResponseEntity.status(status).body(response.copy(refreshToken = null))
        } else ResponseEntity.status(status).body(response)
    }

    @PostMapping("/refresh")
    fun refresh(@RequestBody request: RefreshRequest, servlet: HttpServletRequest, httpResponse: HttpServletResponse): ResponseEntity<RefreshResponse> {
        val browserToken = servlet.cookies?.firstOrNull { it.name == ADMIN_REFRESH_COOKIE }?.value
        val usingCookie = CsrfTokenValidator.requiresValidation(browserToken)
        if (usingCookie) requireValidCsrf(servlet)
        val refreshed = registration.refresh(RefreshRequest(browserToken ?: request.refreshToken))
        return if (usingCookie) {
            writeAdminCookies(httpResponse, refreshed.refreshToken)
            ResponseEntity.ok(refreshed.copy(refreshToken = ""))
        } else ResponseEntity.ok(refreshed)
    }

    @PostMapping("/logout")
    fun logout(@RequestBody request: LogoutRequest, servlet: HttpServletRequest, httpResponse: HttpServletResponse): ResponseEntity<Void> {
        val browserToken = servlet.cookies?.firstOrNull { it.name == ADMIN_REFRESH_COOKIE }?.value
        if (CsrfTokenValidator.requiresValidation(browserToken)) requireValidCsrf(servlet)
        registration.logout(LogoutRequest(browserToken ?: request.refreshToken))
        clearAdminCookies(httpResponse)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/recover")
    fun recover(@RequestBody request: PasswordRecoveryRequest, servlet: HttpServletRequest): ResponseEntity<Void> {
        val rateIdentity = "${clientIp(servlet)}:${request.redId}"
        limits.check("recover", rateIdentity, 5, Duration.ofHours(1))
        recovery.reset(request)
        limits.reset("recover", rateIdentity)
        return ResponseEntity.noContent().build()
    }

    /**
     * تعديل اسم المستخدم.
     *
     * يستدعيه التطبيق من شاشة الإعدادات (`AuthViewModel.updateUsername`).
     * كان المسار غائبًا عن الخادم كليًا، فكان الزر يفشل دائمًا.
     *
     * الحدود مطابقة لما يفرضه التطبيق قبل الإرسال (3..20)، لأن التحقق
     * في العميل وحده يُلتفّ عليه بطلب HTTP مباشر.
     */
    @PatchMapping("/username")
    fun updateUsername(
        @RequestBody request: UpdateUsernameRequest,
        authentication: Authentication,
    ): ResponseEntity<Map<String, String>> {
        val trimmed = request.username.trim()
        require(trimmed.length in 3..20) { "USERNAME_LENGTH_INVALID" }
        require(trimmed.matches(USERNAME_PATTERN)) { "USERNAME_CHARSET_INVALID" }

        val caller = UUID.fromString(authentication.name)
        val user = users.findById(caller).orElseThrow { NoSuchElementException("USER_NOT_FOUND") }

        // فحص التفرّد قبل الكتابة: قيد قاعدة البيانات يحمي التكامل لكنه
        // يعيد خطأً غامضًا بدل رسالة يفهمها المستخدم.
        if (!trimmed.equals(user.username, ignoreCase = true) &&
            users.existsByUsernameIgnoreCase(trimmed)
        ) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to "USERNAME_TAKEN"))
        }

        user.username = trimmed
        user.updatedAt = Instant.now()
        users.save(user)
        return ResponseEntity.ok(mapOf("username" to trimmed))
    }

    /**
     * تعديل الاسم المعروض والصورة والبايو.
     *
     * يستدعيه التطبيق (`ProfileViewModel`). الاسم المعروض غير فريد — بخلاف اسم المستخدم.
     * avatarUrl هو objectKey وسائط مشفّر (لا يخزّن الخادم الصورة، فقط مرجعها).
     */
    @PatchMapping("/profile")
    fun updateProfile(
        @RequestBody request: UpdateProfileRequest,
        authentication: Authentication,
    ): ResponseEntity<Map<String, String?>> {
        val trimmed = request.displayName.trim()
        require(trimmed.isNotBlank() && trimmed.length <= 50) { "DISPLAY_NAME_LENGTH_INVALID" }
        val bio = request.bio?.trim()?.takeIf { it.isNotEmpty() }
        require(bio == null || bio.length <= 280) { "BIO_TOO_LONG" }
        val avatarUrl = request.avatarUrl?.trim()?.takeIf { it.isNotEmpty() }
        require(avatarUrl == null || avatarUrl.length <= 255) { "AVATAR_URL_TOO_LONG" }

        val caller = UUID.fromString(authentication.name)
        // الصورة مرجع MinIO وليس URL حرّاً: لا يجوز ربط ملف الغير أو رابط خارجي.
        if (avatarUrl != null) {
            require(avatarUrl.startsWith("users/$caller/")) { "AVATAR_MUST_BELONG_TO_ACCOUNT" }
            require(media.exists(avatarUrl)) { "AVATAR_MEDIA_NOT_FOUND" }
            require(media.metadata(avatarUrl).mimeType.startsWith("image/")) { "AVATAR_MUST_BE_IMAGE" }
        }
        val user = users.findById(caller).orElseThrow { NoSuchElementException("USER_NOT_FOUND") }
        user.displayName = trimmed
        user.bio = bio
        if (avatarUrl != null) user.avatarUrl = avatarUrl
        user.updatedAt = Instant.now()
        users.save(user)
        return ResponseEntity.ok(mapOf(
            "displayName" to trimmed,
            "avatarUrl" to user.avatarUrl,
            "bio" to user.bio
        ))
    }

    private fun isAdminWebRequest(request: HttpServletRequest, response: AuthResponse): Boolean =
        request.getHeader("X-RED-Admin-Web") == "1" && response.user.role == com.red.server.auth.model.AccountRole.ADMIN && !response.refreshToken.isNullOrBlank()

    private fun writeAdminCookies(response: HttpServletResponse, refreshToken: String) {
        val csrf = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(java.security.SecureRandom()::nextBytes))
        // Refresh stays on /api/auth (HttpOnly, never visible to JS).
        response.addHeader("Set-Cookie", ResponseCookie.from(ADMIN_REFRESH_COOKIE, refreshToken).httpOnly(true).secure(adminCookieSecure)
            .sameSite("Strict").path("/api/auth").maxAge(Duration.ofDays(30)).build().toString())
        // CSRF must be readable by the SPA on `/` so rotate() can send X-RED-CSRF.
        // Path /api/auth hid the cookie from document.cookie → refresh never ran →
        // /api/admin/users returned AUTHENTICATION_REQUIRED after the access JWT expired.
        response.addHeader("Set-Cookie", ResponseCookie.from(ADMIN_CSRF_COOKIE, csrf).httpOnly(false).secure(adminCookieSecure)
            .sameSite("Strict").path("/").maxAge(Duration.ofDays(30)).build().toString())
    }

    private fun clearAdminCookies(response: HttpServletResponse) {
        listOf(
            ADMIN_REFRESH_COOKIE to "/api/auth",
            ADMIN_CSRF_COOKIE to "/",
            ADMIN_CSRF_COOKIE to "/api/auth", // expire the old path so leftover cookies cannot confuse rotate()
        ).forEach { (name, path) ->
            response.addHeader("Set-Cookie", ResponseCookie.from(name, "").httpOnly(name == ADMIN_REFRESH_COOKIE).secure(adminCookieSecure)
                .sameSite("Strict").path(path).maxAge(Duration.ZERO).build().toString())
        }
    }

    private fun requireValidCsrf(request: HttpServletRequest) {
        val cookie = request.cookies?.firstOrNull { it.name == ADMIN_CSRF_COOKIE }?.value
        val header = request.getHeader("X-RED-CSRF")
        require(CsrfTokenValidator.matches(cookie, header)) { "CSRF_VALIDATION_FAILED" }
    }

    /**
     * Extracts client IP, only trusting X-Forwarded-For when configured.
     * ⚠️  X-Forwarded-For can be spoofed by clients unless behind a trusted reverse proxy.
     * Set red.trust-x-forwarded-for=true only when behind a verified proxy that strips client XFF.
     */
    private fun clientIp(request: HttpServletRequest): String {
        if (trustXForwardedFor) {
            val xff = request.getHeader("X-Forwarded-For")
            if (!xff.isNullOrBlank()) {
                return xff.substringBefore(',').trim().takeIf { it.isNotEmpty() } ?: request.remoteAddr
            }
        }
        return request.remoteAddr ?: "unknown"
    }

    companion object {
        private const val ADMIN_REFRESH_COOKIE = "red_admin_refresh"
        private const val ADMIN_CSRF_COOKIE = "red_admin_csrf"
        /** أحرف وأرقام وشرطة سفلية ونقطة — بلا مسافات ولا رموز. */
        private val USERNAME_PATTERN = Regex("^[A-Za-z0-9_.]+$")
    }
}

data class UpdateUsernameRequest(val username: String = "")
data class UpdateProfileRequest(val displayName: String = "", val avatarUrl: String? = null, val bio: String? = null)
