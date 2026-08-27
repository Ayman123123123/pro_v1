package com.red.server.messaging

import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

/**
 * ðŸ“Œ تحكم تثبيت الرسائل â€” V26
 * POST /api/messages/pins â€” تثبيت
 * DELETE /api/messages/pins/{uuid} â€” إلغاء
 * GET /api/messages/pins?conversationId=... â€” قائمة المثبتة
 */
@RestController
@RequestMapping("/api/messages/pins")
class PinnedMessageController(private val pins: PinnedMessageService) {

    data class PinRequest(
        val messageUuid: String,
        val conversationId: String? = null,
        val groupId: String? = null,
        val channelId: String? = null,
        val expiresInSeconds: Long? = null
    )

    @PostMapping
    fun pin(@RequestBody req: PinRequest, auth: Authentication): ResponseEntity<Any> {
        val actorId = UUID.fromString(auth.name)
        return try {
            val expiresAt = req.expiresInSeconds?.let { Instant.now().plusSeconds(it) }
            val res = pins.pin(actorId, req.messageUuid, req.conversationId, req.groupId, req.channelId, expiresAt)
            ResponseEntity.ok(mapOf("success" to true, "pin" to res))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("success" to false, "error" to e.message))
        } catch (e: Exception) {
            ResponseEntity.internalServerError().body(mapOf("success" to false, "error" to e.message))
        }
    }

    @DeleteMapping("/{messageUuid}")
    fun unpin(@PathVariable messageUuid: String, auth: Authentication): ResponseEntity<Any> {
        val actorId = UUID.fromString(auth.name)
        val ok = pins.unpin(actorId, messageUuid)
        return if (ok) ResponseEntity.ok(mapOf("success" to true))
        else ResponseEntity.status(404).body(mapOf("success" to false, "error" to "PIN_NOT_FOUND"))
    }

    @GetMapping
    fun list(
        @RequestParam(required = false) conversationId: String?,
        @RequestParam(required = false) groupId: String?,
        auth: Authentication
    ): ResponseEntity<Any> {
        val actorId = UUID.fromString(auth.name)
        val list = when {
            conversationId != null -> pins.listForConversation(conversationId)
            groupId != null -> pins.listForGroup(groupId)
            else -> return ResponseEntity.badRequest().body(mapOf("error" to "MISSING_SCOPE"))
        }
        return ResponseEntity.ok(mapOf("pins" to list, "count" to list.size))
    }
}

