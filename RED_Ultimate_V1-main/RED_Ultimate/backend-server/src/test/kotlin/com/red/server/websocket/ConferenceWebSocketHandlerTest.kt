package com.red.server.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.red.server.calls.RoomAliasService
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
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
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

    @Test fun `alias bound via REST resolves via WS`() {
        val redis: StringRedisTemplate = mock()
        val ops: ValueOperations<String, String> = mock()
        whenever(redis.opsForValue()).thenReturn(ops)
        whenever(ops.get(any())).thenReturn(null)
        val aliases = RoomAliasService(redis)
        aliases.link("legacyConfAlias02", "CONF_legacyConfAlias02")
        val aliased = ConferenceWebSocketHandler(objectMapper, aliases)
        val alice = Probe("s-alias-1", "73066")
        aliased.handleTextMessage(alice.session, TextMessage("""{"type":"JOIN","roomId":"legacyConfAlias02"}"""))
        val state = alice.sent.map { objectMapper.readTree(it) }.first { it["type"].asText() == "ROOM_STATE" }
        assertEquals("CONF_legacyConfAlias02", state["roomId"].asText())
        val bob = Probe("s-alias-2", "28261")
        aliased.handleTextMessage(bob.session, TextMessage("""{"type":"JOIN","roomId":"CONF_legacyConfAlias02"}"""))
        val aliceAfter = alice.sent.map { objectMapper.readTree(it) }
        assertTrue(aliceAfter.any { it["type"].asText() == "PARTICIPANT_JOINED" }) { "same canonical room: $aliceAfter" }
        assertEquals("HOST", aliased.getRole("legacyConfAlias02", "73066"))
    }

    // ─────────────────────── غرفة الانتظار (Lobby) ───────────────────────

    /** تهيئة غرفة بمضيف فعّال اللوبي، ثم انضمام ضيف فيُحتجَز. */
    private fun lobbySetup(): Pair<Probe, Probe> {
        val host = Probe("s1", "73066")
        handler.handleTextMessage(host.session, TextMessage("""{"type":"JOIN","roomId":"red-room-12345"}"""))
        handler.handleTextMessage(host.session, TextMessage("""{"type":"LOBBY_SET","roomId":"red-room-12345","payload":{"enabled":"true"}}"""))
        val guest = Probe("s2", "28261")
        handler.handleTextMessage(guest.session, TextMessage("""{"type":"JOIN","roomId":"red-room-12345"}"""))
        return Pair(host, guest)
    }

    @Test fun `اللوبي يحتجز الداخل الجديد ويبلغ المضيف ثم القبول يدخله`() {
        val (host, guest) = lobbySetup()
        // المنتظر: LOBBY_WAITING بلا ROOM_STATE (لا وسائط قبل الموافقة)
        val guestTypes = guest.sent.map { objectMapper.readTree(it)["type"].asText() }
        assertTrue(guestTypes.contains("LOBBY_WAITING")) { "الضيف لم يُحتجَز: $guestTypes" }
        assertTrue(guestTypes.none { it == "ROOM_STATE" }) { "وصلت حالة غرفة كاملة لمنتظر: $guestTypes" }
        // المضيف: LOBBY_REQUEST باسم المنتظر
        val request = host.sent.map { objectMapper.readTree(it) }.first { it["type"].asText() == "LOBBY_REQUEST" }
        assertEquals("28261", request["payload"]["userId"].asText())
        // القبول: المقبول يأخذ LOBBY_APPROVED ثم ROOM_STATE، والغرفة PARTICIPANT_JOINED
        guest.sent.clear()
        handler.handleTextMessage(host.session, TextMessage("""{"type":"LOBBY_APPROVE","roomId":"red-room-12345","payload":{"targetUserId":"28261"}}"""))
        val after = guest.sent.map { objectMapper.readTree(it) }
        assertTrue(after.any { it["type"].asText() == "LOBBY_APPROVED" }) { "لم يصل تأكيد القبول: $after" }
        assertTrue(after.any { it["type"].asText() == "ROOM_STATE" }) { "لم تصل حالة الغرفة للمقبول: $after" }
        assertTrue(host.sent.map { objectMapper.readTree(it)["type"].asText() }.contains("PARTICIPANT_JOINED")) {
            "الغرفة لم تُبلَّغ بدخول المقبول: ${host.sent}"
        }
    }

    @Test fun `رفض اللوبي من مضيف حقيقي يصل للمنتظر`() {
        val (host, guest) = lobbySetup()
        guest.sent.clear()
        handler.handleTextMessage(host.session, TextMessage("""{"type":"LOBBY_DENY","roomId":"red-room-12345","payload":{"targetUserId":"28261"}}"""))
        assertTrue(guest.sent.map { objectMapper.readTree(it)["type"].asText() }.contains("LOBBY_DENIED")) {
            "لم يصل LOBBY_DENIED للمنتظر: ${guest.sent}"
        }
    }

    @Test fun `غير المضيف لا يدير اللوبي والغرفة تبقى مفتوحة`() {
        val host = Probe("s1", "73066")
        handler.handleTextMessage(host.session, TextMessage("""{"type":"JOIN","roomId":"red-room-12345"}"""))
        val guest = Probe("s2", "28261")
        handler.handleTextMessage(guest.session, TextMessage("""{"type":"JOIN","roomId":"red-room-12345"}"""))
        guest.sent.clear()
        handler.handleTextMessage(guest.session, TextMessage("""{"type":"LOBBY_SET","roomId":"red-room-12345","payload":{"enabled":"true"}}"""))
        val types = guest.sent.map { objectMapper.readTree(it) }
        assertTrue(types.any { it["type"].asText() == "ERROR" && it["payload"]["code"].asText() == "FORBIDDEN" }) { "لم يُرفض أمر اللوبي من مستمع: $types" }
        // اللوبي لم يتفعّل فعلاً: منضمّ جديد يدخل مباشرة بROOM_STATE
        val third = Probe("s3", "11111")
        handler.handleTextMessage(third.session, TextMessage("""{"type":"JOIN","roomId":"red-room-12345"}"""))
        assertTrue(third.sent.map { objectMapper.readTree(it)["type"].asText() }.contains("ROOM_STATE")) { "اللوبي تفعّل بأمر غير مصرّح!" }
    }

    @Test fun `إيقاف اللوبي يقبل كل المنتظرين`() {
        val (host, guest) = lobbySetup()
        val guest2 = Probe("s3", "11111")
        handler.handleTextMessage(guest2.session, TextMessage("""{"type":"JOIN","roomId":"red-room-12345"}"""))
        guest.sent.clear(); guest2.sent.clear()
        handler.handleTextMessage(host.session, TextMessage("""{"type":"LOBBY_SET","roomId":"red-room-12345","payload":{"enabled":"false"}}"""))
        val guestTypes = guest.sent.map { objectMapper.readTree(it)["type"].asText() }
        val guest2Types = guest2.sent.map { objectMapper.readTree(it)["type"].asText() }
        assertTrue(guestTypes.contains("LOBBY_APPROVED") && guestTypes.contains("ROOM_STATE")) { "الأول لم يُقبَل: $guestTypes" }
        assertTrue(guest2Types.contains("LOBBY_APPROVED") && guest2Types.contains("ROOM_STATE")) { "الثاني لم يُقبَل: $guest2Types" }
    }
}
