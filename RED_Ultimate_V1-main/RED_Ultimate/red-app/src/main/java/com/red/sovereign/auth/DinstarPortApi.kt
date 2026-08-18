package com.red.sovereign.auth

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PortInfo(
    val index: Int,
    val gateway: String,
    val powered: Boolean,
    val status: PortStatus,
    val operator: String?,
    val signalPercent: Int?,
    val signalDbm: Int?,
    val callCount: Int,
    val totalDurationSec: Long
)

@Serializable
enum class PortStatus(val label: String) {
    ACTIVE("ACTIVE"),
    REGISTERED("REGISTERED"),
    BUSY("BUSY"),
    OFFLINE("OFFLINE"),
    UNKNOWN("UNKNOWN");
}

@Serializable
data class PortStatusResponse(
    val ports: List<PortInfo>,
    val gateway: String,
    val updatedAt: Long
)

class DinstarPortApi(tokens: TokenStore) {
    private val client = AuthorizedApiClient(tokens)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getPortStatus(gatewayId: String): ApiResult<List<PortInfo>> {
        return when (val result = client.request("GET", "/api/admin/dinstar/ports/status?gateway=$gatewayId", "")) {
            is ApiResult.Success -> runCatching {
                val response = json.decodeFromString<PortStatusResponse>(result.value)
                ApiResult.Success(result.code, response.ports)
            }.getOrElse { ApiResult.Error(result.code, "INVALID_SERVER_RESPONSE") }
            is ApiResult.Error -> result
        }
    }

    suspend fun setPortPower(gatewayId: String, port: Int, enabled: Boolean): ApiResult<Boolean> {
        return when (val result = client.request("POST", "/api/admin/dinstar/ports/$port/power?gateway=$gatewayId", "{\"enabled\":$enabled}")) {
            is ApiResult.Success -> ApiResult.Success(result.code, true)
            is ApiResult.Error -> ApiResult.Error(result.code, result.message)
        }
    }

    suspend fun resetPort(gatewayId: String, port: Int): ApiResult<Boolean> {
        return when (val result = client.request("POST", "/api/admin/dinstar/ports/$port/reset?gateway=$gatewayId", "")) {
            is ApiResult.Success -> ApiResult.Success(result.code, true)
            is ApiResult.Error -> ApiResult.Error(result.code, result.message)
        }
    }

    suspend fun setPortOperator(gatewayId: String, port: Int, operator: String): ApiResult<Boolean> {
        return when (val result = client.request("POST", "/api/admin/dinstar/ports/$port/operator?gateway=$gatewayId", "{\"operator\":\"$operator\"}")) {
            is ApiResult.Success -> ApiResult.Success(result.code, true)
            is ApiResult.Error -> ApiResult.Error(result.code, result.message)
        }
    }
}