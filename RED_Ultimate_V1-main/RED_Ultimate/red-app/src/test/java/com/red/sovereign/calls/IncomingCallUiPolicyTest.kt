package com.red.sovereign.calls

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IncomingCallUiPolicyTest {
    @Test
    fun `visible RED activity keeps incoming call inside the unified overlay`() {
        assertFalse(IncomingCallUiPolicy.shouldLaunchIncomingActivity(hasResumedRedActivity = true))
    }

    @Test
    fun `background RED process launches lockscreen incoming activity`() {
        assertTrue(IncomingCallUiPolicy.shouldLaunchIncomingActivity(hasResumedRedActivity = false))
    }
}
