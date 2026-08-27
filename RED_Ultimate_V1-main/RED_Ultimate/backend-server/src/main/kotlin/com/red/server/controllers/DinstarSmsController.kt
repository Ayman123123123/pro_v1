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
 * Dinstar SMS Controller â€” إرسال واستقبال SMS عبر UC2000-VE-8G
 * 
 * Endpoints المعرّفة حسب وثائق Dinstar الرسمية:
 * 
 * POST /api/admin/dinstar/sms/send     â†’ إرسال SMS (فردي/مجمّع)
 * POST /api/admin/dinstar/sms/result   â†’ جلب نتائج الإرسال
 * GET  /api/admin/dinstar/sms/incoming â†’ جلب SMS الواردة
 * GET  /api/admin/dinstar/sms/queue    â†’ عدد SMS في الطابور
 * POST /api/admin/dinstar/sms/stop     â†’ إيقاف مهمة إرسال
 * POST /api/admin/dinstar/sms/deliver  â†’ جلب حالة التسليم
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
     * هل المُستدعي أدمن؟ الأدمن وحده يتحكم بمنافذ/بوابات الإرسال بحرية
     * (حملات لوحة التحكم). المستخدم العادي يُحبَس على شريحته المربوطة
     * 1:1 (pstn_gateway_id/pstn_port_index) â€” إغلاق ثغرة إرسال SMS من
     * شريحة غيره أو استهداف بوابة اعتباطية عبر gatewayHost.
     */
    private fun isAdmin(authentication: Authentication): Boolean =
        authentication.authorities.any { it.authority == "ROLE_ADMIN" }

    /** نطاق الشريحة الإلزامي للمستخدم العادي: (gatewayHost, portIndex) */
    private fun resolveBoundScope(user: com.red.server.auth.model.UserAccount): Pair<String, Int>? {
        val gwId = user.pstnGatewayId ?: return null
        val portIdx = user.pstnPortIndex ?: return null
        val host = fleet.findGateway(gwId)?.host ?: return null
        return host to portIdx
    }

    /** يفلتر رسائل واردة على عنصر port محدد مع دعم أشكال الاستجابة الشائعة */
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
     * إرسال SMS عبر Dinstar
     * 
     * الجسم (JSON):
     * {
     *   "text": "محتوى الرسالة",
     *   "param": [{"number": "777123456", "user_id": 1}],
     *   "port": [0, 1],        // اختياري: منافذ محددة
     *   "encoding": "AUTO",    // اختياري: AUTO (افتراضي، يشتق من النص) أو GSM7BIT أو UCS2 â€” الافتراضي AUTO يصلح رسائل عربية كانت تصل Â«?????Â»
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
        // الافتراضي AUTO: تشتق الخدمة الترميز من النص. تثبيت GSM7BIT هنا كان يُبطل الاشتقاق ويجعل كل رسالة عربية تصل Â«?????Â».
        val encoding = body["encoding"]?.toString() ?: DinstarHardwareService.AUTO_ENCODING
        // اختياري: توجيه الإرسال لبوابة بعينها. للأدمن فقط â€” المستخدم العادي يُرسل حصراً من شريحته المربوطة
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

    /** جلب نتائج إرسال SMS */
    @PostMapping("/result")
    fun querySmsResult(@RequestBody body: Map<String, Any?>): Map<String, Any?> {
        val userIds = (body["user_id"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
        val numbers = (body["number"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
        return hardware.querySmsResult(userIds, numbers)
    }

    /** جلب حالة تسليم SMS */
    @PostMapping("/deliver")
    fun querySmsDeliveryStatus(@RequestBody body: Map<String, Any?>): Map<String, Any?> {
        val numbers = (body["number"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
        val timeAfter = body["time_after"]?.toString()
        val timeBefore = body["time_before"]?.toString()
        return hardware.querySmsDeliveryStatus(numbers, timeAfter, timeBefore)
    }

    /** جلب SMS الواردة â€” المستخدم العادي يرى رسائل شريحته فقط */
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

    /** عدد SMS في الطابور */
    @GetMapping("/queue")
    fun querySmsQueueCount(): Map<String, Any?> = hardware.querySmsQueueCount()

    /** إيقاف مهمة إرسال SMS */
    @PostMapping("/stop")
    fun stopSmsTask(@RequestBody body: Map<String, Any?>): Map<String, Any?> {
        val taskId = (body["task_id"] as? Number)?.toInt()
            ?: throw IllegalArgumentException("task_id is required")
        return hardware.stopSmsTask(taskId)
    }
}

