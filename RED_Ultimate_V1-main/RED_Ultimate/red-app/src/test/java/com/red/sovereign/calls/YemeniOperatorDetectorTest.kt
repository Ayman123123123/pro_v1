package com.red.sovereign.calls

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * الخريطة المرجعية هي الخطة الوطنية للترقيم، وهي نفسها المستخدمة في
 * الخادم (DinstarLoadBalancer.OPERATOR_PREFIXES). أي تغيير هنا يجب أن
 * يُقابله تغيير هناك، وإلا اختلف ما يعرضه التطبيق عمّا يوجّهه الخادم.
 *
 * 70 واي · 71 سبأفون · 73 يو (YOU) · 77/78 يمن موبايل · 10 يمن فورجي
 */
class YemeniOperatorDetectorTest {

    // ─── المحمول: البادئات الخمس ───

    @Test fun `70 واي`() {
        assertEquals("واي", YemeniOperatorDetector.getOperatorInfo("701234567")!!.name)
    }

    @Test fun `71 سبأفون`() {
        assertEquals("سبأفون", YemeniOperatorDetector.getOperatorInfo("711234567")!!.name)
    }

    @Test fun `73 يو`() {
        assertEquals("يو", YemeniOperatorDetector.getOperatorInfo("731234567")!!.name)
    }

    @Test fun `77 يمن موبايل`() {
        assertEquals("يمن موبايل", YemeniOperatorDetector.getOperatorInfo("771234567")!!.name)
    }

    @Test fun `78 يمن موبايل`() {
        assertEquals("يمن موبايل", YemeniOperatorDetector.getOperatorInfo("781234567")!!.name)
    }

    // ─── تطبيع الصيغ الدولية والمحلية ───

    @Test fun `كل صيغ الإدخال تعطي النتيجة نفسها`() {
        val expected = "سبأفون"
        listOf(
            "711234567",
            "+967711234567",
            "00967711234567",
            "967711234567",
            "0711234567",
            "+967 71 123 4567",
            "071-123-4567"
        ).forEach { input ->
            assertEquals("فشل التطبيع للإدخال: $input", expected, YemeniOperatorDetector.getOperatorInfo(input)?.name)
        }
    }

    @Test fun `00967 تُزال كاملة قبل 967`() {
        // لو أُزيلت "967" أولًا لبقي "00..." وفشل التصنيف.
        assertEquals("يمن موبايل", YemeniOperatorDetector.getOperatorInfo("00967771234567")!!.name)
    }

    // ─── أسماء الـ API تطابق الخادم ───

    @Test fun `apiName يطابق ما يعيده الخادم`() {
        assertEquals("YTelecom", YemeniOperatorDetector.getOperatorInfo("701234567")!!.apiName)
        assertEquals("Sabafon", YemeniOperatorDetector.getOperatorInfo("711234567")!!.apiName)
        assertEquals("YOU", YemeniOperatorDetector.getOperatorInfo("731234567")!!.apiName)
        assertEquals("YemenMobile", YemeniOperatorDetector.getOperatorInfo("771234567")!!.apiName)
        assertEquals("YemenMobile", YemeniOperatorDetector.getOperatorInfo("781234567")!!.apiName)
    }

    // ─── isMobile: من يصلح لمطابقة «داخل الشبكة» ───

    @Test fun `المحمول قابل لمطابقة داخل الشبكة`() {
        listOf("701234567", "711234567", "731234567", "771234567", "781234567").forEach {
            assertTrue("يجب أن يكون محمولًا: $it", YemeniOperatorDetector.getOperatorInfo(it)!!.isMobile)
        }
    }

    @Test fun `يمن فورجي بيانات ثابتة لا محمول`() {
        val info = YemeniOperatorDetector.getOperatorInfo("101234567")
        assertNotNull(info)
        assertEquals("يمن فورجي", info!!.name)
        assertFalse("Yemen4G ليست شبكة محمول فلا تصلح لمطابقة on-net", info.isMobile)
    }

    @Test fun `الهاتف الثابت ليس محمولًا`() {
        assertFalse(YemeniOperatorDetector.getOperatorInfo("1234567")!!.isMobile)
    }

    // ─── الهاتف الثابت ───

    @Test fun `رموز محافظات الثابت`() {
        assertEquals("هاتف ثابت (صنعاء)", YemeniOperatorDetector.getOperatorInfo("1234567")!!.name)
        assertEquals("هاتف ثابت (عدن)", YemeniOperatorDetector.getOperatorInfo("2345678")!!.name)
        assertEquals("هاتف ثابت (الحديدة)", YemeniOperatorDetector.getOperatorInfo("3567890")!!.name)
        assertEquals("هاتف ثابت (تعز وإب)", YemeniOperatorDetector.getOperatorInfo("4567890")!!.name)
        assertEquals("هاتف ثابت (حضرموت)", YemeniOperatorDetector.getOperatorInfo("5678901")!!.name)
        assertEquals("هاتف ثابت (مأرب)", YemeniOperatorDetector.getOperatorInfo("6789012")!!.name)
        assertEquals("هاتف ثابت (صعدة وعمران)", YemeniOperatorDetector.getOperatorInfo("7890123")!!.name)
    }

    @Test fun `المحمول له أسبقية على رمز الثابت 7`() {
        // "771234567" يبدأ بـ 7 (رمز صعدة للثابت) لكنه محمول بالبادئة 77.
        assertEquals("يمن موبايل", YemeniOperatorDetector.getOperatorInfo("771234567")!!.name)
    }

    // ─── سبأفون عدن 4G: النطاق 722 ───

    @Test fun `النطاق 722 سبأفون عدن فورجي لا هاتف ثابت`() {
        // 722 نطاق مستقل أُطلق مع VoLTE في عدن. قراءة رقمين فقط تعطي 72
        // فيسقط الرقم إلى فرع الهاتف الثابت خطأً، أو يُرفض قبل الطلب.
        val info = YemeniOperatorDetector.getOperatorInfo("722012919")
        assertNotNull(info)
        assertEquals("سبأفون", info!!.name)
        assertTrue(info.isMobile)
    }

    @Test fun `722 يُصنَّف في كل الصيغ الدولية والمحلية`() {
        listOf("722012919", "+967722012919", "00967722012919", "0722012919", "967722012919")
            .forEach { assertEquals("سبأفون", YemeniOperatorDetector.getOperatorInfo(it)?.name, "فشل عند: $it") }
    }

    @Test fun `النطاق 718 سبأفون عدن القديم يطابق 71`() {
        assertEquals("سبأفون", YemeniOperatorDetector.getOperatorInfo("718740712")?.name)
    }

    // ─── الحالات الحدّية ───

    @Test fun `بادئة محمول غير مخصصة ترجع null`() {
        // 721 و723… غير مخصَّصة: 722 وحده هو المخصَّص من نطاق 72
        assertNull(YemeniOperatorDetector.getOperatorInfo("721234567"))
        assertNull(YemeniOperatorDetector.getOperatorInfo("723456789"))
        assertNull(YemeniOperatorDetector.getOperatorInfo("741234567"))
        assertNull(YemeniOperatorDetector.getOperatorInfo("791234567"))
    }

    @Test fun `رقم قصير لا يُصنَّف تخمينًا`() {
        assertNull(YemeniOperatorDetector.getOperatorInfo("12345"))
        assertNull(YemeniOperatorDetector.getOperatorInfo("1"))
    }

    @Test fun `المدخلات غير الصالحة`() {
        assertNull(YemeniOperatorDetector.getOperatorInfo(""))
        assertNull(YemeniOperatorDetector.getOperatorInfo("   "))
        assertNull(YemeniOperatorDetector.getOperatorInfo("abc"))
        assertNull(YemeniOperatorDetector.getOperatorInfo("+967"))
        assertNull(YemeniOperatorDetector.getOperatorInfo("9000000"))
    }
}
