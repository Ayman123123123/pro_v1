package com.red.server.websocket

import java.util.concurrent.ConcurrentHashMap

/**
 * Small per-connection fixed-window guard for WebSocket frames.
 *
 * It is intentionally local to a WebSocket process: authentication and authorization still happen
 * in the backend, while this guard prevents a single connected socket from consuming unbounded CPU
 * or database work before a distributed gateway-level limit is applied.
 */
class WebSocketRateLimiter(
    private val maxMessages: Int,
    private val windowMillis: Long,
    private val clockMillis: () -> Long = System::currentTimeMillis
) {
    private val windows = ConcurrentHashMap<String, Window>()

    init {
        require(maxMessages > 0)
        require(windowMillis > 0)
    }

    fun tryAcquire(connectionId: String): Boolean {
        val now = clockMillis()
        val window = windows.computeIfAbsent(connectionId) { Window(now) }
        synchronized(window) {
            if (now - window.startedAt >= windowMillis) {
                window.startedAt = now
                window.count = 0
            }
            if (window.count >= maxMessages) return false
            window.count += 1
            return true
        }
    }

    fun remove(connectionId: String) {
        windows.remove(connectionId)
    }

    private class Window(var startedAt: Long, var count: Int = 0)
}
