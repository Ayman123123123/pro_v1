package com.red.server.auth

import com.red.server.auth.model.DeviceStatus
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.auth.repository.UserDeviceRepository
import com.red.server.notification.DevicePushTokenService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/devices")
class DeviceController(
    private val devices: UserDeviceRepository,
    private val users: UserAccountRepository,
    private val refreshTokens: RefreshTokenService,
    private val pushTokens: DevicePushTokenService
) {
    @GetMapping
    fun list(authentication: Authentication) =
        devices.findAllByUserIdOrderByCreatedAtAsc(UUID.fromString(authentication.name)).map { it.toResponse() }

    /**
     * يسجّل رمز FCM/VoIP للجهاز — يُستدعى من التطبيق عند كل إطلاق
     * حتى تصل إشعارات المكالمات الواردة إلى الجهاز حتى لو أُغلق التطبيق.
     */
    @PostMapping("/push-token")
    fun registerPushToken(
        @RequestBody request: PushTokenRequest,
        authentication: Authentication
    ): ResponseEntity<Any> {
        val redId = users.findById(UUID.fromString(authentication.name)).orElseThrow { NoSuchElementException("User not found") }.redId
        if (request.token.isBlank()) return ResponseEntity.badRequest().body(mapOf("error" to "token is required"))
        val registered = pushTokens.register(redId, request.token, request.platform)
        return ResponseEntity.ok(mapOf("status" to "ok", "id" to registered.id))
    }

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
