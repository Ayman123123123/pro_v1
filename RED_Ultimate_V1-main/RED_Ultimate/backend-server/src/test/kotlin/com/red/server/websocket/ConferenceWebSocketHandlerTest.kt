package com.red.server.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketMessage
import org.springframework.web.socket.WebSocketSession
import java.net.InetSocketAddress
import java.security.Principal
import java.util.concurrent.CopyOnWriteArrayList

class ConferenceWebSocketHandlerTest {
    private val objectMapper = ObjectMapper()
    private val handler = ConferenceWebSocketHandler(objectMapper)

    /**
     * Minimal WebSocketSession stub that records sent messages. Subclassing
     * AbstractWebSocketHandler is heavier; the delegate pattern below keeps
     * the test focused on the signaling logic.
     */
    private class FakeSession(val sessionId: String, val userId: String) : WebSocketSession {
        private val _attrs: MutableMap<String, Any> = mutableMapOf("userId" to userId)
        val sent: CopyOnWriteArrayList<String> = CopyOnWriteArrayList()
        override fun getId(): String = sessionId
        override fun getAttributes(): MutableMap<String, Any> = _attrs
        override fun sendMessage(message: WebSocketMessage<*>) { if (message is TextMessage) sent.add(message.payload) }
        override fun isOpen(): Boolean = true
        override fun close() {}
        override fun close(status: CloseStatus) {}
        override fun getRemoteAddress(): InetSocketAddress = InetSocketAddress.createUnresolved("localhost", 0)
        override fun getLocalAddress(): InetSocketAddress = InetSocketAddress.createUnresolved("localhost", 0)
        override fun getAcceptedProtocol(): String? = null
        override fun getHandshakeHeaders(): HttpHeaders = HttpHeaders()
        override fun getPrincipal(): Principal? = null
        override fun getBinaryMessageCount(): Int = 0
        override fun getTextMessageCount(): Int = sent.size
    }

    @Test fun `JOIN adds session to room and sends ROOM_STATE`() {
        val session = FakeSession("s1", "73066")
        handler.afterConnectionEstablished(session)
        handler.handleTextMessage(session, TextMessage("""{"type":"JOIN","roomId":"red-room-12345"}"""))
        val messages = session.sent.map { objectMapper.readTree(it) }
        assertTrue(messages.any { it["type"].asText() == "ROOM_STATE" }) { "Expected ROOM_STATE in: $messages" }
    }

    @Test fun `JOIN broadcasts PARTICIPANT_JOINED to existing peers`() {
        val alice = FakeSession("s1", "73066")
        val bob = FakeSession("s2", "28261")
        handler.afterConnectionEstablished(alice)
        handler.afterConnectionEstablished(bob)
        handler.handleTextMessage(alice, TextMessage("""{"type":"JOIN","roomId":"red-room-12345"}"""))
        alice.sent.clear()
        handler.handleTextMessage(bob, TextMessage("""{"type":"JOIN","roomId":"red-room-12345"}"""))
        val aliceMessages = alice.sent.map { objectMapper.readTree(it) }
        assertTrue(aliceMessages.any { it["type"].asText() == "PARTICIPANT_JOINED" }) { "Expected PARTICIPANT_JOINED in: $aliceMessages" }
    }

    @Test fun `OFFER relayed to other peers but not back to sender`() {
        val alice = FakeSession("s1", "73066")
        val bob = FakeSession("s2", "28261")
        handler.afterConnectionEstablished(alice)
        handler.afterConnectionEstablished(bob)
        handler.handleTextMessage(alice, TextMessage("""{"type":"JOIN","roomId":"red-room-12345"}"""))
        handler.handleTextMessage(bob, TextMessage("""{"type":"JOIN","roomId":"red-room-12345"}"""))
        alice.sent.clear()
        bob.sent.clear()
        handler.handleTextMessage(alice, TextMessage("""{"type":"OFFER","roomId":"red-room-12345","payload":{"sdp":"v=0..."}}"""))
        val aliceMessages = alice.sent.map { objectMapper.readTree(it) }
        val bobMessages = bob.sent.map { objectMapper.readTree(it) }
        assertTrue(bobMessages.any { it["type"].asText() == "OFFER" }) { "Bob should receive OFFER" }
        assertTrue(aliceMessages.none { it["type"].asText() == "OFFER" }) { "Alice should NOT receive her own OFFER" }
    }

    @Test fun `invalid roomId rejected`() {
        val session = FakeSession("s1", "73066")
        handler.afterConnectionEstablished(session)
        try {
            handler.handleTextMessage(session, TextMessage("""{"type":"JOIN","roomId":"x"}"""))
            assertTrue(false) { "should have thrown" }
        } catch (e: IllegalArgumentException) {
            assertNotNull(e.message)
        }
    }

    @Test fun `LEAVE notifies other peers`() {
        val alice = FakeSession("s1", "73066")
        val bob = FakeSession("s2", "28261")
        handler.afterConnectionEstablished(alice)
        handler.afterConnectionEstablished(bob)
        handler.handleTextMessage(alice, TextMessage("""{"type":"JOIN","roomId":"red-room-12345"}"""))
        handler.handleTextMessage(bob, TextMessage("""{"type":"JOIN","roomId":"red-room-12345"}"""))
        alice.sent.clear()
        handler.handleTextMessage(bob, TextMessage("""{"type":"LEAVE","roomId":"red-room-12345"}"""))
        val aliceMessages = alice.sent.map { objectMapper.readTree(it) }
        assertTrue(aliceMessages.any { it["type"].asText() == "PARTICIPANT_LEFT" }) { "Alice should see Bob leave" }
    }
}
