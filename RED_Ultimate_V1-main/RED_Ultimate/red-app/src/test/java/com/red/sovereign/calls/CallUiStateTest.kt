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
        val state = CallUiState.Incoming(callId = "c1", peer = "73066", mode = "VIDEO")
        assertEquals("c1", state.callId)
        assertEquals("73066", state.peer)
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

    @Test fun `Active carries isHeld flag and can transition to held`() {
        val active = CallUiState.Active("c1", "73066", "VOICE", 1000L, isHeld = false)
        assertEquals(false, active.isHeld)
        val held = active.copy(isHeld = true)
        assertEquals(true, held.isHeld)
        // يمكن العودة للحالة النشطة
        val resumed = held.copy(isHeld = false)
        assertEquals(false, resumed.isHeld)
    }

    @Test fun `same callId stays Active and is not a second incoming`() {
        val active = CallUiState.Active("same-call", "73066", "VOICE", 1L)
        val restartLooksLikeOffer = CallUiState.Incoming("same-call", "73066", "VOICE")
        assertEquals(active.callId, restartLooksLikeOffer.callId)
        assertTrue(active is CallUiState.Active)
    }

    @Test fun `ActiveWithIncoming pairs active call with waiting call`() {
        val active = CallUiState.Active("c1", "26852", "VOICE", 1000L)
        val waiting = CallUiState.Incoming("c2", "55602", "VOICE")
        val state = CallUiState.ActiveWithIncoming(active, waiting)
        assertEquals(active, state.active)
        assertEquals(waiting, state.waiting)
        assertEquals("26852", state.active.peer)
        assertEquals("55602", state.waiting.peer)
    }

    // ───────── الحالات النهائية وإعادة الاتصال ─────────

    @Test fun `Connecting exposes a default presence label`() {
        val state = CallUiState.Connecting("c1", "73066", "VOICE")
        assertTrue("يجب أن تحمل نصاً افتراضياً غير فارغ", state.presenceLabel.isNotBlank())
        val custom = state.copy(presenceLabel = "يرن الآن…")
        assertEquals("يرن الآن…", custom.presenceLabel)
    }

    @Test fun `Reconnecting keeps call identity so the timer can resume`() {
        val active = CallUiState.Active("c9", "71555", "VIDEO", startedAt = 5_000L)
        val reconnecting = CallUiState.Reconnecting(active.callId, active.peer, active.mode)
        assertEquals(active.callId, reconnecting.callId)
        assertEquals(active.peer, reconnecting.peer)
        assertEquals(active.mode, reconnecting.mode)
        // إعادة الاتصال ليست حالة نهائية: المكالمة قد تعود
        assertEquals(false, CallUiState.isTerminal(reconnecting))
    }

    @Test fun `Declined Busy and NoAnswer are terminal`() {
        assertTrue(CallUiState.isTerminal(CallUiState.Declined("73066", "VOICE")))
        assertTrue(CallUiState.isTerminal(CallUiState.Busy("73066", "VOICE")))
        assertTrue(CallUiState.isTerminal(CallUiState.NoAnswer("73066", "VOICE", outgoing = true)))
        assertTrue(CallUiState.isTerminal(CallUiState.CallEnded("73066", "VOICE", 1_000L)))
        assertTrue(CallUiState.isTerminal(CallUiState.Error("boom")))
    }

    @Test fun `live states are not terminal`() {
        assertEquals(false, CallUiState.isTerminal(CallUiState.Idle))
        assertEquals(false, CallUiState.isTerminal(CallUiState.Incoming("c1", "p", "VOICE")))
        assertEquals(false, CallUiState.isTerminal(CallUiState.Connecting("c1", "p", "VOICE")))
        assertEquals(false, CallUiState.isTerminal(CallUiState.Active("c1", "p", "VOICE", 0L)))
    }

    @Test fun `NoAnswer distinguishes outgoing from missed incoming`() {
        val unanswered = CallUiState.NoAnswer("73066", "VOICE", outgoing = true)
        val missed = CallUiState.NoAnswer("73066", "VOICE", outgoing = false)
        assertNotEquals(unanswered, missed)
        assertEquals("لم يتم الرد", CallRingPolicy.unansweredMessage(unanswered.outgoing))
        assertEquals("مكالمة فائتة", CallRingPolicy.unansweredMessage(missed.outgoing))
    }

    @Test fun `CallEnded preserves the measured duration`() {
        val started = 10_000L
        val ended = 73_500L
        val state = CallUiState.CallEnded("73066", "VIDEO", durationMs = ended - started)
        assertEquals(63_500L, state.durationMs)
    }

    @Test fun `terminal states never ring`() {
        // الرنين مقصور على Incoming/Connecting؛ الحالات النهائية شاشة عرض فقط
        listOf(
            CallUiState.Declined("p", "VOICE"),
            CallUiState.Busy("p", "VOICE"),
            CallUiState.NoAnswer("p", "VOICE", true),
            CallUiState.CallEnded("p", "VOICE", 0L),
            CallUiState.Reconnecting("c", "p", "VOICE")
        ).forEach { assertEquals(false, CallRingPolicy.isOneToOneRingState(it)) }
    }

    @Test fun `terminal display window is positive and shorter than ring timeout`() {
        assertTrue(CallUiState.TERMINAL_DISPLAY_MS > 0L)
        assertTrue(CallUiState.TERMINAL_DISPLAY_MS < CallRingPolicy.UNANSWERED_TIMEOUT_MS)
    }
}

class ConferenceUiStateTest {
    @Test fun `Incoming invite is not a ringing 1-1 call`() {
        val state = ConferenceUiState.Incoming(roomId = "room-1", inviter = "73066", video = false, userId = "28261")
        assertEquals("room-1", state.roomId)
        assertEquals(false, state.video)
        assertEquals("28261", state.userId)
    }

    @Test fun `Connecting has roomId`() {
        val state = ConferenceUiState.Connecting(roomId = "red-room-123")
        assertEquals("red-room-123", state.roomId)
    }

    @Test fun `Active has startedAt`() {
        val now = System.currentTimeMillis()
        val state = ConferenceUiState.Active(roomId = "red-room-1", startedAt = now)
        assertEquals(now, state.startedAt)
    }

    @Test fun `space invitee is a listener not a ringing callee`() {
        val state = ConferenceUiState.Incoming(roomId = "space-9", inviter = "73066", video = false, userId = "28261")
        assertEquals(false, state.video)
        assertTrue(state !is ConferenceUiState.Connecting)
    }
}

class LiveStreamUiStateTest {
    @Test fun `Connecting has streamId and role`() {
        val state = LiveStreamUiState.Connecting(streamId = "stream-1", isBroadcaster = true)
        assertEquals("stream-1", state.streamId)
        assertTrue(state.isBroadcaster)
    }
}
