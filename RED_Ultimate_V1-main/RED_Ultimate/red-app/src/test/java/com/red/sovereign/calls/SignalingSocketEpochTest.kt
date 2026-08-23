package com.red.sovereign.calls

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalingSocketEpochTest {
    @Test
    fun `stale socket callback cannot affect the replacement connection`() {
        val epoch = SignalingSocketEpoch()
        val stale = epoch.begin()

        epoch.invalidate()
        val replacement = epoch.begin()

        assertFalse(epoch.isCurrent(stale))
        assertTrue(epoch.isCurrent(replacement))
    }

    @Test
    fun `closing the active socket invalidates its callbacks`() {
        val epoch = SignalingSocketEpoch()
        val active = epoch.begin()

        epoch.invalidate()

        assertFalse(epoch.isCurrent(active))
    }
}
