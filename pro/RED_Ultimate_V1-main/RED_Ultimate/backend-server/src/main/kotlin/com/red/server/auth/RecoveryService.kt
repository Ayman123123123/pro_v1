package com.red.server.auth

import com.red.server.auth.model.RecoveryCode
import com.red.server.auth.repository.RecoveryCodeRepository
import com.red.server.auth.repository.UserAccountRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Instant

@Service
class RecoveryService(
    private val users: UserAccountRepository,
    private val codes: RecoveryCodeRepository,
    private val passwords: PasswordEncoder,
    private val refreshTokens: RefreshTokenService
) {
    private val random = SecureRandom()
    private val alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"

    @Transactional
    fun createFor(user: com.red.server.auth.model.UserAccount): List<String> {
        val raw = (1..10).map { generate() }
        codes.saveAll(raw.map { RecoveryCode(user = user, codeHash = passwords.encode(it)) })
        return raw
    }

    @Transactional
    fun reset(request: PasswordRecoveryRequest) {
        require(request.newPassword.length in 12..128) { "Password must contain 12-128 characters" }
        val user = users.findByRedId(request.redId) ?: throw InvalidRecoveryCodeException()
        require(!request.newPassword.contains(user.username, ignoreCase = true)) { "Password must not contain the username" }
        val code = codes.findAllByUserIdAndUsedAtIsNull(user.id).firstOrNull { passwords.matches(request.recoveryCode.trim().uppercase(), it.codeHash) }
            ?: throw InvalidRecoveryCodeException()
        code.usedAt = Instant.now(); codes.save(code)
        user.passwordHash = passwords.encode(request.newPassword); user.updatedAt = Instant.now(); users.save(user)
        refreshTokens.revokeAll(user.id)
    }

    private fun generate(): String = buildString {
        repeat(4) { append(alphabet[random.nextInt(alphabet.length)]) }; append('-')
        repeat(4) { append(alphabet[random.nextInt(alphabet.length)]) }; append('-')
        repeat(4) { append(alphabet[random.nextInt(alphabet.length)]) }
    }
}

class InvalidRecoveryCodeException : RuntimeException("Invalid RED ID or recovery code")
data class PasswordRecoveryRequest(val redId: String, val recoveryCode: String, val newPassword: String)
