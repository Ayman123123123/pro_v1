package com.red.sovereign.features.dinstar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات منحنى إعادة الاتصال في DinstarWebSocketBridge.
 *
 * المنطق مُستنسَخ هنا لا مُستدعى: الدالة الأصلية خاصة وتعتمد على
 * OkHttp وAndroid Log، وكلاهما غائب عن اختبارات JVM. الحساب متطابق
 * حرفيًّا مع `scheduleReconnect`، وأي تغيير هناك يجب أن ينعكس هنا.
 *
 * لماذا يُختبر أصلًا: خطأ في السقف أو في حدّ الأُسّ لا يُكتشف بالعين —
 * `1000L shl 30` يفيض إلى قيمة سالبة، فيتحول التأخير إلى إعادة اتصال
 * فورية لا نهائية تستنزف البطارية والخادم معًا.
 */
class DinstarBackoffTest {

    private companion object {
        const val MAX_RECONNECT_ATTEMPTS = 10
        const val MAX_RECONNECT_DELAY_MS = 60_000L
        const val BACKOFF_SHIFT_CAP = 6
    }

    /** نسخة طبق الأصل من الحساب في scheduleReconnect. */
    private fun delayFor(attempt: Int): Long =
        minOf(1000L shl minOf(attempt - 1, BACKOFF_SHIFT_CAP), MAX_RECONNECT_DELAY_MS)

    @Test fun `المنحنى يتضاعف من ثانية حتى السقف`() {
        assertEquals(1_000L, delayFor(1))
        assertEquals(2_000L, delayFor(2))
        assertEquals(4_000L, delayFor(3))
        assertEquals(8_000L, delayFor(4))
        assertEquals(16_000L, delayFor(5))
        assertEquals(32_000L, delayFor(6))
    }

    @Test fun `السقف دقيقة واحدة لا يُتجاوز أبدًا`() {
        // 2^6 = 64s وهي فوق السقف، فيجب أن تُقصّ إلى 60s بدءًا من المحاولة 7
        (7..MAX_RECONNECT_ATTEMPTS).forEach {
            assertEquals("المحاولة $it", MAX_RECONNECT_DELAY_MS, delayFor(it))
        }
    }

    @Test fun `لا فيضان ولا قيمة سالبة عند أرقام كبيرة`() {
        // حدّ الأُسّ يحمي من 1000L shl 30 التي تفيض إلى سالب
        (1..100).forEach {
            val d = delayFor(it)
            assertTrue("المحاولة $it أعطت $d", d in 1_000L..MAX_RECONNECT_DELAY_MS)
        }
    }

    @Test fun `المنحنى تصاعدي لا يتراجع`() {
        val curve = (1..MAX_RECONNECT_ATTEMPTS).map { delayFor(it) }
        assertEquals(curve.sorted(), curve)
    }

    @Test fun `مجموع الانتظار قبل الاستسلام معقول`() {
        // 1+2+4+8+16+32+60*4 = 303 ثانية ≈ 5 دقائق: طويل بما يكفي لعبور
        // انقطاع مؤقت، وقصير بما يكفي ألّا يبدو التطبيق معلَّقًا للأبد.
        val total = (1..MAX_RECONNECT_ATTEMPTS).sumOf { delayFor(it) }
        assertEquals(303_000L, total)
    }
}
