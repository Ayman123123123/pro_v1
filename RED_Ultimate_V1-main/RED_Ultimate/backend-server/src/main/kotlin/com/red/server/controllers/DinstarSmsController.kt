package com.red.server.controllers

import com.red.server.audit.AuditService
import com.red.server.auth.repository.UserAccountRepository
import com.red.server.services.DinstarFleetService
import com.red.server.services.DinstarHardwareService
import com.red.server.services.DinstarSmsContract
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

/**
 * Dinstar SMS Controller â€” Ø¥Ø±Ø³Ø§Ù„ ÙˆØ§Ø³ØªÙ‚Ø¨Ø§Ù„ SMS Ø¹Ø¨Ø± UC2000-VE-8G
 * 
 * Endpoints Ø§Ù„Ù…Ø¹Ø±Ù‘ÙØ© Ø­Ø³Ø¨ ÙˆØ«Ø§Ø¦Ù‚ Dinstar Ø§Ù„Ø±Ø³Ù…ÙŠØ©:
 * 
 * POST /api/admin/dinstar/sms/send     â†’ Ø¥Ø±Ø³Ø§Ù„ SMS (ÙØ±Ø¯ÙŠ/Ù…Ø¬Ù…Ù‘Ø¹)
 * POST /api/admin/dinstar/sms/result   â†’ Ø¬Ù„Ø¨ Ù†ØªØ§Ø¦Ø¬ Ø§Ù„Ø¥Ø±Ø³Ø§Ù„
 * GET  /api/admin/dinstar/sms/incoming â†’ Ø¬Ù„Ø¨ SMS Ø§Ù„ÙˆØ§Ø±Ø¯Ø©
 * GET  /api/admin/dinstar/sms/queue    â†’ Ø¹Ø¯Ø¯ SMS ÙÙŠ Ø§Ù„Ø·Ø§Ø¨ÙˆØ±
 * POST /api/admin/dinstar/sms/stop     â†’ Ø¥ÙŠÙ‚Ø§Ù Ù…Ù‡Ù…Ø© Ø¥Ø±Ø³Ø§Ù„
 * POST /api/admin/dinstar/sms/deliver  â†’ Ø¬Ù„Ø¨ Ø­Ø§Ù„Ø© Ø§Ù„ØªØ³Ù„ÙŠÙ…
 */
@RestController
@RequestMapping("/api/admin/dinstar/sms")
class DinstarSmsController(
    private val hardware: DinstarHardwareService,
    private val audit: AuditService,
    private val users: UserAccountRepository,
    private val fleet: DinstarFleetService,
    private val smsContract: DinstarSmsContract
) {

    /**
     * Ù‡Ù„ Ø§Ù„Ù…ÙØ³ØªØ¯Ø¹ÙŠ Ø£Ø¯Ù…Ù†ØŸ Ø§Ù„Ø£Ø¯Ù…Ù† ÙˆØ­Ø¯Ù‡ ÙŠØªØ­ÙƒÙ… Ø¨Ù…Ù†Ø§ÙØ°/Ø¨ÙˆØ§Ø¨Ø§Øª Ø§Ù„Ø¥Ø±Ø³Ø§Ù„ Ø¨Ø­Ø±ÙŠØ©
     * (Ø­Ù…Ù„Ø§Øª Ù„ÙˆØ­Ø© Ø§Ù„ØªØ­ÙƒÙ…). Ø§Ù„Ù…Ø³ØªØ®Ø¯Ù… Ø§Ù„Ø¹Ø§Ø¯ÙŠ ÙŠÙØ­Ø¨ÙŽØ³ Ø¹Ù„Ù‰ Ø´Ø±ÙŠØ­ØªÙ‡ Ø§Ù„Ù…Ø±Ø¨ÙˆØ·Ø©
     * 1:1 (pstn_gateway_id/pstn_port_index) â€” Ø¥ØºÙ„Ø§Ù‚ Ø«ØºØ±Ø© Ø¥Ø±Ø³Ø§Ù„ SMS Ù…Ù†
     * Ø´Ø±ÙŠØ­Ø© ØºÙŠØ±Ù‡ Ø£Ùˆ Ø§Ø³ØªÙ‡Ø¯Ø§Ù Ø¨ÙˆØ§Ø¨Ø© Ø§Ø¹ØªØ¨Ø§Ø·ÙŠØ© Ø¹Ø¨Ø± gatewayHost.
     */
    private fun isAdmin(authentication: Authentication): Boolean =
        authentication.authorities.any { it.authority == "ROLE_ADMIN" }

    /** Ù†Ø·Ø§Ù‚ Ø§Ù„Ø´Ø±ÙŠØ­Ø© Ø§Ù„Ø¥Ù„Ø²Ø§Ù…ÙŠ Ù„Ù„Ù…Ø³ØªØ®Ø¯Ù… Ø§Ù„Ø¹Ø§Ø¯ÙŠ: (gatewayHost, portIndex) */
    private fun resolveBoundScope(user: com.red.server.auth.model.UserAccount): Pair<String, Int>? {
        val gwId = user.pstnGatewayId ?: return null
        val portIdx = user.pstnPortIndex ?: return null
        val host = fleet.findGateway(gwId)?.host ?: return null
        return host to portIdx
    }

    /** ÙŠÙÙ„ØªØ± Ø±Ø³Ø§Ø¦Ù„ ÙˆØ§Ø±Ø¯Ø© Ø¹Ù„Ù‰ Ø¹Ù†ØµØ± port Ù…Ø­Ø¯Ø¯ Ù…Ø¹ Ø¯Ø¹Ù… Ø£Ø´ÙƒØ§Ù„ Ø§Ù„Ø§Ø³ØªØ¬Ø§Ø¨Ø© Ø§Ù„Ø´Ø§Ø¦Ø¹Ø© */
    private fun filterMessagesForPort(raw: Map<String, Any?>, port: Int): Map<String, Any?> {
        fun match(item: Any?): Boolean {
            if (item !is Map<*, *>) return false
            val p = item["port"] ?: item["port_index"] ?: return false
            return p.toString().trim().toIntOrNull() == port
        }
        val filtered = HashMap(raw)
        for ((k, v) in raw.entries) {
            if (v is List<*> && v.isNotEmpty() && v.first() is Map<*, *> && (v.first() as Map<*, *>).keys.any { it == "port" || it == "port_index" }) {
                filtered[k] = v.filter { match(it) }
            }
        }
        return filtered
    }

    /**
     * Ø¥Ø±Ø³Ø§Ù„ SMS Ø¹Ø¨Ø± Dinstar
     * 
     * Ø§Ù„Ø¬Ø³Ù… (JSON):
     * {
     *   "text": "Ù…Ø­ØªÙˆÙ‰ Ø§Ù„Ø±Ø³Ø§Ù„Ø©",
     *   "param": [{"number": "777123456", "user_id": 1}],
     *   "port": [0, 1],        // Ø§Ø®ØªÙŠØ§Ø±ÙŠ: Ù…Ù†Ø§ÙØ° Ù…Ø­Ø¯Ø¯Ø©
     *   "encoding": "AUTO",    // Ø§Ø®ØªÙŠØ§Ø±ÙŠ: AUTO (Ø§ÙØªØ±Ø§Ø¶ÙŠØŒ ÙŠØ´ØªÙ‚ Ù…Ù† Ø§Ù„Ù†Øµ) Ø£Ùˆ GSM7BIT Ø£Ùˆ UCS2 â€” Ø§Ù„Ø§ÙØªØ±Ø§Ø¶ÙŠ AUTO ÙŠØµÙ„Ø­ Ø±Ø³Ø§Ø¦Ù„ Ø¹Ø±Ø¨ÙŠØ© ÙƒØ§Ù†Øª ØªØµÙ„ Â«?????Â»
     *   "request_status_report": true
     * }
     */
    @PostMapping("/send")
    fun sendSms(
        @RequestBody body: Map<String, Any?>,
        authentication: Authentication
    ): Map<String, Any?> {
        val actor = UUID.fromString(authentication.name)
        val user = users.findById(actor).orElseThrow { NoSuchElementException("User not found") }
        if (!user.pstnEnabled) {
            return mapOf("error" to "SMS_NOT_ENABLED", "message" to "SMS is not enabled for your account")
        }
        val text = body["text"]?.toString() ?: throw IllegalArgumentException("SMS text is required")
        @Suppress("UNCHECKED_CAST")
        val params = (body["param"] as? List<Map<String, Any?>>) ?: throw IllegalArgumentException("param array is required")
        val preparedRecipients = smsContract.prepare(params)
        
        val portList = (body["port"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }
        // Ø§Ù„Ø§ÙØªØ±Ø§Ø¶ÙŠ AUTO: ØªØ´ØªÙ‚ Ø§Ù„Ø®Ø¯Ù…Ø© Ø§Ù„ØªØ±Ù…ÙŠØ² Ù…Ù† Ø§Ù„Ù†Øµ. ØªØ«Ø¨ÙŠØª GSM7BIT Ù‡Ù†Ø§ ÙƒØ§Ù† ÙŠÙØ¨Ø·Ù„ Ø§Ù„Ø§Ø´ØªÙ‚Ø§Ù‚ ÙˆÙŠØ¬Ø¹Ù„ ÙƒÙ„ Ø±Ø³Ø§Ù„Ø© Ø¹Ø±Ø¨ÙŠØ© ØªØµÙ„ Â«?????Â».
        val encoding = body["encoding"]?.toString() ?: DinstarHardwareService.AUTO_ENCODING
        // Ø§Ø®ØªÙŠØ§Ø±ÙŠ: ØªÙˆØ¬ÙŠÙ‡ Ø§Ù„Ø¥Ø±Ø³Ø§Ù„ Ù„Ø¨ÙˆØ§Ø¨Ø© Ø¨Ø¹ÙŠÙ†Ù‡Ø§. Ù„Ù„Ø£Ø¯Ù…Ù† ÙÙ‚Ø· â€” Ø§Ù„Ù…Ø³ØªØ®Ø¯Ù… Ø§Ù„Ø¹Ø§Ø¯ÙŠ ÙŠÙØ±Ø³Ù„ Ø­ØµØ±Ø§Ù‹ Ù…Ù† Ø´Ø±ÙŠØ­ØªÙ‡ Ø§Ù„Ù…Ø±Ø¨ÙˆØ·Ø©
        val requestedHost = body["gatewayHost"]?.toString()

        val effectivePorts: List<Int>?
        val effectiveHost: String?
        if (isAdmin(authentication)) {
            effectivePorts = portList
            effectiveHost = requestedHost
        } else {
            val scope = resolveBoundScope(user)
                ?: return mapOf(
                    "error" to "SIM_NOT_BOUND",
                    "message" to "No permanent SIM bound to this account â€” ask the administrator"
                )
            effectiveHost = scope.first
            effectivePorts = listOf(scope.second)
        }

        audit.record(actor, "DINSTAR_SMS_SEND", text.length.toString(), mapOf(
            "recipientCount" to preparedRecipients.recipients.size, "encoding" to encoding,
            "ports" to (effectivePorts?.toString() ?: "all"),
            "gateway" to (effectiveHost ?: "active"),
            "admin" to isAdmin(authentication)
        ))

        val gatewayResponse = hardware.sendSms(text, preparedRecipients.recipients, effectivePorts, encoding, effectiveHost)
        val taskId = (gatewayResponse["task_id"] as? Number)?.toInt()
        val queueCount = (gatewayResponse["sms_in_queue"] as? Number)?.toInt()
        return mapOf(
            "status" to if (DinstarSmsContract.isAccepted(gatewayResponse)) "ACCEPTED" else "FAILED",
            "taskId" to taskId,
            "queueCount" to queueCount,
            "userIds" to preparedRecipients.userIds
        )
    }

    /** Ø¬Ù„Ø¨ Ù†ØªØ§Ø¦Ø¬ Ø¥Ø±Ø³Ø§Ù„ SMS */
    @PostMapping("/result")
    fun querySmsResult(@RequestBody body: Map<String, Any?>): Map<String, Any?> {
        val userIds = (body["user_id"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
        val numbers = (body["number"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
        return hardware.querySmsResult(userIds, numbers)
    }

    /** Ø¬Ù„Ø¨ Ø­Ø§Ù„Ø© ØªØ³Ù„ÙŠÙ… SMS */
    @PostMapping("/deliver")
    fun querySmsDeliveryStatus(@RequestBody body: Map<String, Any?>): Map<String, Any?> {
        val numbers = (body["number"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
        val timeAfter = body["time_after"]?.toString()
        val timeBefore = body["time_before"]?.toString()
        return hardware.querySmsDeliveryStatus(numbers, timeAfter, timeBefore)
    }

    /** Ø¬Ù„Ø¨ SMS Ø§Ù„ÙˆØ§Ø±Ø¯Ø© â€” Ø§Ù„Ù…Ø³ØªØ®Ø¯Ù… Ø§Ù„Ø¹Ø§Ø¯ÙŠ ÙŠØ±Ù‰ Ø±Ø³Ø§Ø¦Ù„ Ø´Ø±ÙŠØ­ØªÙ‡ ÙÙ‚Ø· */
    @GetMapping("/incoming")
    fun queryIncomingSms(authentication: Authentication): Map<String, Any?> {
        val actor = UUID.fromString(authentication.name)
        val user = users.findById(actor).orElseThrow { NoSuchElementException("User not found") }
        if (!user.pstnEnabled) {
            return mapOf("error" to "SMS_NOT_ENABLED", "messages" to emptyList<Any>())
        }
        if (!isAdmin(authentication)) {
            val scope = resolveBoundScope(user)
                ?: return mapOf("error" to "SIM_NOT_BOUND", "messages" to emptyList<Any>())
            return filterMessagesForPort(hardware.queryIncomingSms(), scope.second)
        }
        return hardware.queryIncomingSms()
    }

    /** Ø¹Ø¯Ø¯ SMS ÙÙŠ Ø§Ù„Ø·Ø§Ø¨ÙˆØ± */
    @GetMapping("/queue")
    fun querySmsQueueCount(): Map<String, Any?> = hardware.querySmsQueueCount()

    /** Ø¥ÙŠÙ‚Ø§Ù Ù…Ù‡Ù…Ø© Ø¥Ø±Ø³Ø§Ù„ SMS */
    @PostMapping("/stop")
    fun stopSmsTask(@RequestBody body: Map<String, Any?>): Map<String, Any?> {
        val taskId = (body["task_id"] as? Number)?.toInt()
            ?: throw IllegalArgumentException("task_id is required")
        return hardware.stopSmsTask(taskId)
    }
}

