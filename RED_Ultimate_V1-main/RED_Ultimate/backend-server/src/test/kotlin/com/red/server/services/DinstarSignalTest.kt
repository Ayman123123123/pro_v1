package com.red.server.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DinstarSignalTest {
    @Test
    fun `unknown CSQ value is never treated as usable signal`() {
        val quality = DinstarSignal.interpret(99)

        assertEquals(99, quality.raw)
        assertNull(quality.dbm)
        assertNull(quality.percent)
        assertFalse(quality.usable)
        assertEquals("NO_SIGNAL", quality.label)
    }

    @Test
    fun `strong known CSQ value remains usable with a bounded percentage`() {
        val quality = DinstarSignal.interpret("31")

        assertEquals(-51, quality.dbm)
        assertEquals(100, quality.percent)
        assertTrue(quality.usable)
        assertEquals("EXCELLENT", quality.label)
    }

    // ── كشف تقنية الراديو (يمن موبايل ليست GSM) ─────────────────────────

    @Test
    fun `detectRat distinguishes LTE CDMA WCDMA GSM`() {
        assertEquals(DinstarSignal.Rat.LTE, DinstarSignal.detectRat("LTE"))
        assertEquals(DinstarSignal.Rat.LTE, DinstarSignal.detectRat("4G"))
        assertEquals(DinstarSignal.Rat.CDMA, DinstarSignal.detectRat("CDMA2000"))
        assertEquals(DinstarSignal.Rat.CDMA, DinstarSignal.detectRat("EVDO"))
        assertEquals(DinstarSignal.Rat.CDMA, DinstarSignal.detectRat("1X"))
        assertEquals(DinstarSignal.Rat.WCDMA, DinstarSignal.detectRat("WCDMA"))
        assertEquals(DinstarSignal.Rat.GSM, DinstarSignal.detectRat("GSM"))
        assertEquals(DinstarSignal.Rat.UNKNOWN, DinstarSignal.detectRat(null))
        assertEquals(DinstarSignal.Rat.UNKNOWN, DinstarSignal.detectRat("???"))
    }

    // ── LTE RSRP ─────────────────────────────────────────────────────────

    @Test
    fun `LTE RSRP maps to dbm with LTE thresholds`() {
        val good = DinstarSignal.interpretLte(60) // 60-140 = -80
        assertEquals(-80, good.dbm)
        assertTrue(good.usable)

        val edge = DinstarSignal.interpretLte(-105) // usable LTE, rejected by GSM thresholds
        assertTrue(edge.usable)

        val dead = DinstarSignal.interpretLte(0) // -140
        assertFalse(dead.usable)

        val unknown = DinstarSignal.interpretLte(99)
        assertFalse(unknown.usable)
        assertNull(unknown.dbm)
    }

    // ── CDMA RSSI ────────────────────────────────────────────────────────

    @Test
    fun `CDMA RSSI uses CDMA thresholds`() {
        assertTrue(DinstarSignal.interpretCdma(-70).usable)
        assertTrue(DinstarSignal.interpretCdma(-100).usable) // WEAK but usable
        assertFalse(DinstarSignal.interpretCdma(-115).usable)
        assertFalse(DinstarSignal.interpretCdma(99).usable)
    }

    // ── التفسير الموحد حسب RAT ───────────────────────────────────────────

    @Test
    fun `rat aware routing picks LTE path for LTE ports`() {
        val q = DinstarSignal.interpretRatAware(mapOf("type" to "LTE", "signal" to 99, "rsrp" to 60))
        assertEquals(-80, q.dbm)
        assertTrue(q.usable)
    }

    @Test
    fun `rat aware routing falls back to CSQ for GSM ports`() {
        val q = DinstarSignal.interpretRatAware(mapOf("type" to "GSM", "signal" to 31))
        assertEquals(-51, q.dbm)
        assertTrue(q.usable)
    }

    // ── مدموج من com.red.server.DinstarSignalTest (أُلغي التكرار) ─────────
    // 3GPP TS 27.007 §8.5 — كان الخلل يجعل شريحة بلا تغطية تبدو ممتازة.

    @Test
    fun `rssi99MeansNoNetworkNotFullSignal merged`() {
        val q = DinstarSignal.interpret(99)
        assertNull(q.percent)
        assertNull(q.dbm)
        assertFalse(q.usable)
        assertEquals("NO_SIGNAL", q.label)
        assertEquals(99, q.raw)
    }

    @Test
    fun `rawMapsToStandardDbm merged`() {
        assertEquals(-113, DinstarSignal.interpret(0).dbm)
        assertEquals(-111, DinstarSignal.interpret(1).dbm)
        assertEquals(-109, DinstarSignal.interpret(2).dbm)
        assertEquals(-81, DinstarSignal.interpret(16).dbm)
        assertEquals(-53, DinstarSignal.interpret(30).dbm)
        assertEquals(-51, DinstarSignal.interpret(31).dbm)
    }

    @Test
    fun `percentIsMonotonic merged`() {
        val values = listOf(0, 5, 10, 16, 20, 25, 31).map {
            requireNotNull(DinstarSignal.interpret(it).percent)
        }
        assertEquals(values.sorted(), values)
        assertEquals(0, values.first())
        assertEquals(100, values.last())
    }

    @Test
    fun `usabilityThreshold merged`() {
        assertTrue(DinstarSignal.interpret(6).usable)
        assertTrue(DinstarSignal.interpret(7).usable)
        assertTrue(DinstarSignal.interpret(31).usable)
        assertFalse(DinstarSignal.interpret(99).usable)
        assertFalse(DinstarSignal.interpret(6).preferred)
        assertFalse(DinstarSignal.interpret(7).preferred)
        assertTrue(DinstarSignal.interpret(12).preferred)
        assertTrue(DinstarSignal.interpret(7, minGoodDbm = -100).preferred)
        assertFalse(DinstarSignal.interpret(6, minGoodDbm = -100).preferred)
    }

    @Test
    fun `labelsFollowStrength merged`() {
        assertEquals("EXCELLENT", DinstarSignal.interpret(31).label)
        assertEquals("GOOD", DinstarSignal.interpret(20).label)
        assertEquals("FAIR", DinstarSignal.interpret(12).label)
        assertEquals("WEAK", DinstarSignal.interpret(8).label)
        assertEquals("WEAK", DinstarSignal.interpret(2).label)
        assertEquals("ACCEPTABLE", DinstarSignal.interpret(8, minGoodDbm = -100).label)
    }

    @Test
    fun `toleratesMalformedInput merged`() {
        assertEquals(-81, DinstarSignal.interpret("16").dbm)
        assertNull(DinstarSignal.interpret(null).percent)
        assertNull(DinstarSignal.interpret("").percent)
        assertNull(DinstarSignal.interpret("abc").percent)
        assertFalse(DinstarSignal.interpret(-5).usable)
        assertEquals("OUT_OF_RANGE", DinstarSignal.interpret(500).label)
    }

    @Test
    fun `extendedRscpRange merged`() {
        assertEquals(-116, DinstarSignal.interpret(100).dbm)
        assertEquals(-25, DinstarSignal.interpret(191).dbm)
        assertEquals("NO_SIGNAL", DinstarSignal.interpret(199).label)
    }

    @Test
    fun `mapCarriesContract merged`() {
        val map = DinstarSignal.interpret(20).toMap()
        assertEquals(setOf("signalRaw", "signalDbm", "signal", "signalUsable", "signalGrade", "signalLabel"), map.keys)
        assertEquals(20, map["signalRaw"])
        assertEquals(-73, map["signalDbm"])
        assertEquals(true, map["signalUsable"])
    }
}
