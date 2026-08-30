package com.red.server.pstn

import com.red.server.services.DinstarFleetService
import com.red.server.services.DinstarHardwareService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.data.redis.core.RedisTemplate
import java.util.UUID

class DinstarLoadBalancerTest {

    private val hardware = mock<DinstarHardwareService>()
    private val fleet = mock<DinstarFleetService>()
    private val jdbc = mock<JdbcTemplate>()
    private val redis = mock<RedisTemplate<String, String>>()

    /**
     * الحجز الدائم مزدوج: هذه الاختبارات تقيس اختيار المنفذ (الترتيب،
     * الإشارة، الاستبعاد) لا استمرار الحجز عبر إعادة التشغيل.
     */
    private val reservations = mock<PersistentReservationService>()

    private fun createLoadBalancer() = DinstarLoadBalancer(hardware, fleet, jdbc, redis, reservations)

    private fun makeGateway(
        id: UUID = UUID.randomUUID(),
        host: String = "192.168.11.1",
        apiPort: Int = 443,
        portCount: Int = 8,
        routingPriority: Int = 0,
        pjsipEndpoint: String? = "sip:gw1",
        enabled: Boolean = true,
        healthState: String = "HEALTHY"
    ) = DinstarFleetService.Gateway(
        id = id,
        name = "GW-$host",
        model = "UC2000-VE-8G",
        host = host,
        scheme = "https",
        apiPort = apiPort,
        portCount = portCount,
        enabled = enabled,
        healthState = healthState,
        routingPriority = routingPriority,
        pjsipEndpoint = pjsipEndpoint,
        serialNumber = "SN12345",
        firmwareVersion = "1.0.0",
        siteLabel = "Site-A",
        consecutiveFailures = 0
    )

    private fun makePort(
        index: Int,
        status: String = "REGISTERED",
        callState: String = "IDLE",
        signalUsable: Boolean = true,
        signalPercent: Int = 85,
        signalDbm: Int = -75,
        operator: String = "YOU"
    ) = mapOf(
        "index" to index,
        "status" to status,
        "callState" to callState,
        "signalUsable" to signalUsable,
        "signal" to signalPercent,
        "signalDbm" to signalDbm,
        "operator" to operator
    )

    @Test
    fun `classifyNumber recognizes Yemeni mobile prefixes`() {
        assertEquals("Sabafon", DinstarLoadBalancer.classifyNumber("+967712345678")?.apiName)
        assertEquals("YOU", DinstarLoadBalancer.classifyNumber("967731234567")?.apiName)
        assertEquals("YemenMobile", DinstarLoadBalancer.classifyNumber("009677712345678")?.apiName)
        assertEquals("YTelecom", DinstarLoadBalancer.classifyNumber("0701234567")?.apiName)
    }

    @Test
    fun `classifyNumber returns null for too short number`() {
        assertNull(DinstarLoadBalancer.classifyNumber("12"))
        assertNull(DinstarLoadBalancer.classifyNumber(""))
    }

    @Test
    fun `classifyNumber handles local zero prefix`() {
        assertEquals("YOU", DinstarLoadBalancer.classifyNumber("0731234567")?.apiName)
        assertEquals("YemenMobile", DinstarLoadBalancer.classifyNumber("0771234567")?.apiName)
    }

    @Test
    fun `classifyNumber Yemen4G is marked non-mobile`() {
        val info = DinstarLoadBalancer.classifyNumber("0101234567")
        assertNotNull(info)
        assertEquals("Yemen4G", info!!.apiName)
        assertEquals(false, info.isMobile)
    }

    @Test
    fun `selectPort returns best port when gateways are registered`() {
        val gw = makeGateway()
        val ports = listOf(
            makePort(0, signalPercent = 50, operator = "Sabafon"),
            makePort(1, signalPercent = 90, operator = "YOU"),
            makePort(2, signalPercent = 80, operator = "YemenMobile")
        )

        whenever(fleet.routableGateways()).thenReturn(listOf(gw))
        whenever(hardware.getHardwareStatus(eq(gw))).thenReturn(ports)

        val lb = createLoadBalancer()
        val result = lb.selectPort("+967731234567")

        assertNotNull(result)
        assertEquals(gw.id, result!!.gatewayId)
        assertEquals(gw.host, result.gatewayHost)
        assertEquals(1, result.portIndex)
        assertEquals("YOU", result.operator)
        verify(fleet).markHealthy(gw.id)
    }

    @Test
    fun `selectPort returns null when all ports are busy`() {
        val gw = makeGateway()
        val ports = listOf(
            makePort(0, callState = "ACTIVE"),
            makePort(1, callState = "DIALING")
        )

        whenever(fleet.routableGateways()).thenReturn(listOf(gw))
        whenever(hardware.getHardwareStatus(eq(gw))).thenReturn(ports)

        val lb = createLoadBalancer()
        val result = lb.selectPort("771234567")
        assertNull(result)
    }

    @Test
    fun `selectPort returns null when no ports registered`() {
        val gw = makeGateway()
        val ports = listOf(
            makePort(0, status = "UNREGISTERED"),
            makePort(1, status = "NOT_REGISTERED")
        )

        whenever(fleet.routableGateways()).thenReturn(listOf(gw))
        whenever(hardware.getHardwareStatus(eq(gw))).thenReturn(ports)

        val lb = createLoadBalancer()
        val result = lb.selectPort("771234567")
        assertNull(result)
    }

    @Test
    fun `selectPort returns null when signal is not usable`() {
        val gw = makeGateway()
        val ports = listOf(
            makePort(0, signalUsable = false, signalPercent = 99)
        )

        whenever(fleet.routableGateways()).thenReturn(listOf(gw))
        whenever(hardware.getHardwareStatus(eq(gw))).thenReturn(ports)

        val lb = createLoadBalancer()
        val result = lb.selectPort("771234567")
        assertNull(result)
    }

    @Test
    fun `selectPort with forcedPort filters to that port only`() {
        val gw = makeGateway()
        val ports = listOf(
            makePort(0, signalPercent = 90),
            makePort(1, signalPercent = 50),
            makePort(2, signalPercent = 80)
        )

        whenever(fleet.routableGateways()).thenReturn(listOf(gw))
        whenever(hardware.getHardwareStatus(eq(gw))).thenReturn(ports)

        val lb = createLoadBalancer()
        val result = lb.selectPort("771234567", forcedPort = 1)

        assertNotNull(result)
        assertEquals(1, result!!.portIndex)
    }

    @Test
    fun `selectPort with forcedPort returns null if that port is busy`() {
        val gw = makeGateway()
        val ports = listOf(
            makePort(0),
            makePort(1, callState = "ACTIVE"),
            makePort(2)
        )

        whenever(fleet.routableGateways()).thenReturn(listOf(gw))
        whenever(hardware.getHardwareStatus(eq(gw))).thenReturn(ports)

        val lb = createLoadBalancer()
        val result = lb.selectPort("771234567", forcedPort = 1)
        assertNull(result)
    }

    @Test
    fun `selectPort falls back to legacy single-gateway when no fleet`() {
        val ports = listOf(
            makePort(0, signalPercent = 75),
            makePort(1, signalPercent = 90, operator = "YOU")
        )

        whenever(fleet.routableGateways()).thenReturn(emptyList())
        whenever(hardware.getHardwareStatus()).thenReturn(ports)

        val lb = createLoadBalancer()
        val result = lb.selectPort("771234567")

        assertNotNull(result)
        assertEquals(1, result!!.portIndex)
        assertEquals(null, result.gatewayId)
        assertEquals("configured", result.gatewayHost)
    }

    @Test
    fun `selectPort marks gateway failure when hardware status fails`() {
        val gw = makeGateway()
        whenever(fleet.routableGateways()).thenReturn(listOf(gw))
        whenever(hardware.getHardwareStatus(eq(gw))).thenThrow(RuntimeException("Gateway unreachable"))

        val lb = createLoadBalancer()
        val result = lb.selectPort("771234567")
        assertNull(result)
        verify(fleet).markFailure(eq(gw.id), any())
    }

    @Test
    fun `selectPort prefers on-net operator match for scoring`() {
        val gw = makeGateway()
        val ports = listOf(
            makePort(0, signalPercent = 95, operator = "YemenMobile"),
            makePort(1, signalPercent = 80, operator = "YOU")
        )

        whenever(fleet.routableGateways()).thenReturn(listOf(gw))
        whenever(hardware.getHardwareStatus(eq(gw))).thenReturn(ports)

        val lb = createLoadBalancer()
        val result = lb.selectPort("+967731234567")
        assertNotNull(result)
        assertEquals(1, result!!.portIndex)
    }

    @Test
    fun `classifyOperator delegates to classifyNumber`() {
        val lb = createLoadBalancer()
        val info = lb.classifyOperator("+967712345678")
        assertEquals("Sabafon", info?.apiName)
    }

    @Test
    fun `operatorsMatch normalizes MTN to YOU`() {
        val gw = makeGateway()
        val ports = listOf(
            makePort(0, signalPercent = 80, operator = "MTN")
        )

        whenever(fleet.routableGateways()).thenReturn(listOf(gw))
        whenever(hardware.getHardwareStatus(eq(gw))).thenReturn(ports)

        val lb = createLoadBalancer()
        val result = lb.selectPort("+967731234567")
        assertNotNull(result)
    }

    @Test
    fun `getOptimalSlotWfq delegates to selectPort`() {
        val gw = makeGateway()
        val ports = listOf(makePort(0, signalPercent = 80))

        whenever(fleet.routableGateways()).thenReturn(listOf(gw))
        whenever(hardware.getHardwareStatus(eq(gw))).thenReturn(ports)

        val lb = createLoadBalancer()
        val slot = lb.getOptimalSlotWfq("771234567")
        assertEquals(0, slot)
    }

    @Test
    fun `releasePort decrements usage counter without going negative`() {
        val gw = makeGateway(id = UUID.fromString("12345678-1234-1234-1234-123456789012"))
        val ports = listOf(makePort(0, signalPercent = 80))

        whenever(fleet.routableGateways()).thenReturn(listOf(gw))
        whenever(hardware.getHardwareStatus(eq(gw))).thenReturn(ports)

        val lb = createLoadBalancer()
        lb.selectPort("771234567")
        lb.releasePort(gw.id, 0)

        lb.releasePort(gw.id, 0)
    }

    @Test
    fun `releasePort with single arg releases across all gateways`() {
        val lb = createLoadBalancer()
        lb.releasePort(0)
    }

    @Test
    fun `releaseAll resets all counters to zero`() {
        val lb = createLoadBalancer()
        lb.releaseAll()
    }
}