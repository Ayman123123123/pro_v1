package com.red.sovereign.features.sms

import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.TokenStore

class SmsApi(private val tokens: TokenStore) {
    suspend fun send(number: String, text: String, port: List<Int>? = null): ApiResult<SmsSendResponse> = ApiResult.Error(500, "STUB")
    suspend fun conversations(): ApiResult<List<SmsConversationDto>> = ApiResult.Error(500, "STUB")
    suspend fun conversation(number: String): ApiResult<List<SmsMessageDto>> = ApiResult.Error(500, "STUB")
    suspend fun markRead(number: String): ApiResult<Boolean> = ApiResult.Error(500, "STUB")
    suspend fun delete(id: String): ApiResult<Boolean> = ApiResult.Error(500, "STUB")
    suspend fun deleteConversation(number: String): ApiResult<Int> = ApiResult.Error(500, "STUB")
    suspend fun refresh(): ApiResult<List<SmsConversationDto>> = ApiResult.Error(500, "STUB")
}
