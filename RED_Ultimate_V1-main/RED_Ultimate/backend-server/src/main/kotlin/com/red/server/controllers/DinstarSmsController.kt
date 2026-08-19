package com.red.server.controllers

import com.red.server.audit.AuditService
import com.red.server.services.DinstarHardwareService
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Dinstar SMS Controller — إرسال واستقبال SMS عبر بوابة DINSTAR.
 * جميع المسارات محمية بدور ADMIN في SecurityConfig.
 */
@RestController
@RequestMapping("/api/admin/dinstar/sms")
class DinstarSmsController(
    private val hardware: DinstarHardwareService,
    private val audit: AuditService
) {
    /**
     * يحافظ العقد على صيغة Dinstar (`param`, و`user_id`) لكنه يرفض الدفعات
     * الفارغة أو الأرقام غير الصالحة قبل الاتصال بالعتاد. حد البايتات لنص
     * العربية يظل في DinstarHardwareService لأنه قاعدة بروتوكول الجهاز.
     */
    @PostMapping("/send")
    fun sendSms(
        @Valid @RequestBody request: SendSmsRequest,
        authentication: Authentication
    ): Map<String, Any?> {
        val actor = UUID.fromString(authentication.name)
        val params = request.toHardwareParams()
        audit.record(actor, "DINSTAR_SMS_SEND", request.text.length.toString(), mapOf(
            "recipientCount" to params.size,
            "encoding" to request.encoding,
            "ports" to (request.port?.toString() ?: "all"),
            "gateway" to (request.gatewayHost ?: "active")
        ))
        return hardware.sendSms(request.text, params, request.port, request.encoding, request.gatewayHost)
    }

    /** جلب نتائج إرسال SMS. */
    @PostMapping("/result")
    fun querySmsResult(@RequestBody body: Map<String, Any?>): Map<String, Any?> {
        val userIds = (body["user_id"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: emptyList()
        val numbers = (body["number"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
        return hardware.querySmsResult(userIds, numbers)
    }

    /** جلب حالة تسليم SMS. */
    @PostMapping("/deliver")
    fun querySmsDeliveryStatus(@RequestBody body: Map<String, Any?>): Map<String, Any?> {
        val numbers = (body["number"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
        val timeAfter = body["time_after"]?.toString()
        val timeBefore = body["time_before"]?.toString()
        return hardware.querySmsDeliveryStatus(numbers, timeAfter, timeBefore)
    }

    /** جلب SMS الواردة. */
    @GetMapping("/incoming")
    fun queryIncomingSms(): Map<String, Any?> = hardware.queryIncomingSms()

    /** عدد SMS في الطابور. */
    @GetMapping("/queue")
    fun querySmsQueueCount(): Map<String, Any?> = hardware.querySmsQueueCount()

    /** إيقاف مهمة إرسال SMS. */
    @PostMapping("/stop")
    fun stopSmsTask(@RequestBody body: Map<String, Any?>): Map<String, Any?> {
        val taskId = (body["task_id"] as? Number)?.toInt()
            ?: throw IllegalArgumentException("task_id is required")
        return hardware.stopSmsTask(taskId)
    }
}

data class SendSmsRequest(
    @field:NotBlank
    @field:Size(max = DinstarHardwareService.MAX_SMS_TEXT_BYTES)
    val text: String,
    @field:Size(min = 1, max = DinstarHardwareService.MAX_SMS_RECIPIENTS)
    @field:Valid
    val param: List<SmsRecipient>,
    @field:Size(max = DinstarHardwareService.MAX_SMS_RECIPIENTS)
    val port: List<@Min(0) Int>? = null,
    @field:Pattern(regexp = "GSM7BIT|UCS2")
    val encoding: String = "GSM7BIT",
    @field:Size(max = 255)
    val gatewayHost: String? = null
) {
    fun toHardwareParams(): List<Map<String, Any?>> = param.map { recipient ->
        buildMap {
            put("number", recipient.number)
            recipient.user_id?.let { put("user_id", it) }
        }
    }
}

data class SmsRecipient(
    @field:NotBlank
    @field:Pattern(regexp = "\\+?[0-9]{6,20}")
    val number: String,
    @field:Min(0)
    val user_id: Int? = null
)
