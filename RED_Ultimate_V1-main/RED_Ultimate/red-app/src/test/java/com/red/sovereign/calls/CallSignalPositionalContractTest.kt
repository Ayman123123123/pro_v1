package com.red.sovereign.calls

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression test for the historical two-positional-argument call pattern.
 * The second argument must always be the recipient RED ID, never callType.
 */
class CallSignalPositionalContractTest {
    @Test
    fun `second positional argument is target user id`() {
        val signal = CallSignal("call-42", "55221", type = CallSignal.OFFER)

        assertEquals("55221", signal.targetUserId)
        assertEquals(CallType.PRIVATE_VOICE.name, signal.callType)
    }
}
