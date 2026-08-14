package com.red.server.services

import com.red.server.websocket.DinstarWebSocketHandler
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * خدمة API شاملة لـ DINSTAR
 * تتعامل مع جميع عمليات HTTP API الموثقة
 */
@Service
class DinstarApiService(
    private val jdbc: JdbcTemplate,
    private val hardware: DinstarHardwareService,
    private val fleet: DinstarFleetService,
    private val webSocketHandler: DinstarWebSocketHandler,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(DinstarApiService::class.java)

    /**
     * جلب حالة الجهاز (CPU, Memory, Flash)
     * POST /api/get_status
     */
    fun getDeviceStatus(gatewayHost: String? = null): Map<String, Any?> {
        return try {
            val gateway = gatewayHost?.let { fleet.findGatewayByHost(it) } ?: fleet.getDefaultGateway()
            if (gateway == null) {
                return mapOf("error" to "No gateway available")
            }

            val status = hardware.getDeviceStatus(gateway)
            
            // حفظ في قاعدة البيانات
            saveDeviceStatus(gateway.id, status)
            
            webSocketHandler.broadcastDeviceStatus(gateway.id, status)
            
            status
        } catch (e: Exception) {
            log.error("Error getting device status", e)
            mapOf("error" to e.message)
        }
    }

    private fun saveDeviceStatus(gatewayId: UUID, status: Map<String, Any?>) {
        try {
            jdbc.update("""
                INSERT INTO dinstar_device_status 
                (gateway_id, cpu_used, memory_total, memory_used, memory_free, 
                 flash_total, flash_used, flash_free, temperature, uptime, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (gateway_id) DO UPDATE SET
                cpu_used = EXCLUDED.cpu_used,
                memory_total = EXCLUDED.memory_total,
                memory_used = EXCLUDED.memory_used,
                memory_free = EXCLUDED.memory_free,
                flash_total = EXCLUDED.flash_total,
                flash_used = EXCLUDED.flash_used,
                flash_free = EXCLUDED.flash_free,
                temperature = EXCLUDED.temperature,
                uptime = EXCLUDED.uptime,
                updated_at = NOW()
            """,
                gatewayId,
                status["cpu_used"],
                status["memory_total"],
                status["memory_used"],
                status["memory_free"],
                status["flash_total"],
                status["flash_used"],
                status["flash_free"],
                status["temperature"],
                status["uptime"]
            )
        } catch (e: Exception) {
            log.error("Error saving device status", e)
        }
    }

    /**
     * جلب سجل المكالمات CDR
     * POST /api/get_cdr
     */
    fun getCdrRecords(
        gatewayHost: String? = null,
        port: Int? = null,
        timeAfter: String? = null,
        timeBefore: String? = null
    ): List<Map<String, Any?>> {
        return try {
            val gateway = gatewayHost?.let { fleet.findGatewayByHost(it) } ?: fleet.getDefaultGateway()
            if (gateway == null) {
                return emptyList()
            }

            val cdrList = hardware.getCdrRecords(gateway, port, timeAfter, timeBefore)
            
            // حفظ في قاعدة البيانات
            cdrList.forEach { cdr ->
                saveCdrRecord(gateway.id, cdr)
            }
            
            cdrList
        } catch (e: Exception) {
            log.error("Error getting CDR records", e)
            emptyList()
        }
    }

    private fun saveCdrRecord(gatewayId: UUID, cdr: Map<String, Any?>) {
        try {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            fun parseTime(key: String): LocalDateTime? = (cdr[key] as? String)
                ?.let { raw -> runCatching { LocalDateTime.parse(raw, formatter) }.getOrNull() }
            val startTime = parseTime("start_time") ?: LocalDateTime.now()
            val answerTime = parseTime("answer_time")
            val endTime = parseTime("end_time")
            val duration = (cdr["duration"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0
            val caller = cdr["caller_number"]?.toString().orEmpty().take(30)
            val callee = cdr["callee_number"]?.toString().orEmpty().take(30)
            val direction = cdr["direction"]?.toString()?.lowercase()
                ?.takeIf { it == "inbound" || it == "outbound" }
                ?: "inbound"
            val status = cdr["status"]?.toString()?.lowercase()
                ?.replace(' ', '_')
                ?.takeIf { it in setOf("answered", "no_answer", "busy", "failed", "cancelled") }
                ?: if (answerTime != null || duration > 0) "answered" else "failed"
            val externalCallId = (cdr["sip_call_id"] ?: cdr["call_id"] ?: cdr["id"])
                ?.toString()?.take(100)
            // Polling the DINSTAR CDR endpoint returns overlapping windows. A stable
            // UUID makes persistence idempotent even when the same row is observed again.
            val stableIdentity = listOf(gatewayId, externalCallId, startTime, caller, callee).joinToString("|")
            val id = UUID.nameUUIDFromBytes(stableIdentity.toByteArray(Charsets.UTF_8))

            jdbc.update("""
                INSERT INTO dinstar_cdr
                (id, gateway_id, port_index, call_id, caller_number, callee_number,
                 direction, call_type, status, duration_seconds, start_time,
                 answer_time, end_time, duration, codec, hangup_cause, sip_call_id,
                 asterisk_channel, raw_data)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSONB))
                ON CONFLICT (id) DO UPDATE SET
                    status = EXCLUDED.status,
                    duration_seconds = EXCLUDED.duration_seconds,
                    answer_time = EXCLUDED.answer_time,
                    end_time = EXCLUDED.end_time,
                    duration = EXCLUDED.duration,
                    codec = EXCLUDED.codec,
                    hangup_cause = EXCLUDED.hangup_cause,
                    raw_data = EXCLUDED.raw_data
            """,
                id,
                gatewayId,
                (cdr["port"] as? Number)?.toInt()?.coerceIn(0, 31) ?: 0,
                externalCallId,
                caller,
                callee,
                direction,
                cdr["call_type"]?.toString()?.uppercase()?.take(20) ?: "VOICE",
                status,
                duration,
                startTime,
                answerTime,
                endTime,
                duration,
                cdr["codec"]?.toString()?.take(20),
                cdr["hangup_cause"]?.toString()?.take(50),
                externalCallId,
                cdr["asterisk_channel"]?.toString()?.take(100),
                objectMapper.writeValueAsString(cdr)
            )
        } catch (e: Exception) {
            log.error("Error saving CDR record", e)
        }
    }

    /**
     * إرسال USSD
     * POST /api/send_ussd
     */
    fun sendUssd(
        gatewayHost: String? = null,
        port: Int,
        code: String
    ): Map<String, Any?> {
        return try {
            val gateway = gatewayHost?.let { fleet.findGatewayByHost(it) } ?: fleet.getDefaultGateway()
            if (gateway == null) {
                return mapOf("error" to "No gateway available")
            }

            val result = hardware.sendUssd(gateway, port, code)
            
            // حفظ في قاعدة البيانات
            saveUssdLog(gateway.id, port, code, result["response_text"] as? String, result["status"] as? String)
            
            webSocketHandler.broadcastUssdResponse(gateway.id, port, result)
            
            result
        } catch (e: Exception) {
            log.error("Error sending USSD", e)
            mapOf("error" to e.message)
        }
    }

    private fun saveUssdLog(gatewayId: UUID, port: Int, code: String, response: String?, status: String?) {
        try {
            jdbc.update("""
                INSERT INTO dinstar_ussd_log 
                (gateway_id, port_index, ussd_code, response_text, status)
                VALUES (?, ?, ?, ?, ?)
            """,
                gatewayId,
                port,
                code,
                response,
                status ?: if (response != null) "SUCCESS" else "FAILED"
            )
        } catch (e: Exception) {
            log.error("Error saving USSD log", e)
        }
    }

    /**
     * تشغيل/إيقاف منفذ
     * POST /api/set_port_info
     */
    fun setPortPower(
        gatewayHost: String? = null,
        port: Int,
        powerOn: Boolean
    ): Map<String, Any?> {
        return try {
            val gateway = gatewayHost?.let { fleet.findGatewayByHost(it) } ?: fleet.getDefaultGateway()
            if (gateway == null) {
                return mapOf("error" to "No gateway available")
            }

            val result = hardware.setPortPower(gateway, port, powerOn)
            
            // تحديث قاعدة البيانات
            jdbc.update("""
                INSERT INTO dinstar_port_control 
                (gateway_id, port_index, power_state, updated_at)
                VALUES (?, ?, ?, NOW())
                ON CONFLICT (gateway_id, port_index) DO UPDATE SET
                power_state = EXCLUDED.power_state,
                updated_at = NOW()
            """,
                gateway.id,
                port,
                powerOn
            )
            
            // تسجيل التغيير
            logConfigChange(gateway.id, null, "PORT_POWER", port, 
                if (powerOn) "ON" else "OFF", null)
            
            webSocketHandler.broadcastPortControl(gateway.id, port, mapOf("power" to powerOn))
            
            result
        } catch (e: Exception) {
            log.error("Error setting port power", e)
            mapOf("error" to e.message)
        }
    }

    /**
     * تعيين تحويل المكالمات
     * POST /api/set_port_info
     */
    fun setCallForward(
        gatewayHost: String? = null,
        port: Int,
        enabled: Boolean,
        number: String? = null,
        condition: String? = null
    ): Map<String, Any?> {
        return try {
            val gateway = gatewayHost?.let { fleet.findGatewayByHost(it) } ?: fleet.getDefaultGateway()
            if (gateway == null) {
                return mapOf("error" to "No gateway available")
            }

            val result = hardware.setCallForward(gateway, port, enabled, number, condition)
            
            // تحديث قاعدة البيانات
            jdbc.update("""
                INSERT INTO dinstar_port_control 
                (gateway_id, port_index, call_forward_enabled, call_forward_number, 
                 call_forward_condition, updated_at)
                VALUES (?, ?, ?, ?, ?, NOW())
                ON CONFLICT (gateway_id, port_index) DO UPDATE SET
                call_forward_enabled = EXCLUDED.call_forward_enabled,
                call_forward_number = EXCLUDED.call_forward_number,
                call_forward_condition = EXCLUDED.call_forward_condition,
                updated_at = NOW()
            """,
                gateway.id,
                port,
                enabled,
                number,
                condition ?: "ALWAYS"
            )
            
            // تسجيل التغيير
            logConfigChange(gateway.id, null, "CALL_FORWARD", port, 
                if (enabled) "ENABLED:$number:$condition" else "DISABLED", null)
            
            webSocketHandler.broadcastPortControl(gateway.id, port, 
                mapOf("callForward" to enabled, "number" to number))
            
            result
        } catch (e: Exception) {
            log.error("Error setting call forward", e)
            mapOf("error" to e.message)
        }
    }

    private fun logConfigChange(
        gatewayId: UUID,
        userId: UUID?,
        changeType: String,
        port: Int?,
        newValue: String?,
        reason: String?
    ) {
        try {
            jdbc.update("""
                INSERT INTO dinstar_config_changes 
                (gateway_id, changed_by, change_type, port_index, new_value, reason)
                VALUES (?, ?, ?, ?, ?, ?)
            """,
                gatewayId,
                userId,
                changeType,
                port,
                newValue,
                reason
            )
        } catch (e: Exception) {
            log.error("Error logging config change", e)
        }
    }

    /**
     * الحصول على إحصائيات شاملة
     */
    fun getStatistics(): Map<String, Any?> {
        return try {
            val stats = mutableMapOf<String, Any>()
            
            // عدد المكالمات
            val callCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dinstar_cdr",
                Int::class.java
            ) ?: 0
            stats["totalCalls"] = callCount
            
            // عدد الرسائل
            val smsCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dinstar_sms_log",
                Int::class.java
            ) ?: 0
            stats["totalSms"] = smsCount
            
            // عدد USSD
            val ussdCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dinstar_ussd_log",
                Int::class.java
            ) ?: 0
            stats["totalUssd"] = ussdCount
            
            // التنبيهات النشطة
            val alertCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dinstar_alerts WHERE acknowledged = false",
                Int::class.java
            ) ?: 0
            stats["activeAlerts"] = alertCount
            
            stats
        } catch (e: Exception) {
            log.error("Error getting statistics", e)
            emptyMap()
        }
    }
}
