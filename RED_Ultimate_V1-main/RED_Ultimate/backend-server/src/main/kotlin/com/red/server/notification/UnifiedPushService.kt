package com.red.server.notification

import org.springframework.stereotype.Service
import tools.jackson.module.kotlin.jacksonObjectMapper

/**
 * خدمة الإشعارات الموحدة السيادية (Sovereign UnifiedPush Service)
 * توفر واجهة برمجية عالية المستوى لبث إشعارات المكالمات والرسائل ودعوات المجموعات
 * مع التشفير السيادي الكامل عبر UnifiedPushSender.
 */
@Service
class UnifiedPushService(
    private val pushSender: UnifiedPushSender
) {
    private val json = jacksonObjectMapper()

    /**
     * إرسال إشعار مكالمة واردة مشفر
     */
    fun sendCallNotification(
        endpoint: String,
        callId: String,
        callerId: String,
        callType: String = "VOICE"
    ): Boolean {
        val payload = mapOf(
            "t" to "CALL",
            "i" to callId,
            "f" to callerId,
            "m" to callType
        )
        val bytes = json.writeValueAsBytes(payload)
        val status = pushSender.send(endpoint, bytes)
        return status in 200..299
    }

    /**
     * إرسال إشعار رسالة جديدة
     */
    fun sendMessageNotification(
        endpoint: String,
        senderId: String,
        messageId: String? = null
    ): Boolean {
        val payload = mapOf(
            "t" to "MESSAGE",
            "i" to senderId,
            "m" to (messageId ?: "")
        )
        val bytes = json.writeValueAsBytes(payload)
        val status = pushSender.send(endpoint, bytes)
        return status in 200..299
    }

    /**
     * إرسال إشعار دعوة لمجموعة
     */
    fun sendGroupInviteNotification(
        endpoint: String,
        groupId: String,
        groupName: String
    ): Boolean {
        val payload = mapOf(
            "t" to "GROUP_INVITE",
            "i" to groupId,
            "n" to groupName
        )
        val bytes = json.writeValueAsBytes(payload)
        val status = pushSender.send(endpoint, bytes)
        return status in 200..299
    }

    /**
     * إلغاء مكالمة رنين واردة
     */
    fun sendCancelCallNotification(
        endpoint: String,
        callId: String
    ): Boolean {
        val payload = mapOf(
            "t" to "CANCEL",
            "i" to callId
        )
        val bytes = json.writeValueAsBytes(payload)
        val status = pushSender.send(endpoint, bytes)
        return status in 200..299
    }
}