package com.red.server.controllers

import com.red.server.audit.AuditService
import com.red.server.services.GatewaySimInventoryService
import com.red.server.services.GatewaySimInventoryUpdate
import com.red.server.services.SimVerificationMethod
import com.red.server.services.SimVerificationState
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

/**
 * وحدة التحكم في جرد شرائح SIM — إدارة تسميات الشرائح وحالة التحقق.
 * 
 *Endpoints:
 * GET  /api/admin/dinstar/sim-inventory                    — قائمة كل الشرائح
 * PUT  /api/admin/dinstar/sim-inventory/{gatewayId}/{port} — تحديث بيانات شريحة
 */
@RestController
@RequestMapping("/api/admin/dinstar/sim-inventory")
class SimInventoryController(
    private val inventory: GatewaySimInventoryService,
    private val audit: AuditService
) {

    @GetMapping
    fun list(): ResponseEntity<List<Map<String, Any?>>> {
        val ports = inventory.list().map { p ->
            mapOf(
                "gatewayId" to p.gatewayId,
                "gatewayName" to p.gatewayName,
                "gatewayModel" to p.gatewayModel,
                "gatewayHost" to p.gatewayHost,
                "portIndex" to p.portIndex,
                "radioType" to p.radioType,
                "registrationState" to p.registrationState,
                "callState" to p.callState,
                "signalPercent" to p.signalPercent,
                "operatorLabel" to p.operatorLabel,
                "simLabel" to p.simLabel,
                "verificationState" to p.verificationState.name,
                "verificationMethod" to p.verificationMethod?.name,
                "msisdnMasked" to p.msisdnMasked,
                "verifiedAt" to p.verifiedAt?.toString()
            )
        }
        return ResponseEntity.ok(ports)
    }

    @PutMapping("/{gatewayId}/{portIndex}")
    fun update(
        @PathVariable gatewayId: UUID,
        @PathVariable portIndex: Int,
        @RequestBody body: Map<String, Any?>,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any?>> {
        val actorId = UUID.fromString(authentication.name)
        
        val operatorLabel = body["operatorLabel"]?.toString()
        val simLabel = body["simLabel"]?.toString()
        val verificationState = (body["verificationState"]?.toString() ?: "UNKNOWN")
            .let { runCatching { SimVerificationState.valueOf(it) }.getOrDefault(SimVerificationState.UNKNOWN) }
        val verificationMethod = body["verificationMethod"]?.toString()?.let {
            runCatching { SimVerificationMethod.valueOf(it) }.getOrNull()
        }
        val lastFourDigits = body["lastFourDigits"]?.toString()?.takeIf { it.isNotBlank() && it.matches(Regex("^[0-9]{1,4}$")) }

        val update = GatewaySimInventoryUpdate(
            operatorLabel = operatorLabel,
            simLabel = simLabel,
            verificationState = verificationState,
            verificationMethod = verificationMethod,
            lastFourDigits = lastFourDigits
        )

        val result = inventory.update(gatewayId, portIndex, update, actorId)
        
        audit.record(actorId, "DINSTAR_SIM_INVENTORY_UPDATE", "$gatewayId/$portIndex", mapOf(
            "operatorLabel" to operatorLabel,
            "simLabel" to simLabel,
            "verificationState" to verificationState.name
        ))

        return ResponseEntity.ok(mapOf(
            "success" to true,
            "gatewayId" to result.gatewayId,
            "portIndex" to result.portIndex,
            "operatorLabel" to result.operatorLabel,
            "simLabel" to result.simLabel,
            "verificationState" to result.verificationState.name
        ))
    }
}
