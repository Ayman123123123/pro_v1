package com.red.server.auth

import com.red.server.auth.model.RefreshSession
import com.red.server.auth.model.UserAccount
import com.red.server.auth.model.UserDevice
import com.red.server.auth.repository.RefreshSessionRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64

@Service
class RefreshTokenService(
    private val sessions: RefreshSessionRepository,
    @Value("\${red.jwt.refresh-expiration-days:30}") private val expirationDays: Long
) {
    private val random = SecureRandom()

    @Transactional
    fun issue(user: UserAccount, device: UserDevice?): IssuedRefreshToken {
        val raw = ByteArray(48).also(random::nextBytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
        val session = sessions.save(
            RefreshSession(
                user = user,
                device = device,
                tokenHash = hash(token),
                expiresAt = Instant.now().plus(expirationDays, ChronoUnit.DAYS)
            )
        )
        return IssuedRefreshToken(token, session)
    }

    @Transactional
    fun rotate(token: String): IssuedRefreshToken {
        val current = sessions.findByTokenHash(hash(token))
            ?: throw InvalidRefreshTokenException()
        if (current.revokedAt != null) {
            // Reuse of a rotated token revokes the whole account session family.
            sessions.findAllByUserIdAndRevokedAtIsNull(current.user.id).forEach {
                it.revokedAt = Instant.now()
                sessions.save(it)
            }
            throw RefreshTokenReuseException()
        }
        if (!current.expiresAt.isAfter(Instant.now())) {
            current.revokedAt = Instant.now()
            sessions.save(current)
            throw InvalidRefreshTokenException()
        }

        current.revokedAt = Instant.now()
        val replacement = issue(current.user, current.device)
        current.replacedBy = replacement.session.id
        sessions.save(current)
        return replacement
    }

    @Transactional
    fun revoke(token: String) {
        sessions.findByTokenHash(hash(token))?.takeIf { it.revokedAt == null }?.let {
            it.revokedAt = Instant.now()
            sessions.save(it)
        }
    }

    @Transactional
    fun revokeDevice(deviceId: java.util.UUID) {
        sessions.findAllByDeviceIdAndRevokedAtIsNull(deviceId).forEach {
            it.revokedAt = Instant.now()
            sessions.save(it)
        }
    }

    @Transactional
    fun revokeAll(userId: java.util.UUID) {
        sessions.findAllByUserIdAndRevokedAtIsNull(userId).forEach {
            it.revokedAt = Instant.now()
            sessions.save(it)
        }
    }

    private fun hash(token: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(token.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

data class IssuedRefreshToken(val token: String, val session: RefreshSession)
class InvalidRefreshTokenException : RuntimeException("Invalid refresh token")
class RefreshTokenReuseException : RuntimeException("Refresh token reuse detected")
