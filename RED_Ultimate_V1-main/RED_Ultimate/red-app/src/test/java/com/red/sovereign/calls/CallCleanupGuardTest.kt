package com.red.sovereign.calls

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallCleanupGuardTest {
    @Test
    fun `cleanup scheduled for an old call cannot clear a newer call`() {
        val guard = CallCleanupGuard()
        val firstCall = guard.beginNewCall()
        val secondCall = guard.beginNewCall()

        assertFalse(guard.isCurrent(firstCall))
        assertTrue(guard.isCurrent(secondCall))
    }
}
