package com.red.server.websocket

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler

@Component
class TypingHandler(private val redis: StringRedisTemplate) : TextWebSocketHandler() {

    /**
     * نشر حالة "يكتب الآن" عبر Redis لكل المشتركين في المحادثة
     */
    fun broadcastTyping(userId: String, conversationId: String, isTyping: Boolean) {
        val payload = if (isTyping) "1" else "0"
        redis.convertAndSend("chat:typing:$conversationId", "$userId:$payload")
    }

    public override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        // يمكن التعامل مع رسائل الكتابة الواردة هنا إذا لزم الأمر
    }
}
