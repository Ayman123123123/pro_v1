package com.red.server

import com.red.server.calls.ConferenceRoomService
import com.red.server.calls.RoomPasswordHasher
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
}
