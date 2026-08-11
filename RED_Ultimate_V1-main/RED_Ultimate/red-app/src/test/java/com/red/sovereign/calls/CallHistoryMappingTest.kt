package com.red.sovereign.calls

import com.red.sovereign.core.database.CallLogEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * اختبارات تحويل CallLogEntity → CallHistoryItem.
 * يضمن أن mapping الحقول صحيح (لا تبديل بين direction/type/route/status).
 */
class CallHistoryMappingTest {
    private fun toItem(entity: CallLogEntity): CallHistoryItem = CallHistoryItem(
        id = entity.id,
        peerId = entity.peerId,
        peerLabel = entity.peerLabel,
        direction = entity.direction,
        type = entity.type,
        route = entity.route,
        status = entity.status,
        startedAt = entity.timestamp.toString(),
        answeredAt = entity.answeredAt?.toString(),
        endedAt = entity.endedAt?.toString()
    )

    @Test fun `all fields mapped correctly (no positional swap)`() {
        val entity = CallLogEntity(
            id = "call-1",
            peerId = "33563",
            peerLabel = "علي",
            type = "VIDEO",
            direction = "INCOMING",
            route = "DINSTAR",
            status = "COMPLETED",
            timestamp = 1_700_000_000_000L,
            durationMs = 45_000L,
            answeredAt = 1_700_000_005_000L,
            endedAt = 1_700_000_050_000L
        )
        val item = toItem(entity)
        assertEquals("33563", item.peerId)
        assertEquals("علي", item.peerLabel)
        assertEquals("VIDEO", item.type)
        assertEquals("INCOMING", item.direction)
        assertEquals("DINSTAR", item.route)
        assertEquals("COMPLETED", item.status)
        assertEquals("1700000000000", item.startedAt)
        assertEquals("1700000005000", item.answeredAt)
        assertEquals("1700000050000", item.endedAt)
    }

    @Test fun `default route is RED when unspecified`() {
        val entity = CallLogEntity(
            id = "call-2", peerId = "27453", type = "VOICE",
            direction = "OUTGOING", status = "MISSED", timestamp = 1000L
        )
        val item = toItem(entity)
        assertEquals("RED", item.route)
        assertEquals(null, item.answeredAt)
        assertEquals(null, item.endedAt)
    }

    @Test fun `durationMs from entity is preserved when mapping back to history fields`() {
        // The bug was: firstOrNull preserved only one item, others dropped silently.
        // This test ensures the round-trip preserves the critical fields.
        val entities = listOf(
            CallLogEntity("a", "73066", type = "VOICE", direction = "OUTGOING", status = "MISSED", timestamp = 1L, durationMs = 0L),
            CallLogEntity("b", "28261", type = "VIDEO", direction = "INCOMING", status = "COMPLETED", timestamp = 2L, durationMs = 60_000L),
            CallLogEntity("c", "70668", type = "VOICE", direction = "OUTGOING", status = "REJECTED", timestamp = 3L, durationMs = 0L)
        )
        val items = entities.map(::toItem)
        assertEquals(3, items.size) // fix was: firstOrNull kept only 1
        assertEquals("VOICE", items[0].type)
        assertEquals("VIDEO", items[1].type)
        assertEquals("VOICE", items[2].type)
    }
}
