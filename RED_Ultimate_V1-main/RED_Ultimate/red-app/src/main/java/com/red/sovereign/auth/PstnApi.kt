package com.red.sovereign.auth

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable data class PstnCallRequest(val number: String, val slotIndex: Int? = null)
@Serializable data class PstnCallResponse(val port: Int?,
    val gateway: String?,
    val callId: String, val status: String, val number: String, val usedToday: Int, val dailyLimit: Int, val slot: Int = -1)

@Serializable
data class BridgeResponse(
    val port: Int?,
    val gateway: String?,
    val callId: String,
    val sipServer: String,
    val sipUsername: String,
    val sipPassword: String,
    val sipTransport: String,
    val targetNumber: String,
    val iceServers: BridgeIceConfig,
    val expiresAt: Long,
    val usedToday: Int,
    val dailyLimit: Int,
    val turnServerUrl: String? = null,
    val turnUsername: String? = null,
    val turnPassword: String? = null,
)

@Serializable
data class BridgeIceConfig(val expiresAt: Long, val iceServers: List<BridgeIceServerDto>)

@Serializable
data class BridgeIceServerDto(val urls: List<String>, val username: String? = null, val credential: String? = null)

// 📨 SMS Models
@Serializable data class SmsSendRequest(val text: String, val gatewayHost: String? = null, val encoding: String = "unicode", val param: List<SmsParam>)
@Serializable data class SmsParam(val number: String, val user_id: String? = null)
@Serializable data class SmsSendResponse(val status: String, val messageId: String? = null)

@Serializable data class SmsIncomingResponse(val messages: List<SmsIncomingMessage>)
@Serializable data class SmsIncomingMessage(
    val port: Int,
    val sender: String,
    val text: String,
    val time: String,
    val coding: String? = null,
    val udh: String? = null
)

class PstnApi(private val tokens: TokenStore) {
    private val client = AuthorizedApiClient(tokens)
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * بدء مكالمة PSTN.
     * @param slotIndex منفذ/شريحة محددة (اختياري) — null يعني الاختيار الذكي
     *                 عبر موزّع الأحمال (إشارة + مشغل داخل الشبكة + استخدام).
     */
    suspend fun dial(number: String, slotIndex: Int? = null): ApiResult<PstnCallResponse> {
        return when (val result = client.request("POST", "/api/pstn/calls", json.encodeToString(PstnCallRequest(number, slotIndex)))) {
            is ApiResult.Success -> runCatching { ApiResult.Success(result.code, json.decodeFromString<PstnCallResponse>(result.value)) }
                .getOrElse { ApiResult.Error(result.code, "INVALID_SERVER_RESPONSE") }
            is ApiResult.Error -> result
        }
    }

    suspend fun hangup(callId: String, port: Int = -1): ApiResult<Boolean> {
        return when (val result = client.request("POST", "/api/pstn/calls/$callId/hangup", "{\"port\":$port}")) {
            is ApiResult.Success -> ApiResult.Success(result.code, true)
            is ApiResult.Error -> result.let { ApiResult.Error(it.code, it.message) }
        }
    }

    suspend fun hangupBridge(callId: String): ApiResult<Boolean> {
        return when (val result = client.request("POST", "/api/pstn/bridge/$callId/hangup", "")) {
            is ApiResult.Success -> ApiResult.Success(result.code, true)
            is ApiResult.Error -> result
        }
    }

    suspend fun bridge(number: String, port: Int? = null): ApiResult<BridgeResponse> {
        val payload = buildMap<String, Any> { put("number", number); if (port != null) put("port", port) }
        return when (val result = client.request("POST", "/api/pstn/bridge", json.encodeToString(payload))) {
            is ApiResult.Success -> runCatching { ApiResult.Success(result.code, json.decodeFromString<BridgeResponse>(result.value)) }
                .getOrElse { ApiResult.Error(result.code, "INVALID_SERVER_RESPONSE") }
            is ApiResult.Error -> result
        }
    }

    /**
     * بيانات الجسر للمكالمة الواردة (اعتماد المالك + SIP creds + ICE).
     * لا يحجز منفذ صادر — يعمل فقط أثناء عرض offer قصير العمر.
     */
    suspend fun incomingBridge(callId: String): ApiResult<BridgeResponse> {
        return when (val result = client.request("POST", "/api/pstn/incoming-bridge", json.encodeToString(mapOf("callId" to callId)))) {
            is ApiResult.Success -> runCatching { ApiResult.Success(result.code, json.decodeFromString<BridgeResponse>(result.value)) }
                .getOrElse { ApiResult.Error(result.code, "INVALID_SERVER_RESPONSE") }
            is ApiResult.Error -> result
        }
    }

    // 📨 SMS — المسار الدائم الموحد /api/sms (سابقاً كان يضرب /api/admin/dinstar/sms مباشرةً
    // على الجهاز بلا حفظ في sms_messages، فتضيع الرسالة عند إعادة تشغيل البوابة).
    // الآن يمر عبر SmsService الدائم 1:1 — كل مستخدم يرسل حتماً من شريحته المربوطة.
    suspend fun sendSms(recipient: String, text: String, encoding: String = "unicode"): ApiResult<SmsSendResponse> {
        // نوحد على /api/sms/send الدائم — port=null يعني "شريحتي المربوطة"
        val payload = mapOf("number" to recipient, "text" to text)
        return when (val result = client.request("POST", "/api/sms/send", json.encodeToString(payload))) {
            is ApiResult.Success -> runCatching { ApiResult.Success(result.code, json.decodeFromString<SmsSendResponse>(result.value)) }
                .getOrElse { ApiResult.Error(result.code, "INVALID_SERVER_RESPONSE") }
            is ApiResult.Error -> result
        }
    }

    @Deprecated("استخدم SmsApi.conversations() — هذا المسار يقرأ صندوق الجهاز المتطاير بلا حفظ")
    suspend fun getInbox(): ApiResult<List<SmsIncomingMessage>> {
        return when (val result = client.request("GET", "/api/admin/dinstar/sms/incoming", "")) {
            is ApiResult.Success -> runCatching { ApiResult.Success(result.code, json.decodeFromString<SmsIncomingResponse>(result.value).messages) }
                .getOrElse { ApiResult.Error(result.code, "INVALID_SERVER_RESPONSE") }
            is ApiResult.Error -> result
        }
    }
}




