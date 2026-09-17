package com.red.server.messaging.v1

import com.red.server.auth.repository.UserAccountRepository
import com.red.server.messaging.AdvancedMessageService
import com.red.server.messaging.DeleteService
import com.red.server.messaging.MessageService
import com.red.server.messaging.PinnedMessageService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import jakarta.validation.Valid
import java.util.UUID

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
        
        val result = messageService.sendEncryptedEnvelope(
            senderRedId = sender.redId,
            conversationId = request.conversationId,
            payload = request.payload,
            messageType = request.messageType,
            senderDeviceId = request.senderDeviceId,
            receiverDeviceId = request.receiverDeviceId,
            ciphertextType = request.ciphertextType,
            replyToMessageId = request.replyToMessageId,
            idempotencyKey = request.idempotencyKey
        )
        
        return ResponseEntity.status(HttpStatus.CREATED).body(SendMessageResponse(
            messageId = result.messageId,
            sequenceNumber = result.sequenceNumber,
            timestamp = result.timestamp
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
        
        val sinceInstant = since?.takeIf { it > 0 }?.let { java.time.Instant.ofEpochMilli(it) }
        val result = advancedMessageService.syncMessages(
            userRedId = user.redId,
            cursor = cursor,
            limit = limit.coerceIn(1, 200),
            conversationId = conversationId,
            since = sinceInstant
        )
        
        return MessageSyncResponse(
            messages = result.messages,
            nextCursor = result.nextCursor,
            hasMore = result.hasMore
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
        
        messageService.acknowledge(
            userRedId = user.redId,
            messageId = messageId,
            deviceId = request.deviceId
        )
        
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
        
        val reaction = messageService.addReaction(
            userRedId = user.redId,
            messageId = messageId,
            emoji = request.emoji,
            action = request.action
        )
        
        return ResponseEntity.ok(ReactionResponse(
            messageId = messageId,
            emoji = reaction.emoji,
            count = reaction.count,
            userReacted = reaction.userReacted
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
        
        val originalMessage = messageService.getMessage(messageId)
            .orElseThrow { NoSuchElementException("Message not found") }
        
        val result = messageService.sendEncryptedEnvelope(
            senderRedId = sender.redId,
            conversationId = originalMessage.conversationId,
            payload = request.payload,
            messageType = request.messageType,
            senderDeviceId = request.senderDeviceId,
            receiverDeviceId = request.receiverDeviceId,
            ciphertextType = request.ciphertextType,
            replyToMessageId = messageId,
            idempotencyKey = request.idempotencyKey
        )
        
        return ResponseEntity.status(HttpStatus.CREATED).body(SendMessageResponse(
            messageId = result.messageId,
            sequenceNumber = result.sequenceNumber,
            timestamp = result.timestamp
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
        val result = if (deletedForEveryone) {
            deleteService.deleteForEveryone(user.redId, messageId)
        } else {
            deleteService.deleteForMe(user.redId, messageId)
        }
        
        return ResponseEntity.ok(DeleteResponse(
            messageId = messageId,
            deletedForEveryone = deletedForEveryone,
            success = result
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
        
        val edited = messageService.editMessage(
            userRedId = user.redId,
            messageId = messageId,
            newPayload = request.newPayload,
            newMessageType = request.newMessageType
        )
        
        return ResponseEntity.ok(EditMessageResponse(
            messageId = messageId,
            edited = edited,
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
        val userId = UUID.fromString(authentication.name)
        val user = users.findById(userId)
            .orElseThrow { NoSuchElementException("User not found") }
        
        val fromInstant = fromDate?.takeIf { it > 0 }?.let { java.time.Instant.ofEpochMilli(it) }
        val toInstant = toDate?.takeIf { it > 0 }?.let { java.time.Instant.ofEpochMilli(it) }
        
        val results = advancedMessageService.searchMessages(
            userRedId = user.redId,
            query = query,
            conversationId = conversationId,
            messageType = messageType,
            senderId = senderId,
            fromDate = fromInstant,
            toDate = toInstant,
            offset = offset.coerceAtLeast(0),
            limit = limit.coerceIn(1, 100)
        )
        
        return MessageSearchResponse(
            messages = results.messages,
            totalHits = results.totalHits,
            offset = offset,
            limit = limit
        )
    }

    @GetMapping("/pinned")
    fun getPinned(
        @RequestParam conversationId: String,
        authentication: Authentication
    ): List<PinnedMessageResponse> {
        val userId = UUID.fromString(authentication.name)
        val user = users.findById(userId)
            .orElseThrow { NoSuchElementException("User not found") }
        
        return pinnedMessageService.getPinnedMessages(user.redId, conversationId)
            .map { PinnedMessageResponse(
                messageId = it.messageId,
                conversationId = it.conversationId,
                pinnedAt = it.pinnedAt,
                pinnedBy = it.pinnedBy
            ) }
    }

    @PostMapping("/{messageId}/pin")
    fun pin(
        @PathVariable messageId: String,
        authentication: Authentication
    ): ResponseEntity<PinnedMessageResponse> {
        val userId = UUID.fromString(authentication.name)
        val user = users.findById(userId)
            .orElseThrow { NoSuchElementException("User not found") }
        
        val message = messageService.getMessage(messageId)
            .orElseThrow { NoSuchElementException("Message not found") }
        
        val pinned = pinnedMessageService.pinMessage(user.redId, message.conversationId, messageId)
        
        return ResponseEntity.ok(PinnedMessageResponse(
            messageId = pinned.messageId,
            conversationId = pinned.conversationId,
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
        val user = users.findById(userId)
            .orElseThrow { NoSuchElementException("User not found") }
        
        val message = messageService.getMessage(messageId)
            .orElseThrow { NoSuchElementException("Message not found") }
        
        pinnedMessageService.unpinMessage(user.redId, message.conversationId, messageId)
        
        return ResponseEntity.noContent().build()
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
    val idempotencyKey: String? = null
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