package com.red.server.calls

import com.red.server.auth.model.AccountStatus
import com.red.server.auth.model.UserAccount
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.services.NotificationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.core.Authentication
import java.util.Optional
import java.util.UUID

/**
 * التحقق الزمني لمجدول المكالمات: مستقبلي/سنة كحد أقصى + نافذة الاستحقاق + تنظيف.
 */
class ScheduledCallReminderTest {
    private val yearMillis = 365L * 24 * 60 * 60 * 1000

    @Test
    fun `past time is rejected`() {
        val now = System.currentTimeMillis()
        assertEquals(
            "SCHEDULED_TIME_MUST_BE_FUTURE",
            ScheduledCallController.validateScheduleTime(now - 1_000, now)
        )
    }

    @Test
    fun `beyond one year is rejected, exactly one year accepted`() {
        val now = System.currentTimeMillis()
        assertEquals(
            "SCHEDULED_TOO_FAR",
            ScheduledCallController.validateScheduleTime(now + yearMillis + 1, now)
        )
        assertNull(ScheduledCallController.validateScheduleTime(now + yearMillis, now))
        assertNull(ScheduledCallController.validateScheduleTime(now + 60_000, now))
    }

    @Test
    fun `due window covers next minute and recent overdue only`() {
        val now = System.currentTimeMillis()
        assertTrue(ScheduledCallController.isDue(now + 30_000, now))
        assertTrue(ScheduledCallController.isDue(now - 30_000, now))
        assertFalse(ScheduledCallController.isDue(now + 10 * 60_000, now))
        assertFalse(ScheduledCallController.isDue(now - 10 * 60_000, now))
    }

    @Test
    fun `expired only beyond retain window`() {
        val now = System.currentTimeMillis()
        assertFalse(ScheduledCallController.isExpired(now - 60_000, now))
        assertTrue(ScheduledCallController.isExpired(now - 10 * 60_000, now))
    }

    @Test
    fun `schedule endpoint rejects past time`() {
        val users = mock<UserAccountRepository>()
        val notifications = mock<NotificationService>()
        val accountId = UUID.randomUUID()
        whenever(users.findById(accountId)).thenReturn(
            Optional.of(
                UserAccount(
                    id = accountId,
                    redId = "sched-owner",
                    username = "owner",
                    displayName = "Sched Owner",
                    status = AccountStatus.APPROVED
                )
            )
        )
        val controller = ScheduledCallController(users, notifications)
        val auth = mock<Authentication> { on { name }.thenReturn(accountId.toString()) }
        val ex = assertThrows(IllegalArgumentException::class.java) {
            controller.schedule(
                ScheduleCallRequest(
                    title = "past",
                    roomId = "room_past_1",
                    timeMillis = System.currentTimeMillis() - 1_000
                ),
                auth
            )
        }
        assertTrue(ex.message!!.contains("SCHEDULED_TIME_MUST_BE_FUTURE"))
    }

    @Test
    fun `remindDue notifies invitees plus owner exactly once`() {
        val users = mock<UserAccountRepository>()
        val notifications = mock<NotificationService>()
        val accountId = UUID.randomUUID()
        whenever(users.findById(accountId)).thenReturn(
            Optional.of(
                UserAccount(
                    id = accountId,
                    redId = "sched-owner-2",
                    username = "owner2",
                    displayName = "Sched Owner 2",
                    status = AccountStatus.APPROVED
                )
            )
        )
        val controller = ScheduledCallController(users, notifications)
        val auth = mock<Authentication> { on { name }.thenReturn(accountId.toString()) }
        val now = System.currentTimeMillis()
        val created = controller.schedule(
            ScheduleCallRequest(
                title = "due soon",
                roomId = "room_due_once_1",
                invitees = listOf("invitee-a", "invitee-b"),
                timeMillis = now + 30_000
            ),
            auth
        )
        val id = created.body!!.id
        try {
            assertEquals(1, controller.remindDue(System.currentTimeMillis()))
            // مدعوّان + المالك = 3 دفعات، ومرة واحدة فقط رغم الجولة الثانية.
            verify(notifications, times(3))
                .sendVoipPushNotification(any(), any(), any(), any())
            assertEquals(0, controller.remindDue(System.currentTimeMillis()))
            verify(notifications, times(3))
                .sendVoipPushNotification(any(), any(), any(), any())
        } finally {
            runCatching { controller.delete(id, auth) }
        }
    }
}
