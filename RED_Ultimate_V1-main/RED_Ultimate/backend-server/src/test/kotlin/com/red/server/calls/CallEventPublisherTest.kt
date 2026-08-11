package com.red.server.calls

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import java.time.Instant

class CallEventPublisherTest {
    private val collected = mutableListOf<CallEvent>()

    @Test
    fun `callStarted publishes CallStarted event`() {
        val publisher = CallEventPublisher(CollectingPublisher(collected))
        publisher.callStarted("c1", "84870", "82937", "VOICE", "RED")
        assertEquals(1, collected.size)
        val event = collected.first() as CallEvent.CallStarted
        assertEquals("c1", event.callId)
        assertEquals("84870", event.initiatorId)
        assertEquals("VOICE", event.type)
    }

    @Test
    fun `callAnswered publishes CallAnswered event with timestamp`() {
        val publisher = CallEventPublisher(CollectingPublisher(collected))
        publisher.callAnswered("c2")
        val event = collected.first() as CallEvent.CallAnswered
        assertEquals("c2", event.callId)
        assertNotNull(event.timestamp)
    }

    @Test
    fun `callEnded includes duration and reason`() {
        val publisher = CallEventPublisher(CollectingPublisher(collected))
        publisher.callEnded("c3", 60_000L, "NORMAL")
        val event = collected.first() as CallEvent.CallEnded
        assertEquals(60_000L, event.durationMs)
        assertEquals("NORMAL", event.reason)
    }

    @Test
    fun `callFailed carries error message`() {
        val publisher = CallEventPublisher(CollectingPublisher(collected))
        publisher.callFailed("c4", "ICE_NEGOTIATION_FAILED")
        val event = collected.first() as CallEvent.CallFailed
        assertEquals("ICE_NEGOTIATION_FAILED", event.error)
    }

    @Test
    fun `callMissed event has no duration`() {
        val publisher = CallEventPublisher(CollectingPublisher(collected))
        publisher.callMissed("c5")
        val event = collected.first() as CallEvent.CallMissed
        assertTrue(event is CallEvent.CallMissed)
    }

    @Test
    fun `event timestamps are monotonically non-decreasing`() {
        val publisher = CallEventPublisher(CollectingPublisher(collected))
        publisher.callStarted("c1", "a", "b", "VOICE", "RED")
        Thread.sleep(5)
        publisher.callAnswered("c1")
        Thread.sleep(5)
        publisher.callEnded("c1", 1000L, "NORMAL")
        val ts1 = (collected[0] as CallEvent.CallStarted).timestamp
        val ts2 = (collected[1] as CallEvent.CallAnswered).timestamp
        val ts3 = (collected[2] as CallEvent.CallEnded).timestamp
        assertTrue(ts1 <= ts2)
        assertTrue(ts2 <= ts3)
    }
}

private class CollectingPublisher(private val collected: MutableList<CallEvent>) : ApplicationEventPublisher {
    override fun publishEvent(event: ApplicationEvent) {
        @Suppress("UNCHECKED_CAST")
        collected.add(event as CallEvent)
    }

    override fun publishEvent(event: Any) {
        if (event is CallEvent) collected.add(event)
    }
}
