package com.red.server.websocket

import com.red.server.auth.security.JwtHandshakeInterceptor
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

/**
 * 🌐 إعدادات WebSocket — التسجيل الموحّد لكل معالجات الوقت الحقيقي.
 *
 * يسجّل هذا الملف مسارات الرسائل والمكالمات والمؤتمرات والبث وسجلات الإدارة،
 * وجميعها تمرّ عبر معترض JWT قبل المصافحة.
 *
 * كل المسارات تمرّ عبر JwtHandshakeInterceptor (Bearer JWT، أو تذكرة إدارية
 * قصيرة العمر لمسار /ws/admin/logs)، * CORS: الأنماط المسموحة من `red.security.allowed-origins`؛ عملاء الجوال لا
 * يرسلون Origin أصلًا فيُقبلون، و"app://" يغطي من يرسله.
 *
 * ROUTES تعكس بدقة ما يُسجَّل أدناه، ويتحقق منها WebSocketRouteContractTest.
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
        /** كل مسارات WebSocket التي يسجلها الخادم — يستهلكها الأندرويد ولوحة الإدارة. */
        val ROUTES: Set<String> = setOf(
            "/ws/master",
            "/ws/calls",
            "/ws/conference",
            "/ws/livestream",
            "/ws/typing",
            "/ws/admin/logs"
        )
    }

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        val origins = allowedOrigins.toTypedArray()

        // ─── WebSocket الرئيسي — رسائل + إشعارات + حالة ───
        registry.addHandler(redMasterHandler, "/ws/master")
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOriginPatterns(*origins)

        // ─── WebSocket الإدارة — سجلات حية (تذكرة إدارية قصيرة العمر) ───
        registry.addHandler(adminLogHandler, "/ws/admin/logs")
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOriginPatterns(*origins)

        // ─── WebSocket المكالمات — إشارات WebRTC (1-1) ───
        registry.addHandler(callWebSocketHandler, "/ws/calls")
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOriginPatterns(*origins)

        // ─── WebSocket المؤتمرات — إشارات WebRTC (جماعية + مساحات صوتية) ───
        registry.addHandler(conferenceWebSocketHandler, "/ws/conference")
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOriginPatterns(*origins)

        // ─── WebSocket البث المباشر — إشارات WebRTC (live) ───
        registry.addHandler(liveStreamWebSocketHandler, "/ws/livestream")
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOriginPatterns(*origins)

        // ─── WebSocket الكتابة — "يكتب الآن" ───
        registry.addHandler(typingHandler, "/ws/typing")
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOriginPatterns(*origins)

    }
}
