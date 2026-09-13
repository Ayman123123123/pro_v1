package com.red.sovereign.sms

import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 📨 YOUNES SMS API — واجهة رسائل الهاتف اليمني عبر DINSTAR.
 *
 * تُطابق مسارات [SmsController] في الخادم:
 * - POST /api/sms/send             → إرسال مع حفظ دائم + حالة
 * - GET  /api/sms/conversations    → قائمة المحادثات (أحدث رسالة + غير مقروءة)
 * - GET  /api/sms/conversation/{n} → سجل كامل الرسائل لرقم
 * - POST /api/sms/read             → تعليم الرسائل كمقروءة
 * - DELETE /api/sms/{id}           → حذف رسالة (ملكية أو مشتركة)
 * - POST /api/sms/refresh          → جلب وارد DINSTAR + إرجاع المحادثات
 *
 * حقول الردود ثابتة في [com.red.server.sms.SmsService.ConversationDto] و
 * [com.red.server.sms.SmsService.MessageDto] — أي تغيير هناك يلزم هنا.
 */
@Serializable
data class SendSmsRequest(val number: String, val text: String, val port: List<Int>? = null)

@Serializable
data class SmsSendResponse(val id: String, val status: String, val number: String, val parts: Int = 1)

@Serializable
data class SmsConversation(
    val number: String,
    val operator: String = "",
    val lastText: String = "",
    val lastTime: Long = 0L,
    val direction: String = "IN",
    val status: String = "RECEIVED",
    val unreadCount: Int = 0
)

@Serializable
data class SmsMessageDto(
    val id: String,
    val number: String,
    val content: String,
    val direction: String,
    val status: String,
    val createdAt: Long,
    val isRead: Boolean = false
)

class SmsApi(tokens: TokenStore) {
    private val client = AuthorizedApiClient(tokens)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun send(number: String, text: String, port: List<Int>? = null): ApiResult<SmsSendResponse> {
        val body = json.encodeToString(SendSmsRequest(number = number, text = text, port = port))
        return decode(client.request("POST", "/api/sms/send", body))
    }

    suspend fun conversations(): ApiResult<List<SmsConversation>> =
        decode(client.request("GET", "/api/sms/conversations"))

    /** جلب وارد الجهاز فوراً ثم إرجاع المحادثات — يستخدمه زر التحديث اليدوي. */
    suspend fun refresh(): ApiResult<List<SmsConversation>> =
        decode(client.request("POST", "/api/sms/refresh", "{}"))

    suspend fun conversation(number: String): ApiResult<List<SmsMessageDto>> =
        decode(client.request("GET", "/api/sms/conversation/${java.net.URLEncoder.encode(number, "UTF-8")}"))

    suspend fun markRead(number: String): ApiResult<Boolean> {
        val body = json.encodeToString(mapOf("number" to number))
        return when (val result = client.request("POST", "/api/sms/read", body)) {
            is ApiResult.Success -> ApiResult.Success(result.code, true)
            is ApiResult.Error -> result
        }
    }

    suspend fun delete(messageId: String): ApiResult<Boolean> {
        return when (val result = client.request("DELETE", "/api/sms/$messageId")) {
            is ApiResult.Success -> ApiResult.Success(result.code, true)
            is ApiResult.Error -> result
        }
    }

    private inline fun <reified T> decode(result: ApiResult<String>): ApiResult<T> = when (result) {
        is ApiResult.Success -> runCatching { ApiResult.Success(result.code, json.decodeFromString<T>(result.value)) }
            .getOrElse { ApiResult.Error(result.code, "INVALID_SERVER_RESPONSE") }
        is ApiResult.Error -> result
    }
}
