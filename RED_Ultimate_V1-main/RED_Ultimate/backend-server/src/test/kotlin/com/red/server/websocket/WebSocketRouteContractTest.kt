package com.red.server.websocket

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * عقد مسارات WebSocket بين الخادم وعميليه.
 *
 * كل مسار هنا له مستهلك فعلي مُتحقَّق منه:
 * - `/ws/master`     RedWebSocketClient (البروتوكول الرئيسي: رسائل/ACK/typing/مزامنة)
 * - `/ws/calls`      CallSignalingClient (إشارات 1:1 والجماعية وZoom)
 * - `/ws/conference` ConferenceSignalingClient
 * - `/ws/livestream` LiveStreamSignalingClient
 * - `/ws/typing`     قناة Redis pub/sub للكتابة (TypingHandler)
 * - `/ws/dinstar`    DinstarWebSocketBridge (حالة المنافذ + CDR + SMS)
 * - `/ws/pstn`       PstnEventSocket (مراحل المكالمة الصادرة + الوارد + SMS)
 * - `/ws/admin/logs` LogStreamerTab في لوحة الإدارة
 *
 * `/ws/pstn` كان ناقصًا من هذا العقد رغم أنه مسجَّل في الخادم ويستهلكه
 * التطبيق فعلًا (PstnEventSocket) — وهو حامل أحداث RINGING/ACTIVE/ENDED.
 */
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
                "/ws/dinstar",
                "/ws/pstn"
            ),
            WebSocketConfig.ROUTES
        )
    }
}
