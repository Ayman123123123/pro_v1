package com.red.server

import com.red.server.auth.ContactService
import com.red.server.auth.model.AccountStatus
import com.red.server.auth.model.UserAccount
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.social.UserStatusService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.startsWith
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

class ContactBlockMediaGrantTest {
    private val jdbc: JdbcTemplate = mock()
    private val users: UserAccountRepository = mock()
    private val redis: RedisTemplate<String, String> = mock()
    private val contacts = ContactService(jdbc, users, redis)

    @Test
    fun `blocking an identity revokes media grants in both directions`() {
        val owner = UUID.randomUUID()
        val target = UserAccount(id = UUID.randomUUID(), redId = "58414", username = "mona", displayName = "Mona", status = AccountStatus.APPROVED)
        whenever(users.findByRedId(target.redId)).thenReturn(target)

        contacts.block(owner, target.redId)

        verify(jdbc).update(
            startsWith("DELETE FROM media_grants"),
            eq(owner), eq(target.id), eq(target.id), eq(owner)
        )
    }
}
