package com.red.server

import com.red.server.auth.model.UserAccount
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.database.SovereignMongoDocuments.ConversationSequence
import com.red.server.database.SovereignMongoDocuments.MessageDocument
import com.red.server.messaging.MessageService
import com.red.sovereign.proto.RedProtos
import com.google.protobuf.ByteString
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyString
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mockito.Mockito.never
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

/**
 * اختبارات حقيقية لخدمة الرسائل: التحقق من UUID v7، التفويض على ACK،
 * منع المحادثة بين الحسابات المحجوبة، وعدم الازدواجية في التخزين.
 */
class MessageServiceTest {
    private val mongo: MongoTemplate = mock()
    private val redis: RedisTemplate<String, String> = mock()
    private val users: UserAccountRepository = mock()
    private val jdbc: JdbcTemplate = mock()
    private val service = MessageService(mongo, redis, users, jdbc)

    // ── أدوات مساعدة ──────────────────────────────────────────────────────────

    /** يولّد UUID بطبعة v7 صحيحة (version bits=0111, variant=10). */
    private fun uuidV7(): String {
        val v4 = UUID.randomUUID()
        val msb = (v4.mostSignificantBits and 0xFFFFFFFFFFFF0FFFL) or 0x0000000000007000L
        val lsb = (v4.leastSignificantBits and 0x3FFFFFFFFFFFFFFFL) or 0x8000000000000000L
        return UUID(msb, lsb).toString()
    }

    private fun chatMessage(
        id: String = uuidV7(),
        sender: String = "RED-AAAA-BBBB",
        receiver: String = "YNS-1234-5678",
        ciphertextType: Int = 2
    ): RedProtos.ChatMessage = RedProtos.ChatMessage.newBuilder()
        .setId(id)
        .setConversationId("conv-${uuidV7().substring(0, 12)}")
        .setSenderId(sender)
        .setReceiverId(receiver)
        .setPayload(ByteString.copyFrom(byteArrayOf(0x01)))
        .setType("TEXT")
        .setSenderDeviceId(1)
        .setReceiverDeviceId(1)
        .setCiphertextType(ciphertextType)
        .build()

    private fun account(redId: String): UserAccount = UserAccount(
        redId = redId,
        username = redId.lowercase(),
        passwordHash = "unused",
        displayName = "Test"
    )

    private fun stubPrelude(message: RedProtos.ChatMessage, blocked: Boolean = false) {
        whenever(users.findByRedId(message.senderId)).thenReturn(account(message.senderId))
        whenever(users.findByRedId(message.receiverId)).thenReturn(account(message.receiverId))
        whenever(jdbc.queryForObject(anyString(), eq(Int::class.java), any(), any())).thenReturn(if (blocked) 1 else 0)
    }

    // ── التحقق من الهوية (UUID v7) ───────────────────────────────────────────

    @Test
    fun `rejects message id that is not a UUID`() {
        val message = chatMessage(id = "not-a-uuid")
        assertThrows<IllegalArgumentException> { service.processIncoming(message) }
    }

    @Test
    fun `rejects UUID that is not version 7`() {
        val message = chatMessage(id = UUID.randomUUID().toString()) // v4
        assertThrows<IllegalArgumentException> { service.processIncoming(message) }
    }

    @Test
    fun `rejects invalid sender identity format`() {
        val message = chatMessage(sender = "attacker@evil.example")
        assertThrows<IllegalArgumentException> { service.processIncoming(message) }
    }

    @Test
    fun `rejects sender equal to receiver`() {
        val message = chatMessage(sender = "RED-AAAA-BBBB", receiver = "RED-AAAA-BBBB")
        assertThrows<IllegalArgumentException> { service.processIncoming(message) }
    }

    @Test
    fun `rejects unknown libsignal ciphertext type for direct message`() {
        val message = chatMessage(ciphertextType = 99)
        assertThrows<IllegalArgumentException> { service.processIncoming(message) }
    }

    // ── مسار التخزين الناجح ──────────────────────────────────────────────────

    @Test
    fun `valid uuid v7 message is stored once with allocated sequence`() {
        val message = chatMessage()
        whenever(mongo.findOne(any<Query>(), eq(MessageDocument::class.java))).thenReturn(null)
        stubPrelude(message)
        whenever(mongo.findAndModify(any<Query>(), any(), any(), eq(ConversationSequence::class.java)))
            .thenReturn(ConversationSequence(message.conversationId, 42))
        whenever(mongo.save(any(MessageDocument::class.java))).thenAnswer { it.arguments[0] }
        whenever(redis.opsForZSet()).thenReturn(mock())

        val stored = service.processIncoming(message)

        org.junit.jupiter.api.Assertions.assertEquals(message.id, stored.uuid)
        org.junit.jupiter.api.Assertions.assertEquals(42L, stored.sequenceNumber)
        org.junit.jupiter.api.Assertions.assertEquals("SENT", stored.status)
        verify(mongo).save(any(MessageDocument::class.java))
    }

    @Test
    fun `identical duplicate message is returned without double storage`() {
        val message = chatMessage()
        val existing = MessageDocument(
            uuid = message.id,
            conversationId = message.conversationId,
            senderId = message.senderId,
            receiverId = message.receiverId,
            payload = message.payload.toByteArray(),
            senderDeviceId = message.senderDeviceId,
            receiverDeviceId = message.receiverDeviceId,
            ciphertextType = message.ciphertextType,
            sequenceNumber = 7
        )
        whenever(mongo.findOne(any<Query>(), eq(MessageDocument::class.java))).thenReturn(existing)
        stubPrelude(message)

        val result = service.processIncoming(message)

        org.junit.jupiter.api.Assertions.assertEquals(7L, result.sequenceNumber)
        verify(mongo, never()).save(any(MessageDocument::class.java))
    }

    // ── الحجب والتفويض ───────────────────────────────────────────────────────

    @Test
    fun `blocks messaging between blocked identities`() {
        val message = chatMessage()
        stubPrelude(message, blocked = true)
        whenever(mongo.findOne(any<Query>(), eq(MessageDocument::class.java))).thenReturn(null)
        assertThrows<IllegalArgumentException> { service.processIncoming(message) }
    }

    @Test
    fun `only the target device may acknowledge a message`() {
        val receiverId = "YNS-1234-5678"
        val doc = MessageDocument(
            uuid = uuidV7(), conversationId = "conv-abc", senderId = "RED-AAAA-BBBB",
            receiverId = receiverId, payload = byteArrayOf(1), senderDeviceId = 1,
            receiverDeviceId = 1, status = "SENT"
        )
        whenever(mongo.findOne(any<Query>(), eq(MessageDocument::class.java))).thenReturn(doc)

        assertThrows<IllegalArgumentException> {
            service.acknowledge(receiverId, receiverDeviceId = 2, messageId = doc.uuid, requestedStatus = "DELIVERED")
        }
    }

    @Test
    fun `acknowledgement advances sent to read with timestamps`() {
        val receiverId = "YNS-1234-5678"
        val doc = MessageDocument(
            uuid = uuidV7(), conversationId = "conv-abc", senderId = "RED-AAAA-BBBB",
            receiverId = receiverId, payload = byteArrayOf(1), senderDeviceId = 1,
            receiverDeviceId = 1, status = "SENT"
        )
        whenever(mongo.findOne(any<Query>(), eq(MessageDocument::class.java))).thenReturn(doc)
        whenever(mongo.save(any(MessageDocument::class.java))).thenAnswer { it.arguments[0] }

        val updated = service.acknowledge(receiverId, receiverDeviceId = 1, messageId = doc.uuid, requestedStatus = "READ")

        org.junit.jupiter.api.Assertions.assertEquals("READ", updated.status)
        org.junit.jupiter.api.Assertions.assertNotNull(updated.readAt)
        org.junit.jupiter.api.Assertions.assertNotNull(updated.deliveredAt)
    }

    @Test
    fun `acknowledgement rejects unknown status`() {
        val receiverId = "YNS-1234-5678"
        val doc = MessageDocument(
            uuid = uuidV7(), conversationId = "conv-abc", senderId = "RED-AAAA-BBBB",
            receiverId = receiverId, payload = byteArrayOf(1), senderDeviceId = 1,
            receiverDeviceId = 1, status = "SENT"
        )
        whenever(mongo.findOne(any<Query>(), eq(MessageDocument::class.java))).thenReturn(doc)

        assertThrows<IllegalArgumentException> {
            service.acknowledge(receiverId, receiverDeviceId = 1, messageId = doc.uuid, requestedStatus = "EVERYWHERE")
        }
    }
}
