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
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

@Service
class JwtService(
    @Value("\${red.security.jwt-secret}") private val configuredSecret: String,
    @Value("\${red.security.jwt-expiration-ms:3600000}") private val expirationMs: Long
) {
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
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusMinutes(10))) // Short-lived ticket: 10 minutes
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
