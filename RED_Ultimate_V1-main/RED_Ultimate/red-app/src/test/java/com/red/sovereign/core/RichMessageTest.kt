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

    // ───────────────────────── تفاعلات الإيموجي (Reactions) ─────────────────────────
    // التفاعل = رسالة RICH_TEXT مشفّرة E2EE بـ action=REACTION + reactionOf + emoji.
    // الخادم يرى ciphertext فقط. هذه الاختبارات تضمن صحة العقد والتحقق.

    @Test
    fun `reaction encodes and decodes round-trip`() {
        val original = RichMessage(action = "REACTION", reactionOf = "msg-123", emoji = "👍")
        val encoded = RichMessage.encode(original)
        val decoded = RichMessage.decode(encoded)
        assertNotNull(decoded)
        assertEquals("REACTION", decoded!!.action)
        assertEquals("msg-123", decoded.reactionOf)
        assertEquals("👍", decoded.emoji)
    }

    @Test
    fun `reaction_remove encodes and decodes round-trip`() {
        val original = RichMessage(action = "REACTION_REMOVE", reactionOf = "msg-456")
        val decoded = RichMessage.decode(RichMessage.encode(original))
        assertNotNull(decoded)
        assertEquals("REACTION_REMOVE", decoded!!.action)
        assertEquals("msg-456", decoded.reactionOf)
        assertNull(decoded.emoji)
    }

    @Test
    fun `reaction requires reactionOf`() {
        try {
            RichMessage(action = "REACTION", emoji = "👍")
            fail("Should throw — reactionOf is required")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("reaction"))
        }
    }

    @Test
    fun `reaction requires emoji`() {
        try {
            RichMessage(action = "REACTION", reactionOf = "msg-1")
            fail("Should throw — emoji is required for REACTION")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("reaction"))
        }
    }

    @Test
    fun `reaction_remove requires reactionOf only`() {
        // REACTION_REMOVE صالح بدون emoji (emoji=null افتراضياً)
        val rm = RichMessage(action = "REACTION_REMOVE", reactionOf = "msg-7")
        assertEquals("REACTION_REMOVE", rm.action)
        assertNull(rm.emoji)
    }

    @Test
    fun `reaction rejects empty emoji`() {
        try {
            RichMessage(action = "REACTION", reactionOf = "msg-1", emoji = "")
            fail("Should throw — empty emoji is invalid")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("emoji"))
        }
    }

    @Test
    fun `reaction rejects overly long emoji`() {
        try {
            RichMessage(action = "REACTION", reactionOf = "msg-1", emoji = "😀".repeat(20))
            fail("Should throw — emoji too long")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("emoji"))
        }
    }

    @Test
    fun `plain message unaffected by reaction fields`() {
        // رسالة عادية يجب ألا تتأثر بحقول التفاعل (تبقى null)
        val msg = RichMessage(text = "مرحبا")
        assertEquals("MESSAGE", msg.action)
        assertNull(msg.reactionOf)
        assertNull(msg.emoji)
    }

    @Test
    fun `edit message unaffected by reaction validation`() {
        // رسالة تعديل يجب أن تبقى صالحة (لا تتقاطع مع تحقق التفاعل)
        val edit = RichMessage(action = "EDIT", text = "نص معدّل", editOf = "msg-9")
        assertEquals("EDIT", edit.action)
        assertEquals("نص معدّل", edit.text)
    }

    @Test
    fun `unknown action rejected`() {
        try {
            RichMessage(action = "BOGUS", text = "x")
            fail("Should throw — unknown action")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("action"))
        }
    }
}
