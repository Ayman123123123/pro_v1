package com.red.server.auth

import com.red.server.auth.model.AccountRole
import com.red.server.auth.model.AccountStatus
import com.red.server.auth.model.DeviceStatus
import com.red.server.auth.model.UserAccount
import com.red.server.auth.model.UserDevice
import java.time.Instant
import java.util.UUID

data class DeviceEnrollmentRequest(
    val deviceName: String,
    val platform: String = "ANDROID",
    val registrationId: Int,
    val protocolDeviceId: Int,
    val signedPreKeyId: Int,
    val kyberPreKeyId: Int,
    val identityKey: String,
    val signedPreKey: String,
    val kyberPreKey: String,
    val signedPreKeySignature: String,
    val kyberPreKeySignature: String
)

data class RegisterRequest(
    val username: String,
    val password: String,
    val displayName: String,
    val device: DeviceEnrollmentRequest
)

data class LoginRequest(
    val username: String,
    val password: String,
    val deviceId: UUID? = null
)

data class RefreshRequest(val refreshToken: String)
data class LogoutRequest(val refreshToken: String)

data class ApprovalActionRequest(
    val userId: UUID,
    val action: AccountStatus,
    val reason: String? = null
)

data class UserAccountResponse(
    val id: UUID,
    val redId: String,
    val username: String,
    val displayName: String,
    val status: AccountStatus,
    val role: AccountRole,
    val createdAt: Instant,
    val updatedAt: Instant,
    val rejectionReason: String?,
    val pstnEnabled: Boolean,
    val pstnDailyLimit: Int,
    val devices: List<DeviceResponse> = emptyList()
)

data class DeviceResponse(
    val id: UUID,
    val deviceName: String,
    val platform: String,
    val identityFingerprint: String,
    val status: DeviceStatus,
    val authorizationCertificate: String?,
    val certificateExpiresAt: Instant?,
    val createdAt: Instant
)

data class AuthResponse(
    val status: AccountStatus,
    val user: UserAccountResponse,
    val deviceId: UUID? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val tokenType: String? = null,
    val expiresInSeconds: Long? = null,
    val recoveryCodes: List<String>? = null,
    val message: String? = null
)

data class RefreshResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresInSeconds: Long
)

fun UserAccount.toResponse(devices: List<UserDevice> = emptyList()) = UserAccountResponse(
    id = id,
    redId = redId,
    username = username,
    displayName = displayName,
    status = status,
    role = role,
    createdAt = createdAt,
    updatedAt = updatedAt,
    rejectionReason = rejectionReason,
    pstnEnabled = pstnEnabled,
    pstnDailyLimit = pstnDailyLimit,
    devices = devices.map { it.toResponse() }
)

fun UserDevice.toResponse() = DeviceResponse(
    id = id,
    deviceName = deviceName,
    platform = platform,
    identityFingerprint = identityFingerprint,
    status = status,
    authorizationCertificate = authorizationCertificate,
    certificateExpiresAt = certificateExpiresAt,
    createdAt = createdAt
)
