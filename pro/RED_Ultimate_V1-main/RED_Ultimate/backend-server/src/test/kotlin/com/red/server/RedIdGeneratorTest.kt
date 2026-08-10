package com.red.server

import com.red.server.auth.RedIdGenerator
import com.red.server.auth.repository.UserAccountRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * اختبارات مولّد هويات YOUNES: الصيغة الصارمة والتفرد.
 */
class RedIdGeneratorTest {

    private val repository: UserAccountRepository = mock()
    private val generator = RedIdGenerator(repository)

    @Test
    fun `generated id follows the strict YNS format`() {
        repeat(50) {
            val id = generator.next()
            assertTrue(
                id.matches(Regex("^YNS-[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{4}-[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{4}$")),
                "معرّف غير صالح: $id"
            )
        }
    }

    @Test
    fun `ids are unique across many generations`() {
        whenever(repository.existsByRedId(org.mockito.kotlin.any())).thenReturn(false)
        val ids = (1..500).map { generator.next() }.toSet()
        assertEquals(500, ids.size)
    }

    @Test
    fun `collision is retried and eventually returns a fresh id`() {
        var called = false
        whenever(repository.existsByRedId(org.mockito.kotlin.any())).thenAnswer {
            if (!called) { called = true; true } else false
        }
        val id = generator.next()
        assertNotEquals("", id)
        assertTrue(id.startsWith("YNS-"))
    }
}
