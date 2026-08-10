package com.red.server.websocket

import com.red.server.auth.model.AccountStatus
import com.red.server.auth.model.DeviceStatus
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.auth.repository.UserDeviceRepository
import org.springframework.stereotype.Component
import java.util.UUID

/** Revalidates a long-lived socket against current account and device approval state. */
@Component
class ApprovedDeviceSessionGuard(
    private val users: UserAccountRepository,
    private val devices: UserDeviceRepository
) {
    fun isStillAuthorized(accountId: String?, deviceId: String?): Boolean = runCatching {
        val account = UUID.fromString(accountId)
        val device = UUID.fromString(deviceId)
        val user = users.findById(account).orElse(null) ?: return@runCatching false
        user.status == AccountStatus.APPROVED &&
            devices.findByIdAndUserId(device, user.id)?.status == DeviceStatus.APPROVED
    }.getOrDefault(false)
}
