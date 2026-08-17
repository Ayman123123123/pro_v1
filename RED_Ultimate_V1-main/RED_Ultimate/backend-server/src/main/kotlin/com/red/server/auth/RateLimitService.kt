package com.red.server.auth

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Duration

@Service
class RateLimitService(private val redis: StringRedisTemplate) {

    // سكربت Lua ذرّي: INCR + PEXPIRE في عملية واحدة — لا يمكن تجاوزها بطلبين متزامنين
    private val rateLimitScript: DefaultRedisScript<Long> = DefaultRedisScript<Long>().apply {
        setScriptText(
            """
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """.trimIndent()
        )
        resultType = Long::class.javaObjectType
    }

    fun check(namespace: String, identity: String, maximum: Long, window: Duration) {
        val key = key(namespace, identity)
        val current = redis.execute(
            rateLimitScript,
            listOf(key),
            window.toMillis().toString()
        ) ?: 1L
        if (current > maximum) throw RateLimitExceededException()
    }

    fun reset(namespace: String, identity: String) { redis.delete(key(namespace, identity)) }

    private fun key(namespace: String, identity: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(identity.lowercase().toByteArray()).joinToString("") { "%02x".format(it) }
        return "red:rate:$namespace:$digest"
    }
}

class RateLimitExceededException : RuntimeException("Too many requests")
