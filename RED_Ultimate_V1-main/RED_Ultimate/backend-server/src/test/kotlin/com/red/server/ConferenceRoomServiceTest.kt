package com.red.server

import com.red.server.calls.ConferenceRoomService
import com.red.server.calls.RoomPasswordHasher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ConferenceRoomServiceTest {
    private val service = ConferenceRoomService(RoomPasswordHasher())

    @Test
    fun `private room allows host and explicit invitees without a shared password`() {
        service.createRoom(
            "room-1", "host-1", "مضيف", "12345", "غرفة خاصة", false, true, null,
            inviteeRedIds = listOf("67890")
        )

        assertTrue(service.canJoin("room-1", "host-1", "12345"))
        assertTrue(service.canJoin("room-1", "guest-1", "67890"))
        assertFalse(service.canJoin("room-1", "stranger-1", "99999"))
    }

    @Test
    fun `password protected private room verifies its password when one is configured`() {
        service.createRoom("room-password", "host-1", "مضيف", "12345", "غرفة خاصة", false, true, "كلمة-آمنة")

        assertFalse(service.verifyPassword("room-password", "خاطئة"))
        assertTrue(service.verifyPassword("room-password", "كلمة-آمنة"))
    }

    @Test
    fun `another host cannot reuse an active room identifier`() {
        service.createRoom("room-2", "host-1", "مضيف", "12345", "جلسة", true, false, null)

        assertThrows<IllegalArgumentException> {
            service.createRoom("room-2", "host-2", "مضيف آخر", "54321", "جلسة أخرى", true, false, null)
        }
    }

    // ── روابط الدعوة وغرفة الانتظار ─────────────────────────────────────────────

    private fun lobbyRoom(id: String = "room-lobby") = service.createRoom(
        id, "host-1", "مضيف", "12345", "غرفة بانتظار", false, false, null
    ).also { check(service.updateRoomFlags(id, waitingRoom = true) != null) }

    @Test
    fun `lobby holds a stranger but admits the host and explicit invitees`() {
        lobbyRoom()

        assertTrue(service.enterLobby("room-lobby", "guest-1", "99999", "ضيف", viaLink = false) != null,
            "الأجنبي يجب أن يقف في الطابور")
        assertTrue(service.enterLobby("room-lobby", "host-1", "12345", "المضيف", viaLink = false) == null,
            "المضيف لا ينتظر إذن نفسه")

        assertEquals(1, service.lobbyCount("room-lobby"))
        assertEquals(listOf("guest-1"), service.admitFromLobby("room-lobby", listOf("guest-1")))
        assertEquals(0, service.lobbyCount("room-lobby"))
        assertEquals(1, service.getParticipantCount("room-lobby"), "الإذن يُدخل الغرفة فورًا")
    }

    @Test
    fun `admitted peer reconnects without waiting again`() {
        lobbyRoom()
        service.enterLobby("room-lobby", "guest-1", "99999", "ضيف", viaLink = false)
        service.admitFromLobby("room-lobby", listOf("guest-1"))

        assertTrue(service.enterLobby("room-lobby", "guest-1", "99999", "ضيف", viaLink = false) == null,
            "إعادة الاتصال بعد الانقطاع لا تُعيد المحاور إلى الطابور")
    }

    @Test
    fun `blocked peer is never queued again while the block stands`() {
        lobbyRoom()
        service.enterLobby("room-lobby", "guest-1", "99999", "ضيف", viaLink = false)
        assertEquals(listOf("guest-1"), service.denyFromLobby("room-lobby", listOf("guest-1"), block = true))

        assertTrue(service.enterLobby("room-lobby", "guest-1", "99999", "ضيف", viaLink = false) == null,
            "الممنوع لا يدخل ولا يصطف — الرفض صامت وإلا أُغرِق المضيف بطابور لا ينفد")
        assertEquals(0, service.getParticipantCount("room-lobby"))
    }

    @Test
    fun `link expires by use count and only on real entry`() {
        lobbyRoom()
        val link = service.createCallLink("room-lobby", ttlMinutes = 0, maxUses = 2)

        // المعاينة (معاينة الرابط في دردشة) لا تستهلك شيئًا.
        repeat(3) { check(service.resolveCallLink(link.token) != null) }
        assertTrue(service.redeemCallLink(link.token))
        assertTrue(service.redeemCallLink(link.token))
        assertFalse(service.redeemCallLink(link.token), "الثالث يستهلك استخدامًا غير موجود")
        assertTrue(service.resolveCallLink(link.token) == null, "رابط مُستنفَد لا يُعاين بعد الآن")
    }

    @Test
    fun `revoking links kills every token of the room at once`() {
        lobbyRoom()
        val a = service.createCallLink("room-lobby")
        val b = service.createCallLink("room-lobby")

        assertEquals(2, service.revokeCallLinks("room-lobby"))
        assertTrue(service.resolveCallLink(a.token) == null)
        assertTrue(service.resolveCallLink(b.token) == null)
    }

    @Test
    fun `closing the lobby admits whoever was already queued`() {
        lobbyRoom()
        service.enterLobby("room-lobby", "guest-1", "99999", "ضيف", viaLink = false)

        service.updateRoomFlags("room-lobby", waitingRoom = false)

        assertEquals(0, service.lobbyCount("room-lobby"))
        assertEquals(1, service.getParticipantCount("room-lobby"),
            "غرفة مفتوحة بلا طابور لا يجوز أن تحبس أحدًا داخله")
    }

    @Test
    fun `admission stops at capacity instead of skipping the rest of the queue`() {
        lobbyRoom()
        repeat(ConferenceRoomService.MAX_PARTICIPANTS) { i -> service.addParticipant("room-lobby", "seat-$i") }
        service.enterLobby("room-lobby", "guest-1", "99999", "ضيف", viaLink = false)

        assertEquals(emptyList<String>(), service.admitFromLobby("room-lobby", listOf("guest-1")))
        assertEquals(1, service.lobbyCount("room-lobby"), "البقيّة في الطابور لا في الغرفة")
    }

    @Test
    fun `closing a room drops its lobby queue and its links`() {
        lobbyRoom()
        val link = service.createCallLink("room-lobby")
        service.enterLobby("room-lobby", "guest-1", "99999", "ضيف", viaLink = true)

        assertTrue(service.closeRoom("room-lobby"))
        assertEquals(0, service.lobbyCount("room-lobby"))
        assertTrue(service.resolveCallLink(link.token) == null,
            "رابط لغرفة أُغلقت يجب أن لا يبقى بوابةً معلّقة")
    }
}
