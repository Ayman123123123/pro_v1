package com.red.server.websocket

import com.red.server.database.RedisManager
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.TimeUnit

/**
 * قناة typing الموحدة — كانت تستخدم chat:typing:{conv} بينما RedMasterHandler يستخدم red:typing + red:typing:{conv}:{user}
 * الآن كلاهما ينشر على نفس القناة الموحدة red:typing مع TTL 5s لتوافق الخدمات الخلفية واختبارات التكامل.
 * المسار /ws/typing يبقى للتوافق لكن المسار المفضل هو /ws/master (TypingRED).
 */
@Component
class TypingHandler(
    private val redis: StringRedisTemplate,
    private val redisManager: RedisManager
) : TextWebSocketHandler() {
    private val log = LoggerFactory.getLogger(TypingHandler::class.java)

    /**
     * نشر حالة "يكتب الآن" عبر Redis لكل المشتركين في المحادثة
     * موحّد مع RedMasterHandler: نفس القناة ونفس TTL
     */
    fun broadcastTyping(userId: String, conversationId: String, isTyping: Boolean) {
        val payload = if (isTyping) "1" else "0"
        // القناة الموحدة
        redis.convertAndSend("red:typing", "$conversationId:$userId:$payload")
        redis.convertAndSend("chat:typing:$conversationId", "$userId:$payload")
        // TTL 5s عبر RedisManager + مفتاح مباشر للتوافق مع المسارات القديمة
        runCatching { redisManager.setTyping(userId, conversationId) }
        if (isTyping) {
            runCatching { redis.opsForValue().set("red:typing:$conversationId:$userId", "1", 5, TimeUnit.SECONDS) }
        } else {
            runCatching { redis.delete("red:typing:$conversationId:$userId") }
        }
    }

    public override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        try {
            val raw = message.payload.trim()
            if (raw.isEmpty()) return
            // يدعم JSON {"conversationId":"...","isTyping":true,"targetUserId":"..."} أو صيغة قديمة "convId:1"
            var convId: String? = null
            var isTyping = true
            var target: String? = null
            if (raw.startsWith("{")) {
                // تحليل JSON بسيط بدون اعتماد Jackson إضافي
                convId = Regex("\"conversationId\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.get(1)
                target = Regex("\"targetUserId\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.get(1)
                isTyping = Regex("\"isTyping\"\\s*:\\s*(true|false)").find(raw)?.groupValues?.get(1)?.toBooleanStrictOrNull() ?: true
            } else if (raw.contains(":")) {
                val parts = raw.split(":")
                convId = parts[0]
                isTyping = parts.getOrNull(1) != "0"
                target = parts.getOrNull(2)
            }
            if (convId.isNullOrBlank()) return
            val userId = session.attributes["userId"] as? String ?: session.attributes["accountId"] as? String ?: "unknown"
            broadcastTyping(userId, convId, isTyping)
            log.debug("Typing via /ws/typing {} -> {} isTyping={}", userId, convId, isTyping)
        } catch (e: Exception) {
            log.warn("Bad typing frame: {}", e.message)
        }
    }
}
