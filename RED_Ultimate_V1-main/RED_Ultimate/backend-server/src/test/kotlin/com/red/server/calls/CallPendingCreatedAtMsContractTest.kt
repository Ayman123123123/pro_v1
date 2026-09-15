package com.red.server.calls

import com.red.server.auth.model.AccountStatus
import com.red.server.auth.model.UserAccount
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.services.NotificationService
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.security.core.Authentication
import java.util.Optional
import java.util.UUID

class CallPendingCreatedAtMsContractTest {
    @Test
    fun `pending pull returns numeric createdAtMs`() {
        val users = mock<UserAccountRepository>()
        val history = mock<CallHistoryService>()
        val notifications = mock<NotificationService>()
        val callerId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val caller = UserAccount(
            id = callerId,
            redId = "pending-caller",
            username = "caller",
            displayName = "Pending Caller",
            status = AccountStatus.APPROVED
        )
        val target = UserAccount(
            id = targetId,
            redId = "pending-target",
            username = "target",
            displayName = "Pending Target",
            status = AccountStatus.APPROVED
        )
        whenever(users.findById(callerId)).thenReturn(Optional.of(caller))
        whenever(users.findById(targetId)).thenReturn(Optional.of(target))

        val controller = CallHistoryController(history, users, notifications)
        val callerAuth = mock<Authentication> { on { name }.thenReturn(callerId.toString()) }
        val targetAuth = mock<Authentication> { on { name }.thenReturn(targetId.toString()) }
        val callId = UUID.randomUUID().toString()

        val stored = controller.pushNotify(
            CallHistoryController.PushNotifyRequest(
                callId = callId,
                targetRedId = target.redId,
                callerId = "spoofed-caller",
                mode = "VOICE",
                offerSdp = "v=0"
            ),
            callerAuth
        )
        assertTrue(stored.statusCode.is2xxSuccessful)

        val pendingRequest = CallHistoryController.PullPendingRequest().apply { this.callId = callId }
        val pulled = controller.pullPending(pendingRequest, targetAuth)
        assertTrue(pulled.statusCode.is2xxSuccessful)
        val body = pulled.body as Map<*, *>
        val createdAtMs = body["createdAtMs"]
        assertTrue(createdAtMs is Number, "createdAtMs must be numeric, got: $createdAtMs")
        assertTrue((createdAtMs as Number).toLong() > 0, "createdAtMs must be positive epoch millis")
    }
}
