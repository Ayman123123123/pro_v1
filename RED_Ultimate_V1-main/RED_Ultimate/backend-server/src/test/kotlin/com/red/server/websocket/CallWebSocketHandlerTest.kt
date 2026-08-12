package com.red.server.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.red.server.calls.CallHistoryDocument
import com.red.server.calls.CallHistoryService
import com.red.server.calls.CallRoute
import com.red.server.calls.CallStatus
import com.red.server.calls.CallType
import com.red.server.services.NotificationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketMessage
import org.springframework.web.socket.WebSocketSession
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

class CallWebSocketHandlerTest {
    private val objectMapper = ObjectMapper().findAndRegisterModules()
    private val history: CallHistoryService = mock()
    private val notifications: NotificationService = mock()
    private val handler = CallWebSocketHandler(objectMapper, history, notifications)

    private class Probe(sessionId: String, userId: String) {
        val sent = CopyOnWriteArrayList<String>()
        private val attrs: MutableMap<String, Any> = mutableMapOf("userId" to userId)
        val session: WebSocketSession = mock<WebSocketSession>().also { sock ->
            whenever(sock.id).thenReturn(sessionId)
            whenever(sock.attributes).thenReturn(attrs)
            whenever(sock.isOpen).thenReturn(true)
            doAnswer { invocation ->
                val message = invocation.getArgument<WebSocketMessage<*>>(0)
                if (message is TextMessage) sent.add(message.payload)
                null
            }.whenever(sock).sendMessage(any())
        }
    }

    @Test
    fun `offline offer is queued and flushed when callee connects`() {
        whenever(
            history.start(any(), any(), any(), any(), any(), anyOrNull())
        ).thenReturn(
            CallHistoryDocument(
                id = "call-1",
                initiatorId = "11111",
                targetId = "22222",
                targetLabel = "22222",
                type = CallType.VOICE,
                route = CallRoute.RED,
                status = CallStatus.RINGING,
                startedAt = Instant.now()
            )
        )

        val caller = Probe("s-caller", "11111")
        handler.afterConnectionEstablished(caller.session)
        handler.handleTextMessage(
            caller.session,
            TextMessage("""{"callId":"call-1","targetUserId":"22222","type":"OFFER","mode":"VOICE","payload":{"sdp":"v=0"}}""")
        )

        verify(notifications).sendVoipPushNotification("22222", "11111", "call-1", "VOICE")
        assertTrue(caller.sent.any { it.contains("RINGING_PUSH_SENT") })

        val callee = Probe("s-callee", "22222")
        handler.afterConnectionEstablished(callee.session)
        assertTrue(callee.sent.any { it.contains("\"OFFER\"") && it.contains("v=0") }) {
            "Queued OFFER must be delivered on connect: ${callee.sent}"
        }
    }

    @Test
    fun `renegotiate is forwarded without creating a new call`() {
        whenever(history.start(any(), any(), any(), any(), any(), anyOrNull())).thenThrow(AssertionError("must not start"))
        val alice = Probe("a", "11111")
        val bob = Probe("b", "22222")
        handler.afterConnectionEstablished(alice.session)
        handler.afterConnectionEstablished(bob.session)
        handler.handleTextMessage(
            alice.session,
            TextMessage("""{"callId":"call-9","targetUserId":"22222","type":"RENEGOTIATE","mode":"VOICE","payload":{"sdp":"restart"}}""")
        )
        assertTrue(bob.sent.any { it.contains("RENEGOTIATE") })
        assertEquals(1, bob.sent.count { it.contains("RENEGOTIATE") })
    }
}
