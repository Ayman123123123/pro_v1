package com.red.server.websocket

import com.red.server.calls.CallHistoryDocument
import com.red.server.calls.CallHistoryService
import com.red.server.calls.CallRoute
import com.red.server.calls.CallStatus
import com.red.server.calls.CallType
import com.red.server.calls.RoomAliasService
import com.red.server.services.NotificationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketMessage
import org.springframework.web.socket.WebSocketSession
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

class CallWebSocketHandlerTest {
    private val mapper = jacksonObjectMapper()
    private val history: CallHistoryService = mock()
    private val notifications: NotificationService = mock()
    private val handler = CallWebSocketHandler(mapper, history, notifications)

    private class Probe(sessionId: String, userId: String) {
        val sent = CopyOnWriteArrayList<String>()
        val session: WebSocketSession = mock<WebSocketSession>().also { sock ->
            whenever(sock.id).thenReturn(sessionId)
            whenever(sock.attributes).thenReturn(mutableMapOf<String, Any>("userId" to userId))
            whenever(sock.isOpen).thenReturn(true)
            doAnswer { invocation ->
                val message = invocation.getArgument<WebSocketMessage<*>>(0)
                if (message is TextMessage) sent.add(message.payload)
                null
            }.whenever(sock).sendMessage(any())
        }
    }

    private fun send(handler: CallWebSocketHandler, source: Probe, json: String) =
        handler.handleTextMessage(source.session, TextMessage(json))

    private fun Probe.received(type: String): Boolean =
        sent.any { mapper.readTree(it)["type"]?.asString() == type }

    private fun Probe.error(code: String): Boolean =
        sent.any { mapper.readTree(it).let { frame ->
            frame["type"]?.asString() == "ERROR" && frame["payload"]?.get("code")?.asString() == code
        } }

    private fun knownCall(id: String, status: CallStatus = CallStatus.RINGING) = CallHistoryDocument(
        id = id,
        initiatorId = "11111",
        targetId = "22222",
        targetLabel = "22222",
        type = CallType.AUDIO_1V1,
        route = CallRoute.RED,
        status = status,
        startedAt = Instant.now()
    )

    @Test
    fun `offline offer is queued and flushed when callee connects`() {
        whenever(history.start(any(), any(), any(), any(), any(), anyOrNull()))
            .thenReturn(knownCall("call-1"))
        val caller = Probe("s-caller", "11111")
        handler.afterConnectionEstablished(caller.session)
        send(handler, caller, """{"callId":"call-1","targetUserId":"22222","type":"OFFER","mode":"VOICE","payload":{"sdp":"v=0"}}""")
        verify(notifications).sendVoipPushNotification("22222", "11111", "call-1", "VOICE")
        assertTrue(caller.received("RINGING_PUSH_SENT"))
        val callee = Probe("s-callee", "22222")
        handler.afterConnectionEstablished(callee.session)
        assertTrue(callee.sent.any { it.contains("\"OFFER\"") && it.contains("v=0") })
    }

    @Test
    fun `renegotiate of a persisted call is forwarded without creating another`() {
        whenever(history.findById("call-9")).thenReturn(knownCall("call-9", CallStatus.ACTIVE))
        val alice = Probe("a", "11111")
        val bob = Probe("b", "22222")
        handler.afterConnectionEstablished(alice.session)
        handler.afterConnectionEstablished(bob.session)
        send(handler, alice, """{"callId":"call-9","targetUserId":"22222","type":"RENEGOTIATE","payload":{"sdp":"restart"}}""")
        assertEquals(1, bob.sent.count { it.contains("RENEGOTIATE") })
        verify(history, never()).start(any(), any(), any(), any(), any(), anyOrNull())
    }

    @Test
    fun `a third account and a redirected participant cannot signal a known call`() {
        whenever(history.findById("call-9")).thenReturn(knownCall("call-9", CallStatus.ACTIVE))
        val alice = Probe("a", "11111")
        val bob = Probe("b", "22222")
        val attacker = Probe("c", "33333")
        listOf(alice, bob, attacker).forEach { handler.afterConnectionEstablished(it.session) }
        send(handler, attacker, """{"callId":"call-9","targetUserId":"22222","type":"END"}""")
        assertTrue(attacker.error("FORBIDDEN"))
        send(handler, alice, """{"callId":"call-9","targetUserId":"33333","type":"ICE"}""")
        assertTrue(alice.error("FORBIDDEN"))
        assertFalse(bob.received("END"))
        assertFalse(attacker.received("ICE"))
        verify(history, never()).end(any(), anyOrNull(), any())
    }

    @Test
    fun `conference invite via trusted REST code does not open a one to one call`() {
        val bob = Probe("b", "22222")
        handler.afterConnectionEstablished(bob.session)
        handler.deliverInvite("22222", "CONFERENCE_INVITE", "room-9", "11111", "SPACE")
        assertTrue(bob.received("CONFERENCE_INVITE"))
        verify(history, never()).start(any(), any(), any(), any(), any(), anyOrNull())
    }

    @Test
    fun `group invite rings online members and keeps seats for one host`() {
        val bob = Probe("b", "22222")
        val carol = Probe("c", "33333")
        handler.afterConnectionEstablished(bob.session)
        handler.afterConnectionEstablished(carol.session)
        send(handler, Probe("host", "11111"), """{"callId":"group-1","type":"GROUP_CALL_INVITE","mode":"VIDEO","inviteeIds":["22222","33333"]}""")
        assertTrue(bob.received("GROUP_CALL_INVITE"))
        assertTrue(carol.received("GROUP_CALL_INVITE"))
        assertEquals(3, handler.groupCallSize("group-1"))
    }

    @Test
    fun `offline group invite is queued and pushed`() {
        send(handler, Probe("host", "11111"), """{"callId":"group-2","type":"GROUP_CALL_INVITE","mode":"VOICE","inviteeIds":["44444"]}""")
        verify(notifications).sendVoipPushNotification("44444", "11111", "group-2", "VOICE")
        val offline = Probe("offline", "44444")
        handler.afterConnectionEstablished(offline.session)
        assertTrue(offline.received("GROUP_CALL_INVITE"))
    }

    @Test
    fun `group status is forwarded only after the host has opened a room`() {
        val host = Probe("host", "11111")
        val bob = Probe("b", "22222")
        handler.afterConnectionEstablished(bob.session)
        send(handler, host, """{"callId":"group-3","targetUserId":"22222","type":"GROUP_CALL_STATUS"}""")
        assertTrue(host.error("FORBIDDEN"))
        assertFalse(bob.received("GROUP_CALL_STATUS"))
        send(handler, host, """{"callId":"group-3","type":"GROUP_CALL_INVITE","inviteeIds":["22222"]}""")
        send(handler, host, """{"callId":"group-3","targetUserId":"22222","type":"GROUP_CALL_STATUS","memberStatus":"no_answer"}""")
        assertTrue(bob.received("GROUP_CALL_STATUS"))
    }

    @Test
    fun `non host cannot add members mute or kick and only members may send media`() {
        val host = Probe("host", "11111")
        val bob = Probe("b", "22222")
        val stranger = Probe("c", "33333")
        listOf(host, bob, stranger).forEach { handler.afterConnectionEstablished(it.session) }
        send(handler, host, """{"callId":"group-4","type":"GROUP_CALL_INVITE","inviteeIds":["22222"]}""")
        send(handler, stranger, """{"callId":"group-4","type":"ICE","targetUserId":"22222"}""")
        send(handler, bob, """{"callId":"group-4","type":"GROUP_CALL_INVITE","inviteeIds":["33333"]}""")
        send(handler, bob, """{"callId":"group-4","type":"GROUP_CALL_KICK","payload":{"memberId":"11111"}}""")
        send(handler, bob, """{"callId":"group-4","type":"GROUP_CALL_MUTE_ALL"}""")
        assertTrue(stranger.error("FORBIDDEN"))
        assertTrue(bob.error("FORBIDDEN"))
        assertFalse(stranger.received("GROUP_CALL_INVITE"))
        assertEquals(2, handler.groupCallSize("group-4"))
    }

    @Test
    fun `kick removes authorization and cancels the kicked users pending invite`() {
        val host = Probe("host", "11111")
        val bob = Probe("b", "22222")
        handler.afterConnectionEstablished(host.session)
        send(handler, host, """{"callId":"group-5","type":"GROUP_CALL_INVITE","inviteeIds":["22222"]}""")
        send(handler, host, """{"callId":"group-5","type":"GROUP_CALL_KICK","payload":{"memberId":"22222"}}""")
        assertFalse(handler.isGroupCallParticipant("group-5", "22222"))
        handler.afterConnectionEstablished(bob.session)
        assertFalse(bob.received("GROUP_CALL_INVITE"))
        send(handler, bob, """{"callId":"group-5","type":"ICE","targetUserId":"11111"}""")
        assertTrue(bob.error("FORBIDDEN"))
        send(handler, host, """{"callId":"group-5","type":"GROUP_CALL_INVITE","inviteeIds":["22222"]}""")
        assertFalse(handler.isGroupCallParticipant("group-5", "22222"))
    }

    @Test
    fun `one device disconnect does not remove the account from a group call`() {
        val host1 = Probe("host-1", "11111")
        val host2 = Probe("host-2", "11111")
        val bob = Probe("b", "22222")
        listOf(host1, host2, bob).forEach { handler.afterConnectionEstablished(it.session) }
        send(handler, host1, """{"callId":"group-6","type":"GROUP_CALL_INVITE","inviteeIds":["22222"]}""")
        handler.afterConnectionClosed(host1.session, CloseStatus.NORMAL)
        assertEquals("11111", handler.groupCallHost("group-6"))
        send(handler, host2, """{"callId":"group-6","type":"GROUP_CALL_STATUS","targetUserId":"22222"}""")
        assertTrue(bob.received("GROUP_CALL_STATUS"))
    }

    @Test
    fun `alias bound via REST resolves via websocket`() {
        val redis: StringRedisTemplate = mock()
        val ops: ValueOperations<String, String> = mock()
        whenever(redis.opsForValue()).thenReturn(ops)
        whenever(ops.get(any())).thenReturn(null)
        val aliases = RoomAliasService(redis)
        aliases.link("legacyCallAlias01", "GRP_legacyCallAlias01")
        val aliased = CallWebSocketHandler(mapper, history, notifications, aliases)
        val bob = Probe("b-alias", "22222")
        aliased.afterConnectionEstablished(bob.session)
        send(aliased, Probe("host-alias", "11111"), """{"callId":"legacyCallAlias01","type":"GROUP_CALL_INVITE","inviteeIds":["22222"]}""")
        assertTrue(bob.sent.any { it.contains("GRP_legacyCallAlias01") })
        assertEquals("11111", aliased.groupCallHost("legacyCallAlias01"))
    }
}
