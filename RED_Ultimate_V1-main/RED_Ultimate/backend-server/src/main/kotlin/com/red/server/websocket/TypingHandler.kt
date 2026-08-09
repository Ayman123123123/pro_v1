package com.red.server.websocket

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Component
class TypingHandler(private val redis: StringRedisTemplate) {

    /**
     * نشر حالة "يكتب الآن" عبر Redis لكل المشتركين في المحادثة
     */
    fun broadcastTyping(userId: String, conversationId: String, isTyping: Boolean) {
        val payload = if (isTyping) "1" else "0"
        redis.convertAndSend("chat:typing:$conversationId", "$userId:$payload")
    }
}
