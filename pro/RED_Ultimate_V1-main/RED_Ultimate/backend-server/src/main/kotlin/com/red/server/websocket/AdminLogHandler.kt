package com.red.server.websocket

import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import org.springframework.stereotype.Component
import java.util.concurrent.CopyOnWriteArrayList

@Component
class AdminLogHandler : TextWebSocketHandler() {
    
    private val adminSessions = CopyOnWriteArrayList<WebSocketSession>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        if (session.attributes["role"] != "ADMIN") {
            session.close(org.springframework.web.socket.CloseStatus.POLICY_VIOLATION)
            return
        }
        adminSessions.add(session)
        session.sendMessage(TextMessage("YOUNES secure admin event stream connected"))
    }

    /**
     * وظيفة مركزية لبث الأحداث من أي مكان في السيرفر إلى لوحة التحكم
     */
    fun broadcastLog(system: String, message: String) {
        val payload = "[${system.take(40)}] ${message.take(2_000)}"
        adminSessions.removeIf { session ->
            if (!session.isOpen) true
            else runCatching { synchronized(session) { session.sendMessage(TextMessage(payload)) } }.isFailure
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: org.springframework.web.socket.CloseStatus) {
        adminSessions.remove(session)
    }
}
