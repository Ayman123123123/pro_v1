package com.red.server

import com.red.server.audit.AuditService
import com.red.server.auth.RedApprovalService
import com.red.server.auth.RefreshTokenService
import com.red.server.auth.model.AccountStatus
import com.red.server.auth.model.DeviceStatus
import com.red.server.auth.model.UserAccount
import com.red.server.auth.model.UserDevice
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.auth.repository.UserDeviceRepository
import com.red.server.auth.security.DeviceCertificateService
import com.red.server.auth.security.IssuedDeviceCertificate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.Instant
import java.util.Optional
import java.util.UUID

class RedApprovalServiceTest {
    private val users = mock(UserAccountRepository::class.java)
    private val devices = mock(UserDeviceRepository::class.java)
    private val certificates = mock(DeviceCertificateService::class.java)
    private val refresh = mock(RefreshTokenService::class.java)
    private val audit = mock(AuditService::class.java)
    private val service = RedApprovalService(users, devices, certificates, refresh, audit)

    @Test
    fun `admin approval signs and approves every pending device`() {
        val user = UserAccount(redId = "RED-TEST-0001", username = "test.user", displayName = "Test")
        val device = UserDevice(user = user, identityFingerprint = "fingerprint")
        val adminId = UUID.randomUUID()
        val expiry = Instant.now().plusSeconds(3600)
        `when`(users.findById(user.id)).thenReturn(Optional.of(user))
        `when`(devices.findAllByUserIdOrderByCreatedAtAsc(user.id)).thenReturn(listOf(device))
        `when`(certificates.issue(user, device)).thenReturn(IssuedDeviceCertificate("certificate", expiry))
        `when`(users.save(any(UserAccount::class.java))).thenAnswer { it.arguments[0] }
        `when`(devices.save(any(UserDevice::class.java))).thenAnswer { it.arguments[0] }

        val result = service.processAction(user.id, AccountStatus.APPROVED, adminId = adminId)

        assertEquals(AccountStatus.APPROVED, result.status)
        assertEquals(adminId, user.approvedBy)
        assertNotNull(user.approvedAt)
        assertEquals(DeviceStatus.APPROVED, device.status)
        assertEquals("certificate", device.authorizationCertificate)
    }
}
