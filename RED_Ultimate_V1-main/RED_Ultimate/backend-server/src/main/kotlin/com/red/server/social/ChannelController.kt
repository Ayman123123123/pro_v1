package com.red.server.social

import jakarta.validation.Valid
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
 * GET /api/channels?search= — بحث سحابي مكمّل (P1-G)
 * GET /api/channels/{id} — تفاصيل
 * POST /api/channels/{id}/join — انضمام (حد: 20/دقيقة)
 * POST /api/channels/{id}/leave — مغادرة
 * POST /api/channels/{id}/messages — نشر رسالة
 * GET /api/channels/{id}/messages — جلب الرسائل
 * POST /api/channels/{id}/moderate — إشراف (ADMIN فقط)
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
    fun create(@Valid @RequestBody req: ChannelService.CreateChannelRequest, auth: Authentication): ResponseEntity<Any> {
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

    /**
     * GET /api/channels?limit=&search=
     *
     * - بلا `search`: القائمة العامة كما كانت (listPublic) — لا تغيير في السلوك.
     * - مع `search`: بحث سحابي مكمّل (searchCloudComplement) للبحث المحلي offline.
     *   الاستعلام الأقصر من حرفين يعيد قائمة فارغة (العميل يمنعه أصلًا عبر
     *   MESSAGE_SEARCH_MIN_LENGTH)، والحد يُقيَّد 1..100 داخل طبقة الخدمة،
     *   وفشل الاستعلام يُلتقط هناك فيعيد قائمة فارغة بدل 500.
     */
    @GetMapping
    fun list(
        @RequestParam(required = false) limit: Int = 20,
        @RequestParam(required = false) search: String? = null
    ): ResponseEntity<Any> {
        // استعلام فارغ/مسافات = قائمة عامة، فلا يُمرَّر إلى مسار البحث.
        val query = search?.trim().orEmpty()
        val list = if (query.isEmpty()) {
            channels.listPublic(limit)
        } else {
            channels.searchCloudComplement(query, limit)
        }
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

    @PostMapping("/{id}/messages")
    fun postMessage(
        @PathVariable id: String,
        @Valid @RequestBody request: PostMessageRequest,
        auth: Authentication
    ): ResponseEntity<Any> {
        val userId = UUID.fromString(auth.name)
        try {
            val message = channels.postMessage(
                actorId = userId,
                channelId = id,
                content = request.content,
                messageType = request.messageType,
                payload = request.payload,
                replyToMessageId = request.replyToMessageId,
                senderDeviceId = request.senderDeviceId,
                ciphertextType = request.ciphertextType
            )
            return ResponseEntity.ok(mapOf("success" to true, "message" to message))
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().body(mapOf("success" to false, "error" to e.message))
        } catch (e: IllegalAccessException) {
            return ResponseEntity.status(403).body(mapOf("success" to false, "error" to e.message))
        }
    }

    @GetMapping("/{id}/messages")
    fun getMessages(
        @PathVariable id: String,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(required = false) before: String?,
        @RequestParam(required = false) after: String?,
        auth: Authentication
    ): ResponseEntity<Any> {
        val userId = UUID.fromString(auth.name)
        try {
            val messages = channels.getMessages(
                actorId = userId,
                channelId = id,
                limit = limit.coerceIn(1, 100),
                before = before,
                after = after
            )
            return ResponseEntity.ok(mapOf("messages" to messages, "count" to messages.size))
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().body(mapOf("success" to false, "error" to e.message))
        }
    }

    @PostMapping("/{id}/moderate")
    fun moderate(
        @PathVariable id: String,
        @Valid @RequestBody request: ModerateRequest,
        auth: Authentication
    ): ResponseEntity<Any> {
        val userId = UUID.fromString(auth.name)
        try {
            val result = channels.moderate(
                actorId = userId,
                channelId = id,
                action = request.action,
                targetId = request.targetId,
                value = request.value
            )
            return ResponseEntity.ok(mapOf("success" to result, "action" to request.action))
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().body(mapOf("success" to false, "error" to e.message))
        } catch (e: SecurityException) {
            return ResponseEntity.status(403).body(mapOf("success" to false, "error" to e.message))
        }
    }
}

data class PostMessageRequest(
    val content: String,
    val messageType: String = "TEXT",
    val payload: String,
    val replyToMessageId: String? = null,
    val senderDeviceId: Int,
    val ciphertextType: String
)

data class ModerateRequest(
    val action: String,
    val targetId: String,
    val value: String? = null
)
