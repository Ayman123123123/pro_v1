package com.red.server.sms

import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

/**
 * 📨 YOUNES Professional SMS Controller — مسارات مستخدم عادي.
 *
 * تدفق العمل:
 * - POST /api/sms/send             → إرسال مع حفظ دائم + حالة
 * - GET  /api/sms/conversations    → قائمة المحادثات (أحدث رسالة + غير مقروءة)
 * - GET  /api/sms/conversation/{n} → سجل كامل الرسائل لرقم
 * - POST /api/sms/read             → تعليم الرسائل كمقروءة
 * - DELETE /api/sms/{id}           → حذف رسالة (ملكية أو مشتركة)
 * - POST /api/sms/refresh          → جلب وارد DINSTAR + إرجاع المحادثات (تحديث يدوي)
 */
@RestController
@RequestMapping("/api/sms")
class SmsController(private val sms: SmsService) {

    @PostMapping("/send")
    fun send(@RequestBody body: SendSmsRequest, authentication: Authentication): ResponseEntity<Any> {
        val userId = UUID.fromString(authentication.name)
        val entity = try {
            sms.send(userId, body.number, body.text, body.port)
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().body(mapOf("error" to (e.message ?: "INVALID_REQUEST")))
        }
        return ResponseEntity.ok(mapOf(
            "id" to entity.id,
            "status" to entity.status.name,
            "number" to entity.number,
            "parts" to entity.smsParts,
            "encoding_auto" to "AUTO"
        ))
    }

    @GetMapping("/conversations")
    fun conversations(authentication: Authentication): ResponseEntity<List<SmsService.ConversationDto>> {
        val userId = UUID.fromString(authentication.name)
        return ResponseEntity.ok(sms.conversations(userId))
    }

    @GetMapping("/conversation/{number}")
    fun conversation(@PathVariable number: String, authentication: Authentication): ResponseEntity<List<SmsService.MessageDto>> {
        val userId = UUID.fromString(authentication.name)
        val normalized = number.filter { it.isDigit() || it == '+' }
        return ResponseEntity.ok(sms.conversation(userId, normalized))
    }

    @PostMapping("/read")
    fun markRead(@RequestBody body: Map<String, String>, authentication: Authentication): ResponseEntity<Map<String, Any>> {
        val number = body["number"]
        if (number.isNullOrBlank()) return ResponseEntity.badRequest().body(mapOf("error" to "number required"))
        val userId = UUID.fromString(authentication.name)
        sms.markRead(userId, number)
        return ResponseEntity.ok(mapOf("ok" to true))
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: UUID, authentication: Authentication): ResponseEntity<Map<String, Any>> {
        val userId = UUID.fromString(authentication.name)
        return try {
            sms.deleteMessage(userId, id)
            ResponseEntity.ok(mapOf("ok" to true))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.status(403).body(mapOf("error" to (e.message ?: "NOT_YOURS")))
        } catch (e: NoSuchElementException) {
            ResponseEntity.notFound().build()
        }
    }

    @PostMapping("/refresh")
    fun refresh(authentication: Authentication): ResponseEntity<List<SmsService.ConversationDto>> {
        sms.ingestIncoming()
        val userId = UUID.fromString(authentication.name)
        return ResponseEntity.ok(sms.conversations(userId))
    }
}

data class SendSmsRequest(
    val number: String,
    val text: String,
    val port: List<Int>? = null
)