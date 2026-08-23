package com.red.server.calls

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConferenceRoomServiceTest {
    private val service = ConferenceRoomService(RoomPasswordHasher())

    @Test
    fun `private meeting authorizes its host and invitees only`() {
        service.createRoom(
            roomId = "meet_private_12345",
            hostId = "account-host",
            hostName = "Host",
            hostRedId = "host-red",
            title = "مكالمة خاصة",
            isSpace = false,
            isPrivate = true,
            password = null,
            inviteeRedIds = listOf("guest-red")
        )

        assertTrue(service.canJoin("meet_private_12345", "account-host", "host-red"))
        assertTrue(service.canJoin("meet_private_12345", "account-guest", "guest-red"))
        assertFalse(service.canJoin("meet_private_12345", "account-stranger", "stranger-red"))
    }
}
