package com.red.server

import com.red.server.auth.ContactService
import com.red.server.auth.model.AccountStatus
import com.red.server.auth.model.UserAccount
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.social.UserStatusService
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import java.util.Optional
import java.util.UUID

class ContactBlockMediaGrantTest {
    private val jdbc: JdbcTemplate = mock()
    private val users: UserAccountRepository = mock()
    private val redis: RedisTemplate<String, String> = mock()
    private val presence: UserStatusService = mock()
    private val contacts = ContactService(jdbc, users, redis, presence)

    @Test
    fun `blocking an identity revokes media grants in both directions`() {
        val owner = UUID.randomUUID()
        val target = UserAccount(id = UUID.randomUUID(), redId = "58414", username = "mona", displayName = "Mona", status = AccountStatus.APPROVED)
        whenever(users.findByRedId(target.redId)).thenReturn(target)

        contacts.block(owner, target.redId)

        val sql = argumentCaptor<String>()
        verify(jdbc, atLeastOnce()).update(sql.capture(), any(), any(), any(), any())
        assertTrue(sql.allValues.any { it.contains("DELETE FROM media_grants") })
    }

    @Test
    fun `contact lookup accepts username with at sign`() {
        val owner = UUID.randomUUID()
        val target = UserAccount(
            id = UUID.randomUUID(),
            redId = "58414",
            username = "mona",
            displayName = "Mona",
            status = AccountStatus.APPROVED
        )
        whenever(users.findByRedId("MONA")).thenReturn(null)
        whenever(users.findByUsernameIgnoreCase("mona")).thenReturn(target)
        whenever(users.findById(owner)).thenReturn(Optional.empty())

        contacts.block(owner, "@mona")

        verify(users).findByUsernameIgnoreCase("mona")
    }
}
