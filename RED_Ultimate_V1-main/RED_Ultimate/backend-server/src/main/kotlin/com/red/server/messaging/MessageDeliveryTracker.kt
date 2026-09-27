package com.red.server.messaging

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * تمثل حالة تسليم الرسالة
 */
enum class DeliveryStatus {
    SENT,
    DELIVERED,
    READ
}

/**
 * نموذج بيانات إيصال التسليم
 */
data class DeliveryReceipt(
    val messageId: String,
    val recipientId: String,
    val status: DeliveryStatus,
    val timestamp: Long
)

/**
 * مكوّن Spring مسؤول عن تتبع إيصالات تسليم الرسائل باستخدام Redis.
 */
@Component
class MessageDeliveryTracker(
    private val redisTemplate: RedisTemplate<String, String>
) {

    companion object {
        private const val KEY_PREFIX = "delivery:"
        private const val TTL_DAYS = 7L
        private const val FIELD_STATUS = "status"
        private const val FIELD_RECIPIENT = "recipientId"
        private const val FIELD_TIMESTAMP = "timestamp"
    }

    fun trackSent(messageId: String, recipientId: String) {
        val key = "$KEY_PREFIX$messageId"
        val ops = redisTemplate.opsForHash<String, String>()
        ops.put(key, FIELD_STATUS, DeliveryStatus.SENT.name)
        ops.put(key, FIELD_RECIPIENT, recipientId)
        ops.put(key, FIELD_TIMESTAMP, System.currentTimeMillis().toString())
        redisTemplate.expire(key, TTL_DAYS, TimeUnit.DAYS)
    }

    fun trackDelivered(messageId: String, recipientId: String) {
        val key = "$KEY_PREFIX$messageId"
        val ops = redisTemplate.opsForHash<String, String>()
        ops.put(key, FIELD_STATUS, DeliveryStatus.DELIVERED.name)
        ops.put(key, FIELD_TIMESTAMP, System.currentTimeMillis().toString())
    }

    fun trackRead(messageId: String, recipientId: String) {
        val key = "$KEY_PREFIX$messageId"
        val ops = redisTemplate.opsForHash<String, String>()
        ops.put(key, FIELD_STATUS, DeliveryStatus.READ.name)
        ops.put(key, FIELD_TIMESTAMP, System.currentTimeMillis().toString())
    }

    fun getDeliveryStatus(messageId: String): Map<String, Any> {
        val key = "$KEY_PREFIX$messageId"
        val ops = redisTemplate.opsForHash<String, String>()
        val entries = ops.entries(key)
        return entries.mapValues { it.value as Any }
    }
}