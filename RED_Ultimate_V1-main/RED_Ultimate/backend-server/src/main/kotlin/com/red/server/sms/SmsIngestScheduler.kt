package com.red.server.sms

import com.red.server.websocket.PstnEventWebSocketHandler
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * 🔄 مجدولة استقبال الرسائل الواردة ومراجعة حالة التسليم.
 *
 * - كل 12s: تجميع الوارد من DINSTAR → تخزين → إرسال `SMS_RECEIVED` للكل.
 * - كل 60s: مراجعة تسليم الرسائل SENT → تحديث الحالة → دفع `SMS_STATUS`.
 *
 * لا يحتاج `spring.scheduling.enabled` صراحته لأن PstnManager يعتمد على
 * `@Scheduled` بالفعل — التفعيل موجود.
 */
@Component
class SmsIngestScheduler(
    private val sms: SmsService,
    private val eventHub: PstnEventWebSocketHandler
) {
    companion object {
        private val log = LoggerFactory.getLogger(SmsIngestScheduler::class.java)
    }

    @Scheduled(fixedDelay = 12_000, initialDelay = 12_000)
    fun ingestIncoming() {
        val fresh = sms.ingestIncoming()
        if (fresh.isEmpty()) return
        log.info("Ingested {} incoming SMS", fresh.size)
        fresh.forEach { msg ->
            eventHub.broadcastSmsReceived(mapOf(
                "id" to msg.id.toString(),
                "number" to msg.number,
                "content" to msg.content,
                "time" to msg.createdAt.epochSecond,
                "port" to (msg.port ?: -1)
            ))
        }
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    fun pollDelivery() {
        val changed = sms.pollDelivery()
        if (changed.isEmpty()) return
        changed.forEach { msg ->
            val owner = msg.ownerId?.toString()
            if (owner != null) {
                eventHub.pushSmsStatus(owner, mapOf(
                    "id" to msg.id.toString(),
                    "number" to msg.number,
                    "status" to msg.status.name
                ))
            }
        }
        log.info("Delivery status updated for {} messages", changed.size)
    }
}