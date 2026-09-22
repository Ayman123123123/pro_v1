package com.red.sovereign.auth

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ForwardRule(
    val enabled: Boolean = false,
    val target: String = ""
)

@Serializable
data class ForwardSettings(
    val always: ForwardRule = ForwardRule(),
    val busy: ForwardRule = ForwardRule(),
    val noAnswer: ForwardRule = ForwardRule(),
    val unreachable: ForwardRule = ForwardRule()
) {
    val hasAnyEnabled: Boolean
        get() = always.enabled || busy.enabled || noAnswer.enabled || unreachable.enabled
}

@Serializable
data class ForwardStatusResponse(
    val settings: ForwardSettings,
    val updatedAt: Long
)

class CallForwardApi(tokens: TokenStore) {
    private val client = AuthorizedApiClient(tokens)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getStatus(): ApiResult<ForwardSettings> {
        return when (val result = client.request("GET", "/api/calls/forward/status", "")) {
            is ApiResult.Success -> runCatching {
                val response = json.decodeFromString<ForwardStatusResponse>(result.value)
                ApiResult.Success(result.code, response.settings)
            }.getOrElse { ApiResult.Error(result.code, "INVALID_SERVER_RESPONSE") }
            is ApiResult.Error -> result
        }
    }

    suspend fun update(settings: ForwardSettings): ApiResult<ForwardSettings> {
        return when (val result = client.request("PUT", "/api/calls/forward", json.encodeToString(settings))) {
            is ApiResult.Success -> runCatching {
                val response = json.decodeFromString<ForwardStatusResponse>(result.value)
                ApiResult.Success(result.code, response.settings)
            }.getOrElse { ApiResult.Error(result.code, "INVALID_SERVER_RESPONSE") }
            is ApiResult.Error -> result
        }
    }

    suspend fun disableAll(): ApiResult<Boolean> {
        return when (val result = client.request("DELETE", "/api/calls/forward", "")) {
            is ApiResult.Success -> ApiResult.Success(result.code, true)
            is ApiResult.Error -> ApiResult.Error(result.code, result.message)
        }
    }
}