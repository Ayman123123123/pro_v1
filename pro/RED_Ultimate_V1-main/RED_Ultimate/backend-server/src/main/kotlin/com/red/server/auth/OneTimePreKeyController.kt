package com.red.server.auth

import com.red.server.auth.security.JwtService
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/devices/{deviceId}/prekeys")
class OneTimePreKeyController(
    private val service: OneTimePreKeyService,
    private val jwt: JwtService
) {
    @PostMapping
    fun upload(
        @PathVariable deviceId: UUID,
        @RequestBody request: PreKeyUploadRequest,
        authentication: Authentication
    ): PreKeyStockResponse {
        requireAuthenticatedDevice(authentication, deviceId)
        return service.upload(UUID.fromString(authentication.name), deviceId, request)
    }

    @GetMapping("/stock")
    fun stock(@PathVariable deviceId: UUID, authentication: Authentication): PreKeyStockResponse {
        requireAuthenticatedDevice(authentication, deviceId)
        return service.stock(UUID.fromString(authentication.name), deviceId)
    }

    private fun requireAuthenticatedDevice(authentication: Authentication, requested: UUID) {
        val token = authentication.credentials as? String ?: throw IllegalArgumentException("Device token required")
        require(jwt.deviceId(token) == requested) { "A device may only manage its own pre-keys" }
    }
}
