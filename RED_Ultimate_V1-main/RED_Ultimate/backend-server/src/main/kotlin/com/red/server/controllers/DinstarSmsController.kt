package com.red.server.controllers

import com.red.server.audit.AuditService
import com.red.server.services.DinstarHardwareService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

/**
 * Dinstar SMS Controller — إرسال واستقبال SMS عبر UC2000-VE-8G
 * 
 * Endpoints المعرّفة حسب وثائق Dinstar الرسمية:
 * 
 * POST /api/admin/dinstar/sms/send     → إرسال SMS (فردي/مجمّع)
 * POST /api/admin/dinstar/sms/result   → جلب نتائج الإرسال
 * GET  /api/admin/dinstar/sms/incoming → جلب SMS الواردة
 * GET  /api/admin/dinstar/sms/queue    → عدد SMS في الطابور
 * POST /api/admin/dinstar/sms/stop     → إيقاف مهمة إرسال
 * POST /api/admin/dinstar/sms/deliver  → جلب حالة التسليم
 */
@RestController
@RequestMapping("/api/admin/dinstar/sms")
class DinstarSmsController(
    private val hardware: DinstarHardwareService,
    private val audit: AuditService
) {

    /**
     * إرسال SMS عبر Dinstar
     * 
     * الجسم (JSON):
     * {
     *   "text": "محتوى الرسالة",
     *   "param": [{"number": "777123456", "user_id": 1}],
     *   "port": [0, 1],        // اختياري: منافذ محددة
     *   "encoding": "GSM7BIT", // اختياري: GSM7BIT أو UCS2
     *   "request_status_report": true
     * }
     */
    @PostMapping("/send")
    fun sendSms(
        @RequestBody body: Map<String, Any?>,
        authentication: Authentication
    ): Map<String, Any?> {
        val actor = UUID.fromString(authentication.name)
        val text = body["text"]?.toString() ?: throw IllegalArgumentException("SMS text is required")
        @Suppress("UNCHECKED_CAST")
        val params = (body["param"] as? List<Map<String, Any?>>) ?: throw IllegalArgumentException("param array is required")
        
        val portList = (body["port"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }
        val encoding = body["encoding"]?.toString() ?: "GSM7BIT"
        
        audit.record(actor, "DINSTAR_SMS_SEND", text.length.toString(), mapOf(
            "recipientCount" to params.size, "encoding" to encoding, "ports" to (portList?.toString() ?: "all")
        ))
        
        return hardware.sendSms(text, params, portList, encoding)
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

    /** جلب SMS الواردة */
    @GetMapping("/incoming")
    fun queryIncomingSms(): Map<String, Any?> = hardware.queryIncomingSms()

    /** عدد SMS في الطابور */
    @GetMapping("/queue")
    fun querySmsQueueCount(): Map<String, Any> = hardware.querySmsQueueCount()

    /** إيقاف مهمة إرسال SMS */
    @PostMapping("/stop")
    fun stopSmsTask(@RequestBody body: Map<String, Any?>): Map<String, Any> {
        val taskId = (body["task_id"] as? Number)?.toInt()
            ?: throw IllegalArgumentException("task_id is required")
        return hardware.stopSmsTask(taskId)
    }
}
