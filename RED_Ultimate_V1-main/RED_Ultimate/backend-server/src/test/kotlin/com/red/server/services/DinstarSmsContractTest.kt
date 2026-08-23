package com.red.server.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DinstarSmsContractTest {
    @Test
    fun `prepare assigns distinct numeric user ids and preserves text parameters`() {
        val prepared = DinstarSmsContract.prepare(
            listOf(
                mapOf("number" to "777123456", "text_param" to listOf("الأول")),
                mapOf("number" to "730123456")
            )
        )

        assertEquals(2, prepared.recipients.size)
        assertEquals(2, prepared.userIds.size)
        assertNotEquals(prepared.userIds[0], prepared.userIds[1])
        assertEquals(prepared.userIds[0], prepared.recipients[0]["user_id"])
        assertEquals("777123456", prepared.recipients[0]["number"])
        assertEquals(listOf("الأول"), prepared.recipients[0]["text_param"])
    }

    @Test
    fun `prepare rejects non numeric or empty destinations`() {
        assertThrows(IllegalArgumentException::class.java) {
            DinstarSmsContract.prepare(listOf(mapOf("number" to "+967777123456")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            DinstarSmsContract.prepare(emptyList())
        }
    }

    @Test
    fun `accepted response recognizes DINSTAR synchronous and queued codes`() {
        assertTrue(DinstarSmsContract.isAccepted(mapOf("error_code" to 200)))
        assertTrue(DinstarSmsContract.isAccepted(mapOf("error_code" to 202)))
    }
}
