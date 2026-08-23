package com.red.sovereign.calls

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutgoingOfferStartPolicyTest {
    @Test
    fun `connected socket starts offer without waiting for another callback`() {
        assertTrue(OutgoingOfferStartPolicy.shouldStart(true, true, "call-1", null))
    }

    @Test
    fun `same call never creates its offer twice`() {
        assertFalse(OutgoingOfferStartPolicy.shouldStart(true, true, "call-1", "call-1"))
    }

    @Test
    fun `disconnected socket waits for onConnected`() {
        assertFalse(OutgoingOfferStartPolicy.shouldStart(true, false, "call-1", null))
    }
}
