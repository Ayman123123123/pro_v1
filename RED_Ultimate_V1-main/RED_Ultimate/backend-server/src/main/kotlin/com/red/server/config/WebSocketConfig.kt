package com.red.server.config

import com.red.server.pstn.DinstarWebSocketHandler
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

/**
 * تكوين WebSocket لدعم الاتصال المباشر مع تطبيق الأندرويد
 */
@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val dinstarWebSocketHandler: DinstarWebSocketHandler
) : WebSocketConfigurer {

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(dinstarWebSocketHandler, "/ws/dinstar")
            .setAllowedOrigins("*") // في الإنتاج يجب تحديد النطاقات المسموحة
    }
}
