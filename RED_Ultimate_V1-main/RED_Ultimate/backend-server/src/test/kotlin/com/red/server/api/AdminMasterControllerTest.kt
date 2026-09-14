package com.red.server.api

import com.red.server.auth.RedApprovalService
import com.red.server.auth.UserAccountResponse
import com.red.server.auth.model.AccountRole
import com.red.server.auth.model.AccountStatus
import com.red.server.services.CoreService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import java.time.Instant
import java.util.UUID

class AdminMasterControllerTest {

    private lateinit var controller: AdminMasterController
    private val mockApproval = mock(RedApprovalService::class.java)
    private val mockCore = mock(CoreService::class.java)

    @BeforeEach
    fun setup() {
        controller = AdminMasterController(mockApproval, mockCore)
    }

    private fun auth(redId: String): Authentication =
        UsernamePasswordAuthenticationToken(redId, null)

    private fun userResponse(id: UUID): UserAccountResponse = UserAccountResponse(
        id = id,
        redId = "YNS-TEST",
        username = "test",
        displayName = "Test User",
        status = AccountStatus.PENDING,
        role = AccountRole.USER,
        createdAt = Instant.now(),
        updatedAt = Instant.now(),
        rejectionReason = null
    )

    @Test
    fun `getGlobalStats delegates to CoreService`() {
        val stats = mapOf("totalUsers" to 100L, "activeCalls" to 5L)
        `when`(mockCore.getAggregatedStats()).thenReturn(stats)

        val result = controller.getGlobalStats() as ResponseEntity<*>
        assertEquals(200, result.statusCode.value())
        assertEquals(stats, result.body)
        verify(mockCore).getAggregatedStats()
    }

    @Test
    fun `getPendingUsers delegates to approval`() {
        val pending = listOf(userResponse(UUID.randomUUID()))
        `when`(mockApproval.getPendingList()).thenReturn(pending)

        val result = controller.getPendingUsers() as ResponseEntity<*>
        assertEquals(200, result.statusCode.value())
        assertEquals(pending, result.body)
        verify(mockApproval).getPendingList()
    }

    @Test
    fun `approveUser delegates to approval processAction`() {
        val userId = UUID.randomUUID()
        val adminId = UUID.randomUUID()
        val approved = userResponse(userId).copy(status = AccountStatus.APPROVED)
        `when`(mockApproval.processAction(userId, AccountStatus.APPROVED, adminId = adminId)).thenReturn(approved)

        val result = controller.approveUser(userId.toString(), auth(adminId.toString())) as ResponseEntity<*>
        assertEquals(200, result.statusCode.value())
        assertEquals(approved, result.body)
        verify(mockApproval).processAction(userId, AccountStatus.APPROVED, adminId = adminId)
    }
}