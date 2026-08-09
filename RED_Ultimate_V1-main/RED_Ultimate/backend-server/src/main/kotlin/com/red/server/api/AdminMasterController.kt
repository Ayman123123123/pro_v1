package com.red.server.api

import com.red.server.infrastructure.dinstar.DinstarMasterClient
import com.red.server.audit.AuditService
import com.red.server.auth.RedApprovalService
import com.red.server.services.CoreService
import com.red.server.services.DinstarHardwareService
import org.springframework.web.bind.annotation.*
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import java.util.UUID

@RestController
@RequestMapping("/api/master/admin")
class AdminMasterController(
    private val dinstar: DinstarMasterClient,
    private val approval: RedApprovalService,
    private val core: CoreService,
    private val hardware: DinstarHardwareService,
    private val audit: AuditService
) {
    // [System A & C] إحصائيات المرور الحية
    @GetMapping("/system/stats")
    fun getGlobalStats() = ResponseEntity.ok(core.getAggregatedStats())

    // [System B] التحكم بالهاردوير DINSTAR
    @GetMapping("/hardware/dinstar/slots")
    fun getDinstarSlots() = ResponseEntity.ok(dinstar.getPortsRealtimeStatus())

    @PostMapping("/hardware/dinstar/action")
    fun executeDinstarAction(@RequestBody request: DinstarActionRequest, authentication: Authentication): ResponseEntity<Any> {
        val actor = UUID.fromString(authentication.name)
        val action = request.action.trim().uppercase()
        // Do not write phone numbers, SIP addresses, or other sensitive command data to audit logs.
        val details = mapOf("slot" to request.slot, "hasPhoneNumber" to !request.phoneNumber.isNullOrBlank(), "hasSipServer" to !request.sipServer.isNullOrBlank())
        val result: Any = try {
            when (action) {
                "DISCOVER" -> hardware.discoverGateway()
                "REBOOT" -> hardware.rebootDevice()
                "UPDATE_SIP" -> hardware.updateSipSettings(requireNotNull(request.sipServer) { "sipServer is required" })
                "DIAL" -> hardware.initiateCall(requireNotNull(request.phoneNumber) { "phoneNumber is required" }, request.slot ?: 0)
                else -> throw IllegalArgumentException("Unsupported DINSTAR action")
            }
        } catch (error: Exception) {
            audit.record(actor, "DINSTAR_ACTION_REJECTED", action, details + mapOf("errorType" to error.javaClass.simpleName))
            throw error
        }
        audit.record(actor, "DINSTAR_ACTION_EXECUTED", action, details)
        return ResponseEntity.ok(result)
    }

    // [Security] إدارة الحسابات والسيادة
    @GetMapping("/users/pending")
    fun getPendingUsers() = ResponseEntity.ok(approval.getPendingList())

    @PostMapping("/users/approve")
    fun approveUser(@RequestParam userId: String, authentication: Authentication) = ResponseEntity.ok(
        approval.processAction(
            UUID.fromString(userId),
            com.red.server.auth.model.AccountStatus.APPROVED,
            adminId = UUID.fromString(authentication.name)
        )
    )
}

data class DinstarActionRequest(
    val action: String,
    val phoneNumber: String? = null,
    val slot: Int? = null,
    val sipServer: String? = null
)
