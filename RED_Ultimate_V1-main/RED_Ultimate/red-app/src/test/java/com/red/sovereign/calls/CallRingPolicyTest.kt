package com.red.sovereign.calls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallRingPolicyTest {
    @Test fun `unanswered timeout matches WhatsApp style 45 seconds`() {
        assertEquals(45_000L, CallRingPolicy.UNANSWERED_TIMEOUT_MS)
    }

    @Test fun `does not expire before the ring window`() {
        assertFalse(CallRingPolicy.shouldExpireUnanswered(20_000, true))
        assertFalse(CallRingPolicy.shouldExpireUnanswered(45_000, false))
    }

    @Test fun `expires exactly at the ring window`() {
        assertTrue(CallRingPolicy.shouldExpireUnanswered(45_000, true))
        assertTrue(CallRingPolicy.shouldExpireUnanswered(60_000, true))
    }

    @Test fun `only 1-1 incoming or connecting is a ring state`() {
        assertTrue(CallRingPolicy.isOneToOneRingState(CallUiState.Incoming("c", "73066", "VOICE")))
        assertTrue(CallRingPolicy.isOneToOneRingState(CallUiState.Connecting("c", "73066", "VIDEO")))
        assertFalse(CallRingPolicy.isOneToOneRingState(CallUiState.Idle))
        assertFalse(CallRingPolicy.isOneToOneRingState(CallUiState.Active("c", "73066", "VOICE", 1L)))
    }

    @Test fun `messages distinguish outgoing timeout from missed incoming`() {
        assertEquals("لم يتم الرد", CallRingPolicy.unansweredMessage(true))
        assertEquals("مكالمة فائتة", CallRingPolicy.unansweredMessage(false))
    }
}
