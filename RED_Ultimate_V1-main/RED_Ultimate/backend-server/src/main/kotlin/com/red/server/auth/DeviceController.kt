package com.red.server.auth

import com.red.server.auth.model.DeviceStatus
import com.red.server.auth.repository.UserDeviceRepository
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/devices")
class DeviceController(
    private val devices: UserDeviceRepository,
    private val refreshTokens: RefreshTokenService
) {
    @GetMapping
    fun list(authentication: Authentication) =
        devices.findAllByUserIdOrderByCreatedAtAsc(UUID.fromString(authentication.name)).map { it.toResponse() }

    @DeleteMapping("/{deviceId}")
    @Transactional
    fun revoke(
        @PathVariable deviceId: UUID,
        authentication: Authentication
    ): ResponseEntity<Void> {
        val device = devices.findByIdAndUserId(deviceId, UUID.fromString(authentication.name))
            ?: throw NoSuchElementException("Device not found")
        device.status = DeviceStatus.REVOKED
        device.revokedAt = Instant.now()
        devices.save(device)
        refreshTokens.revokeDevice(device.id)
        return ResponseEntity.noContent().build()
    }
}

data class PushTokenRequest(
    val token: String = "",
    val platform: String = "ANDROID"
)
