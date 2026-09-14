package com.red.sovereign.calls

import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 🌉 YOUNES PSTN Bridge API — بيانات الجسر الصوتي الحقيقي.
 *
 * **هذا هو المسار الصحيح للمكالمات عبر DINSTAR** (كان التطبيق يستخدم
 * POST /api/pstn/calls القديم الذي يطلب GSM بلا ساق صوتية فيُغلق بعد
 * ثانية — «اختبار» لا مكالمة).
 *
 * التدفق (مطابق لـ [com.red.server.pstn.PstnBridgeController]):
 * 1. POST /api/pstn/bridge {number} → sipServer (WS) + sipUsername +
 *    sipPassword + ICE/TURN + gateway (نظير المنفذ الدقيق مثل
 *    dinstar-gw-192-168-11-1-port-7).
 * 2. التطبيق يسجل SIP عبر WebSocket مع Asterisk ويرسل INVITE مع ترويسة
 *    X-Red-Gw → Asterisk يجسر WebRTC↔GSTM عبر السياق from-red-client-webrtc.
 * 3. POST /api/pstn/bridge/{callId}/hangup لتحرير الحجز عند الإنهاء.
 */
@Serializable
data class PstnBridgeRequest(val number: String)

@Serializable
data class PstnIceServer(val urls: List<String> = emptyList(), val username: String? = null, val credential: String? = null)

@Serializable
data class PstnIceConfiguration(val expiresAt: Long = 0L, val iceServers: List<PstnIceServer> = emptyList())

@Serializable
data class PstnBridgeInfo(
    val callId: String,
    val sipServer: String,
    val sipUsername: String,
    val sipPassword: String,
    val sipTransport: String = "WSS",
    val targetNumber: String,
    val iceServers: PstnIceConfiguration = PstnIceConfiguration(),
    val expiresAt: Long = 0L,
    val usedToday: Int = 0,
    val dailyLimit: Int = 0,
    val turnServerUrl: String? = null,
    val turnUsername: String? = null,
    val turnPassword: String? = null,
    val port: Int? = null,
    val gateway: String? = null
)

class PstnBridgeApi(tokens: TokenStore) {
    private val client = AuthorizedApiClient(tokens)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun bridge(number: String): ApiResult<PstnBridgeInfo> {
        val body = json.encodeToString(PstnBridgeRequest(number))
        return decode(client.request("POST", "/api/pstn/bridge", body))
    }

    suspend fun hangup(callId: String): ApiResult<Boolean> {
        return when (val result = client.request("POST", "/api/pstn/bridge/$callId/hangup", "{}")) {
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
