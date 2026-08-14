package com.red.server.admin

import com.red.server.services.DinstarFleetService
import com.red.server.services.DinstarHardwareService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/admin/dinstar")
class DinstarAdminController(
    private val fleet: DinstarFleetService,
    private val hardware: DinstarHardwareService
) {

    @GetMapping("/fleet/ports")
    fun getFleetStatus(): ResponseEntity<Map<String, Any>> {
        val gateways = fleet.listGateways()
        val statuses = gateways.map { gw ->
            val portsResult = runCatching { hardware.getHardwareStatus(gw) }
            val ports = portsResult.getOrElse { emptyList() }
            val error = portsResult.exceptionOrNull()?.message
            
            mapOf(
                "gateway" to mapOf(
                    "id" to gw.id,
                    "host" to gw.host,
                    "name" to (gw.siteLabel ?: "بوابة ${gw.host}"),
                    "healthState" to if (ports.isNotEmpty()) "ONLINE" else "OFFLINE",
                    "model" to "UC2000",
                    "firmwareVersion" to "Unknown"
                ),
                "ports" to ports,
                "error" to error
            )
        }
        
        return ResponseEntity.ok(mapOf("gateways" to statuses))
    }
    
    @PostMapping("/discover")
    fun discoverFleet(): ResponseEntity<Map<String, Any>> {
        val discovered = fleet.discoverFleet()
        val adopted = fleet.adoptDiscovered(discovered, "Auto-Discovered")
        return ResponseEntity.ok(mapOf(
            "discoveredCount" to discovered.size,
            "adoptedCount" to adopted.size,
            "discovered" to discovered
        ))
    }
    
    @GetMapping("/route-decisions")
    fun getRouteDecisions(): ResponseEntity<List<Map<String, Any?>>> {
        return ResponseEntity.ok(fleet.recentRouteDecisions(100))
    }
}
