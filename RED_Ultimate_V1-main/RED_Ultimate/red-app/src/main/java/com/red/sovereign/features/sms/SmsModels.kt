package com.red.sovereign.features.sms

import kotlinx.serialization.Serializable

@Serializable
data class SmsSendRequest(
    val number: String,
    val text: String,
    val port: List<Int>? = null
)

@Serializable
data class SmsSendResponse(
    val id: String,
    val status: String,
    val number: String,
    val parts: Int,
    val encoding_auto: String = "AUTO"
)

@Serializable
data class SmsConversationDto(
    val number: String,
    val operator: String? = null,
    val lastText: String? = null,
    val lastTime: Long? = null,
    val direction: String? = null,
    val status: String? = null,
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
    val read: Boolean = true
)

/** حدث WebSocket /ws/pstn — توجيهه لكل نوع. */
@Serializable
data class PstnWsEnvelope(
    val type: String,
    val id: String? = null,
    val number: String? = null,
    val content: String? = null,
    val contentText: String? = null,
    val time: Long? = null,
    val port: Int? = null,
    val status: String? = null,
    val callId: String? = null,
    val event: String? = null,
    val cause: String? = null,
    val caller: String? = null,
    val redId: String? = null,
    val message: String? = null
)
