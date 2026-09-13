package com.red.sovereign.calls

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallSystemSurfacePolicyTest {
    @Test
    fun `RED voice and video calls stay in the RED task`() {
        assertFalse(CallSystemSurfacePolicy.usesAndroidTelecom("VOICE"))
        assertFalse(CallSystemSurfacePolicy.usesAndroidTelecom("VIDEO"))
    }

}
