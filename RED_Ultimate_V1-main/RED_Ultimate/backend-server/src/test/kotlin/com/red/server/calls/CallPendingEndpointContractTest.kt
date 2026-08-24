package com.red.server.calls

import com.red.server.auth.model.AccountStatus
import com.red.server.auth.model.UserAccount
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.services.NotificationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import java.lang.reflect.Method
import java.util.Optional
import java.util.UUID

class CallPendingEndpointContractTest {
    @Test
    fun `pending offer pull is exposed as POST endpoint for Android polling`() {
        val method: Method = CallHistoryController::class.java.methods.first {
            it.name == "pullPending" && it.parameterTypes.firstOrNull() == CallHistoryController.PullPendingRequest::class.java
        }
        val mapping = method.getAnnotation(PostMapping::class.java)

        assertTrue(mapping != null, "Android polls pending offers with POST and the backend must expose PostMapping")
        assertTrue(mapping.value.contains("/pending"), "pending POST mapping must remain available")
    }

    @Test
    fun `push notify takes caller identity from JWT rather than the request body`() {
        val users = mock<UserAccountRepository>()
        val history = mock<CallHistoryService>()
        val notifications = mock<NotificationService>()
        val callerId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val caller = UserAccount(
            id = callerId,
            redId = "verified-caller",
            username = "caller",
            displayName = "Verified Caller",
            status = AccountStatus.APPROVED
        )
        val target = UserAccount(
            id = targetId,
            redId = "target-user",
            username = "target",
            displayName = "Target User",
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
        assertEquals(200, stored.statusCode.value())

        val pendingRequest = CallHistoryController.PullPendingRequest().apply { this.callId = callId }
        val pulled = controller.pullPending(pendingRequest, targetAuth)
        assertEquals(200, pulled.statusCode.value())
        val body = pulled.body as Map<*, *>
        assertEquals(caller.redId, body["callerId"])
    }
}
