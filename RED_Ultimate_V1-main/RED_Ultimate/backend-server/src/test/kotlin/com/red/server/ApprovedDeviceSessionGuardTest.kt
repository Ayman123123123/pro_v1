package com.red.server

import com.red.server.auth.model.AccountStatus
import com.red.server.auth.model.DeviceStatus
import com.red.server.auth.model.UserAccount
import com.red.server.auth.model.UserDevice
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.auth.repository.UserDeviceRepository
import com.red.server.websocket.ApprovedDeviceSessionGuard
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID

class ApprovedDeviceSessionGuardTest {
    private val users: UserAccountRepository = mock()
    private val devices: UserDeviceRepository = mock()
    private val guard = ApprovedDeviceSessionGuard(users, devices)

    @Test
    fun `only an approved account with its approved device remains authorized`() {
        val user = UserAccount(id = UUID.randomUUID(), redId = "YNS-ABCD-EFGH", username = "ahmed", displayName = "Ahmed", status = AccountStatus.APPROVED)
        val device = UserDevice(id = UUID.randomUUID(), user = user, status = DeviceStatus.APPROVED)
        whenever(users.findById(user.id)).thenReturn(Optional.of(user))
        whenever(devices.findByIdAndUserId(device.id, user.id)).thenReturn(device)

        assertTrue(guard.isStillAuthorized(user.id.toString(), device.id.toString()))
    }

    @Test
    fun `revoked device or malformed session metadata is rejected`() {
        val user = UserAccount(id = UUID.randomUUID(), status = AccountStatus.APPROVED)
        val device = UserDevice(id = UUID.randomUUID(), user = user, status = DeviceStatus.REVOKED)
        whenever(users.findById(user.id)).thenReturn(Optional.of(user))
        whenever(devices.findByIdAndUserId(device.id, user.id)).thenReturn(device)

        assertFalse(guard.isStillAuthorized(user.id.toString(), device.id.toString()))
        assertFalse(guard.isStillAuthorized("not-a-uuid", device.id.toString()))
    }
}
