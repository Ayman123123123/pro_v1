package com.red.sovereign.auth

import com.red.sovereign.core.UuidV7
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class UuidV7Test {
    @Test fun generatedIdsHaveVersion7AndRfcVariant() {
        repeat(100) {
            val id = UUID.fromString(UuidV7.next())
            assertEquals(7, id.version())
            assertEquals(2, id.variant())
        }
    }

    @Test fun IDsAreUnique() {
        val ids = (1..1000).map { UuidV7.next() }.toSet()
        assertTrue(ids.size == 1000)
    }
}
