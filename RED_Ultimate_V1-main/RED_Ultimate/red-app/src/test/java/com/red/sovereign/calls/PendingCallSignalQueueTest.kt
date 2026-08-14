package com.red.sovereign.calls

import org.junit.Assert.assertEquals
import org.junit.Test

class PendingCallSignalQueueTest {
    @Test
    fun `flush preserves signal order`() {
        val queue = PendingCallSignalQueue()
        queue.enqueue("accept")
        queue.enqueue("ice")
        val sent = mutableListOf<String>()

        queue.flush { sent += it; true }

        assertEquals(listOf("accept", "ice"), sent)
        assertEquals(0, queue.size())
    }

    @Test
    fun `failed send remains queued for the next connection`() {
        val queue = PendingCallSignalQueue()
        queue.enqueue("end")

        queue.flush { false }
        assertEquals(1, queue.size())

        val sent = mutableListOf<String>()
        queue.flush { sent += it; true }
        assertEquals(listOf("end"), sent)
    }

    @Test
    fun `queue discards oldest signal at capacity`() {
        val queue = PendingCallSignalQueue(capacity = 2)
        queue.enqueue("first")
        queue.enqueue("second")
        queue.enqueue("third")
        val sent = mutableListOf<String>()

        queue.flush { sent += it; true }

        assertEquals(listOf("second", "third"), sent)
    }
}
