package com.red.sovereign.calls

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies the exact boot/update actions accepted by [CallBootReceiver]. */
class CallBootReceiverTest {
    @Test
    fun `boot completed is supported`() {
        assertTrue(CallBootReceiver.supportedActions.contains(Intent.ACTION_BOOT_COMPLETED))
    }

    @Test
    fun `package replacement is supported after app update`() {
        assertTrue(CallBootReceiver.supportedActions.contains(Intent.ACTION_MY_PACKAGE_REPLACED))
    }

    @Test
    fun `quickboot is supported for vendor boot broadcasts`() {
        assertTrue(CallBootReceiver.supportedActions.contains("android.intent.action.QUICKBOOT_POWERON"))
    }

    @Test
    fun `user present is not supported to avoid startup on unlock`() {
        assertFalse(CallBootReceiver.supportedActions.contains(Intent.ACTION_USER_PRESENT))
    }

    @Test
    fun `only the documented boot actions are supported`() {
        assertEquals(3, CallBootReceiver.supportedActions.size)
    }
}
