package com.red.server

import com.red.server.auth.PasswordRecoveryRequest
import com.red.server.auth.RecoveryService
import com.red.server.auth.RefreshTokenService
import com.red.server.auth.model.RecoveryCode
import com.red.server.auth.model.UserAccount
import com.red.server.auth.repository.RecoveryCodeRepository
import com.red.server.auth.repository.UserAccountRepository
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.security.crypto.password.PasswordEncoder

class RecoveryServiceTest {
    private val users = mock(UserAccountRepository::class.java)
    private val codes = mock(RecoveryCodeRepository::class.java)
    private val passwords = mock(PasswordEncoder::class.java)
    private val refresh = mock(RefreshTokenService::class.java)
    private val service = RecoveryService(users, codes, passwords, refresh)

    @Test fun `valid recovery code is consumed and all sessions are revoked`() {
        val user = UserAccount(redId = "62908", username = "ahmed", displayName = "Ahmed", passwordHash = "old")
        val code = RecoveryCode(user = user, codeHash = "hash")
        `when`(users.findByRedId(user.redId)).thenReturn(user)
        `when`(codes.findAllByUserIdAndUsedAtIsNull(user.id)).thenReturn(listOf(code))
        `when`(passwords.matches("ABCD-EFGH-JKLM", "hash")).thenReturn(true)
        `when`(passwords.encode("new-password-123")).thenReturn("new-hash")
        `when`(users.save(any(UserAccount::class.java))).thenAnswer { it.arguments[0] }
        `when`(codes.save(any(RecoveryCode::class.java))).thenAnswer { it.arguments[0] }

        service.reset(PasswordRecoveryRequest(user.redId, "ABCD-EFGH-JKLM", "new-password-123"))

        assertNotEquals("old", user.passwordHash)
        assertNotNull(code.usedAt)
        verify(refresh).revokeAll(user.id)
    }
}
