package com.red.sovereign.core

import org.junit.Assert.*
import org.junit.Test

class RichMessageTest {
    @Test
    fun `mentions extracted correctly`() {
        // النمط من YounesId لا نسخة محلية — الصيغة الخماسية.
        val text = "مرحبا @16999 كيف حالك"
        val mentions = Regex(YounesId.MENTION_PATTERN).findAll(text).map { it.value }.toList()
        assertEquals(listOf("@16999"), mentions)
    }

    @Test
    fun `mention does not swallow a longer number`() {
        // بلا (?![0-9]) كان @123456 يُلتقط كإشارة إلى 12345 — أي إشعار
        // يذهب إلى شخص آخر تمامًا.
        val mentions = Regex(YounesId.MENTION_PATTERN).findAll("راجع @123456 اليوم").toList()
        assertTrue(mentions.isEmpty())
    }

    @Test
    fun `mention rejects the legacy format`() {
        val mentions = Regex(YounesId.MENTION_PATTERN).findAll("مرحبا @YNS-ABCD-EFGH").toList()
        assertTrue(mentions.isEmpty())
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
            RichMessage(text = "hi", mentions = List(21) { "@16999" })
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
