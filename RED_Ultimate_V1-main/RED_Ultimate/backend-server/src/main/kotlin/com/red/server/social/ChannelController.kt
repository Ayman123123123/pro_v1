package com.red.server.social

import com.red.server.auth.RateLimitService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.time.Duration
import java.util.UUID

/**
 * 📢 تحكم القنوات — V26 مع Rate Limiting
 * POST /api/channels — إنشاء قناة (حد: 5/ساعة لكل مستخدم)
 * GET /api/channels — قائمة القنوات العامة
 * GET /api/channels/{id} — تفاصيل
 * POST /api/channels/{id}/join — انضمام (حد: 20/دقيقة)
 * POST /api/channels/{id}/leave — مغادرة
 *
 * الهدف: منع إغراق القنوات بالرسائل والاشتراكات (Spamming) عند وجود >1000 عضو.
 */
@RestController
@RequestMapping("/api/channels")
class ChannelController(
    private val channels: ChannelService,
    private val rateLimiter: RateLimitService
) {

    @PostMapping
    fun create(@RequestBody req: ChannelService.CreateChannelRequest, auth: Authentication): ResponseEntity<Any> {
        val userId = auth.name
        return try {
            // Rate limit: 5 قنوات في الساعة
            rateLimiter.check("channel:create", userId, 5, Duration.ofHours(1))
            val res = channels.create(UUID.fromString(userId), req)
            ResponseEntity.ok(mapOf("success" to true, "channel" to res))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("success" to false, "error" to e.message))
        } catch (e: com.red.server.auth.RateLimitExceededException) {
            ResponseEntity.status(429).body(mapOf("success" to false, "error" to "CHANNEL_RATE_LIMIT"))
        }
    }

    @GetMapping
    fun list(@RequestParam(required = false) limit: Int = 20): ResponseEntity<Any> {
        val list = channels.listPublic(limit)
        return ResponseEntity.ok(mapOf("channels" to list, "count" to list.size))
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: String): ResponseEntity<Any> {
        val ch = channels.get(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(ch)
    }

    @PostMapping("/{id}/join")
    fun join(@PathVariable id: String, auth: Authentication): ResponseEntity<Any> {
        val userId = auth.name
        return try {
            rateLimiter.check("channel:join", userId, 20, Duration.ofMinutes(1))
            val ok = channels.join(UUID.fromString(userId), id)
            ResponseEntity.ok(mapOf("success" to ok))
        } catch (e: com.red.server.auth.RateLimitExceededException) {
            ResponseEntity.status(429).body(mapOf("success" to false, "error" to "JOIN_RATE_LIMIT"))
        }
    }

    @PostMapping("/{id}/leave")
    fun leave(@PathVariable id: String, auth: Authentication): ResponseEntity<Any> {
        val ok = channels.leave(UUID.fromString(auth.name), id)
        return ResponseEntity.ok(mapOf("success" to ok))
    }
}
