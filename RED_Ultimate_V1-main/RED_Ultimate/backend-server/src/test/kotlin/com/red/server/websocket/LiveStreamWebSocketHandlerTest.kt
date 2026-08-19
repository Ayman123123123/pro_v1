package com.red.server.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.red.server.calls.LiveStreamService
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
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

class LiveStreamWebSocketHandlerTest {
    private val objectMapper = ObjectMapper().registerKotlinModule()
    private val streams = LiveStreamService()
    private val handler = LiveStreamWebSocketHandler(objectMapper, streams)

    @BeforeEach
    fun startOwnedStream() {
        streams.startStream("stream-12345678", "91179")
    }

    private class Probe(sessionId: String, userId: String) {
        val sent = CopyOnWriteArrayList<String>()
        private val attrs: MutableMap<String, Any> = mutableMapOf(
            "userId" to userId,
            "redId" to userId,
            "accountId" to userId
        )
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

    @Test fun `broadcaster JOIN registers them and notifies viewers`() {
        val broadcaster = Probe("b1", "91179")
        val viewer = Probe("v1", "11154")
        handler.handleTextMessage(broadcaster.session, TextMessage("""{"type":"JOIN","roomId":"stream-12345678","payload":{"role":"broadcaster"}}"""))
        broadcaster.sent.clear()
        handler.handleTextMessage(viewer.session, TextMessage("""{"type":"JOIN","roomId":"stream-12345678","payload":{"role":"viewer"}}"""))
        val bMessages = broadcaster.sent.map { objectMapper.readTree(it) }
        assertTrue(bMessages.any { it["type"].asText() == "VIEWER_JOINED" }) { "Broadcaster should be notified of new viewer" }
    }

    @Test fun `OFFER from broadcaster reaches viewer`() {
        val broadcaster = Probe("b1", "91179")
        val viewer = Probe("v1", "11154")
        handler.handleTextMessage(broadcaster.session, TextMessage("""{"type":"JOIN","roomId":"stream-12345678","payload":{"role":"broadcaster"}}"""))
        handler.handleTextMessage(viewer.session, TextMessage("""{"type":"JOIN","roomId":"stream-12345678","payload":{"role":"viewer"}}"""))
        broadcaster.sent.clear()
        viewer.sent.clear()
        handler.handleTextMessage(broadcaster.session, TextMessage("""{"type":"OFFER","roomId":"stream-12345678","payload":{"sdp":"v=0..."}}"""))
        val vMessages = viewer.sent.map { objectMapper.readTree(it) }
        assertTrue(vMessages.any { it["type"].asText() == "OFFER" }) { "Viewer should receive OFFER" }
    }

    @Test fun `targeted OFFER from broadcaster reaches only that viewer`() {
        val broadcaster = Probe("b1", "91179")
        val first = Probe("v1", "11154")
        val second = Probe("v2", "22261")
        handler.handleTextMessage(broadcaster.session, TextMessage("""{"type":"JOIN","roomId":"stream-12345678","payload":{"role":"broadcaster"}}"""))
        handler.handleTextMessage(first.session, TextMessage("""{"type":"JOIN","roomId":"stream-12345678","payload":{"role":"viewer"}}"""))
        handler.handleTextMessage(second.session, TextMessage("""{"type":"JOIN","roomId":"stream-12345678","payload":{"role":"viewer"}}"""))
        first.sent.clear(); second.sent.clear()
        handler.handleTextMessage(broadcaster.session, TextMessage("""{"type":"OFFER","roomId":"stream-12345678","payload":{"sdp":"v=0...","targetUserId":"11154"}}"""))
        assertTrue(first.sent.any { it.contains("OFFER") }) { "First viewer is the target" }
        assertTrue(second.sent.none { it.contains("OFFER") }) { "Second viewer must keep their own peer connection" }
    }

    @Test fun `ANSWER from viewer reaches broadcaster`() {
        val broadcaster = Probe("b1", "91179")
        val viewer = Probe("v1", "11154")
        handler.handleTextMessage(broadcaster.session, TextMessage("""{"type":"JOIN","roomId":"stream-12345678","payload":{"role":"broadcaster"}}"""))
        handler.handleTextMessage(viewer.session, TextMessage("""{"type":"JOIN","roomId":"stream-12345678","payload":{"role":"viewer"}}"""))
        broadcaster.sent.clear()
        viewer.sent.clear()
        handler.handleTextMessage(viewer.session, TextMessage("""{"type":"ANSWER","roomId":"stream-12345678","payload":{"sdp":"v=0..."}}"""))
        val bMessages = broadcaster.sent.map { objectMapper.readTree(it) }
        assertTrue(bMessages.any { it["type"].asText() == "ANSWER" }) { "Broadcaster should receive ANSWER" }
    }

    @Test fun `broadcaster LEAVE notifies viewer`() {
        val broadcaster = Probe("b1", "91179")
        val viewer = Probe("v1", "11154")
        handler.handleTextMessage(broadcaster.session, TextMessage("""{"type":"JOIN","roomId":"stream-12345678","payload":{"role":"broadcaster"}}"""))
        handler.handleTextMessage(viewer.session, TextMessage("""{"type":"JOIN","roomId":"stream-12345678","payload":{"role":"viewer"}}"""))
        viewer.sent.clear()
        handler.handleTextMessage(broadcaster.session, TextMessage("""{"type":"LEAVE","roomId":"stream-12345678"}"""))
        val vMessages = viewer.sent.map { objectMapper.readTree(it) }
        assertTrue(vMessages.any { it["type"].asText() == "PARTICIPANT_LEFT" }) { "Viewer should see broadcaster leave" }
    }
}
