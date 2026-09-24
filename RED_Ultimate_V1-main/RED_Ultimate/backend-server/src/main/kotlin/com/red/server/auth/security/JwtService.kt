package com.red.server.auth.security

import com.red.server.auth.model.UserAccount
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import jakarta.annotation.PostConstruct
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Service
class JwtService(
    @Value("\${red.jwt.secret}") private val configuredSecret: String,
    @Value("\${red.jwt.access-expiration-minutes:15}") private val accessExpirationMinutes: Long,
    @Value("\${red.jwt.issuer:red-sovereign}") private val issuer: String,
    @Value("\${red.jwt.audience:red-app}") private val audience: String,
    @Value("\${red.jwt.sfu-secret:}") private val configuredSfuSecret: String
) {
    @PostConstruct
    fun validateSecret() {
        require(configuredSecret.isNotBlank()) {
            "FATAL: JWT_SECRET environment variable is not set. The server cannot start without a valid secret."
        }
    }

    @PostConstruct
    fun validateSfuSecretForProd() {
        if (!isProdEnvironment()) return
        require(configuredSfuSecret.isNotBlank()) {
            "FATAL: SFU_TICKET_SECRET (red.jwt.sfu-secret) is not set. Production cannot start with SFU fallback to JWT_SECRET."
        }
        require(configuredSfuSecret.length >= 32 && configuredSfuSecret != "change-me-in-production-please") {
            "FATAL: SFU_TICKET_SECRET must contain at least 32 random characters"
        }
        require(configuredSfuSecret != configuredSecret) {
            "FATAL: SFU_TICKET_SECRET must differ from JWT_SECRET in production (SFU separation required)"
        }
    }

    private fun isProdEnvironment(): Boolean {
        val profiles = buildList {
            add(System.getProperty("spring.profiles.active", ""))
            add(System.getenv("SPRING_PROFILES_ACTIVE") ?: "")
            add(System.getenv("RED_ENV") ?: "")
            add(System.getenv("APP_ENV") ?: "")
        }.joinToString(" ").lowercase()
        return profiles.contains("prod")
    }

    private val expirationMs: Long
        get() = accessExpirationMinutes.coerceIn(1, 60 * 24) * 60_000

    private val key: SecretKey by lazy {
        require(configuredSecret.length >= 32 && configuredSecret != "change-me-in-production-please") {
            "JWT_SECRET must contain at least 32 random characters"
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(configuredSecret.toByteArray(StandardCharsets.UTF_8))
        Keys.hmacShaKeyFor(digest)
    }

    /** Dedicated key for SFU media tickets; falls back to the main key when no separate secret is configured. */
    private val sfuKey: SecretKey by lazy {
        val s = configuredSfuSecret
        if (s.isNotBlank() && s.length >= 32) {
            Keys.hmacShaKeyFor(
                MessageDigest.getInstance("SHA-256").digest(s.toByteArray(StandardCharsets.UTF_8))
            )
        } else {
            // Fail-fast in prod only — dev/test keep the safe fallback (covered by SfuTicketJwtTest).
            check(!isProdEnvironment()) {
                "FATAL: SFU_TICKET_SECRET (red.jwt.sfu-secret) is not set. Production cannot start with SFU fallback to JWT_SECRET."
            }
            key
        }
    }

    fun issue(user: UserAccount, deviceId: UUID? = null): String {
        val now = Instant.now()
        val builder = Jwts.builder()
            .subject(user.id.toString())
            .claim("redId", user.redId)
            .claim("username", user.username)
            .claim("role", user.role.name)
        if (deviceId != null) builder.claim("deviceId", deviceId.toString())
        return builder
            .issuer(issuer)
            .audience().add(audience).and()
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusMillis(expirationMs)))
            .signWith(key)
            .compact()
    }

    /** Issues a short-lived SFU ticket for a specific group/room */
    fun issueSfuTicket(
        user: UserAccount,
        deviceId: UUID,
        groupId: String,
        groupRole: String,
        canProduce: Boolean
    ): String {
        val now = Instant.now()
        val builder = Jwts.builder()
            .subject(user.id.toString())
            .claim("redId", user.redId)
            .claim("username", user.username)
            .claim("role", user.role.name)
            .claim("deviceId", deviceId.toString())
            .claim("sfuGroupId", groupId)
            .claim("sfuGroupRole", groupRole)
            .claim("sfuCanProduce", canProduce)
        return builder
            .issuer(issuer)
            .audience().add(audience).and()
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(10, ChronoUnit.MINUTES))) // Short-lived ticket: 10 minutes
            .signWith(sfuKey)
            .compact()
    }

    // سماح انحراف الساعة 120s (LAN بلا NTP: هاتف/PC قد ينحرف دقائق بعد sleep) —
    // بدونه أي انحراف >0 يرمي ExpiredJwtException ويُسقط الجلسة ظلماً.
    fun parse(token: String): Claims = Jwts.parser()
        .verifyWith(key)
        .clockSkewSeconds(120)
        .build()
        .parseSignedClaims(token)
        .payload

    fun userId(token: String): UUID = UUID.fromString(parse(token).subject)

    fun deviceId(token: String): UUID? = parse(token)["deviceId"]?.toString()?.let(UUID::fromString)

    fun expirationSeconds(): Long = expirationMs / 1000
}
