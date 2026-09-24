package com.red.sovereign.features.pstn

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات وحدة لـ YemeniOperatorDetector في وحدة app (Task 10).
 * تطابق Backend YemenNumberPlan: الثلاثي (722) أولاً ثم الثنائي.
 */
class YemeniOperatorDetectorTest {

    @Test fun `700 واي`() {
        assertEquals("واي", YemeniOperatorDetector.getOperatorInfo("700123456").name)
        assertEquals("واي", YemeniOperatorDetector.getOperatorInfo("709123456").name)
    }

    @Test fun `71 سبأفون`() {
        assertEquals("سبأفون", YemeniOperatorDetector.getOperatorInfo("710123456").name)
        assertEquals("سبأفون", YemeniOperatorDetector.getOperatorInfo("719123456").name)
    }

    @Test fun `722 سبأفون عدن VoLTE`() {
        assertEquals("سبأفون", YemeniOperatorDetector.getOperatorInfo("722012919").name)
        assertEquals("سبأفون", YemeniOperatorDetector.getOperatorInfo("+967722012919").name)
        assertEquals("سبأفون", YemeniOperatorDetector.getOperatorInfo("00967722012919").name)
    }

    @Test fun `73 يو`() {
        assertEquals("يو", YemeniOperatorDetector.getOperatorInfo("730123456").name)
        assertEquals("يو", YemeniOperatorDetector.getOperatorInfo("739123456").name)
    }

    @Test fun `77 و78 يمن موبايل`() {
        assertEquals("يمن موبايل", YemeniOperatorDetector.getOperatorInfo("770123456").name)
        assertEquals("يمن موبايل", YemeniOperatorDetector.getOperatorInfo("789123456").name)
    }

    @Test fun `10 يمن فورجي عرض فقط`() {
        val info = YemeniOperatorDetector.getOperatorInfo("101234567")
        assertEquals("يمن فورجي", info.name)
        assertFalse(info.isMobile)
    }

    @Test fun `تطبيع كل الصيغ`() {
        listOf("711234567", "+967711234567", "00967711234567", "967711234567", "0711234567")
            .forEach {
                assertEquals("فشل عند $it", "سبأفون", YemeniOperatorDetector.getOperatorInfo(it).name)
            }
    }

    @Test fun `بادئات غير مخصصة غير معروفة`() {
        assertEquals("غير محدد", YemeniOperatorDetector.getOperatorInfo("721234567").name)
        assertEquals("غير محدد", YemeniOperatorDetector.getOperatorInfo("741234567").name)
        assertFalse(YemeniOperatorDetector.isValidYemeniMobile("721234567"))
    }

    @Test fun `التحقق من المطابقة مع الباكند`() {
        assertTrue(YemeniOperatorDetector.isValidYemeniMobile("722012919"))
        assertTrue(YemeniOperatorDetector.isValidYemeniMobile("709123456"))
        assertTrue(YemeniOperatorDetector.isValidYemeniMobile("789123456"))
        assertTrue(YemeniOperatorDetector.isDialableMobile("771234567"))
    }

    @Test fun `ألوان العلامات موحدة`() {
        assertEquals(Color(0xFFE31E24), YemeniOperatorDetector.getOperatorInfo("771234567").brandColor)
        assertEquals(Color(0xFFFDB913), YemeniOperatorDetector.getOperatorInfo("711234567").brandColor)
        assertEquals(Color(0xFF00A1E4), YemeniOperatorDetector.getOperatorInfo("701234567").brandColor)
        assertEquals(Color(0xFFFFF200), YemeniOperatorDetector.getOperatorInfo("731234567").brandColor)
    }
}
