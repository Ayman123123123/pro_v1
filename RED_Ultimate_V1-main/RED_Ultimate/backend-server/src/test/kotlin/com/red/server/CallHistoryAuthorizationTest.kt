package com.red.server

import com.red.server.calls.CallEventPublisher
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
    private val publisher: CallEventPublisher = mock()
    private val history = CallHistoryService(mongo, publisher)
    private val call = CallHistoryDocument(
        id = "018f5e23-3f80-7a00-8000-000000000001",
        initiatorId = "16999",
        targetId = "58414",
        targetLabel = "58414",
        type = CallType.VOICE,
        route = CallRoute.RED,
        status = CallStatus.RINGING,
        startedAt = Instant.now()
    )

    @Test
    fun `third account cannot answer or end a call it does not own`() {
        whenever(mongo.findById(call.id, CallHistoryDocument::class.java)).thenReturn(call)

        assertThrows(IllegalArgumentException::class.java) {
            history.answer(call.id, "71852")
        }
        assertThrows(IllegalArgumentException::class.java) {
            history.end(call.id, "71852")
        }

        verify(mongo, never()).save(any<CallHistoryDocument>())
        assertEquals(CallStatus.RINGING, call.status)
    }

    @Test
    fun `only called account can answer a ringing call`() {
        whenever(mongo.findById(call.id, CallHistoryDocument::class.java)).thenReturn(call)
        whenever(mongo.save(any<CallHistoryDocument>())).thenAnswer { it.arguments[0] }

        assertThrows(IllegalArgumentException::class.java) {
            history.answer(call.id, call.initiatorId)
        }
        val updated = history.answer(call.id, call.targetId)

        assertEquals(CallStatus.ACTIVE, updated.status)
        verify(mongo).save(call)
        verify(publisher).callAnswered(call.id)
    }

    @Test
    fun `ending a ringing call is recorded as missed`() {
        whenever(mongo.findById(call.id, CallHistoryDocument::class.java)).thenReturn(call)
        whenever(mongo.save(any<CallHistoryDocument>())).thenAnswer { it.arguments[0] }

        val updated = history.end(call.id, call.initiatorId)

        assertEquals(CallStatus.MISSED, updated.status)
        verify(publisher).callMissed(call.id)
    }

    @Test
    fun `call id reuse cannot redirect an existing call`() {
        whenever(mongo.findById(call.id, CallHistoryDocument::class.java)).thenReturn(call)

        assertThrows(IllegalArgumentException::class.java) {
            history.start(
                initiator = call.initiatorId,
                target = "71852",
                targetLabel = "71852",
                type = CallType.AUDIO_1V1,
                route = CallRoute.RED,
                requestedId = call.id
            )
        }
    }
}
