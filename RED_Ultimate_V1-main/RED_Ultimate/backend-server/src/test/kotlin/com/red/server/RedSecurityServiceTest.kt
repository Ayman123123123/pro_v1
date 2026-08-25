package com.red.server

import com.red.server.auth.model.AccountRole
import com.red.server.auth.model.UserAccount
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.services.AdminUserIntelligenceService
import com.red.server.services.RedSecurityService
import com.red.server.websocket.RedMasterHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional
import java.util.UUID

class RedSecurityServiceTest {
    private val users = mock<UserAccountRepository>()
    private val intelligence = mock<AdminUserIntelligenceService>()
    private val messaging = mock<RedMasterHandler>()
    private val service = RedSecurityService(users, intelligence, messaging)

    @Test
    fun `wipe request is durable and delivered over the authenticated socket`() {
        val actor = UUID.randomUUID()
        val user = UserAccount(redId = "71234", username = "target", displayName = "Target")
        whenever(users.findById(user.id)).thenReturn(Optional.of(user))
        whenever(intelligence.requestRemoteAppWipe(user.id, actor)).thenReturn(user)
        whenever(intelligence.pendingRemoteWipeCommand(user.id)).thenReturn("wipe_${user.id}_123")

        val result = service.sendWipeSignal(user.id.toString(), actor)

        assertEquals("REQUESTED", result["status"])
        assertEquals(true, result["durable"])
        verify(messaging).sendRemoteWipe(user.redId, "wipe_${user.id}_123", "ADMIN_REQUESTED_REMOTE_APP_WIPE")
    }

    @Test
    fun `administrator accounts cannot be remotely wiped`() {
        val actor = UUID.randomUUID()
        val admin = UserAccount(redId = "70001", username = "admin", displayName = "Admin", role = AccountRole.ADMIN)
        whenever(users.findById(admin.id)).thenReturn(Optional.of(admin))

        assertThrows(IllegalArgumentException::class.java) {
            service.sendWipeSignal(admin.id.toString(), actor)
        }
    }
}
