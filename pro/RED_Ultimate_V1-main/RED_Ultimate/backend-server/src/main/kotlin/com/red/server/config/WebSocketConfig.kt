package com.red.server.config

import com.red.server.auth.security.JwtHandshakeInterceptor
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

/**
 * 🌐 YOUNES WebSocket Configuration
 * مسارات WebSocket مع مصادقة JWT
 */
@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val jwtHandshakeInterceptor: JwtHandshakeInterceptor,
    private val redMasterHandler: com.red.server.websocket.RedMasterHandler,
    private val adminLogHandler: com.red.server.websocket.AdminLogHandler,
    private val callWebSocketHandler: com.red.server.websocket.CallWebSocketHandler,
    private val typingHandler: com.red.server.websocket.TypingHandler,
    private val conferenceWebSocketHandler: com.red.server.websocket.ConferenceWebSocketHandler,
    private val liveStreamWebSocketHandler: com.red.server.websocket.LiveStreamWebSocketHandler
) : WebSocketConfigurer {

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        // ─── WebSocket الرئيسي — رسائل + إشعارات + حالة ───
        registry.addHandler(redMasterHandler, "/ws/master")
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOriginPatterns("*")

        // ─── WebSocket الإدارة — سجلات حية ───
        registry.addHandler(adminLogHandler, "/ws/admin/logs")
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOriginPatterns("*")

        // ─── WebSocket المكالمات — إشارات WebRTC (1-1) ───
        registry.addHandler(callWebSocketHandler, "/ws/calls")
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOriginPatterns("*")

        // ─── WebSocket المؤتمرات — إشارات WebRTC (جماعية) ───
        registry.addHandler(conferenceWebSocketHandler, "/ws/conference")
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOriginPatterns("*")

        // ─── WebSocket البث المباشر — إشارات WebRTC (live) ───
        registry.addHandler(liveStreamWebSocketHandler, "/ws/livestream")
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOriginPatterns("*")

        // ─── WebSocket الكتابة — "يكتب الآن" ───
        registry.addHandler(typingHandler, "/ws/typing")
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOriginPatterns("*")
    }
}
