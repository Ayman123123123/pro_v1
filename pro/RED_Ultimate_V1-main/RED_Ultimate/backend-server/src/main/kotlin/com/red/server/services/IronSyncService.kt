package com.red.server.services

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class IronSyncService(private val redis: StringRedisTemplate) {
    fun updateStateVector(userId: String, deviceId: String, lastSeq: Long) {
        val key = "red:vector:$userId:$deviceId"
        val index = "red:vector-index:$userId"
        redis.opsForValue().set(key, lastSeq.toString(), 30, TimeUnit.DAYS)
        redis.opsForSet().add(index, deviceId)
        redis.expire(index, 30, TimeUnit.DAYS)
    }

    fun getDeviceGaps(userId: String): Map<String, Long> {
        val index = "red:vector-index:$userId"
        return (redis.opsForSet().members(index) ?: emptySet()).mapNotNull { deviceId ->
            val value = redis.opsForValue().get("red:vector:$userId:$deviceId")
            if (value == null) { redis.opsForSet().remove(index, deviceId); null }
            else deviceId to value.toLongOrNull().orZero()
        }.toMap()
    }

    private fun Long?.orZero() = this ?: 0L
}
