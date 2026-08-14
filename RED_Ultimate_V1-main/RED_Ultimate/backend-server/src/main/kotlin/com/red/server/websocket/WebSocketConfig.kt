package com.red.server.websocket

import com.red.server.auth.security.JwtHandshakeInterceptor
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

/**
 * The single authoritative WebSocket route table.
 *
 * Every socket is authenticated during the HTTP upgrade. Native Android clients
 * normally omit Origin; browser clients must match the same explicit allow-list
 * used by REST CORS. Keeping all paths here prevents a partial configuration from
 * silently leaving existing handlers unreachable.
 */
@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val masterHandler: RedMasterHandler,
    private val callHandler: CallWebSocketHandler,
    private val conferenceHandler: ConferenceWebSocketHandler,
    private val liveStreamHandler: LiveStreamWebSocketHandler,
    private val typingHandler: TypingHandler,
    private val adminLogHandler: AdminLogHandler,
    private val dinstarHandler: DinstarWebSocketHandler,
    private val authentication: JwtHandshakeInterceptor,
    @Value("\${red.security.allowed-origins:http://localhost,http://127.0.0.1}")
    configuredAllowedOrigins: String
) : WebSocketConfigurer {
    private val allowedOrigins = configuredAllowedOrigins
        .split(',')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()
        .ifEmpty { listOf("http://localhost", "http://127.0.0.1") }
        .toTypedArray()

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        register(registry, masterHandler, "/ws/master")
        register(registry, callHandler, "/ws/calls")
        register(registry, conferenceHandler, "/ws/conference")
        register(registry, liveStreamHandler, "/ws/livestream")
        register(registry, typingHandler, "/ws/typing")
        register(registry, adminLogHandler, "/ws/admin/logs")
        register(registry, dinstarHandler, "/ws/dinstar")
    }

    private fun register(registry: WebSocketHandlerRegistry, handler: WebSocketHandler, path: String) {
        registry.addHandler(handler, path)
            .addInterceptors(authentication)
            .setAllowedOriginPatterns(*allowedOrigins)
    }

    companion object {
        /** Used by contract tests and deployment checks. */
        val ROUTES: Set<String> = linkedSetOf(
            "/ws/master",
            "/ws/calls",
            "/ws/conference",
            "/ws/livestream",
            "/ws/typing",
            "/ws/admin/logs",
            "/ws/dinstar"
        )
    }
}
