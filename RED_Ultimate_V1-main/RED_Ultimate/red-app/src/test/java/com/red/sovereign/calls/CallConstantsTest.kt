package com.red.sovereign.calls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * يضمن ثبات Action constants حتى لا تنكسر التعاقدات بين UI و Service.
 */
class CallConstantsTest {
    @Test fun `YounesCallService action constants are unique`() {
        val actions = setOf(
            YounesCallService.ACTION_LISTEN,
            YounesCallService.ACTION_START,
            YounesCallService.ACTION_ACCEPT,
            YounesCallService.ACTION_REJECT,
            YounesCallService.ACTION_END,
            YounesCallService.ACTION_MIC,
            YounesCallService.ACTION_CAMERA,
            YounesCallService.ACTION_SWITCH_CAMERA,
            YounesCallService.ACTION_SPEAKER,
            YounesCallService.ACTION_BLUETOOTH,
            YounesCallService.ACTION_HOLD,
            YounesCallService.ACTION_RESUME,
            YounesCallService.ACTION_DTMF,
            YounesCallService.ACTION_ACCEPT_SECOND,
            YounesCallService.ACTION_REJECT_SECOND,
            YounesCallService.ACTION_STOP
        )
        // 16 distinct action names
        assertEquals(16, actions.size)
    }

    @Test fun `HOLD and RESUME are distinct`() {
        assertNotEquals(YounesCallService.ACTION_HOLD, YounesCallService.ACTION_RESUME)
    }

    @Test fun `ConferenceService and LiveStreamService have separate action namespaces`() {
        assertNotEquals(
            ConferenceService.ACTION_JOIN,
            LiveStreamService.ACTION_START.substringBeforeLast(".")
        )
    }
}
