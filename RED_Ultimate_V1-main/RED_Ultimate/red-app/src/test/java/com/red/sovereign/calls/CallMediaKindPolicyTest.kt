package com.red.sovereign.calls

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallMediaKindPolicyTest {
    @Test
    fun `one to one video does not allocate simulcast encodings`() {
        assertFalse(CallMediaKind.VIDEO.wantsSimulcast)
    }

    @Test
    fun `conference and live retain simulcast for multi receiver delivery`() {
        assertTrue(CallMediaKind.CONFERENCE.wantsSimulcast)
        assertTrue(CallMediaKind.LIVE.wantsSimulcast)
    }
}
