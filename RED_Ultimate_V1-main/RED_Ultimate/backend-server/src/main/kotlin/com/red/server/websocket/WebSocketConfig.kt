package com.red.server.websocket

import com.red.server.auth.security.JwtHandshakeInterceptor
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

/**
 * إعدادات WebSocket في الخادم — التسجيل الموحّد لكل معالجات الوقت الحقيقي.
 *
 * ⚠️ إصلاح حرج: بعد حذف config/WebSocketConfig المكرر (التزام 6d3a140)، بقي هذا
 * الملف يسجّل `/ws/dinstar` فقط، فأصبحت كل مسارات التطبيق التي يتصل بها عميل
 * الأندرويد غير مسجّلة وتُرجع 404 عند المصافحة:
 *   - /ws/master      (الرسائل، ACK، الكتابة، المزامنة، الحذف — RedMasterHandler)
 *   - /ws/calls       (إشارات مكالمات 1:1 — CallWebSocketHandler)
 *   - /ws/conference  (المؤتمرات والمساحات الصوتية — ConferenceWebSocketHandler)
 *   - /ws/livestream  (البث المباشر — LiveStreamWebSocketHandler)
 *   - /ws/typing      (مؤشر الكتابة — TypingHandler)
 *   - /ws/admin/logs  (سجلات الإدارة الحية — AdminLogHandler)
 *
 * كل المسارات تمرّ عبر JwtHandshakeInterceptor (Bearer JWT أو تذكرة إدارية
 * لمسار /ws/admin/logs)، بما فيها /ws/dinstar الذي كان مفتوحًا بلا مصادقة
 * ويُسرب حالات المنافذ والرسائل القصيرة وسجلات المكالمات لأي متصل.
 *
 * CORS: الأنماط المسموحة من `red.security.allowed-origins`؛ عملاء الجوال
 * لا يرسلون Origin أصلًا فيُقبلون، و"app://" يغطي من يرسله.
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
    private val dinstarWebSocketHandler: DinstarWebSocketHandler,
    @Value("\${red.security.allowed-origins:http://localhost,http://127.0.0.1,app://}")
    private val allowedOrigins: List<String>
) : WebSocketConfigurer {

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        // ─── WebSocket الرئيسي — رسائل + إشعارات + حالة ───
        registry.addHandler(redMasterHandler, "/ws/master")
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOriginPatterns(*allowedOrigins.toTypedArray())

        // ─── WebSocket الإدارة — سجلات حية (تذكرة إدارية قصيرة العمر) ───
        registry.addHandler(adminLogHandler, "/ws/admin/logs")
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOriginPatterns(*allowedOrigins.toTypedArray())

        // ─── WebSocket المكالمات — إشارات WebRTC (1-1) ───
        registry.addHandler(callWebSocketHandler, "/ws/calls")
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOriginPatterns(*allowedOrigins.toTypedArray())

        // ─── WebSocket المؤتمرات — إشارات WebRTC (جماعية + مساحات صوتية) ───
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

        // ─── WebSocket دينستار — أحداث البوابات الحية (مصادق الآن) ───
        registry.addHandler(dinstarWebSocketHandler, "/ws/dinstar")
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOriginPatterns(*allowedOrigins.toTypedArray())
    }
}
