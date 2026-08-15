package com.red.server.auth.security

import com.red.server.auth.model.UserAccount
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
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
    @Value("\${red.jwt.access-expiration-minutes:15}") private val accessExpirationMinutes: Long
) {
    companion object {
        const val SFU_TICKET_TTL_SECONDS = 120L
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

    fun issue(user: UserAccount, deviceId: UUID? = null): String {
        val now = Instant.now()
        val builder = Jwts.builder()
            .subject(user.id.toString())
            .claim("redId", user.redId)
            .claim("username", user.username)
            .claim("role", user.role.name)
        if (deviceId != null) builder.claim("deviceId", deviceId.toString())
        return builder
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusMillis(expirationMs)))
            .signWith(key)
            .compact()
    }

    /**
     * Issues a short-lived capability for exactly one SFU room.
     *
     * This is deliberately not interchangeable with an API access token: the
     * media process requires `scope=sfu`, verifies [roomId] on JOIN and enforces
     * [canProduce] before accepting any audio/video producer.
     */
    fun issueSfuTicket(
        user: UserAccount,
        deviceId: UUID,
        groupId: String,
        groupRole: String,
        canProduce: Boolean
    ): String {
        val now = Instant.now()
        return Jwts.builder()
            .subject(user.id.toString())
            .claim("redId", user.redId)
            .claim("username", user.username)
            .claim("role", user.role.name)
            .claim("deviceId", deviceId.toString())
            .claim("scope", "sfu")
            .claim("roomId", groupId)
            .claim("roomRole", groupRole)
            .claim("canProduce", canProduce)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(SFU_TICKET_TTL_SECONDS, ChronoUnit.SECONDS)))
            .signWith(key)
            .compact()
    }

    fun parse(token: String): Claims = Jwts.parser()
        .verifyWith(key)
        .build()
        .parseSignedClaims(token)
        .payload

    fun userId(token: String): UUID = UUID.fromString(parse(token).subject)

    fun deviceId(token: String): UUID? = parse(token)["deviceId"]?.toString()?.let(UUID::fromString)

    fun expirationSeconds(): Long = expirationMs / 1000
}
