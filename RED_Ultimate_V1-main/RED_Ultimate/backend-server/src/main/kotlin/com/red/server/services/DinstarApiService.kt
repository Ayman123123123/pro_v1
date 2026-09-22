package com.red.server.services

import com.red.server.websocket.DinstarWebSocketHandler
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
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
            
            webSocketHandler.broadcastDeviceStatus(gateway.id.toString(), status)
            
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

    /**
     * حفظ سجل CDR واحد كما ورد من `get_cdr`.
     *
     * كان هذا الموضع يخطئ في طبقتين معًا:
     *
     * 1. **أسماء حقول الجهاز**: يقرأ `start_time`/`answer_time`/`caller_number`
     *    /`callee_number`، والموثّق في `get_cdr` هو
     *    `start_date`/`answer_date`/`source_number`/`destination_number`
     *    (انظر [DinstarApiContract.Cdr.FIELDS]). فكانت كل القيم `null`.
     *
     * 2. **أسماء أعمدة الجدول**: يكتب `duration`/`end_time`/`sip_call_id`
     *    /`asterisk_channel`، وجدول V15 يحمل `duration_seconds` ولا يحمل
     *    البقية — فيفشل الإدراج كاملًا ويُبتلَع في `catch`.
     *
     * وزيادةً: `direction` كان يُدرَج خامًا (`ip->gsm`) فيخرق قيد CHECK،
     * و`status` — وهو `NOT NULL` — لم يكن يُدرَج إطلاقًا.
     */
    private fun saveCdrRecord(gatewayId: UUID, cdr: Map<String, Any?>) {
        try {
            val start = DinstarTime.parse(cdr["start_date"]?.toString())
            if (start == null) {
                log.debug("CDR بلا start_date صالح — تخطّي")
                return
            }
            val answer = DinstarTime.parse(cdr["answer_date"]?.toString())
            val hangup = cdr["hangup"]?.toString()
            val direction = DinstarApiContract.Cdr.normalizeDirection(cdr["direction"]?.toString())
            if (direction == null) {
                log.debug("CDR باتجاه غير معروف {} — تخطّي", cdr["direction"])
                return
            }

            val duration = (cdr["duration"] as? Number)?.toInt() ?: 0
            val ringSec = DinstarApiContract.Cdr.ringSeconds(start, answer)

            // ⚠️ عيبان كانا يُسقطان كل إدراج من هذا الموضع:
            //
            // 1. **`call_id` مفقود**. الجملة تُدرج 16 عمودًا وكان يُمرَّر 15
            //    وسيطًا، فانزلق كل وسيط موضعًا واحدًا: معرّف البوابة يذهب إلى
            //    `call_id`، والمنفذ إلى `port_index`… حتى ينتهي الأمر بخطأ نوع
            //    أو — وهو الأسوأ — بصف يُدرَج بأعمدة مُبدَّلة.
            // 2. **`endTime` كـ`Instant`**. سائق PostgreSQL يرفضه:
            //    «Can't infer the SQL type … java.time.Instant».
            //
            // ترتيب الوسائط هنا يجب أن يطابق ترتيب أعمدة INSERT_SQL حرفيًا،
            // ويحرسه اختبار DinstarCdrMappingTest بقائمة أعمدة صريحة.
            jdbc.update(
                DinstarApiContract.Cdr.INSERT_SQL,
                gatewayId.toString(),
                cdr["call_id"]?.toString() ?: UUID.randomUUID().toString(),
                (cdr["port"] as? Number)?.toInt(),
                java.sql.Timestamp.from(start),
                answer?.let { java.sql.Timestamp.from(it) },
                duration,
                ringSec,
                direction,
                DinstarApiContract.Cdr.callOutcome(answer != null, hangup),
                cdr["source_number"]?.toString() ?: "",
                cdr["destination_number"]?.toString() ?: "",
                cdr["reason"]?.toString(),
                (cdr["gsm_code"] as? Number)?.toInt(),
                cdr["codec"]?.toString(),
                DinstarApiContract.Cdr.endTime(start, answer, ringSec, duration)
                    ?.let { java.sql.Timestamp.from(it) },
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
            
            webSocketHandler.broadcastUssdResponse(gateway.id.toString(), port, result)
            
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
            
            webSocketHandler.broadcastPortControl(gateway.id.toString(), port, mapOf("power" to powerOn))
            
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
            
            webSocketHandler.broadcastPortControl(gateway.id.toString(), port, 
                mapOf("callForward" to enabled, "number" to number))
            
            result
        } catch (e: Exception) {
            log.error("Error setting call forward", e)
            mapOf("error" to e.message)
        }
    }

    private fun logConfigChange(
        gatewayId: UUID,
        userId: String?,
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

    /**
     * إرسال SMS - محفّظ حول hardware.sendSms()
     * POST /api/send_sms
     */
    fun sendSms(
        text: String,
        params: List<Map<String, Any?>>,
        ports: List<Int>? = null,
        encoding: String = "AUTO_ENCODING",
        gatewayHost: String? = null
    ): Map<String, Any?> {
        return try {
            val gateway = gatewayHost?.let { fleet.findGatewayByHost(it) } ?: fleet.getDefaultGateway()
            if (gateway == null) {
                return mapOf("error" to "No gateway available")
            }

            val result = hardware.sendSms(text, params, ports, encoding, gatewayHost)
            
            // حفظ في قاعدة البيانات
            result.forEach { (userId, smsResult) ->
                saveSmsLog(gateway.id, userId, params.find { it["user_id"] == userId } ?: emptyMap(), (smsResult as? Map<String, Any?>) ?: emptyMap())
            }
            
            webSocketHandler.broadcastSmsSent(gateway.id.toString(), result)
            
            result
        } catch (e: Exception) {
            log.error("Error sending SMS", e)
            mapOf("error" to e.message)
        }
    }

    /**
     * جلب نتائج إرسال SMS - محفّظ حول hardware.querySmsResult()
     * POST /api/query_sms_result
     */
    fun querySmsResult(
        userIds: List<Int> = emptyList(),
        numbers: List<String> = emptyList(),
        gatewayHost: String? = null
    ): Map<String, Any?> {
        return try {
            val gateway = gatewayHost?.let { fleet.findGatewayByHost(it) } ?: fleet.getDefaultGateway()
            if (gateway == null) {
                return mapOf("error" to "No gateway available")
            }

            val result = hardware.querySmsResult(userIds, numbers)
            
            // حفظ في قاعدة البيانات
            result.forEach { (userId, smsResult) ->
                saveSmsResult(gateway.id, userId, (smsResult as? Map<String, Any?>) ?: emptyMap())
            }
            
            webSocketHandler.broadcastSmsResult(gateway.id.toString(), result)
            
            result
        } catch (e: Exception) {
            log.error("Error querying SMS result", e)
            mapOf("error" to e.message)
        }
    }

    /**
     * جلب حالة تسليم SMS - محفّظ حول hardware.querySmsDeliveryStatus()
     * POST /api/query_sms_deliver_status
     */
    fun querySmsDeliveryStatus(
        numbers: List<String> = emptyList(),
        timeAfter: String? = null,
        timeBefore: String? = null,
        gatewayHost: String? = null
    ): Map<String, Any?> {
        return try {
            val gateway = gatewayHost?.let { fleet.findGatewayByHost(it) } ?: fleet.getDefaultGateway()
            if (gateway == null) {
                return mapOf("error" to "No gateway available")
            }

            val result = hardware.querySmsDeliveryStatus(numbers, timeAfter, timeBefore)
            
            // حفظ في قاعدة البيانات
            result.forEach { (number, status) ->
                saveSmsDeliveryStatus(gateway.id, number, (status as? Map<String, Any?>), timeAfter, timeBefore)
            }
            
            webSocketHandler.broadcastSmsDeliveryStatus(gateway.id.toString(), result)
            
            result
        } catch (e: Exception) {
            log.error("Error querying SMS delivery status", e)
            mapOf("error" to e.message)
        }
    }

    /**
     * جلب SMS الواردة - محفّظ alrededor hardware.queryIncomingSms()
     * POST /api/query_incoming_sms
     */
    fun queryIncomingSms(
        sinceId: Long = 0,
        flag: String = "all",
        gatewayHost: String? = null
    ): Map<String, Any?> {
        return try {
            val gateway = gatewayHost?.let { fleet.findGatewayByHost(it) } ?: fleet.getDefaultGateway()
            if (gateway == null) {
                return mapOf("error" to "No gateway available")
            }

            val result = hardware.queryIncomingSms(sinceId, flag)
            
            // حفظ في قاعدة البيانات
            val smsList = result["sms"] as? List<Map<String, Any?>> ?: emptyList()
            smsList.forEach { sms ->
                saveIncomingSms(gateway.id, sms)
            }
            
            webSocketHandler.broadcastIncomingSms(gateway.id.toString(), result)
            
            result
        } catch (e: Exception) {
            log.error("Error querying incoming SMS", e)
            mapOf("error" to e.message)
        }
    }

    /**
     * إيقاف مهمة إرسال SMS - محفّظ حول hardware.stopSmsTask()
     * GET /api/stop_sms
     */
    fun stopSmsTask(
        taskId: Int,
        gatewayHost: String? = null
    ): Map<String, Any?> {
        return try {
            val gateway = gatewayHost?.let { fleet.findGatewayByHost(it) } ?: fleet.getDefaultGateway()
            if (gateway == null) {
                return mapOf("error" to "No gateway available")
            }

            val result = hardware.stopSmsTask(taskId)
            
            // تسجيل التغيير
            logConfigChange(gateway.id, null, "STOP_SMS_TASK", null, "Task ID: $taskId", null)
            
            webSocketHandler.broadcastSmsTaskStopped(gateway.id.toString(), taskId)
            
            result
        } catch (e: Exception) {
            log.error("Error stopping SMS task", e)
            mapOf("error" to e.message)
        }
    }

    /**
     * أخذ نسخة احتياطية من configuration
     * POST /api/set_config (with backup action)
     * Store config blob with timestamp
     */
    fun backupConfig(
        gatewayHost: String? = null
    ): Map<String, Any?> {
        return try {
            val gateway = gatewayHost?.let { fleet.findGatewayByHost(it) } ?: fleet.getDefaultGateway()
            if (gateway == null) {
                return mapOf("error" to "No gateway available")
            }

            val config = hardware.getDeviceStatus(gateway)
            val backup = mapOf(
                "gatewayId" to gateway.id.toString(),
                "timestamp" to java.time.Instant.now().toString(),
                "config" to config
            )
            
            // Store in database
            jdbc.update("""
                INSERT INTO dinstar_config_snapshots 
                (gateway_id, snapshot_name, config_json, created_at)
                VALUES (?, ?, ?, NOW())
            """, gateway.id, "manual_backup_${System.currentTimeMillis()}", objectMapper.writeValueAsString(backup))
            
            webSocketHandler.broadcastConfigBackup(gateway.id.toString(), backup)
            
            backup
        } catch (e: Exception) {
            log.error("Error backing up config", e)
            mapOf("error" to e.message)
        }
    }

    /**
     * استعادة configuration من نسخة احتياطية
     * POST /api/set_config (with restore action)
     */
    fun restoreConfig(
        gatewayHost: String? = null,
        backupId: UUID? = null
    ): Map<String, Any?> {
        return try {
            val gateway = gatewayHost?.let { fleet.findGatewayByHost(it) } ?: fleet.getDefaultGateway()
            if (gateway == null) {
                return mapOf("error" to "No gateway available")
            }

            // Get the backup from DB if backupId provided
            val configJson = if (backupId != null) {
                jdbc.queryForObject(
                    "SELECT config_json FROM dinstar_config_snapshots WHERE gateway_id = ? AND is_restore = true LIMIT 1",
                    String::class.java, gateway.id
                ) ?: objectMapper.writeValueAsString(mapOf("error" to "Backup not found"))
            } else {
                objectMapper.writeValueAsString(mapOf("error" to "No backupId specified"))
            }
            
            val config = objectMapper.readValue(configJson, object : TypeReference<Map<String, Any?>>() {})
            val result = hardware.setConfig(gateway, config)
            
            // Log the restore operation
            logConfigChange(gateway.id, null, "CONFIG_RESTORE", null, "Restore from backup $backupId", null)
            
            webSocketHandler.broadcastConfigRestore(gateway.id.toString(), backupId, result)
            
            result
        } catch (e: Exception) {
            log.error("Error restoring config", e)
            mapOf("error" to e.message)
        }
    }

    /**
     * حفظ سجل SMS واحد كما ورد من `send_sms`.
     */
    private fun saveSmsLog(gatewayId: UUID, userId: Any?, params: Map<String, Any?>, result: Map<String, Any?>) {        try {
            jdbc.update("""
                INSERT INTO dinstar_sms_log 
                (gateway_id, user_id, phone_number, text, encoding, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, NOW())
            """,
                gatewayId,
                userId,
                params["number"],
                params["text"],
                params["encoding"],
                result["status"]?.toString()
            )
        } catch (e: Exception) {
            log.error("Error saving SMS log", e)
        }
    }

    /**
     * حفظ نتيجة إرسال SMS.
     */
    private fun saveSmsResult(gatewayId: UUID, userId: Any?, result: Map<String, Any?>) {
        try {
            jdbc.update("""
                INSERT INTO dinstar_sms_result 
                (gateway_id, user_id, message_id, status, status_code, completed_at)
                VALUES (?, ?, ?, ?, ?, NOW())
            """,
                gatewayId,
                userId,
                result["message_id"],
                result["status"],
                result["status_code"]
            )
        } catch (e: Exception) {
            log.error("Error saving SMS result", e)
        }
    }

    /**
     * حفظ حالة تسليم SMS.
     */
    private fun saveSmsDeliveryStatus(gatewayId: UUID, phoneNumber: String?, status: Map<String, Any?>?, timeAfter: String?, timeBefore: String?) {
        try {
            jdbc.update("""
                INSERT INTO dinstar_sms_delivery_status 
                (gateway_id, phone_number, status, status_code, completed_at)
                VALUES (?, ?, ?, ?, NOW())
            """,
                gatewayId,
                phoneNumber,
                status?.get("status"),
                status?.get("status_code")
            )
        } catch (e: Exception) {
            log.error("Error saving SMS delivery status", e)
        }
    }

    /**
     * حفظ SMS واردة واحدة.
     */
    private fun saveIncomingSms(gatewayId: UUID, sms: Map<String, Any?>) {
        try {
            jdbc.update("""
                INSERT INTO dinstar_incoming_sms 
                (gateway_id, phone_number, text, received_at)
                VALUES (?, ?, ?, NOW())
            """,
                gatewayId,
                sms["number"],
                sms["text"]
            )
        } catch (e: Exception) {
            log.error("Error saving incoming SMS", e)
        }
    }
}
