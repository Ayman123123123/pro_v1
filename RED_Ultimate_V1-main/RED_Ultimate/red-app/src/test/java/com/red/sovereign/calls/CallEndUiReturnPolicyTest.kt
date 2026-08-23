package com.red.sovereign.calls

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallEndUiReturnPolicyTest {
    private val ended = CallUiState.CallEnded("peer", "VOICE", 1L, "call-1")

    @Test
    fun `returns to RED only while the same call remains ended`() {
        assertTrue(CallEndUiReturnPolicy.shouldReturnToRed(true, ended))
    }

    @Test
    fun `does not surface RED over a newer active call`() {
        val active = CallUiState.Active("call-2", "peer", "VOICE", 2L)
        assertFalse(CallEndUiReturnPolicy.shouldReturnToRed(false, active))
    }

    @Test
    fun `waits for Telecom teardown before restoring the RED task`() {
        assertTrue(CallEndUiReturnPolicy.TELECOM_SETTLE_DELAY_MS >= 500L)
    }
}
