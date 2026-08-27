package com.red.server.pstn

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * خطة الترقيم اليمنية — المصدر الوحيد لتصنيف المشغّل.
 *
 * كل تأكيد يقابل عطلًا محتملًا يُوجِّه مكالمة عبر شريحة مشغّل آخر
 * بتعرفة أعلى، أو يُسقط رقمًا صالحًا في «غير معروف».
 */
class YemenNumberPlanTest {

    // ── تصنيف بالبادئة ───────────────────────────────────────────────

    @Test
    fun `two-digit prefixes map to the right operator`() {
        assertEquals("YTelecom", YemenNumberPlan.classify("0701234567")?.apiName)
        assertEquals("Sabafon", YemenNumberPlan.classify("+967712345678")?.apiName)
        assertEquals("YOU", YemenNumberPlan.classify("967731234567")?.apiName)
        assertEquals("YemenMobile", YemenNumberPlan.classify("00967771234567")?.apiName)
        assertEquals("YemenMobile", YemenNumberPlan.classify("0781234567")?.apiName)
    }

    @Test
    fun `722 is Sabafon Aden and must beat the unassigned 72 read`() {
        // بخانتين يُقرأ `72` غير المخصَّصة فيسقط في null وتُرفض المكالمة.
        assertEquals("Sabafon", YemenNumberPlan.classify("722123456")?.apiName)
    }

    @Test
    fun `Yemen4G is not a mobile network a SIM can sit on`() {
        val info = YemenNumberPlan.classify("101234567")
        assertEquals("Yemen4G", info?.apiName)
        assertEquals(false, info?.isMobile)
    }

    @Test
    fun `unassigned and too-short numbers stay null`() {
        assertNull(YemenNumberPlan.classify("12"))
        assertNull(YemenNumberPlan.classify(""))
        // `74` غير مخصَّصة
        assertNull(YemenNumberPlan.classify("0741234567"))
    }

    // ── تصنيف بالـ IMSI ──────────────────────────────────────────────

    /**
     * IMSI هو **المسار الفعلي** على HTTP API: `get_port_info` لا تُصدر
     * `operator` إطلاقًا (طلبه يردّ `error_code=400`)، والرقم فارغ قبل
     * «تعلّم الرقم». لذلك خطأ هنا = كل المنافذ «غير معروفة».
     */
    @Test
    fun `known Yemeni MNCs map from IMSI`() {
        // IMSI حقيقية من هذا النشر (UC2000-VE-8G @ .2)
        assertEquals("Sabafon", YemenNumberPlan.classifyImsi("421010673278886")?.apiName)
        assertEquals("YOU", YemenNumberPlan.classifyImsi("42102000000000")?.apiName)
        // IMSI حقيقية من UC2000-VE-8T @ .3
        assertEquals("YemenMobile", YemenNumberPlan.classifyImsi("421035007811331")?.apiName)
        assertEquals("YTelecom", YemenNumberPlan.classifyImsi("42104000000000")?.apiName)
    }

    /**
     * `42111` رُصد فعليًا على شرائح LTE في هذا النشر وهو غائب عن قائمة
     * MNC الكلاسيكية. يجب أن يبقى **مرئيًا** برقمه لا أن يُطمَس إلى
     * UNKNOWN، وأن يبقى `isMobile=true` كي لا يُستبعَد منفذ صالح.
     */
    @Test
    fun `unmapped Yemeni MNC stays visible and routable`() {
        val info = YemenNumberPlan.classifyImsi("421115007811327")
        assertEquals("YE-MNC-11", info?.apiName)
        assertEquals(true, info?.isMobile)
        assertTrue(info!!.arabicName.contains("11"))
    }

    @Test
    fun `non-Yemeni or malformed IMSI is null`() {
        assertNull(YemenNumberPlan.classifyImsi("310260000000000")) // USA T-Mobile
        assertNull(YemenNumberPlan.classifyImsi("4210"))            // أقصر من MCC+MNC
        assertNull(YemenNumberPlan.classifyImsi(null))
        assertNull(YemenNumberPlan.classifyImsi(""))
    }

    // ── التطبيع ──────────────────────────────────────────────────────

    @Test
    fun `normalizeLocal strips every dialing prefix form`() {
        assertEquals("712345678", YemenNumberPlan.normalizeLocal("+967712345678"))
        assertEquals("712345678", YemenNumberPlan.normalizeLocal("00967712345678"))
        assertEquals("712345678", YemenNumberPlan.normalizeLocal("967712345678"))
        assertEquals("712345678", YemenNumberPlan.normalizeLocal("0712345678"))
        assertEquals("712345678", YemenNumberPlan.normalizeLocal("712345678"))
        // مسافات وفواصل لا تكسر التطبيع
        assertEquals("712345678", YemenNumberPlan.normalizeLocal("+967 71 234 5678"))
    }

    @Test
    fun `mobile prefix table covers every assigned range`() {
        // النطاقات الأربعة كاملة — أي ثغرة تُسقط أرقامًا صالحة
        listOf("700", "709", "710", "719", "722", "730", "739", "770", "779", "780", "789")
            .forEach { assertTrue(it in YemenNumberPlan.MOBILE_PREFIXES_3, "missing prefix $it") }
        assertTrue("740" !in YemenNumberPlan.MOBILE_PREFIXES_3)
    }
}
