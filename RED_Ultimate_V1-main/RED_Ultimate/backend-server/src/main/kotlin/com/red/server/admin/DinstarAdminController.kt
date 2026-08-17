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
