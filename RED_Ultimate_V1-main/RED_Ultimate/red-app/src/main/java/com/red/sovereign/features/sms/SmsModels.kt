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

/**
 * محادثة SMS واحدة كما يعيدها الخادم.
 *
 * الحقول غير قابلة لـ null في عقد الخادم
 * (`com.red.server.sms.SmsService.ConversationDto`) — لذلك تُستخدم قيم
 * افتراضية بدل `null` كي لا تنتشر معالجة null في كل الواجهة.
 */
@Serializable
data class SmsConversationDto(
    val number: String,
    val operator: String = "",
    val lastText: String = "",
    val lastTime: Long = 0L,
    val direction: String = "IN",
    val status: String = "RECEIVED",
    val unreadCount: Int = 0
)

/**
 * رسالة SMS واحدة.
 *
 * `isRead` يجب أن يطابق حرفياً `com.red.server.sms.SmsService.MessageDto.isRead`.
 * كان الاسم `read` فكان kotlinx.serialization لا يجد المفتاح فيسقط دائماً على
 * القيمة الافتراضية `true` — أي أن حالة «مقروءة» كانت وهمية دائماً.
 */
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
    val message: String? = null,
    /** معرف قناة AMI للمكالمة الواردة — مطلوب في PSTN_ACCEPT/PSTN_REJECT. */
    val channel: String? = null,
    /** قناة البوابة الخام كما وردت من الخادم (تشخيص). */
    val gateway: String? = null,
    /** الرقم المطلوب (رقم شريحة المالك) في المكالمات الواردة. */
    val called: String? = null,
    /** مضيف البوابة المستقبِلة (192.168.11.2/.3). */ // ALLOW-IP: documentation of fleet IPs
    val gatewayHost: String? = null
)
