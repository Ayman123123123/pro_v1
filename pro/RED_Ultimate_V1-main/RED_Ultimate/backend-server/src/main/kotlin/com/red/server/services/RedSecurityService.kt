package com.red.server.services

import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service

/**
 * RED Security Service — handles device wipe, kill switch, and security operations.
 */
@Service
class RedSecurityService(
    private val redisTemplate: RedisTemplate<String, String>
) {
    /**
     * Send wipe signal to a specific device
     */
    fun sendWipeSignal(userId: String): Map<String, Any> {
        redisTemplate.convertAndSend("security:wipe", userId)
        return mapOf(
            "status" to "SENT",
            "userId" to userId,
            "action" to "WIPE",
            "timestamp" to System.currentTimeMillis()
        )
    }

    /**
     * Activate kill switch — wipes all devices
     */
    fun activateKillSwitch(reason: String): Map<String, Any> {
        redisTemplate.convertAndSend("security:kill-switch", reason)
        return mapOf(
            "status" to "ACTIVATED",
            "reason" to reason,
            "timestamp" to System.currentTimeMillis()
        )
    }

    /**
     * Check device security status
     */
    fun getDeviceSecurityStatus(userId: String): Map<String, Any> {
        val lastSeen = redisTemplate.opsForValue().get("security:last-seen:$userId")
        val isBlocked = redisTemplate.opsForValue().get("security:blocked:$userId") == "true"
        
        return mapOf(
            "userId" to userId,
            "isBlocked" to isBlocked,
            "lastSeen" to (lastSeen ?: "unknown"),
            "securityLevel" to "HIGH"
        )
    }
}
