package com.red.server.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketMessage
import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.CopyOnWriteArrayList

class ConferenceWebSocketHandlerTest {
    private val objectMapper = ObjectMapper().findAndRegisterModules()
    private val handler = ConferenceWebSocketHandler(objectMapper)

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
            doAnswer { null }.whenever(sock).close()
            doAnswer { null }.whenever(sock).close(any<CloseStatus>())
        }
    }

    @Test fun `JOIN adds session to room and sends ROOM_STATE`() {
        val session = Probe("s1", "73066")
        handler.handleTextMessage(session.session, TextMessage("""{"type":"JOIN","roomId":"red-room-12345"}"""))
        val messages = session.sent.map { objectMapper.readTree(it) }
        assertTrue(messages.any { it["type"].asText() == "ROOM_STATE" }) { "Expected ROOM_STATE in: $messages" }
    }

    @Test fun `JOIN broadcasts PARTICIPANT_JOINED to existing peers`() {
        val alice = Probe("s1", "73066")
        val bob = Probe("s2", "28261")
        handler.handleTextMessage(alice.session, TextMessage("""{"type":"JOIN","roomId":"red-room-12345"}"""))
        alice.sent.clear()
        handler.handleTextMessage(bob.session, TextMessage("""{"type":"JOIN","roomId":"red-room-12345"}"""))
        val aliceMessages = alice.sent.map { objectMapper.readTree(it) }
        assertTrue(aliceMessages.any { it["type"].asText() == "PARTICIPANT_JOINED" }) { "Expected PARTICIPANT_JOINED in: $aliceMessages" }
    }

    @Test fun `OFFER relayed to other peers but not back to sender`() {
        val alice = Probe("s1", "73066")
        val bob = Probe("s2", "28261")
        handler.handleTextMessage(alice.session, TextMessage("""{"type":"JOIN","roomId":"red-room-12345"}"""))
        handler.handleTextMessage(bob.session, TextMessage("""{"type":"JOIN","roomId":"red-room-12345"}"""))
        alice.sent.clear()
        bob.sent.clear()
        handler.handleTextMessage(alice.session, TextMessage("""{"type":"OFFER","roomId":"red-room-12345","payload":{"sdp":"v=0..."}}"""))
        val aliceMessages = alice.sent.map { objectMapper.readTree(it) }
        val bobMessages = bob.sent.map { objectMapper.readTree(it) }
        assertTrue(bobMessages.any { it["type"].asText() == "OFFER" }) { "Bob should receive OFFER" }
        assertTrue(aliceMessages.none { it["type"].asText() == "OFFER" }) { "Alice should NOT receive her own OFFER" }
    }

    @Test fun `invalid roomId rejected`() {
        val session = Probe("s1", "73066")
        try {
            handler.handleTextMessage(session.session, TextMessage("""{"type":"JOIN","roomId":"x"}"""))
            assertTrue(false) { "should have thrown" }
        } catch (e: IllegalArgumentException) {
            assertNotNull(e.message)
        }
    }

    @Test fun `targeted OFFER reaches only the named peer`() {
        val alice = Probe("s1", "73066")
        val bob = Probe("s2", "28261")
        val cara = Probe("s3", "11154")
        handler.handleTextMessage(alice.session, TextMessage("""{"type":"JOIN","roomId":"red-room-12345"}"""))
        handler.handleTextMessage(bob.session, TextMessage("""{"type":"JOIN","roomId":"red-room-12345"}"""))
        handler.handleTextMessage(cara.session, TextMessage("""{"type":"JOIN","roomId":"red-room-12345"}"""))
        alice.sent.clear(); bob.sent.clear(); cara.sent.clear()
        handler.handleTextMessage(alice.session, TextMessage("""{"type":"OFFER","roomId":"red-room-12345","payload":{"sdp":"v=0","targetUserId":"28261"}}"""))
        assertTrue(bob.sent.any { it.contains("OFFER") }) { "Bob is the target" }
        assertTrue(cara.sent.none { it.contains("OFFER") }) { "Cara must not receive Alice's offer to Bob" }
        assertTrue(alice.sent.none { it.contains("OFFER") })
    }

    @Test fun `LEAVE notifies other peers`() {
        val alice = Probe("s1", "73066")
        val bob = Probe("s2", "28261")
        handler.handleTextMessage(alice.session, TextMessage("""{"type":"JOIN","roomId":"red-room-12345"}"""))
        handler.handleTextMessage(bob.session, TextMessage("""{"type":"JOIN","roomId":"red-room-12345"}"""))
        alice.sent.clear()
        handler.handleTextMessage(bob.session, TextMessage("""{"type":"LEAVE","roomId":"red-room-12345"}"""))
        val aliceMessages = alice.sent.map { objectMapper.readTree(it) }
        assertTrue(aliceMessages.any { it["type"].asText() == "PARTICIPANT_LEFT" }) { "Alice should see Bob leave" }
    }
}
