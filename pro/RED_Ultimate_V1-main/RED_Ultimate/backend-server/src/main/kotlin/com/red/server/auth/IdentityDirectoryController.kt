package com.red.server.auth

import com.red.server.auth.model.DeviceStatus
import com.red.server.auth.model.UserDevice
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.auth.repository.UserDeviceRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.Base64
import java.util.UUID

@RestController
@RequestMapping("/api/identity/directory")
class IdentityDirectoryController(
    private val users: UserAccountRepository,
    private val devices: UserDeviceRepository,
    private val oneTimePreKeys: OneTimePreKeyService
) {
    /** Static directory lookup does not consume scarce keys and is safe for established sessions. */
    @GetMapping("/{redId}")
    fun bundles(@PathVariable redId: String): IdentityDirectoryResponse {
        val user = users.findByRedId(redId) ?: throw NoSuchElementException("RED identity not found")
        return IdentityDirectoryResponse(
            user.redId,
            devices.findAllByUserIdAndStatus(user.id, DeviceStatus.APPROVED).map { it.toBundle() }
        )
    }

    /** Called only when a sender lacks a session. The returned one-time pair is atomically consumed. */
    @GetMapping("/{redId}/{deviceId}/prekey")
    fun consumeBundle(@PathVariable redId: String, @PathVariable deviceId: UUID): PreKeyBundleResponse {
        val user = users.findByRedId(redId) ?: throw NoSuchElementException("RED identity not found")
        val device = devices.findByIdAndUserId(deviceId, user.id)
            ?.takeIf { it.status == DeviceStatus.APPROVED }
            ?: throw NoSuchElementException("Approved RED device not found")
        return device.toBundle(oneTimePreKeys.consume(device.id))
    }
}

private fun UserDevice.toBundle(oneTime: ConsumedPreKeyPair? = null): PreKeyBundleResponse {
    val encoder = Base64.getEncoder()
    return PreKeyBundleResponse(
        deviceId = id.toString(),
        registrationId = registrationId,
        protocolDeviceId = protocolDeviceId,
        oneTimePreKeyId = oneTime?.ecKeyId,
        oneTimePreKey = oneTime?.ecPublicKey?.let(encoder::encodeToString),
        signedPreKeyId = signedPreKeyId,
        kyberPreKeyId = oneTime?.kyberKeyId ?: kyberPreKeyId,
        identityKey = encoder.encodeToString(identityKey),
        signedPreKey = encoder.encodeToString(signedPreKey),
        kyberPreKey = encoder.encodeToString(oneTime?.kyberPublicKey ?: kyberPreKey),
        signedPreKeySignature = encoder.encodeToString(signedPreKeySignature),
        kyberPreKeySignature = encoder.encodeToString(oneTime?.kyberSignature ?: kyberPreKeySignature),
        identityFingerprint = identityFingerprint,
        authorizationCertificate = requireNotNull(authorizationCertificate),
        certificateExpiresAt = requireNotNull(certificateExpiresAt)
    )
}

data class IdentityDirectoryResponse(val redId: String, val devices: List<PreKeyBundleResponse>)
data class PreKeyBundleResponse(
    val deviceId: String,
    val registrationId: Int,
    val protocolDeviceId: Int,
    val oneTimePreKeyId: Int?,
    val oneTimePreKey: String?,
    val signedPreKeyId: Int,
    val kyberPreKeyId: Int,
    val identityKey: String,
    val signedPreKey: String,
    val kyberPreKey: String,
    val signedPreKeySignature: String,
    val kyberPreKeySignature: String,
    val identityFingerprint: String,
    val authorizationCertificate: String,
    val certificateExpiresAt: Instant
)
