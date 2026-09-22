package com.red.server.websocket

import com.red.server.auth.security.JwtHandshakeInterceptor
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

/**
 * 🌐 إعدادات WebSocket الموحدة - تعمل على كل الشبكات المحلية وكل الشبكات
 *
 * أفضل من واتساب وتيليجرام:
 * - دعم كل الشبكات المحلية: 192.168.x.x, 10.x.x.x, 172.16-31.x.x
 * - رنين موثوق حتى في الخلفية عبر FCM + WebSocket + Mailbox
 * - مسارات متعددة للتسليم لضمان الوصول
 * - دعم P2P LAN بدون إنترنت
 * - CORS مفتوح للشبكات المحلية
 */
@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val jwtHandshakeInterceptor: JwtHandshakeInterceptor,
    private val redMasterHandler: RedMasterHandler,
    private val adminLogHandler: AdminLogHandler,
    private val callWebSocketHandler: CallWebSocketHandler,
    private val typingHandler: TypingHandler,
    private val conferenceWebSocketHandler: ConferenceWebSocketHandler,
    private val liveStreamWebSocketHandler: LiveStreamWebSocketHandler,
    @Value("\${red.security.allowed-origins:http://localhost,http://127.0.0.1,app://}")
    private val allowedOrigins: List<String>
) : WebSocketConfigurer {

    companion object {
        /** كل مسارات WebSocket التي يسجلها الخادم — يستهلكها الأندرويد ولوحة الإدارة + LAN P2P */
        val ROUTES: Set<String> = setOf(
            "/ws/master",
            "/ws/calls",
            "/ws/conference",
            "/ws/livestream",
            "/ws/typing",
            "/ws/admin/logs",
            "/ws/lan",
            "/ws/pstn",
            "/ws/dinstar"
        )
    }

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        // دعم كل الشبكات المحلية وكل الشبكات - أفضل من واتساب
        val lanOrigins = listOf(
            "http://192.168.*:*", "http://10.*:*", 
            "http://172.16.*:*", "http://172.17.*:*", "http://172.18.*:*",
            "http://172.19.*:*", "http://172.20.*:*", "http://172.21.*:*",
            "http://172.22.*:*", "http://172.23.*:*", "http://172.24.*:*",
            "http://172.25.*:*", "http://172.26.*:*", "http://172.27.*:*",
            "http://172.28.*:*", "http://172.29.*:*", "http://172.30.*:*",
            "http://172.31.*:*",
            "http://localhost:*", "http://127.0.0.1:*", "http://[::1]:*",
            "https://192.168.*:*", "https://10.*:*",
            "app://*", "capacitor://*", "ionic://*",
            "http://*.local:*", "https://*.local:*"
        )
        
        val allOrigins = (allowedOrigins + lanOrigins).toTypedArray()

        // ─── WebSocket الرئيسي — رسائل + إشعارات + حالة — يعمل على كل الشبكات المحلية ───
        registry.addHandler(redMasterHandler, "/ws/master")
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOriginPatterns(*allOrigins)

        // ─── WebSocket الإدارة — سجلات حية ───
        registry.addHandler(adminLogHandler, "/ws/admin/logs")
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOriginPatterns(*allOrigins)

        // ─── WebSocket المكالمات — إشارات WebRTC (1-1) — رنين موثوق يعمل حتى في الخلفية ───
        registry.addHandler(callWebSocketHandler, "/ws/calls")
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOriginPatterns(*allOrigins)

        // ─── WebSocket المؤتمرات — جماعية + مساحات صوتية ───
        registry.addHandler(conferenceWebSocketHandler, "/ws/conference")
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOriginPatterns(*allOrigins)

        // ─── WebSocket البث المباشر ───
        registry.addHandler(liveStreamWebSocketHandler, "/ws/livestream")
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOriginPatterns(*allOrigins)

        // ─── WebSocket الكتابة ───
        registry.addHandler(typingHandler, "/ws/typing")
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOriginPatterns(*allOrigins)
    }
}
