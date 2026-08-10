package com.red.sovereign.calls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * يختبر ثابت [CallBootReceiver.SUPPORTED_ACTIONS] — قيمه يجب أن تشمل كل الأفعال المهمة.
 */
class CallBootReceiverTest {
    @Test fun `BOOT_COMPLETED is supported`() {
        assertTrue(CallBootReceiver.SUPPORTED_ACTIONS.contains("android.intent.action.BOOT_COMPLETED"))
    }

    @Test fun `MY_PACKAGE_REPLACED is supported (after app update)`() {
        assertTrue(CallBootReceiver.SUPPORTED_ACTIONS.contains("android.intent.action.MY_PACKAGE_REPLACED"))
    }

    @Test fun `QUICKBOOT_POWERON is supported (HTC/Samsung)`() {
        assertTrue(CallBootReceiver.SUPPORTED_ACTIONS.contains("android.intent.action.QUICKBOOT_POWERON"))
    }

    @Test fun `PACKAGE_REPLACED is supported (general update)`() {
        assertTrue(CallBootReceiver.SUPPORTED_ACTIONS.contains("android.intent.action.PACKAGE_REPLACED"))
    }

    @Test fun `USER_PRESENT is NOT in supported list (security)`() {
        // لا نريد بدء الـ service عند فتح قفل الشاشة فقط
        assertFalse(CallBootReceiver.SUPPORTED_ACTIONS.contains("android.intent.action.USER_PRESENT"))
    }

    @Test fun `exactly 4 actions are supported`() {
        assertEquals(4, CallBootReceiver.SUPPORTED_ACTIONS.size)
    }
}
