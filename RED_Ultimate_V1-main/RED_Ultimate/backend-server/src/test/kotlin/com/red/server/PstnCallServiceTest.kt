package com.red.server

import com.red.server.auth.model.AccountStatus
import com.red.server.auth.model.UserAccount
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.calls.CallHistoryService
import com.red.server.pstn.DinstarLoadBalancer
import com.red.server.pstn.EnhancedPstnManager
import com.red.server.pstn.PersistentReservationService
import com.red.server.pstn.PstnCallProgressTracker
import com.red.server.pstn.PstnCallService
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ScheduledExecutorService

/**
 * يثبّت أن إخفاق ما قبل الاتصال يُرجِع كل قيد حُجز مسبقًا، ولا يصل نداء
 * إلى Asterisk.
 *
 * حُدِّث المزدوجان بعد نقل الخدمة إلى [EnhancedPstnManager] (مجمّع اتصالات
 * AMI بدل اتصال واحد) وإضافة [PersistentReservationService] (حجز يصمد عبر
 * إعادة التشغيل بدل Redis وحده).
 */
class PstnCallServiceTest {
    private val users = mock<UserAccountRepository>()
    private val redis = mock<StringRedisTemplate>()
    private val values = mock<ValueOperations<String, String>>()
    private val pstn = mock<EnhancedPstnManager>()
    private val loadBalancer = mock<DinstarLoadBalancer>()
    private val history = mock<CallHistoryService>()
    private val progress = PstnCallProgressTracker()
    private val retryScheduler = mock<ScheduledExecutorService>()
    private val reservations = mock<PersistentReservationService>()

    private fun service() = PstnCallService(
        users, redis, pstn, loadBalancer, history, progress, retryScheduler, reservations
    )

    @Test
    fun `gateway selection failure releases pre-dial reservation`() {
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
        whenever(values.increment(any())).thenReturn(1)
        whenever(values.setIfAbsent(any(), any(), any())).thenReturn(true)
        whenever(loadBalancer.selectPort(anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(null)

        assertThrows(IllegalStateException::class.java) { service().dial(id, "+967771234567") }

        verify(values).decrement(any())
        verify(redis).delete("red:pstn:active:$id")
        verify(pstn, never()).dialGsm(any(), any(), any(), anyOrNull())
    }

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

        assertThrows(com.red.server.auth.RateLimitExceededException::class.java) {
            service().dial(id, "+967771234567")
        }

        verify(values).decrement(any())
        verify(loadBalancer, never()).selectPort(anyOrNull(), anyOrNull(), anyOrNull())
        verify(pstn, never()).dialGsm(any(), any(), any(), anyOrNull())
    }
}
