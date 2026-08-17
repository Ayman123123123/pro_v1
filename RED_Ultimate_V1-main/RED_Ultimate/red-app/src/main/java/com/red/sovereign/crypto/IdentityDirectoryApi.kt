package com.red.sovereign.crypto

import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.time.Instant
import java.util.Base64

@Serializable data class AuthorityKey(val algorithm: String, val version: String, val publicKey: String)
@Serializable data class IdentityDirectory(val redId: String, val devices: List<RemotePreKeyDevice>)
@Serializable data class RemotePreKeyDevice(
    val deviceId: String,
    val registrationId: Int,
    val protocolDeviceId: Int,
    val oneTimePreKeyId: Int? = null,
    val oneTimePreKey: String? = null,
    val signedPreKeyId: Int,
    val kyberPreKeyId: Int,
    val identityKey: String,
    val signedPreKey: String,
    val kyberPreKey: String,
    val signedPreKeySignature: String,
    val kyberPreKeySignature: String,
    val identityFingerprint: String,
    val authorizationCertificate: String,
    val certificateExpiresAt: String
)

class IdentityDirectoryApi(private val client: AuthorizedApiClient) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun get(redId: String): ApiResult<IdentityDirectory> {
        val authorityResult = authority()
        if (authorityResult is ApiResult.Error) return authorityResult
        val directoryResult = client.request("GET", "/api/identity/directory/$redId")
        if (directoryResult is ApiResult.Error) return directoryResult
        return runCatching {
            val authority = (authorityResult as? ApiResult.Success)?.value
                ?: return ApiResult.Error(500, "AUTHORITY_FETCH_FAILED")
            val directory = json.decodeFromString<IdentityDirectory>((directoryResult as? ApiResult.Success)?.value ?: return ApiResult.Error(500, "DIRECTORY_FETCH_FAILED"))
            require(directory.redId == redId)
            directory.devices.forEach { verify(authority, directory.redId, it) }
            ApiResult.Success(200, directory)
        }.getOrElse { ApiResult.Error(498, "IDENTITY_CERTIFICATE_INVALID") }
    }

    suspend fun consumePreKey(redId: String, deviceId: String): ApiResult<RemotePreKeyDevice> {
        val authorityResult = authority()
        if (authorityResult is ApiResult.Error) return authorityResult
        val result = client.request("GET", "/api/identity/directory/$redId/$deviceId/prekey")
        if (result is ApiResult.Error) return result
        return runCatching {
            val device = json.decodeFromString<RemotePreKeyDevice>((result as? ApiResult.Success)?.value ?: return ApiResult.Error(500, "PREKEY_FETCH_FAILED"))
            require(device.deviceId == deviceId)
            verify((authorityResult as? ApiResult.Success)?.value ?: return ApiResult.Error(500, "AUTHORITY_FETCH_FAILED"), redId, device)
            ApiResult.Success(200, device)
        }.getOrElse { ApiResult.Error(498, "IDENTITY_CERTIFICATE_INVALID") }
    }

    private suspend fun authority(): ApiResult<AuthorityKey> {
        val result = client.request("GET", "/api/identity/authority")
        if (result is ApiResult.Error) return result
        return runCatching {
            val authority = json.decodeFromString<AuthorityKey>((result as? ApiResult.Success)?.value ?: return ApiResult.Error(500, "AUTHORITY_FETCH_FAILED"))
            require(authority.algorithm == "ECDSA_P256_SHA256" && authority.version == "v1")
            ApiResult.Success(200, authority)
        }.getOrElse { ApiResult.Error(498, "IDENTITY_AUTHORITY_INVALID") }
    }

    private fun verify(authority: AuthorityKey, redId: String, device: RemotePreKeyDevice) {
        val decoder = Base64.getUrlDecoder()
        val parts = device.authorizationCertificate.split('.')
        require(parts.size == 2)
        val payload = decoder.decode(parts[0])
        val signature = decoder.decode(parts[1])
        val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(authority.publicKey)))
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey); verifier.update(payload); require(verifier.verify(signature))
        val fields = payload.toString(Charsets.UTF_8).split('|')
        require(fields.size == 7 && fields[0] == "v1")
        require(fields[2] == redId && fields[3] == device.deviceId && fields[4] == device.identityFingerprint)
        require(fields[6].toLong() > Instant.now().epochSecond)
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(Base64.getDecoder().decode(device.identityKey))
            .joinToString("") { "%02x".format(it) }
        require(fingerprint == device.identityFingerprint)
    }
}
