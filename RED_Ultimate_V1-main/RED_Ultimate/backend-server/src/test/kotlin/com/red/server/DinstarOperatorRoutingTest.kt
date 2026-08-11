package com.red.server

import com.red.server.pstn.DinstarLoadBalancer.Companion.classifyNumber
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * تصنيف المشغل اليمني — مبني على خطة الترقيم الوطنية.
 *
 * البادئات الخاطئة ليست خطأً تجميليًا: التصنيف يغذّي وزن «داخل الشبكة»
 * في `DinstarLoadBalancer`، فبادئة معكوسة تعني اختيار شريحة على شبكة
 * أخرى واحتساب المكالمة بتعرفة أعلى.
 *
 * `classifyNumber` دالة نقيّة في الـ companion، فتُختبر بلا بناء الخدمة
 * (التي تتطلب اتصال قاعدة بيانات وعميل أجهزة).
 */
class DinstarOperatorRoutingTest {

    @Test
    fun `كل بادئة محمول تُنسب إلى مشغلها الصحيح`() {
        assertEquals("Sabafon", classifyNumber("711234567")?.apiName)
        assertEquals("YOU", classifyNumber("731234567")?.apiName)
        assertEquals("YemenMobile", classifyNumber("771234567")?.apiName)
        assertEquals("YemenMobile", classifyNumber("781234567")?.apiName)
        assertEquals("YTelecom", classifyNumber("701234567")?.apiName)
    }

    @Test
    fun `الصيغة الدولية وصيغة الصفر تُطبَّعان قبل التصنيف`() {
        val expected = "Sabafon"
        assertEquals(expected, classifyNumber("711234567")?.apiName)
        assertEquals(expected, classifyNumber("+967711234567")?.apiName)
        assertEquals(expected, classifyNumber("00967711234567")?.apiName)
        assertEquals(expected, classifyNumber("0711234567")?.apiName)
        // الفواصل والمسافات شائعة في الإدخال اليدوي
        assertEquals(expected, classifyNumber("+967 71 123 4567")?.apiName)
    }

    @Test
    fun `بادئة يمن فورجي مُصنَّفة لكنها ليست شبكة محمول`() {
        val info = classifyNumber("101234567")
        assertNotNull(info, "10 تخصيص حقيقي في خطة الترقيم فلا يُهمل")
        assertEquals("Yemen4G", info!!.apiName)
        // خدمة بيانات ثابتة: لا شريحة في البوابة عليها، فلا تُطابَق
        // «داخل الشبكة» وإلا مُنح المنفذ أفضلية لا يستحقها
        assertFalse(info.isMobile)
    }

    @Test
    fun `المشغلون المحمولون موسومون بأنهم محمول`() {
        listOf("701234567", "711234567", "731234567", "771234567", "781234567")
            .forEach { assertTrue(classifyNumber(it)!!.isMobile, "يجب أن يكون محمولًا: $it") }
    }

    @Test
    fun `البادئة غير المعروفة لا تُخمَّن`() {
        assertNull(classifyNumber("991234567"))
        assertNull(classifyNumber("7"))
        assertNull(classifyNumber(""))
        assertNull(classifyNumber("abc"))
    }
}
