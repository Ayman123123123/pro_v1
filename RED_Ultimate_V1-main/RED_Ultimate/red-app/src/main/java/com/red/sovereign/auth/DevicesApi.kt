package com.red.sovereign.auth

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * عميل أجهزة المستخدم — يطابق DeviceController في الخادم:
 *   GET    /api/devices            → قائمة جلسات الأجهزة الحقيقية للحساب
 *   DELETE /api/devices/{deviceId} → إلغاء جهاز (يبطل توكنات التحديث الخاصة به)
 */
@Serializable
data class RemoteDevice(
    val id: String,
    val deviceName: String,
    val platform: String,
    val status: String,
    val identityFingerprint: String? = null,
    val createdAt: String? = null
)

class DevicesApi(tokens: TokenStore) {
    private val client = AuthorizedApiClient(tokens)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun list(): ApiResult<List<RemoteDevice>> {
        return when (val result = client.request("GET", "/api/devices")) {
            is ApiResult.Success -> runCatching {
                ApiResult.Success(result.code, json.decodeFromString<List<RemoteDevice>>(result.value))
            }.getOrElse { ApiResult.Error(result.code, "INVALID_SERVER_RESPONSE") }
            is ApiResult.Error -> result.let { ApiResult.Error(it.code, it.message) }
        }
    }

    suspend fun revoke(deviceId: String): ApiResult<Boolean> {
        return when (val result = client.request("DELETE", "/api/devices/$deviceId")) {
            // DELETE يرجع 204 No Content بجسد فارغ — النجاح يُقاس بالكود
            is ApiResult.Success -> ApiResult.Success(result.code, true)
            is ApiResult.Error -> result.let { ApiResult.Error(it.code, it.message) }
        }
    }
}
