package com.red.server.controllers

import com.red.server.audit.AuditService
import com.red.server.pstn.PstnCallService
import com.red.server.services.DinstarFleetService
import com.red.server.services.DinstarHardwareService
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/admin/dinstar")
class DinstarController(
    private val hardware: DinstarHardwareService,
    private val audit: AuditService,
    private val calls: PstnCallService,
    private val fleet: DinstarFleetService,
    private val jdbc: JdbcTemplate
) {

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
    fun resetPort(@PathVariable port: Int, authentication: Authentication): Map<String, Any?> {
        val actor = UUID.fromString(authentication.name)
        val result = hardware.resetPort(port)
        hardware.recordOperation(actor, "PORT_MODULE_RESET", port, "SUCCEEDED")
        audit.record(actor, "DINSTAR_PORT_RESET", port.toString())
        return result
    }

    /**
     * إرسال كود USSD على منفذ بعينه.
     *
     * `gatewayId` اختياري: بدونه يُستخدم الجهاز النشط. مع أسطول متعدد
     * البوابات فهرس المنفذ وحده غامض — المنفذ 3 شريحة مختلفة على كل جهاز،
     * فاستعلام الرصيد كان يذهب إلى شريحة غير المقصودة صامتًا.
     */
    @PostMapping("/ports/{port}/ussd")
    fun sendUssd(@PathVariable port: Int, @RequestBody body: Map<String, String>, authentication: Authentication): Map<String, Any?> {
        val code = body["code"] ?: throw IllegalArgumentException("USSD code is required")
        val actor = UUID.fromString(authentication.name)
        val gateway = body["gatewayId"]
            ?.takeIf { it.isNotBlank() }
            ?.let { raw ->
                val id = runCatching { UUID.fromString(raw) }.getOrNull()
                    ?: throw IllegalArgumentException("gatewayId is not a valid UUID")
                fleet.findGateway(id) ?: throw NoSuchElementException("Gateway $id is not registered")
            }
        val result = if (gateway != null) hardware.sendUssd(gateway, port, code) else hardware.sendUssd(port, code)
        hardware.recordOperation(actor, "USSD_SENT", port, "SUCCEEDED", mapOf("codeLength" to code.length))
        audit.record(actor, "DINSTAR_USSD_SENT", port.toString(), mapOf(
            "codeLength" to code.length, "gateway" to (gateway?.host ?: "active")
        ))
        // Persistent USSD log: gateway buffer is volatile, DB survives
        try {
            jdbc.update(
                "INSERT INTO dinstar_ussd_log(gateway_id,port_index,ussd_code,response_text,status) VALUES (?,?,?,?,?)",
                gateway?.id, port, code, result["response_text"]?.toString(), result["status"]?.toString() ?: "SENT"
            )
        } catch (_: Exception) {}
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

    /**
     * إنشاء قاعدة «تعلّم رقم الشريحة» لمنفذ بعينه.
     *
     * كانت اللوحة تنادي هذا المسار وتتلقّى 404 (`No static resource
     * api/admin/dinstar/ports/{port}/learning`) لأنه لم يكن معرَّفًا إطلاقًا،
     * فكان زر «تعلّم الرقم» صامتًا بلا أي أثر على الجهاز.
     *
     * ## التكلفة — لهذا المسار POST صريح لا جدولة
     * النمط `SMS` يُرسل رسالة مدفوعة (سبأفون: 10 YER لطلب `MMN` إلى `333`).
     * لذلك لا يُستدعى إلا بطلب صريح من مسؤول، ويُسجَّل في التدقيق.
     *
     * الجسم كله اختياري؛ الافتراضي هو الطريق الموثَّق لسبأفون:
     * ```json
     * {"method":"SMS","destination":"333","text":"MMN","writeToSim":true}
     * ```
     */
    @PostMapping("/ports/{port}/learning")
    fun triggerNumberLearning(
        @PathVariable port: Int,
        @RequestBody(required = false) body: Map<String, Any?>?,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any?>> {
        val actor = UUID.fromString(authentication.name)
        val method = (body?.get("method") as? String)?.trim()?.uppercase()
            ?.let { name ->
                DinstarHardwareService.NumberLearningMethod.entries.firstOrNull { it.name == name }
                    ?: return ResponseEntity.badRequest().body(
                        mapOf(
                            "error" to "INVALID_METHOD",
                            "allowed" to DinstarHardwareService.NumberLearningMethod.entries.map { it.name }
                        )
                    )
            }
            ?: DinstarHardwareService.NumberLearningMethod.SMS

        val accepted = try {
            hardware.triggerNumberLearning(
                port = port,
                method = method,
                destination = (body?.get("destination") as? String)?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: DinstarHardwareService.SABAFON_MMN_SHORTCODE,
                text = (body?.get("text") as? String)
                    ?: DinstarHardwareService.SABAFON_MMN_KEYWORD,
                expectedSender = (body?.get("expectedSender") as? String).orEmpty(),
                keywords = (body?.get("keywords") as? String).orEmpty(),
                writeToSim = (body?.get("writeToSim") as? Boolean) ?: true,
                stripFromLeft = (body?.get("stripFromLeft") as? Number)?.toInt() ?: 0,
                addPrefix = (body?.get("addPrefix") as? String).orEmpty()
            )
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "INVALID_REQUEST")))
        }

        val status = if (accepted) "SUCCEEDED" else "FAILED"
        hardware.recordOperation(
            actor, "NUMBER_LEARNING_RULE", port, status, mapOf("method" to method.name)
        )
        audit.record(actor, "DINSTAR_NUMBER_LEARNING", port.toString(), mapOf("method" to method.name))

        return ResponseEntity.ok(
            mapOf(
                "port" to port,
                "method" to method.name,
                "accepted" to accepted,
                // الجهاز يقبل القاعدة فورًا لكن الرقم يظهر بعد رد المشغّل
                "note" to "القاعدة أُنشئت على البوابة؛ الرقم يظهر في get_port_info بعد رد المشغّل"
            )
        )
    }

    @PostMapping("/calls")
    fun adminDial(
        @RequestBody body: Map<String, Any?>,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any?>> {
        val actor = java.util.UUID.fromString(authentication.name)
        val number = body["number"]?.toString()?.trim()
            ?: return ResponseEntity.badRequest().body(mapOf("error" to "NUMBER_REQUIRED"))
        val gatewayHost = body["gatewayHost"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val portIndex = (body["portIndex"] as? Number)?.toInt()
        val callerNumber = body["callerNumber"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        // الافتراضي مكالمة مجسّرة حقيقية. `bridge=false` يُبقي وضع الاختبار
        // القديم (إشغال المنفذ ثانية واحدة بلا صوت) لمن يريد فحص المسار فقط.
        val bridge = when (val raw = body["bridge"]) {
            null -> true
            is Boolean -> raw
            else -> raw.toString().toBooleanStrictOrNull() ?: true
        }
        return try {
            val result = calls.dialAsAdmin(actor, number, gatewayHost, portIndex, callerNumber, bridge)
            audit.record(actor, "DINSTAR_ADMIN_CALL", result.callId, mapOf(
                "number" to number, "gatewayHost" to (gatewayHost ?: "auto"),
                "portIndex" to (portIndex ?: -1), "callerNumber" to (callerNumber ?: "port-default"),
                "selectedPort" to result.slot, "bridge" to bridge
            ))
            hardware.recordOperation(actor, "ADMIN_CALL", result.slot, "SUCCEEDED", mapOf("callId" to result.callId))
            ResponseEntity.ok(mapOf(
                "callId" to result.callId, "status" to result.status,
                "number" to result.number, "port" to result.slot,
                "bridged" to bridge,
                "route" to if (bridge) "Admin WebRTC <-> Asterisk <-> PJSIP <-> DINSTAR <-> GSM"
                           else "Backend -> Asterisk -> PJSIP -> DINSTAR (no audio path)"
            ))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "INVALID_REQUEST")))
        } catch (e: IllegalStateException) {
            audit.record(actor, "DINSTAR_ADMIN_CALL_FAILED", number, mapOf("reason" to (e.message ?: "UNAVAILABLE")))
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(mapOf("error" to (e.message ?: "PORT_UNAVAILABLE")))
        }
    }

    /** Device status — POST /api/get_status */
    @GetMapping("/device-status")
    fun deviceStatus(): Map<String, Any?> = hardware.getDeviceStatus()

    @GetMapping("/cdr/export")
    fun cdrExport(): ResponseEntity<ByteArray> {
        val raw = hardware.queryCdr()
        val list = (raw["cdr"] as? List<Map<String, Any?>>) ?: (raw["query"] as? List<Map<String, Any?>>) ?: emptyList()
        val csv = buildString {
            appendLine("port,direction,source_number,destination_number,start_date,duration,call_result")
            list.forEach { r ->
                fun esc(v: Any?) = "\"${(v?.toString() ?: "").replace("\"", "\"\"")}\""
                appendLine(listOf(r["port"], r["direction"], esc(r["source_number"]), esc(r["destination_number"]), r["start_date"], r["duration"], esc(r["call_result"])).joinToString(","))
            }
        }.toByteArray(Charsets.UTF_8)
        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=\"cdr-${java.time.LocalDate.now()}.csv\"")
            .header("Content-Type", "text/csv; charset=utf-8")
            .body(csv)
    }

    /** Voice always follows the authorized Asterisk route. */
    @PostMapping("/dial")
    fun directDial() = ResponseEntity.status(410).body(
        mapOf("error" to "USE_AUTHORIZED_PSTN_CALL_API", "route" to "Backend → Asterisk → DINSTAR")
    )
}
