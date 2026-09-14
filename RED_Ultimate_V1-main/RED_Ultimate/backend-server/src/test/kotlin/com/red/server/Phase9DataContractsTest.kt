package com.red.server

import com.google.protobuf.ByteString
import com.red.server.auth.DeviceController
import com.red.server.auth.RefreshTokenService
import com.red.server.auth.model.RefreshSession
import com.red.server.auth.model.UserAccount
import com.red.server.auth.model.UserDevice
import com.red.server.auth.repository.RefreshSessionRepository
import com.red.server.auth.security.JwtService
import com.red.server.database.ChannelMessageDocument
import com.red.server.database.ConversationSequence
import com.red.server.database.MessageDocument
import com.red.server.database.RedisManager
import com.red.server.messaging.MessageService
import com.red.server.messaging.PinnedMessageService
import com.red.server.social.UuidV7
import com.red.sovereign.proto.RedProtos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mongodb.core.FindAndModifyOptions
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.data.redis.core.ListOperations
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * P9 — عقود البيانات: حلّ تعارض Sequence، عضوية القنوات في PostgreSQL،
 * إبطال الجلسات الأخرى، وTTLs مفاتيح Redis.
 */
class Phase9DataContractsTest {

    private fun selfMessage(): RedProtos.ChatMessage = RedProtos.ChatMessage.newBuilder()
        .setId(UuidV7.next())
        .setSenderId("10000")
        .setReceiverId("10000")
        .setConversationId("self-note-10000")
        .setSenderDeviceId(1)
        .setReceiverDeviceId(1)
        .setCiphertextType(2)
        .setPayload(ByteString.copyFrom(ByteArray(8) { 1 }))
        .build()

    @Test
    fun `sequence conflict reallocates once and retries the save`() {
        val mongo: MongoTemplate = mock()
        val service = MessageService(mongo, mock<RedisTemplate<String, String>>(), mock(), mock<JdbcTemplate>())
        whenever(mongo.findAndModify(any<Query>(), any<Update>(), any<FindAndModifyOptions>(), eq(ConversationSequence::class.java)))
            .thenReturn(ConversationSequence("self-note-10000", 7), ConversationSequence("self-note-10000", 8))
        whenever(mongo.findOne(any<Query>(), eq(MessageDocument::class.java))).thenReturn(null)
        whenever(mongo.save(any<MessageDocument>()))
            .thenThrow(DuplicateKeyException("dup (conversationId, sequenceNumber)"))
            .thenAnswer { it.arguments[0] as MessageDocument }

        val saved = service.processIncoming(selfMessage())

        assertEquals(8L, saved.sequenceNumber)
        verify(mongo, times(2)).save(any<MessageDocument>())
    }

    @Test
    fun `duplicate save with existing uuid returns stored message without reallocation`() {
        val mongo: MongoTemplate = mock()
        val service = MessageService(mongo, mock<RedisTemplate<String, String>>(), mock(), mock<JdbcTemplate>())
        val msg = selfMessage()
        val existing = MessageDocument(
            uuid = msg.id, conversationId = msg.conversationId,
            senderId = msg.senderId, receiverId = msg.receiverId,
            payload = msg.payload.toByteArray(), messageType = "TEXT",
            senderDeviceId = 1, receiverDeviceId = 1, ciphertextType = 2,
            sequenceNumber = 7L
        )
        whenever(mongo.findAndModify(any<Query>(), any<Update>(), any<FindAndModifyOptions>(), eq(ConversationSequence::class.java)))
            .thenReturn(ConversationSequence(msg.conversationId, 7))
        // الفحص المسبق لا يجدها (سباق)، لكن كتلة الـ catch تجدها — تُرجع دون إعادة حفظ
        whenever(mongo.findOne(any<Query>(), eq(MessageDocument::class.java))).thenReturn(null, existing)
        whenever(mongo.save(any<MessageDocument>())).thenThrow(DuplicateKeyException("dup uuid"))

        val saved = service.processIncoming(msg)

        assertEquals(7L, saved.sequenceNumber)
        verify(mongo, times(1)).save(any<MessageDocument>())
    }

    @Test
    fun `channel pin authorizes owner via postgres membership`() {
        val mongo: MongoTemplate = mock()
        val jdbc: JdbcTemplate = mock()
        val service = PinnedMessageService(mongo, jdbc)
        val channelId = UUID.randomUUID().toString()
        val actorId = UUID.randomUUID()
        whenever(mongo.findOne(any<Query>(), eq(ChannelMessageDocument::class.java))).thenReturn(
            ChannelMessageDocument(
                uuid = "m1", channelId = channelId, senderId = "10000",
                senderDeviceId = 1, payload = ByteArray(0), ciphertextType = 2
            )
        )
        whenever(
            jdbc.queryForObject(
                eq("SELECT role FROM channel_members WHERE channel_id=? AND user_id=?"),
                eq(String::class.java), any(), any()
            )
        ).thenReturn("ADMIN")

        val pin = service.pin(actorId, "m1", channelId = channelId)

        assertEquals("m1", pin.messageUuid)
        assertEquals(channelId, pin.channelId)
    }

    @Test
    fun `channel pin rejects subscriber role`() {
        val mongo: MongoTemplate = mock()
        val jdbc: JdbcTemplate = mock()
        val service = PinnedMessageService(mongo, jdbc)
        val channelId = UUID.randomUUID().toString()
        whenever(mongo.findOne(any<Query>(), eq(ChannelMessageDocument::class.java))).thenReturn(
            ChannelMessageDocument(
                uuid = "m1", channelId = channelId, senderId = "10000",
                senderDeviceId = 1, payload = ByteArray(0), ciphertextType = 2
            )
        )
        whenever(
            jdbc.queryForObject(
                eq("SELECT role FROM channel_members WHERE channel_id=? AND user_id=?"),
                eq(String::class.java), any(), any()
            )
        ).thenReturn("SUBSCRIBER")

        assertThrows<IllegalArgumentException> { service.pin(UUID.randomUUID(), "m1", channelId = channelId) }
    }

    @Test
    fun `revokeOthers keeps current device and revokes sessions without device`() {
        val repo: RefreshSessionRepository = mock()
        val service = RefreshTokenService(repo, 30)
        val user = UserAccount(redId = "10000", username = "u", displayName = "U")
        val current = UserDevice(user = user, identityFingerprint = "cur")
        val other = UserDevice(user = user, identityFingerprint = "oth")
        val sCurrent = RefreshSession(user = user, device = current, tokenHash = "h-cur", expiresAt = Instant.now().plusSeconds(60))
        val sOther = RefreshSession(user = user, device = other, tokenHash = "h-oth", expiresAt = Instant.now().plusSeconds(60))
        val sOrphan = RefreshSession(user = user, device = null, tokenHash = "h-orphan", expiresAt = Instant.now().plusSeconds(60))
        whenever(repo.findAllByUserIdAndRevokedAtIsNull(user.id)).thenReturn(listOf(sCurrent, sOther, sOrphan))
        whenever(repo.save(any<RefreshSession>())).thenAnswer { it.arguments[0] as RefreshSession }

        val revoked = service.revokeOthers(user.id, current.id)

        assertEquals(2, revoked)
        assertNull(sCurrent.revokedAt)
        assertNotNull(sOther.revokedAt)
        assertNotNull(sOrphan.revokedAt)
    }

    @Test
    fun `revoke-others endpoint resolves current device from access token`() {
        val refresh: RefreshTokenService = mock()
        val jwt: JwtService = mock()
        val controller = DeviceController(mock(), mock(), refresh, mock(), mock(), jwt)
        val userId = UUID.randomUUID()
        val deviceId = UUID.randomUUID()
        val auth = UsernamePasswordAuthenticationToken(userId.toString(), "access.token", emptyList())
        whenever(jwt.deviceId("access.token")).thenReturn(deviceId)
        whenever(refresh.revokeOthers(userId, deviceId)).thenReturn(3)

        val result = controller.revokeOthers(auth)

        assertTrue(result.statusCode.is2xxSuccessful)
        val body = result.body as Map<*, *>
        assertEquals(3, body["revoked"])
        assertEquals(deviceId.toString(), body["currentDeviceKept"])
    }

    @Test
    fun `redis notify and search writes carry TTL`() {
        val redis: StringRedisTemplate = mock()
        val valueOps: ValueOperations<String, String> = mock()
        val listOps: ListOperations<String, String> = mock()
        whenever(redis.opsForValue()).thenReturn(valueOps)
        whenever(redis.opsForList()).thenReturn(listOps)
        whenever(valueOps.increment(any<String>())).thenReturn(5L)
        val manager = RedisManager(redis)

        manager.incrementUnreadNotifications("u1")
        manager.pushNotification("u1", "{}")
        manager.addRecentSearch("u1", "q")

        verify(redis).expire(eq("red:notify:unread:u1"), eq(30L), eq(TimeUnit.DAYS))
        verify(redis).expire(eq("red:notify:queue:u1"), eq(30L), eq(TimeUnit.DAYS))
        verify(redis).expire(eq("red:search:recent:u1"), eq(30L), eq(TimeUnit.DAYS))
    }
}
