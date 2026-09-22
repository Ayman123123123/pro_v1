package com.red.server.calls

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class AdminCallHistoryMapperTest {
    @Test
    fun `product types map onto the admin SQL vocabulary`() {
        assertEquals("VOIP_AUDIO", AdminCallHistoryMapper.sqlType("VOICE", "RED"))
        assertEquals("VOIP_VIDEO", AdminCallHistoryMapper.sqlType("VIDEO", "RED"))
        assertEquals("CONFERENCE", AdminCallHistoryMapper.sqlType("GROUP", "RED"))
        assertEquals("LIVE_BROADCAST", AdminCallHistoryMapper.sqlType("LIVE", "RED"))
        assertEquals("AUDIO_SPACE", AdminCallHistoryMapper.sqlType("SPACE", "RED"))
        assertEquals("PSTN_DINSTAR", AdminCallHistoryMapper.sqlType("VOICE", "DINSTAR"))
    }

    @Test
    fun `uuid call ids are kept and room ids become stable uuids`() {
        val id = "11111111-1111-1111-1111-111111111111"
        assertEquals(UUID.fromString(id), AdminCallHistoryMapper.sqlId(id))
        val room = AdminCallHistoryMapper.sqlId("space-73066-1")
        assertEquals(room, AdminCallHistoryMapper.sqlId("space-73066-1"))
        assertTrue(room != AdminCallHistoryMapper.sqlId("space-other"))
    }

    @Test
    fun `phone-looking targets are detected for PSTN rows`() {
        assertTrue(AdminCallHistoryMapper.looksLikePhone("777123456"))
        assertFalse(AdminCallHistoryMapper.looksLikePhone("73066"))
        assertFalse(AdminCallHistoryMapper.looksLikePhone("space-room"))
    }
}
