package com.red.server.config

import com.red.server.auth.security.JwtHandshakeInterceptor
import com.red.server.websocket.AdminLogHandler
import com.red.server.websocket.CallWebSocketHandler
import com.red.server.websocket.RedMasterHandler
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val redMasterHandler: RedMasterHandler,
    private val callWebSocketHandler: CallWebSocketHandler,
    private val adminLogHandler: AdminLogHandler,
    private val jwtHandshakeInterceptor: JwtHandshakeInterceptor,
    @Value("\${red.security.allowed-origins:http://localhost,http://127.0.0.1}")
    private val configuredAllowedOrigins: String
) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        val origins = configuredAllowedOrigins.split(',').map(String::trim).filter(String::isNotEmpty).toTypedArray()
        require(origins.isNotEmpty() && origins.none { it == "*" }) { "Explicit WebSocket origins are required" }
        // The only messaging protocol. Android and backend both use RedProtos.RedRED.
        registry.addHandler(redMasterHandler, "/ws/master")
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOriginPatterns(*origins)

        // WebRTC signaling only; media is handled by WebRTC/mediasoup.
        registry.addHandler(callWebSocketHandler, "/ws/calls")
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOriginPatterns(*origins)

        registry.addHandler(adminLogHandler, "/ws/admin/logs")
            .addInterceptors(jwtHandshakeInterceptor)
            .setAllowedOriginPatterns(*origins)
    }
}
