package com.red.server.messaging.v1

import com.google.protobuf.ByteString
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.messaging.AdvancedMessageService
import com.red.server.messaging.DeleteService
import com.red.server.messaging.MessageService
import com.red.server.messaging.PinnedMessageService
import com.red.server.social.UuidV7
import com.red.sovereign.proto.RedProtos
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import jakarta.validation.Valid
import java.util.Base64
import java.util.UUID

/**
 * V1 التوافقي — محوّل رقيق فوق الخدمات القائمة (لا منطق مكرر هنا).
 * كل المسارات تفوّض إلى MessageService/Advanced/Delete/Pinned الموجودة.
 */
@RestController
@RequestMapping("/api/v1/messages")
class MessageV1Controller(
    private val messageService: MessageService,
    private val advancedMessageService: AdvancedMessageService,
    private val deleteService: DeleteService,
    private val pinnedMessageService: PinnedMessageService,
    private val users: UserAccountRepository
) {

    @PostMapping("/send")
    fun send(
        @Valid @RequestBody request: SendMessageRequest,
        authentication: Authentication
    ): ResponseEntity<SendMessageResponse> {
        val senderId = UUID.fromString(authentication.name)
        val sender = users.findById(senderId)
            .orElseThrow { NoSuchElementException("User not found") }

        val receiverId = request.receiverId?.trim()?.takeIf { it.isNotEmpty() }
            ?: inferPeer(sender.redId, request.conversationId)
        val chat = RedProtos.ChatMessage.newBuilder()
            .setId(resolveId(request.idempotencyKey))
            .setConversationId(request.conversationId)
            .setSenderId(sender.redId)
            .setReceiverId(receiverId)
            .setPayload(ByteString.copyFrom(decodePayload(request.payload)))
            .setType(request.messageType.ifBlank { "TEXT" })
            .setSenderDeviceId(parseDevice(request.senderDeviceId, "senderDeviceId"))
            .setReceiverDeviceId(request.receiverDeviceId?.let { parseDevice(it, "receiverDeviceId") } ?: 1)
            .setCiphertextType(parseCipher(request.ciphertextType))
            .build()
        val saved = messageService.processIncoming(chat, request.replyToMessageId, null)

        return ResponseEntity.status(HttpStatus.CREATED).body(SendMessageResponse(
            messageId = saved.uuid,
            sequenceNumber = saved.sequenceNumber,
            timestamp = saved.createdAt
        ))
    }

    @GetMapping("/sync")
    fun sync(
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(required = false) conversationId: String?,
        @RequestParam(required = false) since: Long?,
        authentication: Authentication
    ): MessageSyncResponse {
        val userId = UUID.fromString(authentication.name)
        val user = users.findById(userId)
            .orElseThrow { NoSuchElementException("User not found") }
        val capped = limit.coerceIn(1, 50)

        val docs = if (!conversationId.isNullOrBlank()) {
            val from = cursor?.toLongOrNull()?.coerceAtLeast(0) ?: 0
            messageService.getMissedMessages(user.redId, conversationId, from, 0, capped)
        } else {
            val sinceInstant = since?.takeIf { it > 0 }?.let { java.time.Instant.ofEpochMilli(it) }
                ?: java.time.Instant.now().minusSeconds(48 * 3600)
            messageService.pendingFor(user.redId, sinceInstant, capped)
        }
        val items = docs.map { doc ->
            MessageSyncItem(
                messageId = doc.uuid,
                conversationId = doc.conversationId,
                senderId = doc.senderId,
                payload = Base64.getEncoder().encodeToString(doc.payload),
                messageType = doc.messageType,
                timestamp = doc.createdAt,
                sequenceNumber = doc.sequenceNumber,
                senderDeviceId = doc.senderDeviceId.toString(),
                receiverDeviceId = doc.receiverDeviceId.toString(),
                ciphertextType = doc.ciphertextType.toString(),
                replyToMessageId = doc.replyToMessageUuid
            )
        }
        return MessageSyncResponse(
            messages = items,
            nextCursor = docs.lastOrNull()?.sequenceNumber?.toString(),
            hasMore = docs.size >= capped
        )
    }

    @PostMapping("/{messageId}/ack")
    fun acknowledge(
        @PathVariable messageId: String,
        @RequestBody request: AcknowledgeRequest,
        authentication: Authentication
    ): ResponseEntity<Unit> {
        val userId = UUID.fromString(authentication.name)
        val user = users.findById(userId)
            .orElseThrow { NoSuchElementException("User not found") }
        val deviceId = request.deviceId.toIntOrNull()?.coerceIn(1, 127) ?: 1
        messageService.acknowledge(user.redId, deviceId, messageId, "DELIVERED")
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{messageId}/react")
    fun react(
        @PathVariable messageId: String,
        @Valid @RequestBody request: ReactionRequest,
        authentication: Authentication
    ): ResponseEntity<ReactionResponse> {
        val userId = UUID.fromString(authentication.name)
        val user = users.findById(userId)
            .orElseThrow { NoSuchElementException("User not found") }
        val remove = request.action.equals("remove", ignoreCase = true)
        messageService.toggleReaction(messageId, user.redId, request.emoji, remove)
        val doc = messageService.findMessage(messageId)
        val count = doc?.reactions?.count { it.emoji == request.emoji.trim() } ?: if (remove) 0 else 1
        return ResponseEntity.ok(ReactionResponse(
            messageId = messageId,
            emoji = request.emoji.trim(),
            count = count,
            userReacted = !remove
        ))
    }

    @PostMapping("/{messageId}/reply")
    fun reply(
        @PathVariable messageId: String,
        @Valid @RequestBody request: ReplyRequest,
        authentication: Authentication
    ): ResponseEntity<SendMessageResponse> {
        val senderId = UUID.fromString(authentication.name)
        val sender = users.findById(senderId)
            .orElseThrow { NoSuchElementException("User not found") }

        val original = messageService.findMessage(messageId)
            ?: throw NoSuchElementException("Message not found")
        val peer = when (sender.redId) {
            original.senderId -> original.receiverId
            original.receiverId -> original.senderId
            else -> throw IllegalArgumentException("NOT_A_PARTICIPANT")
        }
        val chat = RedProtos.ChatMessage.newBuilder()
            .setId(resolveId(request.idempotencyKey))
            .setConversationId(original.conversationId)
            .setSenderId(sender.redId)
            .setReceiverId(peer)
            .setPayload(ByteString.copyFrom(decodePayload(request.payload)))
            .setType(request.messageType.ifBlank { "TEXT" })
            .setSenderDeviceId(parseDevice(request.senderDeviceId, "senderDeviceId"))
            .setReceiverDeviceId(request.receiverDeviceId?.let { parseDevice(it, "receiverDeviceId") } ?: 1)
            .setCiphertextType(parseCipher(request.ciphertextType))
            .build()
        val saved = messageService.processIncoming(chat, messageId, null)

        return ResponseEntity.status(HttpStatus.CREATED).body(SendMessageResponse(
            messageId = saved.uuid,
            sequenceNumber = saved.sequenceNumber,
            timestamp = saved.createdAt
        ))
    }

    @DeleteMapping("/{messageId}")
    fun delete(
        @PathVariable messageId: String,
        @RequestParam(defaultValue = "me") scope: String,
        authentication: Authentication
    ): ResponseEntity<DeleteResponse> {
        val userId = UUID.fromString(authentication.name)
        val user = users.findById(userId)
            .orElseThrow { NoSuchElementException("User not found") }

        val deletedForEveryone = scope == "everyone"
        val success = if (deletedForEveryone) {
            deleteService.deleteForEveryone(messageId, user.redId) != null
        } else {
            // الحذف لي فقط عميلِي (الخادم يحتفظ للطرف الآخر) — لا مسار مكرر هنا
            true
        }
        return ResponseEntity.ok(DeleteResponse(
            messageId = messageId,
            deletedForEveryone = deletedForEveryone,
            success = success
        ))
    }

    @PatchMapping("/{messageId}")
    fun edit(
        @PathVariable messageId: String,
        @Valid @RequestBody request: EditMessageRequest,
        authentication: Authentication
    ): ResponseEntity<EditMessageResponse> {
        val userId = UUID.fromString(authentication.name)
        val user = users.findById(userId)
            .orElseThrow { NoSuchElementException("User not found") }

        advancedMessageService.editMessage(messageId, user.redId, decodePayload(request.newPayload))

        return ResponseEntity.ok(EditMessageResponse(
            messageId = messageId,
            edited = true,
            editedAt = java.time.Instant.now()
        ))
    }

    @GetMapping("/search")
    fun search(
        @RequestParam query: String,
        @RequestParam(required = false) conversationId: String?,
        @RequestParam(required = false) messageType: String?,
        @RequestParam(required = false) senderId: String?,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(required = false) fromDate: Long?,
        @RequestParam(required = false) toDate: Long?,
        authentication: Authentication
    ): MessageSearchResponse {
        // لا بحث خادمي موحّد هنا — البحث المحلي (FTS5/Room) هو الأساس؛ نُرجع فارغًا بدل مسار مكرر مكسور
        return MessageSearchResponse(emptyList(), 0, offset.coerceAtLeast(0), limit.coerceIn(1, 100))
    }

    @GetMapping("/pinned")
    fun getPinned(
        @RequestParam conversationId: String,
        authentication: Authentication
    ): List<PinnedMessageResponse> {
        return pinnedMessageService.listForConversation(conversationId)
            .map {
                PinnedMessageResponse(
                    messageId = it.messageUuid,
                    conversationId = it.conversationId ?: it.groupId ?: it.channelId ?: conversationId,
                    pinnedAt = it.pinnedAt,
                    pinnedBy = it.pinnedBy
                )
            }
    }

    @PostMapping("/{messageId}/pin")
    fun pin(
        @PathVariable messageId: String,
        authentication: Authentication
    ): ResponseEntity<PinnedMessageResponse> {
        val userId = UUID.fromString(authentication.name)
        val user = users.findById(userId)
            .orElseThrow { NoSuchElementException("User not found") }

        val message = messageService.findMessage(messageId)
            ?: throw NoSuchElementException("Message not found")

        val pinned = pinnedMessageService.pin(userId, messageId, conversationId = message.conversationId)

        return ResponseEntity.ok(PinnedMessageResponse(
            messageId = pinned.messageUuid,
            conversationId = pinned.conversationId ?: message.conversationId,
            pinnedAt = pinned.pinnedAt,
            pinnedBy = pinned.pinnedBy
        ))
    }

    @DeleteMapping("/{messageId}/pin")
    fun unpin(
        @PathVariable messageId: String,
        authentication: Authentication
    ): ResponseEntity<Unit> {
        val userId = UUID.fromString(authentication.name)
        users.findById(userId).orElseThrow { NoSuchElementException("User not found") }
        pinnedMessageService.unpin(userId, messageId)
        return ResponseEntity.noContent().build()
    }

    // ─── محوّلات رقيقة (لا منطق نطاق هنا) ───

    private fun resolveId(idempotencyKey: String?): String {
        val parsed = runCatching { UUID.fromString(idempotencyKey) }.getOrNull()
        if (parsed != null && parsed.version() == 7) return parsed.toString()
        return UuidV7.next()
    }

    private fun inferPeer(actorRedId: String, conversationId: String): String {
        val latest = messageService.getMissedMessages(actorRedId, conversationId, 0, 0, 1).firstOrNull()
        if (latest != null) {
            return if (latest.senderId == actorRedId) latest.receiverId else latest.senderId
        }
        if (conversationId.contains("self") || conversationId.contains("note") ||
            conversationId == actorRedId || conversationId.contains(actorRedId)
        ) return actorRedId
        throw IllegalArgumentException("INVALID_TARGET_CONVERSATION")
    }

    private fun decodePayload(raw: String): ByteArray {
        if (raw.isBlank()) throw IllegalArgumentException("payload is required")
        runCatching { return Base64.getDecoder().decode(raw.trim()) }.getOrNull()
        return raw.toByteArray(Charsets.UTF_8)
    }

    private fun parseDevice(raw: String, field: String): Int {
        val v = raw.trim().toIntOrNull() ?: throw IllegalArgumentException("Invalid $field")
        require(v in 1..127) { "Invalid $field" }
        return v
    }

    private fun parseCipher(raw: String): Int {
        val v = raw.trim().toIntOrNull() ?: return 2
        require(v == 2 || v == 3 || v == 4 || v == 7) { "Unsupported libsignal ciphertext type" }
        return v
    }
}

data class SendMessageRequest(
    val conversationId: String,
    val payload: String,
    val messageType: String = "TEXT",
    val senderDeviceId: String,
    val receiverDeviceId: String?,
    val ciphertextType: String,
    val replyToMessageId: String? = null,
    val idempotencyKey: String? = null,
    val receiverId: String? = null
)

data class SendMessageResponse(
    val messageId: String,
    val sequenceNumber: Long,
    val timestamp: java.time.Instant
)

data class MessageSyncResponse(
    val messages: List<MessageSyncItem>,
    val nextCursor: String?,
    val hasMore: Boolean
)

data class MessageSyncItem(
    val messageId: String,
    val conversationId: String,
    val senderId: String,
    val payload: String,
    val messageType: String,
    val timestamp: java.time.Instant,
    val sequenceNumber: Long,
    val senderDeviceId: String,
    val receiverDeviceId: String?,
    val ciphertextType: String,
    val replyToMessageId: String?
)

data class AcknowledgeRequest(
    val deviceId: String
)

data class ReactionRequest(
    val emoji: String,
    val action: String = "add"
)

data class ReactionResponse(
    val messageId: String,
    val emoji: String,
    val count: Int,
    val userReacted: Boolean
)

data class ReplyRequest(
    val payload: String,
    val messageType: String = "TEXT",
    val senderDeviceId: String,
    val receiverDeviceId: String?,
    val ciphertextType: String,
    val idempotencyKey: String? = null
)

data class DeleteResponse(
    val messageId: String,
    val deletedForEveryone: Boolean,
    val success: Boolean
)

data class EditMessageRequest(
    val newPayload: String,
    val newMessageType: String? = null
)

data class EditMessageResponse(
    val messageId: String,
    val edited: Boolean,
    val editedAt: java.time.Instant
)

data class MessageSearchResponse(
    val messages: List<MessageSearchHit>,
    val totalHits: Long,
    val offset: Int,
    val limit: Int
)

data class MessageSearchHit(
    val messageId: String,
    val conversationId: String,
    val senderId: String,
    val snippet: String,
    val messageType: String,
    val timestamp: java.time.Instant,
    val score: Float
)

data class PinnedMessageResponse(
    val messageId: String,
    val conversationId: String,
    val pinnedAt: java.time.Instant,
    val pinnedBy: String
)
