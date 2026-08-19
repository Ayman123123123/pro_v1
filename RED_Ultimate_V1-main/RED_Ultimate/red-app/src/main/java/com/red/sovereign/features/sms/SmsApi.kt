package com.red.sovereign.features.sms

import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * عميل REST لمسارات SMS الاحترافية الجديدة: /api/sms (إرسال، محادثات، سجل محادثة، تعليم كمقروءة، حذف، تحديث يدوي).
 */
class SmsApi(private val tokens: TokenStore) {
    private val client = AuthorizedApiClient(tokens)
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    suspend fun send(number: String, text: String, port: List<Int>? = null): ApiResult<SmsSendResponse> {
        return when (val r = client.request("POST", "/api/sms/send", json.encodeToString(SmsSendRequest(number, text, port)))) {
            is ApiResult.Success -> runCatching { ApiResult.Success(r.code, json.decodeFromString<SmsSendResponse>(r.value)) }
                .getOrElse { ApiResult.Error(r.code, "INVALID_SERVER_RESPONSE") }
            is ApiResult.Error -> r
        }
    }

    suspend fun conversations(): ApiResult<List<SmsConversationDto>> {
        return when (val r = client.request("GET", "/api/sms/conversations", null)) {
            is ApiResult.Success -> runCatching { ApiResult.Success(r.code, json.decodeFromString<List<SmsConversationDto>>(r.value)) }
                .getOrElse { ApiResult.Error(r.code, "INVALID_SERVER_RESPONSE") }
            is ApiResult.Error -> r
        }
    }

    suspend fun conversation(number: String): ApiResult<List<SmsMessageDto>> {
        val safe = number.filter { it.isDigit() || it == '+' }
        return when (val r = client.request("GET", "/api/sms/conversation/$safe", null)) {
            is ApiResult.Success -> runCatching { ApiResult.Success(r.code, json.decodeFromString<List<SmsMessageDto>>(r.value)) }
                .getOrElse { ApiResult.Error(r.code, "INVALID_SERVER_RESPONSE") }
            is ApiResult.Error -> r
        }
    }

    suspend fun markRead(number: String): ApiResult<Boolean> {
        return when (val r = client.request("POST", "/api/sms/read", json.encodeToString(mapOf("number" to number)))) {
            is ApiResult.Success -> ApiResult.Success(r.code, true)
            is ApiResult.Error -> r.let { ApiResult.Error(it.code, it.message) }
        }
    }

    suspend fun delete(id: String): ApiResult<Boolean> {
        return when (val r = client.request("DELETE", "/api/sms/$id", null)) {
            is ApiResult.Success -> ApiResult.Success(r.code, true)
            is ApiResult.Error -> r.let { ApiResult.Error(it.code, it.message) }
        }
    }

    suspend fun refresh(): ApiResult<List<SmsConversationDto>> {
        return when (val r = client.request("POST", "/api/sms/refresh", "{}")) {
            is ApiResult.Success -> runCatching { ApiResult.Success(r.code, json.decodeFromString<List<SmsConversationDto>>(r.value)) }
                .getOrElse { ApiResult.Error(r.code, "INVALID_SERVER_RESPONSE") }
            is ApiResult.Error -> r
        }
    }
}
