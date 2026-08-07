package com.red.sovereign.auth

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable data class PstnCallRequest(val number: String)
@Serializable data class PstnCallResponse(val callId: String, val status: String, val number: String, val usedToday: Int, val dailyLimit: Int)

class PstnApi(tokens: TokenStore) {
    private val client = AuthorizedApiClient(tokens)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun dial(number: String): ApiResult<PstnCallResponse> {
        return when (val result = client.request("POST", "/api/pstn/calls", json.encodeToString(PstnCallRequest(number)))) {
            is ApiResult.Success -> runCatching { ApiResult.Success(result.code, json.decodeFromString<PstnCallResponse>(result.value)) }
                .getOrElse { ApiResult.Error(result.code, "INVALID_SERVER_RESPONSE") }
            is ApiResult.Error -> result
        }
    }
}
