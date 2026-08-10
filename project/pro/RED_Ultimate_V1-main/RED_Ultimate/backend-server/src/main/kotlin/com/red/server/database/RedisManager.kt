package com.red.server.database

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class RedisManager(private val redis: StringRedisTemplate) {

    /**
     * توليد الرقم التسلسلي التالي للمحادثة (حرج للتوصيل المضمون)
     */
    fun incrementSequence(conversationId: String): Long {
        return redis.opsForValue().increment("red:seq:$conversationId") ?: 1L
    }

    /**
     * تتبع حالة الاتصال اللحظية
     */
    fun setPresence(userId: String, isOnline: Boolean) {
        val status = if (isOnline) "ONLINE" else "OFFLINE"
        redis.opsForValue().set("red:presence:$userId", status, 5, TimeUnit.MINUTES)
    }

    /**
     * معالجة "يكتب الآن"
     */
    fun setTyping(userId: String, conversationId: String) {
        redis.convertAndSend("red:typing", "$conversationId:$userId")
    }
}
