package com.red.server

import com.red.server.services.DinstarModelProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DinstarModelProfileTest {
    @Test
    fun `8G profile is explicitly GSM only and exposes eight ports`() {
        val profile = DinstarModelProfile.parse("UC2000-VE-8G")
        assertEquals(8, profile.portCount)
        assertEquals(0..7, profile.portRange)
        assertFalse(profile.supportsVolte)
        assertTrue(profile.radioCapability.startsWith("GSM"))
    }

    @Test
    fun `8T remains a distinct VoLTE profile and unknown labels are rejected`() {
        val profile = DinstarModelProfile.parse("uc2000-ve-8t")
        assertTrue(profile.supportsVolte)
        assertThrows(IllegalArgumentException::class.java) { DinstarModelProfile.parse("UC2000-VE-16T") }
    }

    @Test
    fun `8T adds the wideband codecs that 8G does not carry`() {
        // ورقة بيانات UC2000-VE: الطراز T يضيف G.722 وAMR إلى القائمة.
        // تفاوض Asterisk يعتمد هذه القائمة، فخلطها بين الطرازين يُنتج
        // مكالمة بلا ترميز مشترك.
        val g = DinstarModelProfile.parse("UC2000-VE-8G")
        val t = DinstarModelProfile.parse("UC2000-VE-8T")
        assertTrue(t.codecs.containsAll(g.codecs), "الطراز T يدعم كل ما يدعمه G")
        assertTrue("G.722" in t.codecs && "AMR" in t.codecs)
        assertFalse("G.722" in g.codecs, "الطراز G لا يدعم G.722")
    }

    @Test
    fun `four-channel variants report four ports not eight`() {
        // كان عدد المنافذ مثبّتًا على 8 في الشيفرة؛ لو وُصل طراز رباعي
        // لاستعلم الخادم عن منافذ غير موجودة ولحاول التوجيه إليها.
        val g4 = DinstarModelProfile.parse("UC2000-VE-4G")
        assertEquals(4, g4.portCount)
        assertEquals(0..3, g4.portRange)
        assertEquals(4, g4.simSlots)
    }

    @Test
    fun `metadata never claims carrier compatibility`() {
        // التوافق مع مشغّل بعينه يعتمد على متغيّر الراديو والتغطية
        // وملف الشريحة — لا يجوز استنتاجه من اسم الطراز.
        DinstarModelProfile.entries.forEach { profile ->
            val meta = profile.metadata()
            assertEquals(true, meta["carrierCompatibilityRequiresLiveRegistration"])
            assertEquals(true, meta["hotSwappableSim"])
            assertTrue(meta["httpApiAuth"].toString().contains("Digest"))
            assertEquals(profile.portCount, meta["simSlots"])
        }
    }

    @Test
    fun `parse error names the supported models`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            DinstarModelProfile.parse("DWG2000-16G")
        }
        assertTrue(error.message!!.contains("UC2000-VE-8G"))
        assertTrue(error.message!!.contains("UC2000-VE-8T"))
    }
}
