package com.red.server.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketMessage
import org.springframework.web.socket.WebSocketSession
import java.net.InetSocketAddress
import java.security.Principal
import java.util.concurrent.CopyOnWriteArrayList

class LiveStreamWebSocketHandlerTest {
    private val objectMapper = ObjectMapper()
    private val handler = LiveStreamWebSocketHandler(objectMapper)

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

    @Test fun `broadcaster JOIN registers them and notifies viewers`() {
        val broadcaster = FakeSession("b1", "YNS-AAAA")
        val viewer = FakeSession("v1", "YNS-BBBB")
        handler.afterConnectionEstablished(broadcaster)
        handler.afterConnectionEstablished(viewer)
        handler.handleTextMessage(broadcaster, TextMessage("""{"type":"JOIN","roomId":"stream-12345678","payload":{"role":"broadcaster"}}"""))
        broadcaster.sent.clear()
        handler.handleTextMessage(viewer, TextMessage("""{"type":"JOIN","roomId":"stream-12345678","payload":{"role":"viewer"}}"""))
        val bMessages = broadcaster.sent.map { objectMapper.readTree(it) }
        assertTrue(bMessages.any { it["type"].asText() == "VIEWER_JOINED" }) { "Broadcaster should be notified of new viewer" }
    }

    @Test fun `OFFER from broadcaster reaches viewer`() {
        val broadcaster = FakeSession("b1", "YNS-AAAA")
        val viewer = FakeSession("v1", "YNS-BBBB")
        handler.afterConnectionEstablished(broadcaster)
        handler.afterConnectionEstablished(viewer)
        handler.handleTextMessage(broadcaster, TextMessage("""{"type":"JOIN","roomId":"stream-12345678","payload":{"role":"broadcaster"}}"""))
        handler.handleTextMessage(viewer, TextMessage("""{"type":"JOIN","roomId":"stream-12345678","payload":{"role":"viewer"}}"""))
        broadcaster.sent.clear()
        viewer.sent.clear()
        handler.handleTextMessage(broadcaster, TextMessage("""{"type":"OFFER","roomId":"stream-12345678","payload":{"sdp":"v=0..."}}"""))
        val vMessages = viewer.sent.map { objectMapper.readTree(it) }
        assertTrue(vMessages.any { it["type"].asText() == "OFFER" }) { "Viewer should receive OFFER" }
    }

    @Test fun `ANSWER from viewer reaches broadcaster`() {
        val broadcaster = FakeSession("b1", "YNS-AAAA")
        val viewer = FakeSession("v1", "YNS-BBBB")
        handler.afterConnectionEstablished(broadcaster)
        handler.afterConnectionEstablished(viewer)
        handler.handleTextMessage(broadcaster, TextMessage("""{"type":"JOIN","roomId":"stream-12345678","payload":{"role":"broadcaster"}}"""))
        handler.handleTextMessage(viewer, TextMessage("""{"type":"JOIN","roomId":"stream-12345678","payload":{"role":"viewer"}}"""))
        broadcaster.sent.clear()
        viewer.sent.clear()
        handler.handleTextMessage(viewer, TextMessage("""{"type":"ANSWER","roomId":"stream-12345678","payload":{"sdp":"v=0..."}}"""))
        val bMessages = broadcaster.sent.map { objectMapper.readTree(it) }
        assertTrue(bMessages.any { it["type"].asText() == "ANSWER" }) { "Broadcaster should receive ANSWER" }
    }

    @Test fun `broadcaster LEAVE notifies viewer`() {
        val broadcaster = FakeSession("b1", "YNS-AAAA")
        val viewer = FakeSession("v1", "YNS-BBBB")
        handler.afterConnectionEstablished(broadcaster)
        handler.afterConnectionEstablished(viewer)
        handler.handleTextMessage(broadcaster, TextMessage("""{"type":"JOIN","roomId":"stream-12345678","payload":{"role":"broadcaster"}}"""))
        handler.handleTextMessage(viewer, TextMessage("""{"type":"JOIN","roomId":"stream-12345678","payload":{"role":"viewer"}}"""))
        viewer.sent.clear()
        handler.handleTextMessage(broadcaster, TextMessage("""{"type":"LEAVE","roomId":"stream-12345678"}"""))
        val vMessages = viewer.sent.map { objectMapper.readTree(it) }
        assertTrue(vMessages.any { it["type"].asText() == "PARTICIPANT_LEFT" }) { "Viewer should see broadcaster leave" }
    }
}
