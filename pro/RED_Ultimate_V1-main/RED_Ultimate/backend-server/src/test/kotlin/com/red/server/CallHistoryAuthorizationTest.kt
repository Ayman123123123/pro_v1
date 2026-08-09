package com.red.server

import com.red.server.calls.CallHistoryDocument
import com.red.server.calls.CallHistoryService
import com.red.server.calls.CallRoute
import com.red.server.calls.CallStatus
import com.red.server.calls.CallType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.mongodb.core.MongoTemplate
import java.time.Instant

class CallHistoryAuthorizationTest {
    private val mongo: MongoTemplate = mock()
    private val history = CallHistoryService(mongo)
    private val call = CallHistoryDocument(
        id = "018f5e23-3f80-7a00-8000-000000000001",
        initiatorId = "YNS-ABCD-EFGH",
        targetId = "YNS-JKLM-NPQR",
        targetLabel = "YNS-JKLM-NPQR",
        type = CallType.VOICE,
        route = CallRoute.RED,
        status = CallStatus.RINGING,
        startedAt = Instant.now()
    )

    @Test
    fun `third account cannot answer or end a call it does not own`() {
        whenever(mongo.findById(call.id, CallHistoryDocument::class.java)).thenReturn(call)

        assertThrows(IllegalArgumentException::class.java) {
            history.authorizeSignal(call.id, "YNS-STUV-WXYZ", "ANSWER")
        }
        assertThrows(IllegalArgumentException::class.java) {
            history.authorizeSignal(call.id, "YNS-STUV-WXYZ", "END")
        }

        verify(mongo, never()).save(any<CallHistoryDocument>())
        assertEquals(CallStatus.RINGING, call.status)
    }

    @Test
    fun `only called account can answer a ringing call`() {
        whenever(mongo.findById(call.id, CallHistoryDocument::class.java)).thenReturn(call)
        whenever(mongo.save(any<CallHistoryDocument>())).thenAnswer { it.arguments[0] }

        assertThrows(IllegalArgumentException::class.java) {
            history.authorizeSignal(call.id, call.initiatorId, "ANSWER")
        }
        val updated = history.authorizeSignal(call.id, call.targetId, "ANSWER")

        assertEquals(CallStatus.ACTIVE, updated.status)
        verify(mongo).save(call)
    }

    @Test
    fun `call id reuse cannot redirect an existing call`() {
        whenever(mongo.findById(call.id, CallHistoryDocument::class.java)).thenReturn(call)

        assertThrows(IllegalArgumentException::class.java) {
            history.start(
                initiator = call.initiatorId,
                target = "YNS-STUV-WXYZ",
                targetLabel = "YNS-STUV-WXYZ",
                type = CallType.VOICE,
                route = CallRoute.RED,
                requestedId = call.id
            )
        }
    }
}
