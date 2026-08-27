package com.red.server.dinstar

import com.red.server.admin.service.AdminService
import com.red.server.pstn.PstnManager
import com.red.server.services.DinstarHardwareService
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * ðŸ“ž Human Behavior â†’ Phone Number Learning â€” Call mode
 *
 * Ù…Ø­Ø±Ùƒ Ø³Ù„ÙˆÙƒ Ø¨Ø´Ø±ÙŠ: ÙŠÙÙ†Ø´Ø¦ Ù…ÙƒØ§Ù„Ù…Ø§Øª Ù‚ØµÙŠØ±Ø© Ø¹Ø´ÙˆØ§Ø¦ÙŠØ© Ø§Ù„ØªÙˆÙ‚ÙŠØª ÙˆØ§Ù„Ù…Ø¯Ø© Ù…Ù† Ø´Ø±Ø§Ø¦Ø­ DINSTAR Ù†Ø­Ùˆ
 * Ù…Ø¬Ù…Ù‘Ø¹ Ø£Ø±Ù‚Ø§Ù… Ù…ØªØ¹Ù„ÙŽÙ‘Ù…Ø©ØŒ Ø¶Ù…Ù† Ù†Ø§ÙØ°Ø© Ø²Ù…Ù†ÙŠØ© ÙˆØ³Ù‚Ù ÙŠÙˆÙ…ÙŠ Ù„ÙƒÙ„ Ù…Ù†ÙØ° â€” Ø¹Ø¨Ø± Ø§Ù„Ù…Ø³Ø§Ø± Ø§Ù„Ù…Ø¹ØªÙ…Ø¯ Ø±Ø³Ù…ÙŠØ§Ù‹
 * (Younes â†’ Asterisk AMI â†’ PJSIP â†’ DINSTAR) ÙˆÙ„ÙŠØ³ Ø¹Ø¨Ø± Ù†Ù‚Ø§Ø· API Ù…Ø®ØªÙ„Ù‚Ø© Ø¹Ù„Ù‰ Ø§Ù„Ø¨ÙˆØ§Ø¨Ø©.
 */
@Service
class NumberLearningService(
    private val jdbc: JdbcTemplate,
    private val pstn: PstnManager,
    private val hardware: DinstarHardwareService,
    private val adminAudit: AdminService
) {
    companion object {
        private val log = LoggerFactory.getLogger(NumberLearningService::class.java)
        private val rnd = SecureRandom()
        private val ZONE: ZoneId = ZoneId.of("Asia/Aden")
    }

    // â”â”â”â”â”â”â”â”â”â” Config â”â”â”â”â”â”â”â”â”â”

    fun getConfig(): Map<String, Any?> {
        val row = jdbc.queryForMap("SELECT * FROM number_learning_config WHERE id = 1")
        return mapOf(
            "mode" to row["mode"],
            "windowStartMinute" to row["window_start_minute"],
            "windowEndMinute" to row["window_end_minute"],
            "minDurationSeconds" to row["min_duration_seconds"],
            "maxDurationSeconds" to row["max_duration_seconds"],
            "minIntervalMinutes" to row["min_interval_minutes"],
            "maxIntervalMinutes" to row["max_interval_minutes"],
            "dailyCapPerPort" to row["daily_cap_per_port"],
            "enabledPorts" to (row["enabled_ports"]?.toString() ?: ""),
            // SMS mode â€” comprehensive
            "smsMode" to (row["sms_mode"] ?: "OFF"),
            "smsDailyCapPerPort" to (row["sms_daily_cap_per_port"] ?: 4),
            "smsMinIntervalMinutes" to (row["sms_min_interval_minutes"] ?: 60),
            "smsMaxIntervalMinutes" to (row["sms_max_interval_minutes"] ?: 240),
            "smsTemplate" to (row["sms_template"] ?: "Ù…Ø±Ø­Ø¨Ø§ â€” Ø±Ø³Ø§Ù„Ø© ØªØ¹Ù„Ù…"),
            "autoLearnFromCdr" to (row["auto_learn_from_cdr"] ?: false),
            "autoLearnFromInbound" to (row["auto_learn_from_inbound"] ?: true),
            "poolSize" to jdbc.queryForObject("SELECT COUNT(*) FROM number_learning_pool WHERE active", Int::class.java),
            "poolActiveSize" to jdbc.queryForObject("SELECT COUNT(*) FROM number_learning_pool WHERE active", Int::class.java),
            "poolTotalSize" to jdbc.queryForObject("SELECT COUNT(*) FROM number_learning_pool", Int::class.java),
            "updatedAt" to row["updated_at"]
        )
    }

    @Transactional
    fun updateConfig(adminId: UUID, patch: Map<String, Any?>): Map<String, Any?> {
        val sets = mutableListOf<String>()
        val args = mutableListOf<Any>()

        fun add(column: String, value: Any?) { sets += "$column = ?"; args += value!! }

        (patch["mode"] as? String)?.let {
            require(it in setOf("OFF", "LEARN", "MAINTAIN")) { "mode must be OFF|LEARN|MAINTAIN" }
            add("mode", it)
        }
        fun intRange(key: String, column: String, min: Int, max: Int, required: Boolean = false) {
            val v = (patch[key] as? Number)?.toInt() ?: return
            require(v in min..max) { "$key must be $min..$max" }
            add(column, v)
        }
        intRange("windowStartMinute", "window_start_minute", 0, 1439)
        intRange("windowEndMinute", "window_end_minute", 0, 1439)
        intRange("minDurationSeconds", "min_duration_seconds", 3, 600)
        intRange("maxDurationSeconds", "max_duration_seconds", 3, 900)
        intRange("minIntervalMinutes", "min_interval_minutes", 1, 1440)
        intRange("maxIntervalMinutes", "max_interval_minutes", 1, 1440)
        intRange("dailyCapPerPort", "daily_cap_per_port", 1, 100)
        (patch["enabledPorts"] as? String)?.let {
            // Ø§Ù„Ø£Ø³Ø·ÙˆÙ„ ÙŠØ¯Ø¹Ù… 16 Ù…Ù†ÙØ°Ø§Ù‹ (8G + 8T) ÙƒØ¨Ù‚ÙŠØ© Ø§Ù„Ù†Ø¸Ø§Ù…Ø› ÙƒØ§Ù† Ø§Ù„Ù†Ù…Ø· 0-7 ÙŠØ±ÙØ¶
            // Ù†ØµÙ Ø§Ù„Ù…Ù†Ø§ÙØ° ÙØªÙØ³ØªØ¨Ø¹Ø¯ Ø´Ø±Ø§Ø¦Ø­ 8..15 Ù…Ù† ØªØ¹Ù„Ù‘Ù… Ø§Ù„Ø£Ø±Ù‚Ø§Ù… ØµØ§Ù…ØªØ©Ù‹.
            require(it.matches(Regex("^((1[0-5]|[0-9])(,(1[0-5]|[0-9]))*)?$"))) { "enabledPorts must be CSV of 0-15 or empty" }
            add("enabled_ports", it.trim())
        }
        // SMS mode â€” comprehensive
        (patch["smsMode"] as? String)?.let {
            require(it in setOf("OFF", "LEARN", "MAINTAIN")) { "smsMode must be OFF|LEARN|MAINTAIN" }
            add("sms_mode", it)
        }
        intRange("smsDailyCapPerPort", "sms_daily_cap_per_port", 1, 50)
        intRange("smsMinIntervalMinutes", "sms_min_interval_minutes", 1, 1440)
        intRange("smsMaxIntervalMinutes", "sms_max_interval_minutes", 1, 1440)
        (patch["smsTemplate"] as? String)?.let {
            require(it.length in 1..160) { "smsTemplate must be 1..160 chars" }
            add("sms_template", it.trim())
        }
        (patch["autoLearnFromCdr"] as? Boolean)?.let { add("auto_learn_from_cdr", it) }
        (patch["autoLearnFromInbound"] as? Boolean)?.let { add("auto_learn_from_inbound", it) }

        if ((patch["minDurationSeconds"] != null || patch["maxDurationSeconds"] != null)) {
            val cur = jdbc.queryForMap("SELECT min_duration_seconds, max_duration_seconds FROM number_learning_config WHERE id=1")
            val mn = (patch["minDurationSeconds"] as? Number)?.toInt() ?: (cur["min_duration_seconds"] as Number).toInt()
            val mx = (patch["maxDurationSeconds"] as? Number)?.toInt() ?: (cur["max_duration_seconds"] as Number).toInt()
            require(mx >= mn) { "max_duration_seconds must be >= min_duration_seconds" }
        }
        if (patch["minIntervalMinutes"] != null || patch["maxIntervalMinutes"] != null) {
            val cur = jdbc.queryForMap("SELECT min_interval_minutes, max_interval_minutes FROM number_learning_config WHERE id=1")
            val mn = (patch["minIntervalMinutes"] as? Number)?.toInt() ?: (cur["min_interval_minutes"] as Number).toInt()
            val mx = (patch["maxIntervalMinutes"] as? Number)?.toInt() ?: (cur["max_interval_minutes"] as Number).toInt()
            require(mx >= mn) { "max_interval_minutes must be >= min_interval_minutes" }
        }
        if (patch["smsMinIntervalMinutes"] != null || patch["smsMaxIntervalMinutes"] != null) {
            val cur = jdbc.queryForMap("SELECT sms_min_interval_minutes, sms_max_interval_minutes FROM number_learning_config WHERE id=1")
            val mn = (patch["smsMinIntervalMinutes"] as? Number)?.toInt() ?: (cur["sms_min_interval_minutes"] as Number).toInt()
            val mx = (patch["smsMaxIntervalMinutes"] as? Number)?.toInt() ?: (cur["sms_max_interval_minutes"] as Number).toInt()
            require(mx >= mn) { "sms_max_interval_minutes must be >= sms_min_interval_minutes" }
        }

        require(sets.isNotEmpty()) { "No valid config fields supplied" }
        sets += "updated_at = CURRENT_TIMESTAMP"
        args += adminId
        jdbc.update("UPDATE number_learning_config SET ${sets.joinToString(",")} WHERE id = 1", *args.toTypedArray())

        adminAudit.recordAudit(
            adminId = adminId, adminUsername = null, action = "NUMBER_LEARNING_CONFIG_UPDATED",
            category = "DINSTAR", metadata = mapOf("keys" to patch.keys.toList())
        )
        return getConfig()
    }

    // â”â”â”â”â”â”â”â”â”â” Pool â”â”â”â”â”â”â”â”â”â”

    fun listPool(): List<Map<String, Any?>> =
        jdbc.queryForList("SELECT id, number, label, source, active, added_at, last_used_at, success_count, fail_count, notes FROM number_learning_pool ORDER BY added_at DESC")

    fun addPoolNumbers(adminId: UUID, numbers: List<Map<String, String?>>): Map<String, Any?> {
        require(numbers.isNotEmpty() && numbers.size <= 500) { "1..500 numbers per request" }
        var inserted = 0; var skipped = 0
        numbers.forEach { entry ->
            val raw = entry["number"]?.trim().orEmpty()
            val normalized = normalizeNumber(raw)
            if (normalized == null) { skipped++; return@forEach }
            val insertedRows = jdbc.update(
                """INSERT INTO number_learning_pool(id,number,label,source,added_at) VALUES (?,?,?,?,CURRENT_TIMESTAMP)
                   ON CONFLICT (number) DO NOTHING""",
                UUID.randomUUID(), normalized, entry["label"], entry["source"] ?: "MANUAL"
            )
            if (insertedRows > 0) inserted++ else skipped++
        }
        adminAudit.recordAudit(
            adminId = adminId, adminUsername = null, action = "NUMBER_LEARNING_POOL_ADDED",
            category = "DINSTAR", metadata = mapOf("inserted" to inserted, "skipped" to skipped)
        )
        return mapOf("inserted" to inserted, "skipped" to skipped)
    }

    fun setPoolEntryActive(adminId: UUID, id: UUID, active: Boolean): Map<String, Any?> {
        val rows = jdbc.update("UPDATE number_learning_pool SET active=? WHERE id=?", active, id)
        require(rows == 1) { "Pool entry not found" }
        return mapOf("id" to id, "active" to active)
    }

    fun deletePoolEntry(adminId: UUID, id: UUID): Map<String, Any?> {
        val rows = jdbc.update("DELETE FROM number_learning_pool WHERE id=?", id)
        require(rows == 1) { "Pool entry not found" }
        adminAudit.recordAudit(adminId = adminId, adminUsername = null, action = "NUMBER_LEARNING_POOL_DELETED", category = "DINSTAR", targetId = id.toString())
        return mapOf("deleted" to true)
    }

    /** Yemeni local format (6..12 digits, known mobile prefix or â‰¥9 digits) */
    private fun normalizeNumber(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        val local = when {
            digits.startsWith("00967") -> digits.removePrefix("00967")
            digits.startsWith("+967") -> digits.removePrefix("967") // + Ù…ÙÙÙ„ØªØ± Ø£Ø¹Ù„Ø§Ù‡
            digits.startsWith("967") -> digits.removePrefix("967")
            digits.startsWith("0") -> digits.removePrefix("0")
            else -> digits
        }
        if (!local.matches(Regex("^[0-9]{6,12}$"))) return null
        val prefix = local.substring(0, minOf(2, local.length))
        val validMobile = prefix in setOf("71", "72", "73", "74", "75", "76", "77", "78", "70", "10", "11", "12")
        return if (validMobile || local.length >= 9) local else null
    }

    // â”â”â”â”â”â”â”â”â”â” Calls & Engine â”â”â”â”â”â”â”â”â”â”

    fun listCalls(limit: Int): List<Map<String, Any?>> =
        jdbc.queryForList("SELECT id,port,number,correlation_id,mode,status,duration_seconds,error,started_at,next_eligible_at FROM number_learning_calls ORDER BY started_at DESC LIMIT ?", limit.coerceIn(1, 200))

    fun stats(): Map<String, Any?> {
        val today = LocalDate.now(ZONE)
        val todayCount = jdbc.queryForObject("SELECT COUNT(*) FROM number_learning_calls WHERE started_at::date = ?", Long::class.java, today)
        val failedToday = jdbc.queryForObject("SELECT COUNT(*) FROM number_learning_calls WHERE started_at::date = ? AND status='FAILED'", Long::class.java, today)
        val nextEligible = jdbc.queryForObject(
            "SELECT MAX(next_eligible_at) FROM number_learning_calls WHERE started_at::date = ? AND status IN ('ORIGINATED','COMPLETED')",
            java.sql.Timestamp::class.java, today
            )?.toInstant()
        return mapOf(
            "todayTotal" to todayCount,
            "todayFailed" to failedToday,
            "nextEligibleAt" to nextEligible,
            "zone" to ZONE.id
        )
    }

    /**
     * Ù†Ø¨Ø¶Ø© Ø§Ù„Ù…Ø­Ø±Ùƒ: ÙƒÙ„ Ø¯Ù‚ÙŠÙ‚Ø© ØªÙ‚ÙŠÙ‘Ù… Ø§Ù„Ù†Ø§ÙØ°Ø©/Ø§Ù„Ø³Ù‚Ù/Ø§Ù„ÙÙˆØ§ØµÙ„ ÙˆØªÙØ·Ù„Ù‚ Ù…ÙƒØ§Ù„Ù…Ø© ÙˆØ§Ø­Ø¯Ø© Ø¹Ù†Ø¯ Ø§Ù„Ø£Ù‡Ù„ÙŠØ©.
     * ÙŠØ¹Ù…Ù„ ÙÙ‚Ø· Ø¹Ù†Ø¯Ù…Ø§ mode != OFF.
     */
    @Scheduled(fixedDelay = 60_000)
    fun tick() {
        runCatching { tickInternal() }.onFailure { log.warn("Number-learning tick failed: {}", it.message) }
    }

    private fun tickInternal() {
        val cfg = jdbc.queryForMap("SELECT * FROM number_learning_config WHERE id = 1")
        val mode = cfg["mode"]?.toString() ?: "OFF"
        if (mode == "OFF") return

        val now = LocalTime.now(ZONE)
        val start = (cfg["window_start_minute"] as Number).toInt()
        val end = (cfg["window_end_minute"] as Number).toInt()
        val minutesNow = now.hour * 60 + now.minute
        val inWindow = if (start < end) minutesNow in start until end else minutesNow >= start || minutesNow < end
        if (!inWindow) return

        val pool = jdbc.queryForList("SELECT number FROM number_learning_pool WHERE active").map { it["number"].toString() }
        if (pool.isEmpty()) return

        val enabledPortsRaw = cfg["enabled_ports"]?.toString()?.trim().orEmpty()
        val ports: List<Int> = if (enabledPortsRaw.isBlank()) (0..15).toList()
        else enabledPortsRaw.split(',').mapNotNull { it.trim().toIntOrNull() }

        val cap = (cfg["daily_cap_per_port"] as Number).toInt()
        val minDur = (cfg["min_duration_seconds"] as Number).toInt()
        val maxDur = (cfg["max_duration_seconds"] as Number).toInt()
        val minInt = (cfg["min_interval_minutes"] as Number).toLong()
        val maxInt = (cfg["max_interval_minutes"] as Number).toLong()
        val today = LocalDate.now(ZONE)

        for (port in ports) {
            val usedToday = jdbc.queryForObject(
                "SELECT COUNT(*) FROM number_learning_calls WHERE port = ? AND started_at::date = ? AND status <> 'FAILED'",
                Long::class.java, port, today
            ) ?: 0L
            if (usedToday >= cap) continue

            val eligibleAt = jdbc.queryForObject(
                "SELECT MAX(next_eligible_at) FROM number_learning_calls WHERE port = ? AND started_at::date = ?",
                java.sql.Timestamp::class.java, port, today
            )?.toInstant()
            if (eligibleAt != null && Instant.now().isBefore(eligibleAt)) continue

            originateOne(port, pool[rnd.nextInt(pool.size)], mode, minDur, maxDur, minInt, maxInt)
            return // Ù…ÙƒØ§Ù„Ù…Ø© ÙˆØ§Ø­Ø¯Ø© Ù„ÙƒÙ„ Ù†Ø¨Ø¶Ø© â€” Ø¥ÙŠÙ‚Ø§Ø¹ Ø¨Ø´Ø±ÙŠ ØºÙŠØ± Ù…ØªØ²Ø§Ù…Ù† Ø¨ÙŠÙ† Ø§Ù„Ù…Ù†Ø§ÙØ°
        }
    }

    /** ØªØ´ØºÙŠÙ„ ÙŠØ¯ÙˆÙŠ ÙÙˆØ±ÙŠ (ÙŠØªØ¬Ø§ÙˆØ² Ø§Ù„ÙÙˆØ§ØµÙ„ Ù„ÙƒÙ† Ù„ÙŠØ³ Ø§Ù„Ø³Ù‚Ù ÙˆÙ„Ø§ Ù†Ø§ÙØ°Ø© Ø§Ù„ØªØ´ØºÙŠÙ„ Ø¹Ù†Ø¯ OFF) */
    fun triggerNow(adminId: UUID, port: Int?): Map<String, Any?> {
        val cfg = jdbc.queryForMap("SELECT * FROM number_learning_config WHERE id = 1")
        val mode = cfg["mode"]?.toString() ?: "OFF"
        require(mode != "OFF") { "Enable LEARN or MAINTAIN mode first" }
        val targetPort = port ?: 0
        require(targetPort in 0..15) { "port must be 0-15" }
        val cap = (cfg["daily_cap_per_port"] as Number).toInt()
        val today = LocalDate.now(ZONE)
        val used = jdbc.queryForObject("SELECT COUNT(*) FROM number_learning_calls WHERE port=? AND started_at::date=? AND status<>'FAILED'", Long::class.java, targetPort, today) ?: 0L
        if (used >= cap) return mapOf("status" to "CAPPED", "usedToday" to used, "cap" to cap)

        val pool = jdbc.queryForList("SELECT number FROM number_learning_pool WHERE active").map { it["number"].toString() }
        require(pool.isNotEmpty()) { "Learning pool is empty" }
        val minDur = (cfg["min_duration_seconds"] as Number).toInt()
        val maxDur = (cfg["max_duration_seconds"] as Number).toInt()
        val result = originateOne(targetPort, pool[rnd.nextInt(pool.size)], mode, minDur, maxDur, 0L, 0L)
        adminAudit.recordAudit(adminId = adminId, adminUsername = null, action = "NUMBER_LEARNING_TRIGGERED", category = "DINSTAR", targetType = "PORT", targetId = targetPort.toString())
        return result
    }

    private fun originateOne(port: Int, number: String, mode: String, minDur: Int, maxDur: Int, minIntervalMin: Long, maxIntervalMin: Long): Map<String, Any?> {
        val duration = if (maxDur > minDur) minDur + rnd.nextInt(maxDur - minDur + 1) else minDur
        val correlationId = try {
            pstn.dialGsm(number, waitSeconds = duration)
        } catch (e: Exception) {
            jdbc.update(
                "INSERT INTO number_learning_calls(id,port,number,correlation_id,mode,status,duration_seconds,error,started_at,next_eligible_at) VALUES (?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,NULL)",
                UUID.randomUUID(), port, number, null, mode, "FAILED", duration, e.message?.take(300)
            )
            log.warn("Learning call failed port={} number={}: {}", port, number, e.message)
            return mapOf("status" to "FAILED", "port" to port, "number" to number, "error" to e.message)
        }
        val jitterMin = if (maxIntervalMin > minIntervalMin) minIntervalMin + rnd.nextInt((maxIntervalMin - minIntervalMin + 1).toInt()).toLong() else minIntervalMin
        val nextEligible = Instant.now().plusSeconds(jitterMin * 60)
        jdbc.update(
            "INSERT INTO number_learning_calls(id,port,number,correlation_id,mode,status,duration_seconds,started_at,next_eligible_at) VALUES (?,?,?,?,?,?,?,CURRENT_TIMESTAMP,?)",
            UUID.randomUUID(), port, number, correlationId, mode, "ORIGINATED", duration, nextEligible
        )
        log.info("Learning call originated: port={} number={} duration={}s corr={}", port, number, duration, correlationId)
        return mapOf("status" to "ORIGINATED", "port" to port, "number" to number, "durationSeconds" to duration, "correlationId" to correlationId, "nextEligibleAt" to nextEligible)
    }

    /** ØªØ±Ù‚ÙŠØ© Ø§Ù„Ø­Ø§Ù„Ø§Øª: ORIGINATED Ø§Ù„ØªÙŠ Ø§Ù†ØªÙ‡Øª Ù…Ø¯ØªÙ‡Ø§ ØªÙØ¹Ù„ÙŽÙ‘Ù… COMPLETED (Ù…Ø¯Ø© ØªÙ‚Ø¯ÙŠØ±ÙŠØ© Ø¨Ù…Ù‚Ø¯Ø§Ø± Wait Ø§Ù„Ù…Ø·Ù„ÙˆØ¨) */
    @Scheduled(fixedDelay = 120_000)
    fun sweepCompleted() {
        runCatching {
            jdbc.update("UPDATE number_learning_calls SET status='COMPLETED' WHERE status='ORIGINATED' AND started_at + (duration_seconds || ' seconds')::interval <= CURRENT_TIMESTAMP")
        }.onFailure { log.warn("sweepCompleted failed: {}", it.message) }
    }

    // â”â”â”â”â”â”â”â”â”â” Native-gateway probe (read-only) â”â”â”â”â”â”â”â”â”â”

    /**
     * ÙØ­Øµ Ø¢Ù…Ù† (GET ÙÙ‚Ø·) Ù„Ù…Ø³Ø§Ø±Ø§Øª Human Behavior/Number Learning Ø§Ù„Ø£ØµÙ„ÙŠØ© Ø§Ù„Ù…Ø­ØªÙ…Ù„Ø© Ø¹Ù„Ù‰ Ø§Ù„Ø¨ÙˆØ§Ø¨Ø©.
     * Ø§Ù„Ù‡Ø¯Ù: Ø¹Ù†Ø¯ ØªÙˆÙØ± Ø¨ÙŠØ§Ù†Ø§Øª Ø¯Ø®ÙˆÙ„ ØµØ­ÙŠØ­Ø© Ù†Ø¹Ø±Ù ÙÙˆØ±Ø§Ù‹ Ø¥Ù† ÙƒØ§Ù†Øª Ø§Ù„ÙÙŠØ±Ù…ÙˆÙŽÙ† ØªØ¯Ø¹Ù… API Ø£ØµÙ„ÙŠØ§Ù‹ Ù„Ù†Ù†ØªÙ‚Ù„ Ø¥Ù„ÙŠÙ‡.
     */
    fun probeNativeEndpoints(): Map<String, Any?> = hardware.probeHumanBehaviorEndpoints()
}

