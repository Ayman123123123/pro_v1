package com.red.server.controllers

import com.red.server.audit.AuditService
import com.red.server.services.GatewaySimInventoryService
import com.red.server.services.GatewaySimInventoryUpdate
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/admin/dinstar/inventory")
class GatewaySimInventoryController(
    private val inventory: GatewaySimInventoryService,
    private val audit: AuditService
) {
    @GetMapping
    fun list() = inventory.list()

    @PutMapping("/{gatewayId}/ports/{portIndex}")
    fun update(
        @PathVariable gatewayId: UUID,
        @PathVariable portIndex: Int,
        @RequestBody request: GatewaySimInventoryUpdate,
        authentication: Authentication
    ): ResponseEntity<Any> {
        val actor = UUID.fromString(authentication.name)
        val result = inventory.update(gatewayId, portIndex, request, actor)
        audit.record(
            actor,
            "SIM_INVENTORY_UPDATED",
            "$gatewayId:$portIndex",
            mapOf(
                "operatorLabelSet" to !request.operatorLabel.isNullOrBlank(),
                "simLabelSet" to !request.simLabel.isNullOrBlank(),
                "verificationState" to request.verificationState.name,
                "verificationMethod" to request.verificationMethod?.name,
                "lastFourDigitsSet" to !request.lastFourDigits.isNullOrBlank()
            )
        )
        return ResponseEntity.ok(result)
    }
}
