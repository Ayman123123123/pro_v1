package com.red.server.auth

import com.red.server.auth.model.DeviceStatus
import com.red.server.auth.model.UserAccount
import com.red.server.auth.model.UserDevice
import com.red.server.auth.repository.UserDeviceRepository
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.util.Base64

@Service
class DeviceEnrollmentService(private val devices: UserDeviceRepository) {
    fun enroll(user: UserAccount, request: DeviceEnrollmentRequest): UserDevice {
        val identityKey = decode(request.identityKey, "identityKey", 16, 1024)
        val signedPreKey = decode(request.signedPreKey, "signedPreKey", 16, 4096)
        val kyberPreKey = decode(request.kyberPreKey, "kyberPreKey", 32, 16_384)
        val signedSignature = decode(request.signedPreKeySignature, "signedPreKeySignature", 32, 512)
        val kyberSignature = decode(request.kyberPreKeySignature, "kyberPreKeySignature", 32, 512)
        require(request.registrationId in 1..16_380) { "Invalid libsignal registration ID" }
        require(request.protocolDeviceId in 1..127) { "Invalid libsignal device ID" }
        require(request.signedPreKeyId >= 0 && request.kyberPreKeyId >= 0) { "Invalid pre-key ID" }
        val name = request.deviceName.trim()
        require(name.length in 1..100) { "deviceName must be 1-100 characters" }
        val platform = (request.platform ?: "ANDROID").trim().uppercase()
        require(platform in setOf("ANDROID", "IOS", "DESKTOP", "WEB")) { "Unsupported device platform" }

        return devices.save(
            UserDevice(
                user = user,
                deviceName = name,
                platform = platform,
                registrationId = request.registrationId,
                protocolDeviceId = request.protocolDeviceId,
                signedPreKeyId = request.signedPreKeyId,
                kyberPreKeyId = request.kyberPreKeyId,
                identityKey = identityKey,
                signedPreKey = signedPreKey,
                kyberPreKey = kyberPreKey,
                signedPreKeySignature = signedSignature,
                kyberPreKeySignature = kyberSignature,
                identityFingerprint = sha256(identityKey),
                status = DeviceStatus.PENDING
            )
        )
    }

    private fun decode(value: String, field: String, min: Int, max: Int): ByteArray {
        val bytes = runCatching { Base64.getDecoder().decode(value) }
            .recoverCatching { Base64.getUrlDecoder().decode(value) }
            .getOrElse { throw IllegalArgumentException("$field must be valid Base64") }
        require(bytes.size in min..max) { "$field has an invalid size" }
        return bytes
    }

    private fun sha256(value: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { "%02x".format(it) }
}
