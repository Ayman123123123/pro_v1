package com.red.server.websocket

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WebSocketRouteContractTest {
    @Test
    fun `server registers every route consumed by Android and admin dashboard`() {
        assertEquals(
            setOf(
                "/ws/master",
                "/ws/calls",
                "/ws/conference",
                "/ws/livestream",
                "/ws/typing",
                "/ws/admin/logs",
                "/ws/dinstar"
            ),
            WebSocketConfig.ROUTES
        )
    }
}
