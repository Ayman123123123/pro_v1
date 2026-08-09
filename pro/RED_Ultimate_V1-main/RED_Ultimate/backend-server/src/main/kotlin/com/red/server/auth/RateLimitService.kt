package com.red.server.auth

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Duration

@Service
class RateLimitService(private val redis: StringRedisTemplate) {
    fun check(namespace: String, identity: String, maximum: Long, window: Duration) {
        val key = key(namespace, identity)
        val count = redis.opsForValue().increment(key) ?: 1L
        if (count == 1L) redis.expire(key, window)
        if (count > maximum) throw RateLimitExceededException()
    }

    fun reset(namespace: String, identity: String) { redis.delete(key(namespace, identity)) }

    private fun key(namespace: String, identity: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(identity.lowercase().toByteArray()).joinToString("") { "%02x".format(it) }
        return "red:rate:$namespace:$digest"
    }
}

class RateLimitExceededException : RuntimeException("Too many requests")
