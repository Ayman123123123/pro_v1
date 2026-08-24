package com.red.server.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DinstarSignalTest {
    @Test
    fun `unknown CSQ value is never treated as usable signal`() {
        val quality = DinstarSignal.interpret(99)

        assertEquals(99, quality.raw)
        assertNull(quality.dbm)
        assertNull(quality.percent)
        assertFalse(quality.usable)
        assertEquals("NO_SIGNAL", quality.label)
    }

    @Test
    fun `strong known CSQ value remains usable with a bounded percentage`() {
        val quality = DinstarSignal.interpret("31")

        assertEquals(-51, quality.dbm)
        assertEquals(100, quality.percent)
        assertTrue(quality.usable)
        assertEquals("EXCELLENT", quality.label)
    }
}
