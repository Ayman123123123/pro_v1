package com.red.server.config

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration

/**
 * Distributed lock for @Scheduled tasks using Redis.
 * Prevents multiple instances of the backend from running the same scheduled task simultaneously.
 * 
 * It intercepts all methods annotated with @Scheduled.
 * The lock is held for a minimum of 5 seconds to prevent clock-skew double runs (e.g., cron jobs),
 * and a maximum of 10 minutes (to recover from crashes).
 */
@Aspect
@Configuration
class ScheduledLockAspect(private val redisTemplate: StringRedisTemplate) {
    private val log = LoggerFactory.getLogger(ScheduledLockAspect::class.java)

    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled)")
    fun lockScheduledTask(joinPoint: ProceedingJoinPoint): Any? {
        val methodName = joinPoint.signature.toShortString()
        val lockKey = "red:lock:scheduled:$methodName"
        
        // Max lock time of 10 minutes in case the node crashes while executing
        val acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "LOCKED", Duration.ofMinutes(10))
        
        if (acquired == true) {
            log.debug("Acquired distributed lock for scheduled task: {}", methodName)
            val startTime = System.currentTimeMillis()
            try {
                return joinPoint.proceed()
            } finally {
                val elapsed = System.currentTimeMillis() - startTime
                // Keep the lock for at least 5 seconds to prevent another node with slight clock skew
                // from executing the same cron job.
                val remainingWait = 5_000L - elapsed
                if (remainingWait > 0) {
                    redisTemplate.expire(lockKey, Duration.ofMillis(remainingWait))
                } else {
                    redisTemplate.delete(lockKey)
                }
            }
        } else {
            log.debug("Skipping scheduled task (locked by another node): {}", methodName)
            return null
        }
    }
}
