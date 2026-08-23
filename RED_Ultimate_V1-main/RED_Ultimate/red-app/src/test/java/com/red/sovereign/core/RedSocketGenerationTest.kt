package com.red.sovereign.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedSocketGenerationTest {
    @Test
    fun `stale master socket callbacks are rejected after replacement`() {
        val generation = RedSocketGeneration()
        val stale = generation.begin()
        generation.invalidate()
        val replacement = generation.begin()

        assertFalse(generation.isCurrent(stale))
        assertTrue(generation.isCurrent(replacement))
    }
}
