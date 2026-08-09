package com.red.server.auth

import com.red.server.auth.model.AccountStatus
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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
    @Value("\${red.trust-x-forwarded-for:false}") private val trustXForwardedFor: Boolean = false
) {

    @PostMapping("/register")
    fun register(@RequestBody request: RegisterRequest, servlet: HttpServletRequest): ResponseEntity<AuthResponse> {
        limits.check("register", clientIp(servlet), 5, Duration.ofHours(1))
        return ResponseEntity.status(HttpStatus.CREATED).body(registration.register(request))
    }

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest, servlet: HttpServletRequest): ResponseEntity<AuthResponse> {
        val rateIdentity = "${clientIp(servlet)}:${request.username}"
        limits.check("login", rateIdentity, 10, Duration.ofMinutes(15))
        val response = registration.login(request)
        limits.reset("login", rateIdentity)
        val status = when (response.status) {
            AccountStatus.APPROVED -> HttpStatus.OK
            AccountStatus.PENDING -> HttpStatus.LOCKED
            AccountStatus.REJECTED, AccountStatus.SUSPENDED, AccountStatus.BANNED -> HttpStatus.FORBIDDEN
        }
        return ResponseEntity.status(status).body(response)
    }

    @PostMapping("/refresh")
    fun refresh(@RequestBody request: RefreshRequest): RefreshResponse = registration.refresh(request)

    @PostMapping("/logout")
    fun logout(@RequestBody request: LogoutRequest): ResponseEntity<Void> {
        registration.logout(request)
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
}
