package com.red.sovereign.calls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression contract for incoming-call latency policy.
 *
 * These thresholds pin the deterministic client-side constants that govern
 * delivery pacing; network delivery itself is covered end-to-end on paired
 * devices (measured RINGING ≈ 109ms on 2026-08-20).
 */
class RealtimeCallDeliveryPolicyTest {
    @Test
    fun `websocket ring acknowledgement window stays bounded`() {
        // نافذة انتظار إقرار الرنين عبر WebSocket — يجب أن تبقى ضمن حدود معقولة
        assertTrue(CallDeliveryEngine.RING_ACK_TIMEOUT_MS in 1..5_000L)
        assertTrue(CallDeliveryEngine.BASE_RETRY_DELAY_MS <= CallDeliveryEngine.RING_ACK_TIMEOUT_MS)
    }

    @Test
    fun `delivery retries never exceed a small bounded count`() {
        assertEquals(3, CallDeliveryEngine.MAX_DELIVERY_RETRIES)
        assertTrue(CallDeliveryEngine.HTTP_TIMEOUT_MS <= 5_000L)
    }

    @Test
    fun `ringing grace matches the documented no-answer window`() {
        assertEquals(5_000L, CallPresenceMonitor.RINGING_GRACE_MS)
    }
}
