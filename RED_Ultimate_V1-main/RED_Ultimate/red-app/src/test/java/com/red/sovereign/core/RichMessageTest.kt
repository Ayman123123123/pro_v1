package com.red.sovereign.core

import org.junit.Assert.*
import org.junit.Test

class RichMessageTest {
    @Test
    fun `mentions extracted correctly`() {
        val text = "مرحبا @YNS-ABCD-EFGH كيف حالك"
        val mentions = Regex("""@(?:YNS|RED)-[23456789A-HJ-NP-Z]{4}-[23456789A-HJ-NP-Z]{4}""").findAll(text).map { it.value }.toList()
        assertEquals(listOf("@YNS-ABCD-EFGH"), mentions)
    }
    @Test
    fun `hashtags extracted correctly`() {
        val text = "هذا #مهم جدا #يمن"
        val hashtags = Regex("""#[\w\u0600-\u06FF]{2,30}""").findAll(text).map { it.value }.toList()
        assertEquals(listOf("#مهم", "#يمن"), hashtags)
    }
    @Test
    fun `disappearingMs only allowed values`() {
        val allowed = setOf(0L, 3600000L, 86400000L, 604800000L)
        assertTrue(3600000L in allowed)
        assertFalse(12345L in allowed)
    }
    @Test
    fun `RichMessage validation rejects too many mentions`() {
        try {
            RichMessage(text = "hi", mentions = List(21) { "@YNS-ABCD-EFGH" })
            fail("Should throw")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("mentions"))
        }
    }
    @Test
    fun `hashtags limit 10`() {
        try {
            RichMessage(text = "hi", hashtags = List(11) { "#a" })
            fail("Should throw")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("hashtags"))
        }
    }
}
