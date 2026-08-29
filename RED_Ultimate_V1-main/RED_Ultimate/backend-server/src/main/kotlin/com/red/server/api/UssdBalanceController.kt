package com.red.server.api

import com.red.server.services.DinstarFleetService
import com.red.server.services.DinstarHardwareService
import com.red.server.services.GatewaySimInventoryService
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/admin/dinstar/ussd")
class UssdBalanceController(
    private val fleetService: DinstarFleetService,
    private val hardwareService: DinstarHardwareService,
    private val inventoryService: GatewaySimInventoryService
) {
    @GetMapping("/balance")
    fun checkBalanceLive(
        @RequestParam gatewayId: UUID, 
        @RequestParam port: Int, 
        @RequestParam code: String
    ): Map<String, Any?> {
        val gateway = fleetService.findGateway(gatewayId)
            ?: throw IllegalArgumentException("Gateway not found")
            
        // 1. Send USSD
        hardwareService.sendUssd(gateway, port, code)
        
        // 2. Poll for the USSD reply
        var result: Map<String, Any?>? = null
        for (i in 1..6) { // Poll every 3s for up to 18s
            Thread.sleep(3000)
            val replyResponse = hardwareService.queryUssdReply(gateway, port)
            
            @Suppress("UNCHECKED_CAST")
            val ports = replyResponse["result"] as? List<Map<String, Any?>> ?: continue
            val portData = ports.find { (it["port"] as? Number)?.toInt() == port }
            
            if (portData != null && portData["reply"] != null && portData["reply"].toString().isNotBlank()) {
                result = portData
                break
            }
        }
        
        return mapOf(
            "gateway" to gateway.name,
            "port" to port,
            "code" to code,
            "result" to (result ?: mapOf("error" to "No reply received from network within timeout"))
        )
    }
}
