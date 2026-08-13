package com.red.sovereign.calls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshNegotiationTest {
    @Test fun `newcomer offers to every other peer and never to self`() {
        assertTrue(MeshNegotiation.shouldOfferTo("28261", "73066", isNewcomer = true))
        assertFalse(MeshNegotiation.shouldOfferTo("73066", "73066", isNewcomer = true))
        assertFalse(MeshNegotiation.shouldOfferTo("28261", "73066", isNewcomer = false))
        assertFalse(MeshNegotiation.shouldOfferTo("", "73066", isNewcomer = true))
    }

    @Test fun `polite peer accepts a colliding remote offer`() {
        assertTrue(MeshNegotiation.shouldAcceptRemoteOffer("11111", "22222", haveLocalOffer = false))
        assertTrue(MeshNegotiation.shouldAcceptRemoteOffer("11111", "22222", haveLocalOffer = true))
        assertFalse(MeshNegotiation.shouldAcceptRemoteOffer("33333", "22222", haveLocalOffer = true))
    }

    @Test fun `mesh stops attaching after eight distinct peers`() {
        assertTrue(MeshNegotiation.canAttach(8, alreadyAttached = true))
        assertFalse(MeshNegotiation.canAttach(8, alreadyAttached = false))
        assertTrue(MeshNegotiation.canAttach(7, alreadyAttached = false))
        assertEquals(8, MeshNegotiation.MAX_PEERS)
    }

    @Test fun `targetUserId is taken from the signal payload`() {
        assertEquals("28261", MeshNegotiation.targetOf(mapOf("targetUserId" to "28261")))
        assertEquals("fallback", MeshNegotiation.targetOf(emptyMap(), "fallback"))
    }
}
