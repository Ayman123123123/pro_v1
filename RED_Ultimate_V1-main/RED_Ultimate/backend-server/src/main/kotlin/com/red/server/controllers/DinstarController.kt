package com.red.server.controllers

import com.red.server.audit.AuditService
import com.red.server.services.DinstarHardwareService
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/admin/dinstar")
class DinstarController(
    private val hardware: DinstarHardwareService,
    private val audit: AuditService,
    private val jdbc: JdbcTemplate
) {

    @GetMapping("/status")
    fun status() = hardware.getHardwareStatus()

    /** جرد البوابات المسجلة في قاعدة البيانات (telecom_gateways). */
    @GetMapping("/inventory")
    fun inventory(): List<Map<String, Any>> = jdbc.query(
        "SELECT id, name, vendor, model, host, scheme, api_port, capabilities_json, last_seen_at FROM telecom_gateways ORDER BY created_at DESC"
    ) { rs, _ ->
        mapOf(
            "id" to rs.getString("id"),
            "name" to rs.getString("name"),
            "vendor" to rs.getString("vendor"),
            "model" to rs.getString("model"),
            "host" to rs.getString("host"),
            "scheme" to rs.getString("scheme"),
            "apiPort" to rs.getInt("api_port"),
            "capabilities" to rs.getString("capabilities_json"),
            "lastSeenAt" to rs.getTimestamp("last_seen_at")?.toInstant()?.toString()
        )
    }

    /** تحديث جرد شريحة في منفذ (SIM inventory) مع تسجيل العملية. */
    @PutMapping("/inventory/{gatewayId}/ports/{portIndex}")
    fun updateInventory(
        @PathVariable gatewayId: UUID,
        @PathVariable portIndex: Int,
        @RequestBody body: Map<String, Any?>,
        authentication: Authentication
    ): Map<String, Any> {
        require(portIndex in 0..31) { "portIndex must be 0-31" }
        val lastFourDigits = (body["lastFourDigits"] as? String)?.takeIf { it.matches(Regex("^\\d{4}$")) }
        val note = (body["note"] as? String)?.takeIf { it.length <= 200 }
        val updated = jdbc.update(
            "UPDATE gateway_port_snapshots SET sim_number_masked = COALESCE(?, sim_number_masked), note = COALESCE(?, note), observed_at = CURRENT_TIMESTAMP WHERE gateway_id = ? AND port_index = ?",
            lastFourDigits?.let { "••••$it" }, note, gatewayId, portIndex
        )
        if (updated == 0) {
            jdbc.update(
                "INSERT INTO gateway_port_snapshots(gateway_id, port_index, sim_number_masked, note, observed_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)",
                gatewayId, portIndex, lastFourDigits?.let { "••••$it" }, note
            )
        }
        audit.record(
            UUID.fromString(authentication.name), "DINSTAR_INVENTORY_UPDATED",
            gatewayId.toString(), mapOf("portIndex" to portIndex)
        )
        return mapOf("status" to "UPDATED", "gatewayId" to gatewayId.toString(), "portIndex" to portIndex)
    }

    @GetMapping("/discover")
    fun discover() = hardware.discoverGateway()

    @GetMapping("/capabilities")
    fun capabilities() = hardware.capabilities()

    /** CDR — call detail records from the gateway (POST to gateway per Dinstar docs) */
    @GetMapping("/cdr")
    fun cdr() = hardware.queryCdr()

    @PostMapping("/ports/{port}/reset")
    fun resetPort(@PathVariable port: Int, authentication: Authentication): Map<String, Any?> {
        val actor = UUID.fromString(authentication.name)
        val result = hardware.resetPort(port)
        hardware.recordOperation(actor, "PORT_MODULE_RESET", port, "SUCCEEDED")
        audit.record(actor, "DINSTAR_PORT_RESET", port.toString())
        return result
    }

    @PostMapping("/ports/{port}/ussd")
    fun sendUssd(@PathVariable port: Int, @RequestBody body: Map<String, String>, authentication: Authentication): Map<String, Any?> {
        val code = body["code"] ?: throw IllegalArgumentException("USSD code is required")
        val actor = UUID.fromString(authentication.name)
        val result = hardware.sendUssd(port, code)
        hardware.recordOperation(actor, "USSD_SENT", port, "SUCCEEDED", mapOf("codeLength" to code.length))
        audit.record(actor, "DINSTAR_USSD_SENT", port.toString(), mapOf("codeLength" to code.length))
        return result
    }

    @GetMapping("/ports/{port}/ussd")
    fun queryUssd(@PathVariable port: Int) = hardware.queryUssd(port)

    /** Query port info for a specific port */
    @GetMapping("/ports/{port}")
    fun getPortInfo(@PathVariable port: Int, authentication: Authentication): Map<String, Any> {
        val actor = UUID.fromString(authentication.name)
        audit.record(actor, "DINSTAR_PORT_INFO", port.toString())
        val portInfo = hardware.getHardwareStatus().find { it["index"] == port }
        val status = portInfo ?: mapOf("error" to "Port not found")
        return mapOf("port" to port, "status" to status)
    }

    /** Explicitly disabled until the exact firmware exposes a documented operation. */
    @PostMapping("/reboot")
    fun reboot(): Nothing = hardware.rebootDevice()

    @PostMapping("/config/sip")
    fun updateSip(@RequestBody data: Map<String, String>): Nothing =
        hardware.updateSipSettings(data["sip_ip"].orEmpty())

    /** Call Forward — set or check */
    @PostMapping("/ports/{port}/callforward")
    fun setCallForward(
        @PathVariable port: Int,
        @RequestBody body: Map<String, String>,
        authentication: Authentication
    ): Map<String, Any?> {
        val param = body["param"] ?: throw IllegalArgumentException("param is required (Unconditional/NoReply/Busy/Not_Reachable/CancelAll)")
        val number = body["number"] ?: ""
        return hardware.setCallForward(port, param, number)
    }

    /** Power on/off port */
    @PostMapping("/ports/{port}/power")
    fun setPortPower(@PathVariable port: Int, @RequestBody body: Map<String, String>): Map<String, Any?> {
        val on = body["on"]?.toBoolean() ?: true
        return hardware.setPortPower(port, on)
    }

    /** Device status — POST /api/get_status */
    @GetMapping("/device-status")
    fun deviceStatus(): Map<String, Any?> = hardware.getDeviceStatus()

    /** Voice always follows the authorized Asterisk route. */
    @PostMapping("/dial")
    fun directDial() = ResponseEntity.status(410).body(
        mapOf("error" to "USE_AUTHORIZED_PSTN_CALL_API", "route" to "Backend → Asterisk → DINSTAR")
    )
}
