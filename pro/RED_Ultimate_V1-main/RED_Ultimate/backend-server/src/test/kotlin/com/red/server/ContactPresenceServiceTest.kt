package com.red.server

import com.red.server.auth.ContactService
import com.red.server.auth.PublicRedProfile
import com.red.server.auth.repository.UserAccountRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.ZSetOperations
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import java.util.UUID

class ContactPresenceServiceTest {
    private val jdbc = mock<JdbcTemplate>()
    private val users = mock<UserAccountRepository>()
    private val redis = mock<RedisTemplate<String, String>>()
    private val zset = mock<ZSetOperations<String, String>>()
    private val service = ContactService(jdbc, users, redis)

    @Test
    fun `presence excludes identities that are not established contacts`() {
        val owner = UUID.randomUUID()
        whenever(jdbc.query<PublicRedProfile>(any(), any<RowMapper<PublicRedProfile>>(), eq(owner)))
            .thenReturn(listOf(PublicRedProfile("RED-7K4M-82QX", "ahmed", "أحمد")))
        whenever(redis.opsForZSet()).thenReturn(zset)
        whenever(zset.score("red:presence:index", "RED-7K4M-82QX"))
            .thenReturn(System.currentTimeMillis().toDouble())

        val presence = service.presence(owner, listOf("RED-7K4M-82QX", "RED-9M3N-4QXR"))

        assertEquals(mapOf("RED-7K4M-82QX" to true), presence)
    }
}
