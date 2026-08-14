package com.red.sovereign.calls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * يضمن أن:
 * 1. كل service له Action constants فريدة
 * 2. لا تكرار في الـ constants
 * 3. الـ Companion actions معرفة بشكل صحيح
 */
class CallActionContractTest {
    @Test fun `YounesCallService has unique action constants`() {
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
            YounesCallService.ACTION_STOP_RECORDING,
            YounesCallService.ACTION_START_RECORDING,
            YounesCallService.ACTION_STOP
        )
        assertEquals("Expected 18 distinct action constants", 18, actions.size)
    }

    @Test fun `HOLD and RESUME have different semantics`() {
        assertNotEquals(YounesCallService.ACTION_HOLD, YounesCallService.ACTION_RESUME)
    }

    @Test fun `RECORDING_START and RECORDING_STOP are distinct`() {
        assertNotEquals(YounesCallService.ACTION_START_RECORDING, YounesCallService.ACTION_STOP_RECORDING)
    }

    @Test fun `ConferenceService and LiveStreamService action namespaces are separated`() {
        val conferenceActions = setOf(
            ConferenceService.ACTION_JOIN,
            ConferenceService.ACTION_LEAVE,
            ConferenceService.ACTION_TOGGLE_MIC,
            ConferenceService.ACTION_TOGGLE_VIDEO,
            ConferenceService.ACTION_SET_QUALITY
        )
        val liveActions = setOf(
            LiveStreamService.ACTION_START,
            LiveStreamService.ACTION_STOP,
            LiveStreamService.ACTION_TOGGLE_MIC,
            LiveStreamService.ACTION_TOGGLE_VIDEO
        )
        // No overlap between conference and live actions
        assertTrue("Conference and Live action sets must not overlap", conferenceActions.intersect(liveActions).isEmpty())
    }
}
