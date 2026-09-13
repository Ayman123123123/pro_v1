package com.red.server.agents

import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap

@Component
class RedAgentWebSocketHandler : TextWebSocketHandler() {
    private val sessions = ConcurrentHashMap<String, WebSocketSession>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        sessions[session.id] = session
        session.sendMessage(TextMessage("CONNECTED: RED Ultimate 20 AI Agents WebSocket Stream Active"))
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val payload = message.payload
        // Broadcast or process AI agent request
        sessions.values.forEach { s ->
            if (s.isOpen) {
                s.sendMessage(TextMessage("AI_AGENT_BROADCAST: Received -> $payload"))
            }
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: org.springframework.web.socket.CloseStatus) {
        sessions.remove(session.id)
    }
}
