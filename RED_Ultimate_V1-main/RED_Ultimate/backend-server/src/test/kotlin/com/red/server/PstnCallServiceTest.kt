package com.red.server

import com.red.server.auth.model.AccountStatus
import com.red.server.auth.model.UserAccount
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.calls.CallHistoryService
import com.red.server.pstn.DinstarLoadBalancer
import com.red.server.pstn.PstnCallService
import com.red.server.pstn.PstnManager
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.jdbc.core.JdbcTemplate
import java.util.Optional
import java.util.UUID

class PstnCallServiceTest {
    private val users = mock<UserAccountRepository>()
    private val redis = mock<StringRedisTemplate>()
    private val values = mock<ValueOperations<String, String>>()
    private val jdbc = mock<JdbcTemplate>()
    private val pstn = mock<PstnManager>()
    private val loadBalancer = mock<DinstarLoadBalancer>()
    private val history = mock<CallHistoryService>()

    @Test
    fun `daily limit rejection rolls back reservation and never reaches Asterisk`() {
        val id = UUID.randomUUID()
        val user = UserAccount(
            id = id,
            redId = "90787",
            username = "pstn-test",
            displayName = "PSTN Test",
            status = AccountStatus.APPROVED,
            pstnEnabled = true,
            pstnDailyLimit = 2
        )
        whenever(users.findById(id)).thenReturn(Optional.of(user))
        whenever(redis.opsForValue()).thenReturn(values)
        whenever(values.increment(any())).thenReturn(3)

        val service = PstnCallService(users, redis, jdbc, pstn, loadBalancer, history)
        assertThrows(IllegalArgumentException::class.java) { service.dial(id, "+967771234567") }

        verify(values).decrement(any())
        verify(loadBalancer, never()).selectPort(any())
        verifyNoInteractions(pstn)
    }
}
