package com.red.server

import com.red.server.auth.DeviceEnrollmentRequest
import com.red.server.auth.DeviceEnrollmentService
import com.red.server.auth.RedIdGenerator
import com.red.server.auth.RecoveryService
import com.red.server.auth.RefreshTokenService
import com.red.server.auth.RegisterRequest
import com.red.server.auth.RegistrationService
import com.red.server.auth.model.AccountStatus
import com.red.server.auth.model.UserAccount
import com.red.server.auth.model.UserDevice
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.auth.repository.UserDeviceRepository
import com.red.server.auth.security.JwtService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.Base64

class RegistrationServiceTest {
    private val users = mock<UserAccountRepository>()
    private val devices = mock<UserDeviceRepository>()
    private val encoder = mock<PasswordEncoder>()
    private val redIds = mock<RedIdGenerator>()
    private val enrollment = mock<DeviceEnrollmentService>()
    private val recovery = mock<RecoveryService>()
    private val refresh = mock<RefreshTokenService>()
    private val jwt = mock<JwtService>()
    private val service = RegistrationService(users, devices, encoder, redIds, enrollment, recovery, refresh, jwt)

    @Test
    fun `new account and its first device remain pending and receive no token`() {
        whenever(users.existsByUsernameIgnoreCase("ahmed.red")).thenReturn(false)
        whenever(redIds.next()).thenReturn("85248")
        whenever(encoder.encode("a-strong-password")).thenReturn("argon2-hash")
        whenever(users.saveAndFlush(any<UserAccount>())).thenAnswer { it.arguments[0] }
        whenever(enrollment.enroll(any<UserAccount>(), any<DeviceEnrollmentRequest>()))
            .thenAnswer { UserDevice(user = it.arguments[0] as UserAccount, identityFingerprint = "abc") }
        whenever(recovery.createFor(any<UserAccount>())).thenReturn(listOf("ABCD-EFGH-JKLM"))

        val result = service.register(
            RegisterRequest("Ahmed.Red", "a-strong-password", "أحمد", deviceRequest())
        )

        assertEquals(AccountStatus.PENDING, result.status)
        assertEquals("85248", result.user.redId)
        assertEquals("ahmed.red", result.user.username)
        assertNull(result.accessToken)
        assertNull(result.refreshToken)
        assertEquals(listOf("ABCD-EFGH-JKLM"), result.recoveryCodes)
        assertEquals("ACCOUNT_PENDING_ADMIN_APPROVAL", result.message)
    }

    @Test
    fun `phone-like or malformed username is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.register(RegisterRequest("777123456", "a-strong-password", "أحمد", deviceRequest()))
        }
    }

    private fun deviceRequest(): DeviceEnrollmentRequest {
        val key = Base64.getEncoder().encodeToString(ByteArray(64) { 1 })
        return DeviceEnrollmentRequest("Pixel", registrationId = 42, protocolDeviceId = 1, signedPreKeyId = 7, kyberPreKeyId = 8, identityKey = key, signedPreKey = key, kyberPreKey = key,
            signedPreKeySignature = key, kyberPreKeySignature = key)
    }
}
