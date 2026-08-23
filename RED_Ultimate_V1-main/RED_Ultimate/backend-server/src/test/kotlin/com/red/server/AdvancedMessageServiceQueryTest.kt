package com.red.server

import com.red.server.messaging.expiredDisappearingMessagesQuery
import org.bson.Document
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class AdvancedMessageServiceQueryTest {
    @Test
    fun `expired-message query requires a real expiry value before comparing time`() {
        val now = Instant.parse("2026-08-21T00:00:00Z")
        val criteria = expiredDisappearingMessagesQuery(now).queryObject["disappearAt"] as Document

        assertTrue(criteria.containsKey("\$ne"))
        assertEquals(null, criteria["\$ne"])
        assertEquals(now, criteria["\$lte"])
    }
}
