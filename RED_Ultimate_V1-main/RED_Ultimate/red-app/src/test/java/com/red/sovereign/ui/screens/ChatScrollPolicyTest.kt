package com.red.sovereign.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatScrollPolicyTest {
    @Test
    fun `first conversation render pins to its latest message`() {
        assertTrue(ChatScrollPolicy.shouldKeepPinned(-1, 0, null, "message-1"))
    }

    @Test
    fun `new message preserves reader position when reviewing history`() {
        assertFalse(ChatScrollPolicy.shouldKeepPinned(8, 30, "message-29", "message-30"))
    }

    @Test
    fun `new message follows only when user is near the end`() {
        assertTrue(ChatScrollPolicy.shouldKeepPinned(28, 30, "message-29", "message-30"))
    }
}
