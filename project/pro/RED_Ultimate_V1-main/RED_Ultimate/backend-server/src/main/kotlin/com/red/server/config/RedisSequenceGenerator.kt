package com.red.server.config

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

/**
 * RED Redis Logic
 * مسؤول عن توليد أرقام التسلسل (Sequence) ومنع ضياع الرسائل
 */
@Component
class RedisSequenceGenerator(private val redis: StringRedisTemplate) {

    fun getNextSequence(conversationId: String): Long {
        return redis.opsForValue().increment("seq:$conversationId") ?: 0L
    }

    fun setUserOnline(userId: String) {
        redis.opsForValue().set("status:$userId", "ONLINE", 60, java.util.concurrent.TimeUnit.SECONDS)
    }

    fun isUserOnline(userId: String): Boolean {
        return redis.hasKey("status:$userId")
    }
}
