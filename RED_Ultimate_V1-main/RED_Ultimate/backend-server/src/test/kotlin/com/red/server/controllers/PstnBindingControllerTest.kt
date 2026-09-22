package com.red.server.controllers

import com.red.server.audit.AuditService
import com.red.server.auth.model.AccountStatus
import com.red.server.auth.model.UserAccount
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.pstn.DinstarLoadBalancer
import com.red.server.services.DinstarFleetService
import com.red.server.services.DinstarHardwareService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Optional
import java.util.UUID

class PstnBindingControllerTest {

    private val users = mock<UserAccountRepository>()
    private val fleet = mock<DinstarFleetService>()
    private val hardware = mock<DinstarHardwareService>()
    private val loadBalancer = mock<DinstarLoadBalancer>()
    private val audit = mock<AuditService>()

    private lateinit var controller: PstnBindingController

    private val gatewayId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val gateway = DinstarFleetService.Gateway(
        id = gatewayId,
        name = "DINSTAR UC2000-VE-8G @ 192.168.11.2",
        model = "UC2000-VE-8G",
        host = "192.168.11.2",
        scheme = "https",
        apiPort = 443,
        portCount = 8,
        enabled = true,
        healthState = "ONLINE",
        routingPriority = 0,
        pjsipEndpoint = "dinstar-gw-192-168-11-2",
        serialNumber = "SN123",
        firmwareVersion = "1.0",
        siteLabel = null,
        consecutiveFailures = 0
    )

    private val emptyGatewayId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
    private val emptyGateway = DinstarFleetService.Gateway(
        id = emptyGatewayId,
        name = "DINSTAR @ 192.168.11.3",
        model = "UC2000-VE-8G",
        host = "192.168.11.3",
        scheme = "https",
        apiPort = 443,
        portCount = 8,
        enabled = true,
        healthState = "ONLINE",
        routingPriority = 1,
        pjsipEndpoint = "dinstar-gw-192-168-11-3",
        serialNumber = "SN999",
        firmwareVersion = "1.0",
        siteLabel = null,
        consecutiveFailures = 0
    )

    private fun makeUser(id: UUID, redId: String, gwId: UUID?, port: Int?, number: String?): UserAccount {
        return UserAccount(
            id = id,
            redId = redId,
            username = "user-$redId",
            displayName = "User $redId",
            status = AccountStatus.APPROVED,
            pstnEnabled = true,
            pstnDailyLimit = 10,
            pstnGatewayId = gwId,
            pstnPortIndex = port,
            pstnNumber = number
        )
    }

    @BeforeEach
    fun setup() {
        controller = PstnBindingController(users, fleet, hardware, loadBalancer, audit)
    }

    private fun auth(userId: UUID) = UsernamePasswordAuthenticationToken(userId.toString(), null, emptyList())

    // â”€â”€ reconcile detects mismatch when liveNumber != bound number â”€â”€

    @Test
    fun `reconcile detects mismatch when liveNumber differs from bound number`() {
        whenever(fleet.listGateways()).thenReturn(listOf(gateway))
        whenever(fleet.findGateway(gatewayId)).thenReturn(gateway)
        val livePorts = listOf(mapOf<String, Any?>(
            "index" to 0,
            "number" to "712065000",
            "numberMasked" to "â€¢â€¢â€¢â€¢5000",
            "imsi" to "4210155555000",
            "imsiMasked" to "â€¢â€¢â€¢â€¢5000",
            "operator" to "Sabafon",
            "signalUsable" to true,
            "status" to "REGISTER_OK"
        ))
        whenever(hardware.getHardwareStatus(eq(gateway))).thenReturn(livePorts)

        val boundUserId = UUID.randomUUID()
        val boundUser = makeUser(boundUserId, "ffd", gatewayId, 0, "712065805")
        whenever(users.findAll()).thenReturn(listOf(boundUser))
        whenever(fleet.findGateway(gatewayId)).thenReturn(gateway)

        val result = controller.reconcile()
        @Suppress("UNCHECKED_CAST")
        val ports = result["ports"] as List<Map<String, Any?>>
        assertEquals(1, ports.size)
        val port0 = ports[0]
        assertEquals(0, port0["index"])
        assertEquals(true, port0["mismatch"])
        assertEquals("MISMATCH", port0["suggestedAction"])
        assertEquals("712065000", port0["liveNumber"])
        assertEquals("712065805", port0["boundNumber"])
        @Suppress("UNCHECKED_CAST")
        val summary = result["summary"] as Map<String, Any?>
        assertEquals(1, summary["totalPorts"])
        assertEquals(1, summary["boundPorts"])
        assertEquals(1, summary["mismatched"])
        assertEquals(0, summary["ok"])
    }

    @Test
    fun `reconcile flags needsNumberLearning when liveNumber blank and fallback via IMSI`() {
        whenever(fleet.listGateways()).thenReturn(listOf(gateway))
        whenever(fleet.findGateway(gatewayId)).thenReturn(gateway)
        val livePorts = listOf(mapOf<String, Any?>(
            "index" to 2,
            "number" to null,
            "numberMasked" to null,
            "imsi" to "4210112345678",
            "imsiMasked" to "â€¢â€¢â€¢â€¢5678",
            "operator" to "Sabafon",
            "signalUsable" to true,
            "status" to "REGISTER_OK"
        ))
        whenever(hardware.getHardwareStatus(eq(gateway))).thenReturn(livePorts)
        val boundUser = makeUser(UUID.randomUUID(), "ddd", gatewayId, 2, "712068639")
        whenever(users.findAll()).thenReturn(listOf(boundUser))

        val result = controller.reconcile()
        @Suppress("UNCHECKED_CAST")
        val ports = result["ports"] as List<Map<String, Any?>>
        val p = ports.first()
        assertEquals(true, p["needsNumberLearning"])
        assertEquals("5678", p["imsiLast4"])
        assertNull(p["liveNumber"])
        assertEquals(false, p["mismatch"])
        assertEquals("OK", p["suggestedAction"])
        assertTrue((p["reason"] as String).contains("fallback IMSI"))
        @Suppress("UNCHECKED_CAST")
        val summary = result["summary"] as Map<String, Any?>
        assertEquals(1, summary["ok"])
        assertEquals(0, summary["mismatched"])
    }

    @Test
    fun `reconcile reports UNBOUND_HAS_SIM when port has SIM but no binding`() {
        whenever(fleet.listGateways()).thenReturn(listOf(gateway))
        whenever(fleet.findGateway(gatewayId)).thenReturn(gateway)
        val livePorts = listOf(mapOf<String, Any?>(
            "index" to 5,
            "number" to "712064924",
            "numberMasked" to "â€¢â€¢â€¢â€¢4924",
            "imsi" to "4210111114924",
            "imsiMasked" to "â€¢â€¢â€¢â€¢4924",
            "operator" to "Sabafon",
            "signalUsable" to true,
            "status" to "REGISTER_OK"
        ))
        whenever(hardware.getHardwareStatus(eq(gateway))).thenReturn(livePorts)
        whenever(users.findAll()).thenReturn(emptyList())

        val result = controller.reconcile()
        @Suppress("UNCHECKED_CAST")
        val ports = result["ports"] as List<Map<String, Any?>>
        val p = ports.first()
        assertEquals("UNBOUND_HAS_SIM", p["suggestedAction"])
        @Suppress("UNCHECKED_CAST")
        val summary = result["summary"] as Map<String, Any?>
        assertEquals(1, summary["unboundWithSim"])
        assertEquals(0, summary["boundPorts"])
    }

    @Test
    fun `reconcile ignores empty gateway without SIM evidence`() {
        whenever(fleet.listGateways()).thenReturn(listOf(gateway, emptyGateway))
        whenever(fleet.findGateway(gatewayId)).thenReturn(gateway)
        whenever(fleet.findGateway(emptyGatewayId)).thenReturn(emptyGateway)
        // gateway .2 has SIMs
        val livePortsSim = listOf(mapOf<String, Any?>(
            "index" to 0,
            "number" to null,
            "imsiMasked" to "â€¢â€¢â€¢â€¢1234",
            "operator" to "Sabafon",
            "signalUsable" to true,
            "status" to "REGISTER_OK"
        ))
        // empty gateway .3 has no SIM
        val emptyPorts = (0..7).map { idx -> mapOf<String, Any?>(
            "index" to idx,
            "number" to null,
            "numberMasked" to null,
            "imsi" to null,
            "imsiMasked" to null,
            "operator" to "UNKNOWN",
            "signalUsable" to false,
            "status" to "UNREGISTERED"
        )}
        whenever(hardware.getHardwareStatus(eq(gateway))).thenReturn(livePortsSim)
        whenever(hardware.getHardwareStatus(eq(emptyGateway))).thenReturn(emptyPorts)
        val boundUser = makeUser(UUID.randomUUID(), "ffd", gatewayId, 0, "712065805")
        whenever(users.findAll()).thenReturn(listOf(boundUser))

        val result = controller.reconcile()
        @Suppress("UNCHECKED_CAST")
        val ports = result["ports"] as List<Map<String, Any?>>
        // Only ports from SIM-loaded gateway should be counted
        assertEquals(1, ports.size)
        assertEquals("192.168.11.2", ports[0]["host"])
        @Suppress("UNCHECKED_CAST")
        val summary = result["summary"] as Map<String, Any?>
        assertEquals(1, summary["totalPorts"])
    }

    @Test
    fun `reconcile counts orphanBindings for bindings pointing to non-live ports`() {
        whenever(fleet.listGateways()).thenReturn(listOf(gateway))
        whenever(fleet.findGateway(gatewayId)).thenReturn(gateway)
        val livePorts = listOf(mapOf<String, Any?>(
            "index" to 0,
            "number" to "712065805",
            "imsiMasked" to "â€¢â€¢â€¢â€¢5805",
            "operator" to "Sabafon",
            "signalUsable" to true,
            "status" to "REGISTER_OK"
        ))
        whenever(hardware.getHardwareStatus(eq(gateway))).thenReturn(livePorts)
        // One binding on port 0 (live) and one orphan on port 7 which is not in livePorts
        val u1 = makeUser(UUID.randomUUID(), "ffd", gatewayId, 0, "712065805")
        val u2 = makeUser(UUID.randomUUID(), "orphan", gatewayId, 7, "712065999")
        whenever(users.findAll()).thenReturn(listOf(u1, u2))

        val result = controller.reconcile()
        @Suppress("UNCHECKED_CAST")
        val summary = result["summary"] as Map<String, Any?>
        assertEquals(1, summary["orphanBindings"])
    }

    @Test
    fun `discover alias includes learnable flag and instruction`() {
        whenever(fleet.listGateways()).thenReturn(listOf(gateway))
        whenever(fleet.findGateway(gatewayId)).thenReturn(gateway)
        val livePorts = listOf(mapOf<String, Any?>(
            "index" to 1,
            "number" to null,
            "imsiMasked" to "â€¢â€¢â€¢â€¢2242",
            "operator" to "Sabafon",
            "signalUsable" to true,
            "status" to "REGISTER_OK"
        ))
        whenever(hardware.getHardwareStatus(eq(gateway))).thenReturn(livePorts)
        val boundUser = makeUser(UUID.randomUUID(), "www", gatewayId, 1, "712065242")
        whenever(users.findAll()).thenReturn(listOf(boundUser))

        val result = controller.discover()
        @Suppress("UNCHECKED_CAST")
        val ports = result["ports"] as List<Map<String, Any?>>
        val p = ports.first()
        assertEquals(true, p["learnable"])
        assertNotNull(p["learnInstruction"])
        assertTrue((p["learnInstruction"] as String).contains("Phone Number Learning"))
        assertTrue(result.containsKey("learnInstruction"))
        assertEquals(true, result["anyLearnable"])
    }

    @Test
    fun `discover when live number present not learnable`() {
        whenever(fleet.listGateways()).thenReturn(listOf(gateway))
        whenever(fleet.findGateway(gatewayId)).thenReturn(gateway)
        val livePorts = listOf(mapOf<String, Any?>(
            "index" to 0,
            "number" to "712065805",
            "imsiMasked" to "â€¢â€¢â€¢â€¢5805",
            "operator" to "Sabafon",
            "signalUsable" to true,
            "status" to "REGISTER_OK"
        ))
        whenever(hardware.getHardwareStatus(eq(gateway))).thenReturn(livePorts)
        val boundUser = makeUser(UUID.randomUUID(), "ffd", gatewayId, 0, "712065805")
        whenever(users.findAll()).thenReturn(listOf(boundUser))

        val result = controller.discover()
        @Suppress("UNCHECKED_CAST")
        val ports = result["ports"] as List<Map<String, Any?>>
        assertEquals(false, ports[0]["learnable"])
        assertNull(ports[0]["learnInstruction"])
    }

    // â”€â”€ bulk rejects duplicate port within batch (422) â”€â”€

    @Test
    fun `bulk rejects duplicate port within batch`() {
        val actorId = UUID.randomUUID()
        val userId1 = UUID.randomUUID()
        val userId2 = UUID.randomUUID()
        val user1 = makeUser(userId1, "aaa", null, null, null).apply {
            // keep pstnNumber blank? but bulk will require number
            pstnNumber = "712065388"
        }
        val user2 = makeUser(userId2, "bbb", null, null, null).apply { pstnNumber = "712065999" }

        whenever(fleet.findGateway(gatewayId)).thenReturn(gateway)

        val body = mapOf<String, Any?>(
            "bindings" to listOf(
                mapOf("userId" to userId1.toString(), "gatewayId" to gatewayId.toString(), "portIndex" to 4, "number" to "712065388"),
                mapOf("userId" to userId2.toString(), "gatewayId" to gatewayId.toString(), "portIndex" to 4, "number" to "712065999")
            )
        )

        val response = controller.bulk(body, auth(actorId))
        assertEquals(422, response.statusCode.value())
        assertTrue((response.body?.get("error") as String).contains("BULK_VALIDATION_FAILED"))
        @Suppress("UNCHECKED_CAST")
        val details = response.body?.get("details") as List<Map<String, Any?>>
        assertTrue(details.any { it["error"] == "PORT_DUPLICATE_IN_BATCH" })
        verify(users, never()).save(any())
    }

    @Test
    fun `bulk rejects PORT_ALREADY_BOUND conflict with 422 and no save`() {
        val actorId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val existingId = UUID.randomUUID()
        val existingUser = makeUser(existingId, "existing", gatewayId, 1, "712065242")
        val requestingUser = makeUser(userId, "newuser", null, null, null).apply { pstnNumber = "712065242" }

        whenever(users.findById(userId)).thenReturn(Optional.of(requestingUser))
        whenever(fleet.findGateway(gatewayId)).thenReturn(gateway)
        whenever(users.findByPstnGatewayIdAndPstnPortIndex(gatewayId, 1)).thenReturn(existingUser)
        whenever(users.findAll()).thenReturn(emptyList())

        val body = mapOf<String, Any?>(
            "bindings" to listOf(
                mapOf("userId" to userId.toString(), "gatewayId" to gatewayId.toString(), "portIndex" to 1, "number" to "712065111")
            )
        )

        val response = controller.bulk(body, auth(actorId))
        assertEquals(422, response.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val details = response.body?.get("details") as List<Map<String, Any?>>
        assertTrue(details.any { it["error"] == "PORT_ALREADY_BOUND" })
        verify(users, never()).save(any())
    }

    @Test
    fun `bulk succeeds atomically and audits each`() {
        val actorId = UUID.randomUUID()
        val userId1 = UUID.randomUUID()
        val userId2 = UUID.randomUUID()
        val u1 = makeUser(userId1, "aaa", null, null, "712065388")
        val u2 = makeUser(userId2, "qqq", null, null, "712064924")
        // need to stub findById
        whenever(users.findById(userId1)).thenReturn(Optional.of(u1))
        whenever(users.findById(userId2)).thenReturn(Optional.of(u2))
        whenever(fleet.findGateway(gatewayId)).thenReturn(gateway)
        whenever(users.findByPstnGatewayIdAndPstnPortIndex(eq(gatewayId), any())).thenReturn(null)
        whenever(users.findByPstnNumber(any())).thenReturn(null)
        whenever(users.save(any())).thenAnswer { it.arguments[0] as UserAccount }

        val body = mapOf<String, Any?>(
            "bindings" to listOf(
                mapOf("userId" to userId1.toString(), "gatewayId" to gatewayId.toString(), "portIndex" to 4, "number" to "712065388"),
                mapOf("userId" to userId2.toString(), "gatewayId" to gatewayId.toString(), "portIndex" to 5, "number" to "712064924")
            )
        )

        val response = controller.bulk(body, auth(actorId))
        assertEquals(HttpStatus.OK, response.statusCode)
        @Suppress("UNCHECKED_CAST")
        val results = response.body?.get("results") as List<Map<String, Any?>>
        assertEquals(2, results.size)
        assertEquals(2, response.body?.get("count"))
        verify(users).save(eq(u1))
        verify(users).save(eq(u2))
        // audit each â€” 2 bindings = 2 audit records
        verify(audit, org.mockito.kotlin.times(2)).record(eq(actorId), eq("PSTN_SIM_BOUND"), any(), any())
    }

    @Test
    fun `bulk rejects duplicate number within batch`() {
        val actorId = UUID.randomUUID()
        val userId1 = UUID.randomUUID()
        val userId2 = UUID.randomUUID()
        val u1 = makeUser(userId1, "aaa", null, null, "712065388")
        val u2 = makeUser(userId2, "bbb", null, null, "712065999")
        whenever(users.findById(userId1)).thenReturn(Optional.of(u1))
        whenever(users.findById(userId2)).thenReturn(Optional.of(u2))
        whenever(fleet.findGateway(gatewayId)).thenReturn(gateway)
        whenever(users.findByPstnGatewayIdAndPstnPortIndex(eq(gatewayId), any())).thenReturn(null)
        whenever(users.findByPstnNumber(any())).thenReturn(null)

        val body = mapOf<String, Any?>(
            "bindings" to listOf(
                mapOf("userId" to userId1.toString(), "gatewayId" to gatewayId.toString(), "portIndex" to 2, "number" to "712065000"),
                mapOf("userId" to userId2.toString(), "gatewayId" to gatewayId.toString(), "portIndex" to 3, "number" to "712065000")
            )
        )

        val response = controller.bulk(body, auth(actorId))
        assertEquals(400, response.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val details = response.body?.get("details") as List<Map<String, Any?>>
        assertTrue(details.any { it["error"] == "NUMBER_DUPLICATE_IN_BATCH" })
        verify(users, never()).save(any())
    }

    @Test
    fun `bulk rejects when number already bound globally`() {
        val actorId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val otherUserId = UUID.randomUUID()
        val other = makeUser(otherUserId, "other", gatewayId, 6, "712065754")
        val requesting = makeUser(userId, "req", null, null, "712065000")
        whenever(users.findById(userId)).thenReturn(Optional.of(requesting))
        whenever(fleet.findGateway(gatewayId)).thenReturn(gateway)
        whenever(users.findByPstnGatewayIdAndPstnPortIndex(eq(gatewayId), any())).thenReturn(null)
        whenever(users.findByPstnNumber("712065754")).thenReturn(other)

        val body = mapOf<String, Any?>(
            "bindings" to listOf(
                mapOf("userId" to userId.toString(), "gatewayId" to gatewayId.toString(), "portIndex" to 2, "number" to "712065754")
            )
        )

        val response = controller.bulk(body, auth(actorId))
        assertEquals(400, response.statusCode.value())
        @Suppress("UNCHECKED_CAST")
        val details = response.body?.get("details") as List<Map<String, Any?>>
        assertTrue(details.any { it["error"] == "NUMBER_ALREADY_BOUND" })
    }

    // â”€â”€ V35 unique index file check â”€â”€

    @Test
    fun `V38 migration creates partial unique index on pstn_number`() {
        // Resolve migration file regardless of working directory
        val candidates = listOf(
            Paths.get("src/main/resources/db/migration/V38__Pstn_Number_Unique_And_Reconcile.sql"),
            Paths.get("backend-server/src/main/resources/db/migration/V38__Pstn_Number_Unique_And_Reconcile.sql"),
            Paths.get("C:/Users/hpc01/Pictures/pro_new/RED_Ultimate_V1-main/RED_Ultimate/backend-server/src/main/resources/db/migration/V38__Pstn_Number_Unique_And_Reconcile.sql")
        )
        val path = candidates.firstOrNull { Files.exists(it) }
            ?: fail("V35 migration file not found; checked: $candidates")
        val content = Files.readString(path)
        assertTrue(content.contains("ux_users_pstn_number"), "Index name ux_users_pstn_number must exist")
        assertTrue(content.contains("CREATE UNIQUE INDEX"), "Must be UNIQUE INDEX")
        assertTrue(content.contains("WHERE pstn_number IS NOT NULL"), "Must be partial WHERE IS NOT NULL")
        assertTrue(content.contains("users(pstn_number)"), "Must index users(pstn_number)")
        // Helper view is optional but should exist if we added it
        assertTrue(content.contains("v_pstn_reconcile") || content.contains("CREATE OR REPLACE VIEW"), "Helper view should be present")
    }

    @Test
    fun `reconcile ORPHAN detection when bound port has no SIM`() {
        whenever(fleet.listGateways()).thenReturn(listOf(gateway))
        whenever(fleet.findGateway(gatewayId)).thenReturn(gateway)
        val livePorts = listOf(mapOf<String, Any?>(
            "index" to 3,
            "number" to null,
            "imsi" to null,
            "imsiMasked" to null,
            "operator" to "UNKNOWN",
            "signalUsable" to false,
            "status" to "UNREGISTERED"
        ))
        whenever(hardware.getHardwareStatus(eq(gateway))).thenReturn(livePorts)
        val boundUser = makeUser(UUID.randomUUID(), "nmn", gatewayId, 3, "712065191")
        whenever(users.findAll()).thenReturn(listOf(boundUser))

        val result = controller.reconcile()
        @Suppress("UNCHECKED_CAST")
        val ports = result["ports"] as List<Map<String, Any?>>
        assertEquals("ORPHAN_BINDING_NEEDS_CLEAR", ports[0]["suggestedAction"])
        assertEquals(true, ports[0]["mismatch"])
        @Suppress("UNCHECKED_CAST")
        val summary = result["summary"] as Map<String, Any?>
        assertEquals(1, summary["orphanBindings"])
    }
}
