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

    fun parse(token: String): Claims = Jwts.parser()
        .verifyWith(key)
        .build()
        .parseSignedClaims(token)
        .payload

    fun userId(token: String): UUID = UUID.fromString(parse(token).subject)

    fun deviceId(token: String): UUID? = parse(token)["deviceId"]?.toString()?.let(UUID::fromString)

    fun issueSfuTicket(user: UserAccount, deviceId: UUID, roomId: String, role: String, canProduce: Boolean): String {
        require(roomId.matches(Regex("^[A-Za-z0-9_-]{8,128}$"))) { "Invalid SFU room ID" }
        val now = Instant.now()
        return Jwts.builder()
            .subject(user.id.toString())
            .claim("redId", user.redId)
            .claim("deviceId", deviceId.toString())
            .claim("scope", "sfu")
            .claim("roomId", roomId)
            .claim("role", role)
            .claim("canProduce", canProduce)
            .id(UUID.randomUUID().toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(120)))
            .signWith(key)
            .compact()
    }

    fun expirationSeconds(): Long = expirationMs / 1000
}
