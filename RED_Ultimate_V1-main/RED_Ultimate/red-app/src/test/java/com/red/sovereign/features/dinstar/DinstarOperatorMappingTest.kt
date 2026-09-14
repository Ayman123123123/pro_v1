package com.red.sovereign.features.dinstar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات خريطة مشغّلي اليمن في العميل.
 *
 * المرجع: الخطة الوطنية للترقيم — 70 واي، 71 سبأفون، 73 يو (MTN سابقًا)،
 * 77 و78 يمن موبايل، 10 يمن فورجي. والنطاق 722 لسبأفون عدن 4G/VoLTE،
 * وهو نطاق مستقل لا امتداد لـ71.
 *
 * هذا الملف يحرس مصدر الحقيقة الوحيد في العميل (YemenOperator)، وهو
 * نفسه المستخدم في DinstarLoadBalancer على الخادم. اختلافهما يعني أن
 * ما يعرضه التطبيق يخالف ما يوجّهه الخادم فعليًّا.
 */
class DinstarOperatorMappingTest {

    // ─── البادئات الأساسية ───

    @Test fun `البادئات الأربع الأساسية`() {
        assertEquals(YemenOperator.Y_TELECOM, YemenOperator.fromNumber("703456789"))
        assertEquals(YemenOperator.SABAFON, YemenOperator.fromNumber("711234567"))
        assertEquals(YemenOperator.YOU, YemenOperator.fromNumber("733456789"))
        assertEquals(YemenOperator.YEMEN_MOBILE, YemenOperator.fromNumber("773456789"))
    }

    @Test fun `يمن موبايل تملك نطاقين 77 و 78`() {
        assertEquals(YemenOperator.YEMEN_MOBILE, YemenOperator.fromNumber("773456789"))
        assertEquals(YemenOperator.YEMEN_MOBILE, YemenOperator.fromNumber("783456789"))
    }

    // ─── النطاق 722: سبأفون عدن 4G ───

    @Test fun `النطاق 722 سبأفون لا غير معروف`() {
        // قراءة رقمين فقط تعطي 72 — غير مخصَّص — فيُرفض الرقم قبل الطلب.
        assertEquals(YemenOperator.SABAFON, YemenOperator.fromNumber("722012919"))
        assertNotEquals(YemenOperator.UNKNOWN, YemenOperator.fromNumber("722012919"))
    }

    @Test fun `722 في كل الصيغ الدولية والمحلية`() {
        listOf("722012919", "+967722012919", "00967722012919", "0722012919", "967722012919")
            .forEach { assertEquals("فشل عند: $it", YemenOperator.SABAFON, YemenOperator.fromNumber(it)) }
    }

    @Test fun `718 عدن القديم يطابق نطاق 71`() {
        assertEquals(YemenOperator.SABAFON, YemenOperator.fromNumber("718740712"))
    }

    @Test fun `الأطول أولا 722 يسبق 72 غير المخصص`() {
        // 722 مخصَّص، وبقية نطاق 72 ليست كذلك — الترتيب هو ما يفرّق.
        assertEquals(YemenOperator.SABAFON, YemenOperator.fromNumber("722000000"))
        assertEquals(YemenOperator.UNKNOWN, YemenOperator.fromNumber("721234567"))
        assertEquals(YemenOperator.UNKNOWN, YemenOperator.fromNumber("723456789"))
    }

    // ─── التطبيع ───

    @Test fun `صيغ رمز الدولة كلها تُطبَّع`() {
        listOf("+967771234567", "00967771234567", "967771234567", "0771234567", "771234567")
            .forEach { assertEquals("فشل عند: $it", YemenOperator.YEMEN_MOBILE, YemenOperator.fromNumber(it)) }
    }

    @Test fun `الفواصل والمسافات لا تكسر التصنيف`() {
        assertEquals(YemenOperator.SABAFON, YemenOperator.fromNumber("+967 71 123 4567"))
        assertEquals(YemenOperator.SABAFON, YemenOperator.fromNumber("071-123-4567"))
    }

    // ─── الحالات الحدّية ───

    @Test fun `المدخلات غير الصالحة ترجع UNKNOWN لا استثناء`() {
        listOf("", "   ", "abc", "+967", "7", "0")
            .forEach { assertEquals("فشل عند: '$it'", YemenOperator.UNKNOWN, YemenOperator.fromNumber(it)) }
    }

    // ─── اتساق الجدول ───

    @Test fun `لا تتقاسم بادئة واحدة بين مشغلين`() {
        val all = YemenOperator.entries.flatMap { op -> op.prefixes.map { it to op } }
        val dupes = all.groupBy { it.first }.filterValues { it.size > 1 }
        assertTrue("بادئات مكرّرة بين مشغّلين: ${dupes.keys}", dupes.isEmpty())
    }

    @Test fun `fromApiOperatorName يقبل الاسمين القديم والجديد`() {
        // MTN اليمن صارت YOU في 2021 — الخادم قد يُرجع أيًّا منهما.
        assertEquals(YemenOperator.YOU, YemenOperator.fromApiOperatorName("MTN"))
        assertEquals(YemenOperator.YOU, YemenOperator.fromApiOperatorName("YOU"))
        assertEquals(YemenOperator.YOU, YemenOperator.fromApiOperatorName("Yemeni Omani United"))
        assertEquals(YemenOperator.SABAFON, YemenOperator.fromApiOperatorName("Sabafon"))
        assertEquals(YemenOperator.YEMEN_MOBILE, YemenOperator.fromApiOperatorName("Yemen Mobile"))
        assertEquals(YemenOperator.UNKNOWN, YemenOperator.fromApiOperatorName(null))
    }
}
