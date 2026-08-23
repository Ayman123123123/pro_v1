package com.red.server.messaging

import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID

/**
 * 📌 تحكم تثبيت الرسائل — V26
 * POST /api/messages/pins — تثبيت
 * DELETE /api/messages/pins/{uuid} — إلغاء
 * GET /api/messages/pins?conversationId=... — قائمة المثبتة
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
        // يحتاج RED ID للممثل لإظهاره في الرسالة المثبتة — نستخدم auth.name كـ UUID ثم نجلبه من DB لاحقاً
        // للتبسيط نمرر الـ RED ID كـ actorId string (سيُحل في الخدمة)
        return try {
            val expiresAt = req.expiresInSeconds?.let { Instant.now().plusSeconds(it) }
            // نحتاج RED ID — نجلبه من JWT claims أو نستخدم actorId كـ redId مؤقتاً
            // في النظام الحالي auth.name هو UUID، لكن PinnedMessageService يستخدمه كـ pinnedBy
            // سنمرر actorId.toString كـ actorRedId للتوافق
            val res = pins.pin(actorId, actorId.toString(), req.messageUuid, req.conversationId, req.groupId, req.channelId, expiresAt)
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
            groupId != null -> pins.listForGroup(actorId, groupId)
            else -> return ResponseEntity.badRequest().body(mapOf("error" to "MISSING_SCOPE"))
        }
        return ResponseEntity.ok(mapOf("pins" to list, "count" to list.size))
    }
}
