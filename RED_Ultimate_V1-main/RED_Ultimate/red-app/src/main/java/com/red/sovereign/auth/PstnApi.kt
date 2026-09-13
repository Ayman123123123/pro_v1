package com.red.sovereign.auth

import kotlinx.serialization.Serializable

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
    suspend fun dial(number: String, slotIndex: Int? = null): ApiResult<PstnCallResponse> = ApiResult.Error(500, "STUB")
    suspend fun hangup(callId: String, port: Int = -1): ApiResult<Boolean> = ApiResult.Error(500, "STUB")
    suspend fun hangupBridge(callId: String): ApiResult<Boolean> = ApiResult.Error(500, "STUB")
    suspend fun bridge(number: String, port: Int? = null): ApiResult<BridgeResponse> = ApiResult.Error(500, "STUB")
    suspend fun incomingBridge(callId: String): ApiResult<BridgeResponse> = ApiResult.Error(500, "STUB")
    suspend fun sendSms(recipient: String, text: String, encoding: String = "unicode"): ApiResult<SmsSendResponse> = ApiResult.Error(500, "STUB")
    suspend fun getInbox(): ApiResult<List<SmsIncomingMessage>> = ApiResult.Error(500, "STUB")
}

data class PstnBridgeInfo(
    val sipServer: String,
    val sipUsername: String,
    val sipPassword: String,
    val targetNumber: String,
    val callId: String,
    val gateway: String? = null,
    val iceServers: BridgeIceConfig = BridgeIceConfig(0L, emptyList())
)
