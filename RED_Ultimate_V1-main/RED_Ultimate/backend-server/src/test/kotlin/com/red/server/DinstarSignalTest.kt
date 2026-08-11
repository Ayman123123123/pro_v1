package com.red.server

import com.red.server.services.DinstarSignal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * اختبارات تفسير إشارة بوابات DINSTAR حسب 3GPP TS 27.007 §8.5.
 *
 * أهم اختبار هنا هو [rssi99MeansNoNetworkNotFullSignal]: كان الخلل
 * الذي يجعل شريحة بلا تغطية تبدو ممتازة فيختارها موزّع الأحمال.
 */
class DinstarSignalTest {

    @Test
    @DisplayName("القراءة 99 تعني انعدام الشبكة لا إشارة كاملة")
    fun rssi99MeansNoNetworkNotFullSignal() {
        val q = DinstarSignal.interpret(99)

        // السلوك القديم: coerceIn(0,31) ⇒ 31 ⇒ 100%
        assertNull(q.percent, "القراءة 99 يجب ألا تُنتج نسبة مئوية إطلاقًا")
        assertNull(q.dbm, "لا يمكن اشتقاق dBm من قراءة غير قابلة للكشف")
        assertFalse(q.usable, "منفذ بلا قياس إشارة لا يصلح لحمل مكالمة")
        assertEquals("NO_SIGNAL", q.label)
        assertEquals(99, q.raw, "تُحفظ القراءة الخام للتشخيص")
    }

    @Test
    @DisplayName("القراءة 199 في النطاق الممتد تعني انعدام الشبكة أيضًا")
    fun extendedUnknownIsAlsoNoSignal() {
        val q = DinstarSignal.interpret(199)
        assertNull(q.percent)
        assertFalse(q.usable)
        assertEquals("NO_SIGNAL", q.label)
    }

    @Test
    @DisplayName("تحويل القراءة الخام إلى dBm يطابق الجدول المعياري")
    fun rawMapsToStandardDbm() {
        // 3GPP TS 27.007 §8.5: dBm = 2 × raw − 113
        assertEquals(-113, DinstarSignal.interpret(0).dbm)
        assertEquals(-111, DinstarSignal.interpret(1).dbm)
        assertEquals(-109, DinstarSignal.interpret(2).dbm)
        assertEquals(-81, DinstarSignal.interpret(16).dbm)
        assertEquals(-53, DinstarSignal.interpret(30).dbm)
        assertEquals(-51, DinstarSignal.interpret(31).dbm)
    }

    @Test
    @DisplayName("النسبة المئوية تتدرّج ولا تنعكس")
    fun percentIsMonotonic() {
        val values = listOf(0, 5, 10, 16, 20, 25, 31).map {
            requireNotNull(DinstarSignal.interpret(it).percent)
        }
        assertEquals(values.sorted(), values, "النسبة يجب أن تزيد مع القراءة")
        assertEquals(0, values.first(), "‎-113 dBm هو الحد الأدنى")
        assertEquals(100, values.last(), "‎-51 dBm هو الحد الأعلى")
    }

    @Test
    @DisplayName("عتبة الصلاحية تستبعد الإشارة الضعيفة جدًا")
    fun usabilityThreshold() {
        // ‎-100 dBm هو الحد؛ القراءة 6 ⇒ ‎-101 dBm (تحته)، و7 ⇒ ‎-99 dBm (فوقه)
        assertFalse(DinstarSignal.interpret(6).usable, "‎-101 dBm أضعف من أن تحمل مكالمة")
        assertTrue(DinstarSignal.interpret(7).usable, "‎-99 dBm مقبولة")
        assertTrue(DinstarSignal.interpret(31).usable)
    }

    @Test
    @DisplayName("التصنيفات النصية تتبع القوة الفعلية")
    fun labelsFollowStrength()  {
        assertEquals("EXCELLENT", DinstarSignal.interpret(31).label) // ‎-51
        assertEquals("GOOD", DinstarSignal.interpret(20).label)      // ‎-73
        assertEquals("FAIR", DinstarSignal.interpret(12).label)      // ‎-89
        assertEquals("WEAK", DinstarSignal.interpret(8).label)       // ‎-97
        assertEquals("UNUSABLE", DinstarSignal.interpret(2).label)   // ‎-109
    }

    @Test
    @DisplayName("القيم النصية والغائبة والخارجة عن المدى لا تُسقط الخدمة")
    fun toleratesMalformedInput() {
        // بعض إصدارات البرنامج الثابت تُرسل الحقل كسلسلة
        assertEquals(-81, DinstarSignal.interpret("16").dbm)

        assertNull(DinstarSignal.interpret(null).percent)
        assertNull(DinstarSignal.interpret("").percent)
        assertNull(DinstarSignal.interpret("abc").percent)
        assertFalse(DinstarSignal.interpret(-5).usable)
        assertEquals("OUT_OF_RANGE", DinstarSignal.interpret(500).label)
    }

    @Test
    @DisplayName("النطاق الممتد لـ TD-SCDMA يُفسَّر بمعادلته الخاصة")
    fun extendedRscpRange() {
        // 100 ⇒ ‎-116 dBm، 191 ⇒ ‎-25 dBm
        assertEquals(-116, DinstarSignal.interpret(100).dbm)
        assertEquals(-25, DinstarSignal.interpret(191).dbm)
        assertNotNull(DinstarSignal.interpret(150).dbm)
    }

    @Test
    @DisplayName("خريطة النتيجة تحمل الحقول التي تعتمدها اللوحة والموزّع")
    fun mapCarriesContract() {
        val map = DinstarSignal.interpret(20).toMap()
        assertEquals(setOf("signalRaw", "signalDbm", "signal", "signalUsable", "signalLabel"), map.keys)
        assertEquals(20, map["signalRaw"])
        assertEquals(-73, map["signalDbm"])
        assertEquals(true, map["signalUsable"])
    }
}
