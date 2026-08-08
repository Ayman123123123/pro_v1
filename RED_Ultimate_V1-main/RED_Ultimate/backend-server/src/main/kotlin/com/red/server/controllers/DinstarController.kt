package com.red.server.controllers

import com.red.server.audit.AuditService
import com.red.server.services.DinstarHardwareService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/admin/dinstar")
class DinstarController(private val hardware: DinstarHardwareService, private val audit: AuditService) {

    @GetMapping("/status")
    fun status() = hardware.getHardwareStatus()

    @GetMapping("/discover")
    fun discover() = hardware.discoverGateway()

    @GetMapping("/capabilities")
    fun capabilities() = hardware.capabilities()

    /** CDR — call detail records from the gateway (POST to gateway per Dinstar docs) */
    @GetMapping("/cdr")
    fun cdr() = hardware.queryCdr()

    @PostMapping("/ports/{port}/reset")
    fun resetPort(@PathVariable port: Int, authentication: Authentication): Map<String, Any> {
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
        return mapOf("port" to port, "status" to hardware.getHardwareStatus().find { it["index"] == port })
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
    ): Map<String, Any> {
        val param = body["param"] ?: throw IllegalArgumentException("param is required (Unconditional/NoReply/Busy/Not_Reachable/CancelAll)")
        val number = body["number"] ?: ""
        return hardware.setCallForward(port, param, number)
    }

    /** Power on/off port */
    @PostMapping("/ports/{port}/power")
    fun setPortPower(@PathVariable port: Int, @RequestBody body: Map<String, String>): Map<String, Any> {
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
