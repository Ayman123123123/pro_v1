package com.red.server.config

import com.red.server.auth.security.JwtHandshakeInterceptor
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

/**
 * 🌐 YOUNES WebSocket Configuration
 * مسارات WebSocket مع مصادقة JWT وقيود أصول صريحة (لا "*").
 */
@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val jwtHandshakeInterceptor: JwtHandshakeInterceptor,
    private val redMasterHandler: com.red.server.websocket.RedMasterHandler,
    private val adminLogHandler: com.red.server.websocket.AdminLogHandler,
    private val callWebSocketHandler: com.red.server.websocket.CallWebSocketHandler,
    private val typingHandler: com.red.server.websocket.TypingHandler,
    @Value("\${red.security.allowed-origins:http://localhost,http://127.0.0.1}")
    private val configuredAllowedOrigins: String
) : WebSocketConfigurer {

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        val origins = configuredAllowedOrigins.split(',').map(String::trim).filter(String::isNotEmpty).toTypedArray()
        require(origins.isNotEmpty() && origins.none { it == "*" }) { "Explicit WebSocket origins are required" }

        // ─── WebSocket الرئيسي — رسائل + إشعارات + حالة ───
        registry.addHandler(redMasterHandler, "/ws/master")
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOriginPatterns(*origins)

        // ─── WebSocket الإدارة — سجلات حية ───
        registry.addHandler(adminLogHandler, "/ws/admin/logs")
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOriginPatterns(*origins)

        // ─── WebSocket المكالمات — إشارات WebRTC ───
        registry.addHandler(callWebSocketHandler, "/ws/calls")
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOriginPatterns(*origins)

        // ─── WebSocket الكتابة — "يكتب الآن" ───
        registry.addHandler(typingHandler, "/ws/typing")
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOriginPatterns(*origins)
    }
}
