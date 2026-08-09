package com.red.sovereign.calls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات state machine للـ YounesCallService.
 * يضمن أن الحالات (Idle, Incoming, Connecting, Active, Error) تتدفق بشكل صحيح.
 */
class CallUiStateTest {
    @Test fun `Idle is the default state`() {
        val initial = CallUiState.Idle
        assertTrue(initial is CallUiState.Idle)
    }

    @Test fun `Incoming carries callId, peer, mode`() {
        val state = CallUiState.Incoming(callId = "c1", peer = "YNS-AAAA-BBBB", mode = "VIDEO")
        assertEquals("c1", state.callId)
        assertEquals("YNS-AAAA-BBBB", state.peer)
        assertEquals("VIDEO", state.mode)
    }

    @Test fun `Active carries startedAt timestamp`() {
        val now = System.currentTimeMillis()
        val state = CallUiState.Active(callId = "c1", peer = "YNS", mode = "VOICE", startedAt = now)
        assertEquals(now, state.startedAt)
    }

    @Test fun `Error carries message`() {
        val state = CallUiState.Error("WebRTC failed")
        assertEquals("WebRTC failed", state.message)
    }

    @Test fun `states are distinct`() {
        val idle = CallUiState.Idle
        val incoming = CallUiState.Incoming("c1", "YNS", "VOICE")
        val active = CallUiState.Active("c1", "YNS", "VOICE", 0L)
        val error = CallUiState.Error("err")
        assertNotEquals(idle, incoming)
        assertNotEquals(incoming, active)
        assertNotEquals(active, error)
    }
}

class ConferenceUiStateTest {
    @Test fun `Connecting has roomId`() {
        val state = ConferenceUiState.Connecting(roomId = "red-room-123")
        assertEquals("red-room-123", state.roomId)
    }

    @Test fun `Active has startedAt`() {
        val now = System.currentTimeMillis()
        val state = ConferenceUiState.Active(roomId = "red-room-1", startedAt = now)
        assertEquals(now, state.startedAt)
    }
}

class LiveStreamUiStateTest {
    @Test fun `Connecting has streamId and role`() {
        val state = LiveStreamUiState.Connecting(streamId = "stream-1", isBroadcaster = true)
        assertEquals("stream-1", state.streamId)
        assertTrue(state.isBroadcaster)
    }
}
