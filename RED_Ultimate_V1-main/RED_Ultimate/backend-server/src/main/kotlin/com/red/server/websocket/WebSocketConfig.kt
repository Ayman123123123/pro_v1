package com.red.server.websocket

import com.red.server.auth.security.JwtHandshakeInterceptor
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

/**
 * 🌐 YOUNES WebSocket Configuration
 * كل مسارات WebSocket مع مصادقة JWT إجبارية.
 *
 * ⚠️  CORS: النطاقات المسموحة تُضبط عبر `red.security.allowed-origins`.
 * كل نقاط WebSocket تتطلب JWT عبر JwtHandshakeInterceptor.
 * لتطبيقات الجوال (بلا origin ثابت) يُسمح بنمط "app://".
 *
 * ملاحظة استعادة (2026-08-19):
 *   كان هذا الملف يسجّل مساراً واحداً فقط هو /ws/dinstar، بلا JWT
 *   وبـ setAllowedOrigins("*") المفتوح. النسخة الكاملة (7 مسارات + JWT
 *   + قيود CORS) كانت في com.red.server.config.WebSocketConfig وفُقدت
 *   أثناء توحيد الحزم (التزام 6d3a140). أُعيد دمجها هنا مع الإبقاء على
 *   /ws/dinstar. المرجع: الفرعان arena/019fe9e3-pro-v1 و
 *   fix/restore-websocket-registrations.
 *
 *   المسارات الستة التي كانت تُرجع 404 عند المصافحة قبل الإصلاح:
 *     - /ws/master      الرسائل، ACK، الكتابة، المزامنة، الحذف — RedMasterHandler
 *     - /ws/calls       إشارات مكالمات 1:1 — CallWebSocketHandler
 *     - /ws/conference  المؤتمرات والمساحات الصوتية — ConferenceWebSocketHandler
 *     - /ws/livestream  البث المباشر — LiveStreamWebSocketHandler
 *     - /ws/typing      مؤشر الكتابة — TypingHandler
 *     - /ws/admin/logs  سجلات الإدارة الحية — AdminLogHandler
 *
 *   و/ws/dinstar كان مفتوحاً بلا مصادقة ويُسرّب حالات المنافذ والرسائل
 *   القصيرة وسجلات المكالمات لأي متصل؛ صار خلف JwtHandshakeInterceptor.
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
    private val dinstarWebSocketHandler: DinstarWebSocketHandler,
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
            "/ws/admin/logs",
            "/ws/dinstar"
        )
    }

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        val origins = allowedOrigins.toTypedArray()

        // ─── WebSocket الرئيسي — رسائل + إشعارات + حالة ───
        registry.addHandler(redMasterHandler, "/ws/master")
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOriginPatterns(*origins)

        // ─── WebSocket الإدارة — سجلات حية ───
        registry.addHandler(adminLogHandler, "/ws/admin/logs")
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOriginPatterns(*origins)

        // ─── WebSocket المكالمات — إشارات WebRTC (1-1) ───
        registry.addHandler(callWebSocketHandler, "/ws/calls")
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOriginPatterns(*origins)

        // ─── WebSocket المؤتمرات — إشارات WebRTC (جماعية) ───
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

        // ─── WebSocket DINSTAR — حالة البوابات والمنافذ حيّة ───
        // كان سابقاً بلا JWT وبـ "*" مفتوح؛ صار مؤمّناً كبقية المسارات.
        registry.addHandler(dinstarWebSocketHandler, "/ws/dinstar")
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOriginPatterns(*origins)
    }
}
