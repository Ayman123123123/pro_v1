package com.red.server.controllers

import com.red.server.services.DinstarFleetService
import com.red.server.services.DinstarHardwareService
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

/**
 * وحدة التحكم في المنافذ — Call Forward + Port Power + Port Reset.
 * 
 * تجميع لكل عمليات التحكم في المنافذ في endpoint واحد للعرض الشامل.
 * 
 * Endpoints:
 * GET  /api/admin/dinstar/port-control              — حالة كل المنافذ مع التحكم
 * GET  /api/admin/dinstar/port-control/{gwId}/{port} — حالة منفذ واحد تفصيلية
 */
@RestController
@RequestMapping("/api/admin/dinstar/port-control")
class PortControlController(
    private val fleet: DinstarFleetService,
    private val hardware: DinstarHardwareService,
    private val jdbc: JdbcTemplate
) {

    @GetMapping
    fun listPorts(): Map<String, Any?> {
        val gateways = fleet.listGateways(onlyEnabled = true)
        val allPorts = mutableListOf<Map<String, Any?>>()

        for (gateway in gateways) {
            val ports = try {
                hardware.getHardwareStatus(gateway)
            } catch (e: Exception) {
                emptyList()
            }

            // قراءة حالة الطاقة والتحويل من قاعدة البيانات
            val controlData = readPortControlData(gateway.id)

            ports.forEach { port ->
                val index = (port["index"] as? Number)?.toInt() ?: return@forEach
                val control = controlData[index] ?: emptyMap()
                allPorts.add(port + mapOf(
                    "gatewayId" to gateway.id.toString(),
                    "gatewayHost" to gateway.host,
                    "gatewayName" to gateway.name,
                    "powerState" to (control["powerState"] ?: true),
                    "callForwardState" to (control["callForwardState"] ?: "NONE"),
                    "callForwardNumber" to control["callForwardNumber"]
                ))
            }

            // إذا لم تُرجع البوابة منافذ، نضيف صفوفًا فارغة من السجل
            if (ports.isEmpty()) {
                for (i in 0 until gateway.portCount) {
                    val control = controlData[i] ?: emptyMap()
                    allPorts.add(mapOf(
                        "gatewayId" to gateway.id.toString(),
                        "gatewayHost" to gateway.host,
                        "gatewayName" to gateway.name,
                        "portIndex" to i,
                        "radioType" to "—",
                        "registrationState" to "UNKNOWN",
                        "callState" to "UNKNOWN",
                        "signalPercent" to null,
                        "signalDbm" to null,
                        "signalUsable" to false,
                        "operator" to "—",
                        "powerState" to (control["powerState"] ?: true),
                        "callForwardState" to (control["callForwardState"] ?: "NONE"),
                        "callForwardNumber" to control["callForwardNumber"]
                    ))
                }
            }
        }

        return mapOf("ports" to allPorts, "total" to allPorts.size)
    }

    @GetMapping("/{gatewayId}/{portIndex}")
    fun portDetail(
        @PathVariable gatewayId: UUID,
        @PathVariable portIndex: Int
    ): Map<String, Any?> {
        val gateway = fleet.findGateway(gatewayId) ?: throw IllegalArgumentException("Gateway not found")
        val ports = try {
            hardware.getHardwareStatus(gateway)
        } catch (e: Exception) {
            emptyList()
        }

        val port = ports.firstOrNull { (it["index"] as? Number)?.toInt() == portIndex }
            ?: return mapOf("error" to "Port not found", "gatewayId" to gatewayId, "portIndex" to portIndex)

        val controlData = readPortControlData(gatewayId)
        val control = controlData[portIndex] ?: emptyMap()

        return port + mapOf(
            "gatewayId" to gatewayId.toString(),
            "gatewayHost" to gateway.host,
            "gatewayName" to gateway.name,
            "powerState" to (control["powerState"] ?: true),
            "callForwardState" to (control["callForwardState"] ?: "NONE"),
            "callForwardNumber" to control["callForwardNumber"]
        )
    }

    /**
     * قراءة حالة التحكم (طاقة + تحويل) لكل منفذ في بوابة.
     * يُخزَّن في جدول port_control_state.
     */
    private fun readPortControlData(gatewayId: UUID): Map<Int, Map<String, Any?>> {
        return try {
            jdbc.query(
                """SELECT port_index, power_state, call_forward_state, call_forward_number
                   FROM port_control_state WHERE gateway_id=?""",
                { rs, _ ->
                    rs.getInt("port_index") to mapOf(
                        "powerState" to rs.getBoolean("power_state"),
                        "callForwardState" to (rs.getString("call_forward_state") ?: "NONE"),
                        "callForwardNumber" to rs.getString("call_forward_number")
                    )
                },
                gatewayId
            ).toMap()
        } catch (e: Exception) {
            // الجدول قد لا يكون موجودًا بعد
            emptyMap()
        }
    }
}
