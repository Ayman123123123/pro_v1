package com.red.server.websocket

import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import com.red.server.calls.LiveStreamService
import com.red.server.calls.RoomAliasService
import com.red.server.calls.RoomPasswordHasher
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
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.util.concurrent.CopyOnWriteArrayList

class LiveStreamWebSocketHandlerTest {
    private val objectMapper = jacksonObjectMapper()
    private val streams = LiveStreamService(
        mock<RoomPasswordHasher>(),
        mock(),
        mock()
    )
    private val accessGuard: com.red.server.websocket.ApprovedDeviceSessionGuard = mock<com.red.server.websocket.ApprovedDeviceSessionGuard>().also {
        whenever(it.isStillAuthorized(any(), any())).thenReturn(true)
    }
    private val handler = LiveStreamWebSocketHandler(objectMapper, streams, accessGuard)

    @BeforeEach
    fun startOwnedStream() {
        streams.startStream("stream-12345678", "91179")
    }

    private class Probe(sessionId: String, userId: String) {
        val sent = CopyOnWriteArrayList<String>()
        private val attrs: MutableMap<String, Any> = mutableMapOf(
            "userId" to userId,
            "redId" to userId,
                        "accountId" to userId,
            "deviceId" to "device-test"

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
        assertTrue(bMessages.any { it["type"].asString() == "VIEWER_JOINED" }) { "Broadcaster should be notified of new viewer" }
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
        assertTrue(vMessages.any { it["type"].asString() == "OFFER" }) { "Viewer should receive OFFER" }
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
        assertTrue(bMessages.any { it["type"].asString() == "ANSWER" }) { "Broadcaster should receive ANSWER" }
    }

    @Test fun `broadcaster LEAVE notifies viewer`() {
        val broadcaster = Probe("b1", "91179")
        val viewer = Probe("v1", "11154")
        handler.handleTextMessage(broadcaster.session, TextMessage("""{"type":"JOIN","roomId":"stream-12345678","payload":{"role":"broadcaster"}}"""))
        handler.handleTextMessage(viewer.session, TextMessage("""{"type":"JOIN","roomId":"stream-12345678","payload":{"role":"viewer"}}"""))
        viewer.sent.clear()
        handler.handleTextMessage(broadcaster.session, TextMessage("""{"type":"LEAVE","roomId":"stream-12345678"}"""))
        val vMessages = viewer.sent.map { objectMapper.readTree(it) }
        assertTrue(vMessages.any { it["type"].asString() == "PARTICIPANT_LEFT" }) { "Viewer should see broadcaster leave" }
    }

    @Test fun `alias bound via REST resolves via WS`() {
        val redis: StringRedisTemplate = mock()
        val ops: ValueOperations<String, String> = mock()
        whenever(redis.opsForValue()).thenReturn(ops)
        whenever(ops.get(any())).thenReturn(null)
        val aliases = RoomAliasService(redis)
        aliases.link("legacyLiveAlias03", "LIVE_legacyLiveAlias03")
        streams.startStream("LIVE_legacyLiveAlias03", "91179")
        val aliased = LiveStreamWebSocketHandler(objectMapper, streams, accessGuard, aliases)
        val viewer = Probe("v-alias", "11154")
        aliased.handleTextMessage(viewer.session, TextMessage("""{"type":"JOIN","roomId":"legacyLiveAlias03","payload":{"role":"viewer"}}"""))
        val joined = viewer.sent.map { objectMapper.readTree(it) }
        assertTrue(joined.none { it["type"].asString() == "ERROR" && it["payload"]["code"].asString() == "STREAM_NOT_FOUND" }) {
            "WS must resolve REST alias, got: ${viewer.sent}"
        }
    }
}
