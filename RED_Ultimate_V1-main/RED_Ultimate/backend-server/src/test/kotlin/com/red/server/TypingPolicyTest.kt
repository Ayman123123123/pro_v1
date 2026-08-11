package com.red.server

import com.red.server.auth.model.UserAccount
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.messaging.MessageService
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.jdbc.core.JdbcTemplate

class TypingPolicyTest {
    private val mongo: MongoTemplate = mock()
    private val redis: RedisTemplate<String, String> = mock()
    private val users: UserAccountRepository = mock()
    private val jdbc: JdbcTemplate = mock()
    private val messages = MessageService(mongo, redis, users, jdbc)
    private val sender = UserAccount(redId = "16999", username = "ahmed", displayName = "Ahmed")
    private val receiver = UserAccount(redId = "58414", username = "mona", displayName = "Mona")

    @Test
    fun `typing obeys the same block policy as messages`() {
        whenever(users.findByRedId(sender.redId)).thenReturn(sender)
        whenever(users.findByRedId(receiver.redId)).thenReturn(receiver)
        whenever(jdbc.queryForObject(any<String>(), eq(Int::class.java), any(), any(), any(), any())).thenReturn(0)

        assertDoesNotThrow { messages.requireTypingAllowed(sender.redId, receiver.redId, "conversation-001") }

        whenever(jdbc.queryForObject(any<String>(), eq(Int::class.java), any(), any(), any(), any())).thenReturn(1)
        assertThrows(IllegalArgumentException::class.java) {
            messages.requireTypingAllowed(sender.redId, receiver.redId, "conversation-001")
        }
    }
}
