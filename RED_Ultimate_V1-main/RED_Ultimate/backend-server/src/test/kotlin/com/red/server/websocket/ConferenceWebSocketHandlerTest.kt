package com.red.server.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.junit.jupiter.api.Assertions.assertEquals
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
    private val objectMapper = ObjectMapper().registerKotlinModule()
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

    // ─── حراسة المنصة والحالات المحفوظة (البند الثامن) ───

    /** ينضم أليس مضيفًا وبوب مستمعًا، ويعيد الاثنين. */
    private fun hostAndListener(room: String = "red-room-12345"): Pair<Probe, Probe> {
        val host = Probe("s1", "73066")
        val listener = Probe("s2", "28261")
        handler.handleTextMessage(host.session, TextMessage("""{"type":"JOIN","roomId":"$room"}"""))
        handler.handleTextMessage(listener.session, TextMessage("""{"type":"JOIN","roomId":"$room"}"""))
        host.sent.clear(); listener.sent.clear()
        return host to listener
    }

    @Test fun `المستمع لا ينشر وسائط ويتلقى NOT_ON_STAGE`() {
        val (host, listener) = hostAndListener()
        handler.handleTextMessage(listener.session, TextMessage("""{"type":"PRODUCE","roomId":"red-room-12345"}"""))
        val err = listener.sent.map { objectMapper.readTree(it) }
        assertTrue(err.any { it["type"].asText() == "ERROR" && it["payload"]["code"].asText() == "NOT_ON_STAGE" }) {
            "توقعنا رفض النشر للمستمع، فوصل: $err"
        }
        // والأهم: لم تتسرّب الحزمة إلى بقية الغرفة.
        assertTrue(host.sent.none { objectMapper.readTree(it)["type"].asText() == "PRODUCE" }) {
            "تسرّب PRODUCE من مستمع إلى الغرفة: ${host.sent}"
        }
    }

    @Test fun `المضيف ينشر وسائط بلا منع`() {
        val (host, listener) = hostAndListener()
        handler.handleTextMessage(host.session, TextMessage("""{"type":"PRODUCE","roomId":"red-room-12345"}"""))
        assertTrue(listener.sent.any { objectMapper.readTree(it)["type"].asText() == "PRODUCE" }) {
            "لم يصل نشر المضيف: ${listener.sent}"
        }
    }

    @Test fun `المتحدّث المعتمَد ينشر بعد الترقية`() {
        val (host, listener) = hostAndListener()
        handler.handleTextMessage(
            host.session,
            TextMessage("""{"type":"APPROVE_SPEAKER","roomId":"red-room-12345","payload":{"targetUserId":"28261"}}""")
        )
        host.sent.clear()
        handler.handleTextMessage(listener.session, TextMessage("""{"type":"PRODUCE","roomId":"red-room-12345"}"""))
        assertTrue(host.sent.any { objectMapper.readTree(it)["type"].asText() == "PRODUCE" }) {
            "المتحدّث المعتمَد مُنع من النشر: ${host.sent}"
        }
    }

    @Test fun `اليد المرفوعة تصل المنضمّ الجديد في ROOM_STATE`() {
        val (_, listener) = hostAndListener()
        handler.handleTextMessage(listener.session, TextMessage("""{"type":"RAISE_HAND","roomId":"red-room-12345"}"""))
        val late = Probe("s3", "55555")
        handler.handleTextMessage(late.session, TextMessage("""{"type":"JOIN","roomId":"red-room-12345"}"""))
        val state = late.sent.map { objectMapper.readTree(it) }.first { it["type"].asText() == "ROOM_STATE" }
        assertEquals("true", state["payload"]["28261_hand"].asText())
    }

    @Test fun `الموافقة على التحدّث تُنزل اليد`() {
        val (host, listener) = hostAndListener()
        handler.handleTextMessage(listener.session, TextMessage("""{"type":"RAISE_HAND","roomId":"red-room-12345"}"""))
        handler.handleTextMessage(
            host.session,
            TextMessage("""{"type":"APPROVE_SPEAKER","roomId":"red-room-12345","payload":{"targetUserId":"28261"}}""")
        )
        val late = Probe("s3", "55555")
        handler.handleTextMessage(late.session, TextMessage("""{"type":"JOIN","roomId":"red-room-12345"}"""))
        val state = late.sent.map { objectMapper.readTree(it) }.first { it["type"].asText() == "ROOM_STATE" }
        assertEquals("false", state["payload"]["28261_hand"].asText())
        assertEquals("SPEAKER", state["payload"]["28261_role"].asText())
    }

    @Test fun `الكتم الإداري يبقى ساريًا لمن ينضم لاحقًا`() {
        val (host, _) = hostAndListener()
        handler.handleTextMessage(
            host.session,
            TextMessage("""{"type":"APPROVE_SPEAKER","roomId":"red-room-12345","payload":{"targetUserId":"28261"}}""")
        )
        handler.handleTextMessage(
            host.session,
            TextMessage("""{"type":"MUTE_USER","roomId":"red-room-12345","payload":{"targetUserId":"28261"}}""")
        )
        val late = Probe("s3", "55555")
        handler.handleTextMessage(late.session, TextMessage("""{"type":"JOIN","roomId":"red-room-12345"}"""))
        val state = late.sent.map { objectMapper.readTree(it) }.first { it["type"].asText() == "ROOM_STATE" }
        assertEquals("true", state["payload"]["28261_muted"].asText())
        // والصوت يُعلَن مكتومًا لا "true" ثابتة كما كان.
        assertEquals("false", state["payload"]["28261_audio"].asText())
    }

    @Test fun `المستمع يظهر بلا صوت ولا صورة في ROOM_STATE`() {
        hostAndListener()
        val late = Probe("s3", "55555")
        handler.handleTextMessage(late.session, TextMessage("""{"type":"JOIN","roomId":"red-room-12345"}"""))
        val state = late.sent.map { objectMapper.readTree(it) }.first { it["type"].asText() == "ROOM_STATE" }
        assertEquals("false", state["payload"]["28261_audio"].asText())
        assertEquals("false", state["payload"]["28261_video"].asText())
        // والمضيف بالمقابل ناشر.
        assertEquals("true", state["payload"]["73066_audio"].asText())
    }

    @Test fun `المستمع لا يرقّي نفسه`() {
        val (_, listener) = hostAndListener()
        handler.handleTextMessage(
            listener.session,
            TextMessage("""{"type":"APPROVE_SPEAKER","roomId":"red-room-12345","payload":{"targetUserId":"28261"}}""")
        )
        val messages = listener.sent.map { objectMapper.readTree(it) }
        assertTrue(messages.any { it["type"].asText() == "ERROR" && it["payload"]["code"].asText() == "FORBIDDEN" }) {
            "توقعنا رفض الترقية الذاتية: $messages"
        }
        // ويظل ممنوعًا من النشر فعليًّا.
        listener.sent.clear()
        handler.handleTextMessage(listener.session, TextMessage("""{"type":"PRODUCE","roomId":"red-room-12345"}"""))
        assertTrue(listener.sent.map { objectMapper.readTree(it) }
            .any { it["type"].asText() == "ERROR" && it["payload"]["code"].asText() == "NOT_ON_STAGE" })
    }

    @Test fun `خفض اليد يمسح الطلب`() {
        val (_, listener) = hostAndListener()
        handler.handleTextMessage(listener.session, TextMessage("""{"type":"RAISE_HAND","roomId":"red-room-12345"}"""))
        handler.handleTextMessage(
            listener.session,
            TextMessage("""{"type":"RAISE_HAND","roomId":"red-room-12345","payload":{"lowered":"true"}}""")
        )
        val late = Probe("s3", "55555")
        handler.handleTextMessage(late.session, TextMessage("""{"type":"JOIN","roomId":"red-room-12345"}"""))
        val state = late.sent.map { objectMapper.readTree(it) }.first { it["type"].asText() == "ROOM_STATE" }
        assertEquals("false", state["payload"]["28261_hand"].asText())
    }

    @Test fun `رفع اليد يصل صاحبه أيضًا`() {
        val (_, listener) = hostAndListener()
        handler.handleTextMessage(listener.session, TextMessage("""{"type":"RAISE_HAND","roomId":"red-room-12345"}"""))
        assertTrue(listener.sent.map { objectMapper.readTree(it) }.any { it["type"].asText() == "RAISE_HAND" }) {
            "لم يصل تأكيد رفع اليد لصاحبها: ${listener.sent}"
        }
    }
}
