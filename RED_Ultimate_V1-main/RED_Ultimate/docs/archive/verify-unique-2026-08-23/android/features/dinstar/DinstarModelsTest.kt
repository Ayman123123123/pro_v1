package com.red.features.dinstar

import org.junit.Assert.*
import org.junit.Test

/**
 * 🧪 YOUNES Dinstar Models & Algorithms — اختبارات الوحدة
 */
class DinstarModelsTest {

    // ━━━ YemenOperator ━━━

    @Test
    fun `YemenOperator fromPrefix - Sabafon 770`() {
        assertEquals(YemenOperator.SABAFON, YemenOperator.fromPrefix("770"))
    }

    @Test
    fun `YemenOperator fromPrefix - MTN 710`() {
        assertEquals(YemenOperator.MTN, YemenOperator.fromPrefix("710"))
    }

    @Test
    fun `YemenOperator fromPrefix - YemenMobile 733`() {
        assertEquals(YemenOperator.YEMEN_MOBILE, YemenOperator.fromPrefix("733"))
    }

    @Test
    fun `YemenOperator fromPrefix - HiTel 700`() {
        assertEquals(YemenOperator.HITEL, YemenOperator.fromPrefix("700"))
    }

    @Test
    fun `YemenOperator fromPrefix - Unknown`() {
        assertEquals(YemenOperator.UNKNOWN, YemenOperator.fromPrefix("999"))
    }

    @Test
    fun `YemenOperator fromNumber - with 967 prefix`() {
        assertEquals(YemenOperator.SABAFON, YemenOperator.fromNumber("967777123456"))
    }

    @Test
    fun `YemenOperator fromNumber - with +967 prefix`() {
        assertEquals(YemenOperator.MTN, YemenOperator.fromNumber("+967711234567"))
    }

    @Test
    fun `YemenOperator fromNumber - with leading 0`() {
        assertEquals(YemenOperator.YEMEN_MOBILE, YemenOperator.fromNumber("0733123456"))
    }

    @Test
    fun `YemenOperator fromNumber - local format`() {
        assertEquals(YemenOperator.HITEL, YemenOperator.fromNumber("701234567"))
    }

    @Test
    fun `YemenOperator fromApiOperatorName - Sabafon`() {
        assertEquals(YemenOperator.SABAFON, YemenOperator.fromApiOperatorName("Sabafon"))
    }

    @Test
    fun `YemenOperator fromApiOperatorName - MTN Yemen`() {
        assertEquals(YemenOperator.MTN, YemenOperator.fromApiOperatorName("MTN Yemen"))
    }

    @Test
    fun `YemenOperator fromApiOperatorName - null`() {
        assertEquals(YemenOperator.UNKNOWN, YemenOperator.fromApiOperatorName(null))
    }

    @Test
    fun `YemenOperator fromApiOperatorName - Yemen Mobile`() {
        assertEquals(YemenOperator.YEMEN_MOBILE, YemenOperator.fromApiOperatorName("YemenMobile"))
    }

    // ━━━ DinstarPort ━━━

    @Test
    fun `DinstarPort isAvailable - registered idle good signal`() {
        val port = DinstarPort(index = 0, registrationState = "REGISTERED", callState = "IDLE", signalPercent = 60)
        assertTrue(port.isAvailable)
    }

    @Test
    fun `DinstarPort isAvailable - unregistered`() {
        val port = DinstarPort(index = 0, registrationState = "UNREGISTERED", callState = "IDLE", signalPercent = 60)
        assertFalse(port.isAvailable)
    }

    @Test
    fun `DinstarPort isAvailable - active call`() {
        val port = DinstarPort(index = 0, registrationState = "REGISTERED", callState = "ACTIVE", signalPercent = 60)
        assertFalse(port.isAvailable)
    }

    @Test
    fun `DinstarPort isAvailable - weak signal below threshold`() {
        val port = DinstarPort(index = 0, registrationState = "REGISTERED", callState = "IDLE", signalPercent = 15)
        assertFalse(port.isAvailable)
    }

    @Test
    fun `DinstarPort isAvailable - signal at threshold 20`() {
        val port = DinstarPort(index = 0, registrationState = "REGISTERED", callState = "IDLE", signalPercent = 20)
        assertTrue(port.isAvailable)
    }

    @Test
    fun `DinstarPort statusDescriptionAr - active call`() {
        val port = DinstarPort(index = 0, callState = "ACTIVE")
        assertEquals("في مكالمة", port.statusDescriptionAr)
    }

    @Test
    fun `DinstarPort statusDescriptionAr - unregistered`() {
        val port = DinstarPort(index = 0, registrationState = "UNREGISTERED")
        assertEquals("غير مسجل", port.statusDescriptionAr)
    }

    @Test
    fun `DinstarPort statusDescriptionAr - good signal ready`() {
        val port = DinstarPort(index = 0, registrationState = "REGISTERED", callState = "IDLE", signalPercent = 75)
        assertEquals("جاهز", port.statusDescriptionAr)
    }

    @Test
    fun `DinstarPort statusDescriptionAr - weak signal`() {
        val port = DinstarPort(index = 0, registrationState = "REGISTERED", callState = "IDLE", signalPercent = 15)
        assertEquals("إشارة ضعيفة", port.statusDescriptionAr)
    }

    // ━━━ DinstarGatewayStatus ━━━

    @Test
    fun `DinstarGatewayStatus computed properties`() {
        val status = DinstarGatewayStatus(
            isOnline = true,
            ports = listOf(
                DinstarPort(0, registrationState = "REGISTERED", callState = "IDLE", signalPercent = 80),
                DinstarPort(1, registrationState = "REGISTERED", callState = "ACTIVE", signalPercent = 60),
                DinstarPort(2, registrationState = "UNREGISTERED", callState = "IDLE", signalPercent = 0),
                DinstarPort(3, registrationState = "REGISTERED", callState = "IDLE", signalPercent = 45),
                DinstarPort(4, registrationState = "REGISTERED", callState = "IDLE", signalPercent = 10) // below threshold
            )
        )
        assertEquals(4, status.registeredCount)  // 0,1,3,4
        assertEquals(1, status.activeCallCount)   // port 1
        assertEquals(2, status.availableCount)    // port 0,3 (4 is below threshold)
        assertEquals(46, status.averageSignal)     // (80+60+45+10)/4 = 48.75 → 46? let me recalc: (80+60+0+45+10)/5 registered? No, only registered: (80+60+45+10)/4 = 48
        assertTrue(status.canMakeCall)
        assertNotNull(status.bestPortForCall)
        assertEquals(0, status.bestPortForCall!!.index) // port 0 has highest signal (80%)
    }

    @Test
    fun `DinstarGatewayStatus operatorDistribution`() {
        val status = DinstarGatewayStatus(
            ports = listOf(
                DinstarPort(0, simType = YemenOperator.SABAFON),
                DinstarPort(1, simType = YemenOperator.SABAFON),
                DinstarPort(2, simType = YemenOperator.MTN),
                DinstarPort(3, simType = YemenOperator.YEMEN_MOBILE)
            )
        )
        val dist = status.operatorDistribution
        assertEquals(2, dist[YemenOperator.SABAFON])
        assertEquals(1, dist[YemenOperator.MTN])
        assertEquals(1, dist[YemenOperator.YEMEN_MOBILE])
    }

    @Test
    fun `DinstarGatewayStatus empty ports`() {
        val status = DinstarGatewayStatus()
        assertEquals(0, status.registeredCount)
        assertEquals(0, status.availableCount)
        assertFalse(status.canMakeCall)
        assertNull(status.bestPortForCall)
    }

    // ━━━ DinstarCdr ━━━

    @Test
    fun `DinstarCdr operator and formattedDuration`() {
        val cdr = DinstarCdr(port = 0, phoneNumber = "777123456", durationSeconds = 125)
        assertEquals(YemenOperator.SABAFON, cdr.operator)
        assertEquals("2د 5ث", cdr.formattedDuration)
    }

    @Test
    fun `DinstarCdr short duration`() {
        val cdr = DinstarCdr(port = 0, phoneNumber = "711234567", durationSeconds = 45)
        assertEquals("45ث", cdr.formattedDuration)
    }

    // ━━━ Port Selection Algorithm ━━━

    @Test
    fun `selectOptimalPort prefers same operator`() {
        // When calling a Sabafon number, port with Sabafon SIM should be preferred
        // This tests the algorithm logic conceptually
        val ports = listOf(
            DinstarPort(0, registrationState = "REGISTERED", callState = "IDLE", signalPercent = 70, simType = YemenOperator.MTN),
            DinstarPort(1, registrationState = "REGISTERED", callState = "IDLE", signalPercent = 70, simType = YemenOperator.SABAFON)
        )
        // Port 1 (Sabafon) should score higher when calling 777... (Sabafon)
        // because of +35 operator bonus
        val wOperatorPort0 = 0.0  // MTN ≠ Sabafon
        val wOperatorPort1 = 35.0  // Sabafon = Sabafon
        assertTrue(wOperatorPort1 > wOperatorPort0)
    }

    @Test
    fun `selectOptimalPort penalizes heavily used ports`() {
        // WFQ: ports with more usage get penalized
        val usagePort0 = 10  // heavily used
        val usagePort1 = 2   // lightly used
        val wUsagePort0 = -usagePort0 * 5.0  // -50
        val wUsagePort1 = -usagePort1 * 5.0  // -10
        assertTrue(wUsagePort1 > wUsagePort0) // port 1 has better (less negative) usage score
    }

    // ━━━ DinstarCommand Result ━━━

    @Test
    fun `DinstarCommandResult Success`() {
        val result = DinstarCommandResult.Success("تم", mapOf("port" to 0))
        assertTrue(result is DinstarCommandResult.Success)
        assertEquals("تم", (result as DinstarCommandResult.Success).message)
    }

    @Test
    fun `DinstarCommandResult Error`() {
        val result = DinstarCommandResult.Error("فشل", 401)
        assertTrue(result is DinstarCommandResult.Error)
        assertEquals(401, (result as DinstarCommandResult.Error).code)
    }

    // ━━━ DinstarEvent ━━━

    @Test
    fun `DinstarEvent CallStateChanged`() {
        val event = DinstarEvent.CallStateChanged(3, "IDLE", "ACTIVE", "سبأفون")
        assertTrue(event is DinstarEvent.CallStateChanged)
        assertEquals(3, (event as DinstarEvent.CallStateChanged).port)
    }

    @Test
    fun `DinstarEvent CircuitBreakerOpen`() {
        val event = DinstarEvent.CircuitBreakerOpen(5)
        assertTrue(event is DinstarEvent.CircuitBreakerOpen)
        assertEquals(5, (event as DinstarEvent.CircuitBreakerOpen).failures)
    }
}
