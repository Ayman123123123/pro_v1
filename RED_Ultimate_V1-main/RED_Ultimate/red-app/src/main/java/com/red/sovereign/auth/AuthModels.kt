package com.red.sovereign.auth

import kotlinx.serialization.Serializable

@Serializable
data class DeviceEnrollmentRequest(
    val deviceName: String,
    val platform: String,
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

@Serializable data class RegisterRequest(val username: String, val password: String, val displayName: String, val device: DeviceEnrollmentRequest)
@Serializable data class LoginRequest(val username: String, val password: String, val deviceId: String? = null)
@Serializable data class RefreshRequest(val refreshToken: String)
@Serializable data class LogoutRequest(val refreshToken: String)
@Serializable data class PasswordRecoveryRequest(val redId: String, val recoveryCode: String, val newPassword: String)

@Serializable
data class DeviceResponse(
    val id: String,
    val deviceName: String,
    val platform: String,
    val identityFingerprint: String,
    val status: String,
    val authorizationCertificate: String? = null,
    val certificateExpiresAt: String? = null
)

@Serializable
data class UserResponse(
    val id: String,
    val redId: String,
    val username: String,
    val displayName: String,
    val status: String,
    val role: String,
    val rejectionReason: String? = null,
    val pstnEnabled: Boolean = false,
    val pstnDailyLimit: Int = 0,
    val devices: List<DeviceResponse> = emptyList()
)

@Serializable
data class AuthResponse(
    val status: String,
    val user: UserResponse,
    val deviceId: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val tokenType: String? = null,
    val expiresInSeconds: Long? = null,
    val recoveryCodes: List<String>? = null,
    val message: String? = null
)

@Serializable data class RefreshResponse(val accessToken: String, val refreshToken: String, val tokenType: String, val expiresInSeconds: Long)
@Serializable data class ErrorResponse(val error: String? = null)
