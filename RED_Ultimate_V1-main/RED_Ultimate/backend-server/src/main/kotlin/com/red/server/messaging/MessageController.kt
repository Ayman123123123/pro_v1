package com.red.server.messaging

import com.red.server.auth.repository.UserAccountRepository
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID
import com.google.protobuf.ByteString
import com.red.sovereign.proto.RedProtos

/**
 * ➡️ تحكم الرسائل — V50
 * POST /api/messages/forward — تحويل رسالة إلى محادثة خاصة أو مجموعة.
 *
 * ينشئ نسخة UUIDv7 جديدة تحمل forwardedFromConversationId ويزيد forwardCount
 * في المصدر. replyToMessageUuid اختياري ويُتحقق أنه في المحادثة الهدف (وإلا 400).
 * التحويل إلى قناة مرفوض (بث أحادي — النشر عبر مسار القنوات فقط).
 *
 * ملاحظة E2EE: الحمولة تُنسخ مشفّرة كما هي؛ إعادة التشفير لمفاتيح الهدف
 * مسؤولية العميل عند الحاجة (كما في واتساب/سيجنال).
 */
@RestController
@RequestMapping("/api/messages")
class MessageController(
    private val messages: MessageService,
    private val users: UserAccountRepository
) {

    /**
     * AUTO-FIX (message reliability): offline catch-up. Messages stored for this user while the
     * device was offline are returned as base64 RedProtos.RedRED envelopes - exactly what the live
     * WebSocket path delivers - so the app can feed them into its existing decrypt/store pipeline.
     */
    @GetMapping("/catchup")
    fun catchup(
        @RequestParam("since", defaultValue = "0") since: Long,
        @RequestParam("limit", defaultValue = "50") limit: Int,
        auth: Authentication
    ): List<Map<String, String?>> {
        val sinceInstant = if (since > 0) java.time.Instant.ofEpochMilli(since) else java.time.Instant.now().minusSeconds(48 * 3600)
        // FIX (messages reach device): auth.name is the account UUID (JwtAuthenticationFilter sets the
        // principal to user.id), while MessageDocument.receiverId holds the 5-digit YOUNES RED ID.
        // Passing the UUID straight through matched nothing, so offline catch-up always returned an
        // empty list and any message that missed the live socket never surfaced. Resolve the RED ID.
        val accountRedId = users.findById(UUID.fromString(auth.name))
            .orElseThrow { NoSuchElementException("USER_NOT_FOUND") }
            .redId
        val docs = messages.pendingFor(accountRedId, sinceInstant, limit)
        return docs.map { doc ->
            val envelope = RedProtos.RedRED.newBuilder().setMessage(
                RedProtos.ChatMessage.newBuilder()
                    .setId(doc.uuid).setConversationId(doc.conversationId)
                    .setSenderId(doc.senderId).setReceiverId(doc.receiverId)
                    .setPayload(ByteString.copyFrom(doc.payload))
                    .setTimestamp(doc.createdAt.toEpochMilli())
                    .setSequenceNumber(doc.sequenceNumber).setType(doc.messageType)
                    .setSenderDeviceId(doc.senderDeviceId).setReceiverDeviceId(doc.receiverDeviceId)
                    .setCiphertextType(doc.ciphertextType).build()
            ).build()
            mapOf("uuid" to doc.uuid, "envelope" to java.util.Base64.getEncoder().encodeToString(envelope.toByteArray()))
        }
    }

    data class ForwardRequest(
        val messageUuid: String,
        val targetConversationId: String,
        val replyToMessageUuid: String? = null
    )

    @PostMapping("/forward")
    fun forward(@RequestBody req: ForwardRequest, auth: Authentication): ResponseEntity<Any> {
        require(req.messageUuid.isNotBlank()) { "INVALID_MESSAGE_UUID" }
        require(req.targetConversationId.length in 8..128) { "INVALID_TARGET_CONVERSATION" }
        req.replyToMessageUuid?.let { require(it.isNotBlank()) { "INVALID_REPLY_TARGET" } }
        val actor = users.findById(UUID.fromString(auth.name))
            .orElseThrow { NoSuchElementException("USER_NOT_FOUND") }
        // IllegalArgumentException → 400 و NoSuchElementException → 404 عبر AuthExceptionHandler
        // مع تمرير رموز النطاق الثابتة (REPLY_TARGET_NOT_FOUND ... إلخ) للعميل.
        val result = messages.forwardMessage(actor.redId, req.messageUuid, req.targetConversationId, req.replyToMessageUuid)
        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "messageUuid" to result.messageUuid,
                "scopeKind" to result.scopeKind,
                "scopeId" to result.scopeId,
                "forwardedFrom" to result.forwardedFrom,
                "sequenceNumber" to result.sequenceNumber
            )
        )
    }
}
