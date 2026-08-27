package com.red.server.pstn

import com.red.server.auth.model.AccountStatus
import com.red.server.auth.model.UserAccount
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import java.util.UUID

class PstnCallControllerTest {

    private lateinit var controller: PstnCallController
    private val calls = mock<PstnCallService>()

    @BeforeEach
    fun setup() {
        controller = PstnCallController(calls)
    }

    private fun auth(uuid: UUID): Authentication =
        UsernamePasswordAuthenticationToken(uuid.toString(), null, emptyList())

    private fun testUser(uuid: UUID) = UserAccount(
        id = uuid,
        redId = "90735",
        username = "pstn-user",
        displayName = "PSTN User",
        status = AccountStatus.APPROVED,
        pstnEnabled = true,
        pstnDailyLimit = 10
    )

    private fun testUserDisabled(uuid: UUID) = UserAccount(
        id = uuid,
        redId = "90735",
        username = "pstn-user",
        displayName = "PSTN User",
        status = AccountStatus.APPROVED,
        pstnEnabled = false,
        pstnDailyLimit = 5
    )

    @Test
    fun `dial returns PSTN_NOT_ENABLED when user has no PSTN access`() {
        val userId = UUID.randomUUID()
        val user = testUserDisabled(userId)
        whenever(calls.getUser(userId)).thenReturn(user)

        val response = controller.dial(PstnCallRequest("+967771234567"), auth(userId))
        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertTrue(response.body.toString().contains("PSTN_NOT_ENABLED"))
    }

    @Test
    fun `dial returns ALREADY_IN_PSTN_CALL when user has active call`() {
        val userId = UUID.randomUUID()
        val user = testUser(userId)
        whenever(calls.getUser(userId)).thenReturn(user)
        whenever(calls.hasActiveCall(userId)).thenReturn(true)

        val response = controller.dial(PstnCallRequest("+967771234567"), auth(userId))
        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertTrue(response.body.toString().contains("ALREADY_IN_PSTN_CALL"))
    }

    @Test
    fun `dial returns ok with PstnCallResponse on success`() {
        val userId = UUID.randomUUID()
        val user = testUser(userId)
        whenever(calls.getUser(userId)).thenReturn(user)
        whenever(calls.hasActiveCall(userId)).thenReturn(false)
        whenever(calls.dial(any(), any(), anyOrNull<Int>())).thenReturn(
            PstnCallResponse("action-1", "DIALING", "771234567", 1, 10, 3)
        )

        val response = controller.dial(PstnCallRequest("+967771234567"), auth(userId))
        assertEquals(HttpStatus.OK, response.statusCode)
        verify(calls).dial(eq(userId), eq("+967771234567"), anyOrNull<Int>())
    }

    @Test
    fun `dial ignores user supplied slotIndex because binding is admin only`() {
        val userId = UUID.randomUUID()
        val user = testUser(userId)
        whenever(calls.getUser(userId)).thenReturn(user)
        whenever(calls.hasActiveCall(userId)).thenReturn(false)
        whenever(calls.dial(any(), any(), anyOrNull<Int>())).thenReturn(
            PstnCallResponse("action-2", "DIALING", "771234567", 1, 10, 5)
        )

        controller.dial(PstnCallRequest("+967771234567", slotIndex = 5), auth(userId))
        // الربط الدائم قرار أدمن: slotIndex من المستخدم يُتجاهل ويُمرَّر null
        verify(calls).dial(eq(userId), eq("+967771234567"), anyOrNull<Int>())
    }

    @Test
    fun `hangup delegates to the service with the authenticated caller`() {
        val userId = UUID.randomUUID()
        whenever(calls.hangup(userId, "call-1")).thenReturn(PstnHangupResponse("call-1", 3, true))

        val response = controller.hangup("call-1", auth(userId))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("call-1", response.body!!.callId)
        assertEquals(3, response.body!!.port)
        assertTrue(response.body!!.released)
        verify(calls).hangup(userId, "call-1")
    }

    @Test
    fun `hangup reports not released when the call was not the active one`() {
        val userId = UUID.randomUUID()
        whenever(calls.hangup(userId, "call-1")).thenReturn(PstnHangupResponse("call-1", -1, false))

        val response = controller.hangup("call-1", auth(userId))

        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(-1, response.body!!.port)
        assertEquals(false, response.body!!.released)
    }

    @Test
    fun `status returns PstnStatus map`() {
        val userId = UUID.randomUUID()
        val statusMap = mapOf<String, Any>(
            "pstnEnabled" to true,
            "pstnDailyLimit" to 10,
            "usedToday" to 3,
            "activeCall" to false,
            "callId" to "",
            "port" to -1,
            "gatewayId" to "",
            "route" to "Asterisk to PJSIP to DINSTAR"
        )
        whenever(calls.getPstnStatus(userId)).thenReturn(statusMap)

        val response = controller.status(auth(userId))
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(true, response.body!!["pstnEnabled"])
        assertEquals(10, response.body!!["pstnDailyLimit"])
        assertEquals(3, response.body!!["usedToday"])
        assertEquals(false, response.body!!["activeCall"])
    }
}