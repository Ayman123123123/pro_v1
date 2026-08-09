package com.red.server

import com.red.server.websocket.WebSocketRateLimiter
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WebSocketRateLimiterTest {
    @Test
    fun `limits one connection without affecting another and resets its window`() {
        var now = 1_000L
        val limiter = WebSocketRateLimiter(maxMessages = 2, windowMillis = 1_000) { now }

        assertTrue(limiter.tryAcquire("first"))
        assertTrue(limiter.tryAcquire("first"))
        assertFalse(limiter.tryAcquire("first"))
        assertTrue(limiter.tryAcquire("second"))

        now += 1_000
        assertTrue(limiter.tryAcquire("first"))
    }

    @Test
    fun `removing a closed connection discards its counter`() {
        val limiter = WebSocketRateLimiter(maxMessages = 1, windowMillis = 60_000)
        assertTrue(limiter.tryAcquire("socket"))
        assertFalse(limiter.tryAcquire("socket"))
        limiter.remove("socket")
        assertTrue(limiter.tryAcquire("socket"))
    }
}
