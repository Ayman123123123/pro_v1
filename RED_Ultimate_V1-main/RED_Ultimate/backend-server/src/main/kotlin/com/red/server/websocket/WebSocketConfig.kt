package com.red.server.websocket

import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

/**
 * إعدادات WebSocket في الخادم.
 * 
 * يسجل معالج DINSTAR على المسار `/ws/dinstar`.
 * الاتصال متاح لكل مستخدم مصادق (JWT cookie أو header).
 */
@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val dinstarWebSocketHandler: DinstarWebSocketHandler
) : WebSocketConfigurer {

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(dinstarWebSocketHandler, "/ws/dinstar")
            .setAllowedOrigins("*") // في الإنتاج يُقيَّد بنطاق اللوحة
    }
}
