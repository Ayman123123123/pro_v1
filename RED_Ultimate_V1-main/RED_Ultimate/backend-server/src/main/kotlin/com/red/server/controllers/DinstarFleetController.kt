package com.red.server.controllers

import com.red.server.audit.AuditService
import com.red.server.auth.model.AccountStatus
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.pstn.DinstarLoadBalancer
import com.red.server.services.DinstarFleetService
import com.red.server.services.DinstarHardwareService
import com.red.server.services.DinstarModelProfile
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/admin/dinstar/fleet")
class DinstarFleetController(
    private val fleet: DinstarFleetService,
    private val hardware: DinstarHardwareService,
    private val loadBalancer: DinstarLoadBalancer,
    private val audit: AuditService,
    private val users: UserAccountRepository
) {

    /**
     * خريطة المنفذ ← الحساب المالك (الربط الدائم 1:1). تُجلب مرة لكل بوابة
     * لتجنب استعلام لكل منفذ، وتُلحق بكل منفذ في واجهات العرض كي تعرض لوحة
     * الإدارة فوراً «من يملك هذه الشريحة».
     */
    private fun boundUsersByPort(gatewayId: UUID): Map<Int, com.red.server.auth.model.UserAccount> =
        users.findByPstnGatewayId(gatewayId)
            .filter { it.pstnPortIndex != null }
            .associateBy { it.pstnPortIndex!! }

    private fun annotateOwner(portMap: Map<String, Any?>, owner: com.red.server.auth.model.UserAccount?): Map<String, Any?> =
        if (owner == null) portMap else portMap + mapOf(
            "boundRedId" to owner.redId,
            "boundUsername" to owner.username
        )

    @GetMapping
    fun list(): List<Map<String, Any?>> = fleet.listGateways().map(::present)

    @GetMapping("/models")
    fun models(): List<Map<String, Any>> = DinstarModelProfile.entries.map { it.metadata() }

    @PostMapping("/discover")
    fun discover(
        @RequestBody(required = false) body: Map<String, Any?>?,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any?>> {
        val actor = UUID.fromString(authentication.name)
        @Suppress("UNCHECKED_CAST")
        val subnets = (body?.get("subnets") as? List<String>).orEmpty()
        val adopt = body?.get("adopt") as? Boolean ?: false
        val site = body?.get("siteLabel") as? String

        val found = fleet.discoverFleet(subnets)
        val adopted = if (adopt) fleet.adoptDiscovered(found, site) else emptyList()

        audit.record(actor, "DINSTAR_FLEET_DISCOVER", subnets.joinToString(","),
            mapOf("found" to found.size, "adopted" to adopted.size))

        return ResponseEntity.ok(mapOf(
            "scannedSubnets" to subnets,
            "found" to found.map {
                mapOf(
                    "host" to it.host, "apiPort" to it.apiPort, "scheme" to it.scheme,
                    "model" to it.model, "portCount" to it.portCount,
                    "serialNumber" to it.serialNumber, "firmwareVersion" to it.firmwareVersion,
                    "registeredPorts" to it.registeredPorts
                )
            },
            "adoptedIds" to adopted
        ))
    }

    @PostMapping("/probe")
    fun probe(@RequestBody body: Map<String, Any?>): ResponseEntity<Map<String, Any?>> {
        val host = (body["host"] as? String)?.trim().orEmpty()
        require(host.isNotBlank()) { "host is required" }
        val port = (body["apiPort"] as? Number)?.toInt() ?: 443
        val scheme = (body["scheme"] as? String) ?: "https"

        val result = fleet.probeHost(host, port, scheme)
            ?: return ResponseEntity.ok(mapOf(
                "reachable" to false,
                "message" to "No response from gateway"
            ))

        return ResponseEntity.ok(mapOf(
            "reachable" to true, "host" to result.host, "model" to result.model,
            "portCount" to result.portCount, "serialNumber" to result.serialNumber,
            "firmwareVersion" to result.firmwareVersion, "registeredPorts" to result.registeredPorts
        ))
    }

    @PostMapping
    fun register(
        @RequestBody body: Map<String, Any?>,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any?>> {
        val actor = UUID.fromString(authentication.name)
        val host = (body["host"] as? String)?.trim().orEmpty()
        require(host.isNotBlank()) { "host is required" }
        val model = (body["model"] as? String) ?: "UC2000-VE-8G"
        val profile = DinstarModelProfile.parse(model)

        val id = fleet.upsertGateway(
            host = host,
            apiPort = (body["apiPort"] as? Number)?.toInt() ?: 443,
            scheme = (body["scheme"] as? String) ?: "https",
            model = profile.modelId,
            portCount = (body["portCount"] as? Number)?.toInt() ?: profile.portCount,
            name = (body["name"] as? String) ?: "DINSTAR ${profile.modelId} @ $host",
            pjsipEndpoint = body["pjsipEndpoint"] as? String,
            siteLabel = body["siteLabel"] as? String,
            routingPriority = (body["routingPriority"] as? Number)?.toInt() ?: 100,
            discoveryMethod = "MANUAL"
        )
        audit.record(actor, "DINSTAR_GATEWAY_REGISTERED", id.toString(), mapOf("host" to host, "model" to profile.modelId))
        return ResponseEntity.status(201).body(mapOf("id" to id, "host" to host, "model" to profile.modelId))
    }

    @PostMapping("/{id}/enabled")
    fun setEnabled(
        @PathVariable id: UUID,
        @RequestBody body: Map<String, Any?>,
        authentication: Authentication
    ): Map<String, Any?> {
        val actor = UUID.fromString(authentication.name)
        val enabled = body["enabled"] as? Boolean ?: true
        requireNotNull(fleet.findGateway(id)) { "Gateway not found" }
        fleet.setEnabled(id, enabled)
        audit.record(actor, if (enabled) "DINSTAR_GATEWAY_ENABLED" else "DINSTAR_GATEWAY_DISABLED", id.toString())
        return mapOf("id" to id, "enabled" to enabled)
    }

    @DeleteMapping("/{id}")
    fun remove(@PathVariable id: UUID, authentication: Authentication): Map<String, Any?> {
        val actor = UUID.fromString(authentication.name)
        requireNotNull(fleet.findGateway(id)) { "Gateway not found" }
        fleet.removeGateway(id)
        audit.record(actor, "DINSTAR_GATEWAY_REMOVED", id.toString())
        return mapOf("id" to id, "removed" to true)
    }

    @GetMapping("/{id}/ports")
    fun ports(@PathVariable id: UUID): List<Map<String, Any?>> {
        val gateway = requireNotNull(fleet.findGateway(id)) { "Gateway not found" }
        val owners = boundUsersByPort(id)
        return runCatching { hardware.getHardwareStatus(gateway) }
            .onSuccess { fleet.markHealthy(id) }
            .onFailure { fleet.markFailure(id, it.message ?: "port query failed") }
            .getOrThrow()
            .map { p ->
                val idx = (p["index"] as? Number)?.toInt()
                annotateOwner(p, idx?.let { owners[it] })
            }
    }

    @GetMapping("/ports")
    fun allPorts(): Map<String, Any?> {
        val gateways = fleet.listGateways(onlyEnabled = true)
        val perGateway = gateways.map { gw ->
            val owners = boundUsersByPort(gw.id)
            val result = runCatching { hardware.getHardwareStatus(gw) }
            if (result.isSuccess) fleet.markHealthy(gw.id)
            else fleet.markFailure(gw.id, result.exceptionOrNull()?.message ?: "unreachable")
            mapOf(
                "gateway" to present(gw),
                "ports" to result.getOrDefault(emptyList()).map { p ->
                    val idx = (p["index"] as? Number)?.toInt()
                    annotateOwner(p, idx?.let { owners[it] })
                },
                "error" to result.exceptionOrNull()?.message
            )
        }
        val allPorts = perGateway.flatMap {
            @Suppress("UNCHECKED_CAST")
            (it["ports"] as List<Map<String, Any?>>)
        }
        return mapOf(
            "gateways" to perGateway,
            "totals" to mapOf(
                "gateways" to gateways.size,
                "online" to gateways.count { it.healthState == "ONLINE" },
                "ports" to allPorts.size,
                "registered" to allPorts.count {
                    // القيمة الخام من الجهاز قد تكون REGISTER_OK أو
                    // REGISTERED/Mobile Registered — كلها مسجّل.
                    val s = it["status"]?.toString()?.trim().orEmpty()
                    s.equals("REGISTERED", true) || s.equals("REGISTER_OK", true) ||
                        s.equals("Mobile Registered", true)
                },
                "usable" to allPorts.count { it["signalUsable"] == true }
            )
        )
    }

    @PostMapping("/routing/select")
    fun previewRouting(@RequestBody body: Map<String, Any?>): ResponseEntity<Map<String, Any?>> {
        val number = (body["number"] as? String)?.trim().orEmpty()
        require(number.isNotBlank()) { "number is required" }

        val selection = loadBalancer.selectPort(number)
            ?: return ResponseEntity.status(503).body(mapOf(
                "error" to "NO_USABLE_PORT",
                "message" to "No usable port in fleet"
            ))

        return ResponseEntity.ok(mapOf(
            "selected" to mapOf(
                "gatewayId" to selection.gatewayId,
                "gatewayHost" to selection.gatewayHost,
                "pjsipEndpoint" to selection.pjsipEndpoint,
                "portIndex" to selection.portIndex,
                "operator" to selection.operator,
                "signalDbm" to selection.signalDbm,
                "score" to selection.score,
                "onNet" to selection.reason.contains("on-net")
            ),
            "targetOperator" to loadBalancer.classifyOperator(number)?.apiName,
            "reason" to selection.reason
        ))
    }

    @GetMapping("/routing/decisions")
    fun routingDecisions(): List<Map<String, Any?>> = fleet.recentRouteDecisions()

    private fun present(g: DinstarFleetService.Gateway): Map<String, Any?> = mapOf(
        "id" to g.id, "name" to g.name, "model" to g.model, "host" to g.host,
        "scheme" to g.scheme, "apiPort" to g.apiPort, "portCount" to g.portCount,
        "enabled" to g.enabled, "healthState" to g.healthState,
        "routingPriority" to g.routingPriority, "pjsipEndpoint" to g.pjsipEndpoint,
        "serialNumber" to g.serialNumber, "firmwareVersion" to g.firmwareVersion,
        "siteLabel" to g.siteLabel, "consecutiveFailures" to g.consecutiveFailures
    )
}
