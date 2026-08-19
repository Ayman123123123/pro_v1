package com.red.server.config

import com.red.server.auth.security.JwtHandshakeInterceptor
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

/**
 * 🌐 YOUNES WebSocket Configuration
 * مسارات WebSocket مع مصادقة JWT
 *
 * ⚠️  CORS: Allowed origins are configured via `red.security.allowed-origins` env var.
 * All WebSocket endpoints REQUIRE JWT authentication via JwtHandshakeInterceptor.
 * For mobile apps (no fixed origin), allow "app://" pattern.
 */
@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val jwtHandshakeInterceptor: JwtHandshakeInterceptor,
    private val redMasterHandler: com.red.server.websocket.RedMasterHandler,
    private val adminLogHandler: com.red.server.websocket.AdminLogHandler,
    private val callWebSocketHandler: com.red.server.websocket.CallWebSocketHandler,
     private val dinstarWebSocketHandler: com.red.server.websocket.DinstarWebSocketHandler,
     private val pstnEventWebSocketHandler: com.red.server.websocket.PstnEventWebSocketHandler,
     private val typingHandler: com.red.server.websocket.TypingHandler,
    private val conferenceWebSocketHandler: com.red.server.websocket.ConferenceWebSocketHandler,
    private val liveStreamWebSocketHandler: com.red.server.websocket.LiveStreamWebSocketHandler,
    @Value("\${red.security.allowed-origins:http://localhost,http://127.0.0.1,app://}")
    private val allowedOrigins: List<String>
) : WebSocketConfigurer {

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        // ─── WebSocket الرئيسي — رسائل + إشعارات + حالة ───
        registry.addHandler(redMasterHandler, "/ws/master")
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOriginPatterns(*allowedOrigins.toTypedArray())

        // ─── WebSocket الإدارة — سجلات حية ───
        registry.addHandler(adminLogHandler, "/ws/admin/logs")
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOriginPatterns(*allowedOrigins.toTypedArray())

        // ─── WebSocket المكالمات — إشارات WebRTC (1-1) ───
        registry.addHandler(callWebSocketHandler, "/ws/calls")
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOriginPatterns(*allowedOrigins.toTypedArray())

        // ─── WebSocket المؤتمرات — إشارات WebRTC (جماعية) ───
        registry.addHandler(conferenceWebSocketHandler, "/ws/conference")
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOriginPatterns(*allowedOrigins.toTypedArray())

        // ─── WebSocket البث المباشر — إشارات WebRTC (live) ───
        registry.addHandler(liveStreamWebSocketHandler, "/ws/livestream")
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOriginPatterns(*allowedOrigins.toTypedArray())

        // ─── WebSocket الكتابة — "يكتب الآن" ───
        registry.addHandler(typingHandler, "/ws/typing")
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOriginPatterns(*allowedOrigins.toTypedArray())

        // ─── WebSocket DINSTAR — أحداث البوابات (منافذ/CDR/SMS/USSD) ───
        registry.addHandler(dinstarWebSocketHandler, "/ws/dinstar")
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOriginPatterns(*allowedOrigins.toTypedArray())

        // ─── WebSocket المكالمات PSTN — أحداث DINSTAR (RINGING/ANSWERED/ENDED) + SMS realtime ───
        registry.addHandler(pstnEventWebSocketHandler, "/ws/pstn")
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOriginPatterns(*allowedOrigins.toTypedArray())
    }
}
