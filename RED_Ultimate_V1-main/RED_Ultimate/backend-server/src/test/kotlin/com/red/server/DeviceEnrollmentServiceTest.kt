package com.red.server

import com.red.server.auth.DeviceEnrollmentRequest
import com.red.server.auth.DeviceEnrollmentService
import com.red.server.auth.model.DeviceStatus
import com.red.server.auth.model.UserAccount
import com.red.server.auth.model.UserDevice
import com.red.server.auth.repository.UserDeviceRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.Base64

class DeviceEnrollmentServiceTest {
    private val devices = mock(UserDeviceRepository::class.java)
    private val service = DeviceEnrollmentService(devices)

    @Test
    fun `public key material is stored pending and private key is not part of request`() {
        `when`(devices.save(any(UserDevice::class.java))).thenAnswer { it.arguments[0] }
        val bytes = ByteArray(64) { 7 }
        val encoded = Base64.getEncoder().encodeToString(bytes)
        val device = service.enroll(
            UserAccount(redId = "RED-TEST-0001", username = "test", displayName = "Test"),
            DeviceEnrollmentRequest("Pixel", registrationId = 42, protocolDeviceId = 1, signedPreKeyId = 7, kyberPreKeyId = 8, identityKey = encoded, signedPreKey = encoded,
                kyberPreKey = encoded, signedPreKeySignature = encoded, kyberPreKeySignature = encoded)
        )
        assertEquals(DeviceStatus.PENDING, device.status)
        assertEquals(64, device.identityFingerprint.length)
    }

    @Test
    fun `invalid public key encoding is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            service.enroll(
                UserAccount(),
                DeviceEnrollmentRequest("Pixel", registrationId = 42, protocolDeviceId = 1, signedPreKeyId = 7, kyberPreKeyId = 8, identityKey = "not-base64!", signedPreKey = "x",
                    kyberPreKey = "x", signedPreKeySignature = "x", kyberPreKeySignature = "x")
            )
        }
    }
}
