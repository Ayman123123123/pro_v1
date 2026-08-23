package com.red.sovereign.calls

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelecomDisconnectGuardTest {
    @Test
    fun `local disconnect callback is consumed once`() {
        val guard = TelecomDisconnectGuard()
        guard.markLocalDisconnect("call-1")

        assertTrue(guard.consumeIfLocal("call-1"))
        assertFalse(guard.consumeIfLocal("call-1"))
    }

    @Test
    fun `different call is never treated as a local echo`() {
        val guard = TelecomDisconnectGuard()
        guard.markLocalDisconnect("call-1")

        assertFalse(guard.consumeIfLocal("call-2"))
        assertTrue(guard.consumeIfLocal("call-1"))
    }
}
