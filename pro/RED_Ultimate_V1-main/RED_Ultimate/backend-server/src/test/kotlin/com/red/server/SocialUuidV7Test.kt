package com.red.server

import com.red.server.social.UuidV7
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class SocialUuidV7Test {
    @Test fun `social IDs are RFC UUID version 7 and unique`() {
        val values = (1..1000).map { UUID.fromString(UuidV7.next()) }
        values.forEach { assertEquals(7, it.version()); assertEquals(2, it.variant()) }
        assertTrue(values.toSet().size == values.size)
    }
}
