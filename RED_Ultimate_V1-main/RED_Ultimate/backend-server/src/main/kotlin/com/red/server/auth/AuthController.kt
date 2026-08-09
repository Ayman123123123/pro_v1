package com.red.server.auth

import com.red.server.auth.model.AccountStatus
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.CacheControl
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
    private val limits: RateLimitService
) {

    @PostMapping("/register")
    fun register(@RequestBody request: RegisterRequest, servlet: HttpServletRequest): ResponseEntity<AuthResponse> {
        limits.check("register", clientIp(servlet), 5, Duration.ofHours(1))
        // Registration returns one-time recovery codes; never allow a browser or proxy to cache them.
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(registration.register(request))
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
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(response)
    }

    @PostMapping("/temporary-password-change")
    fun changeTemporaryPassword(@RequestBody request: TemporaryPasswordChangeRequest, servlet: HttpServletRequest): ResponseEntity<Void> {
        limits.check("temporary-password-change", "${clientIp(servlet)}:${request.username}", 5, Duration.ofMinutes(15))
        registration.changeTemporaryPassword(request)
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build()
    }

    @PostMapping("/refresh")
    fun refresh(@RequestBody request: RefreshRequest): ResponseEntity<RefreshResponse> =
        ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(registration.refresh(request))

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

    private fun clientIp(request: HttpServletRequest): String =
        request.getHeader("X-Forwarded-For")?.substringBefore(',')?.trim()?.takeIf { it.isNotEmpty() } ?: request.remoteAddr
}
