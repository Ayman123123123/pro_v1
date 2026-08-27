package com.red.server.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.jdbc.core.JdbcTemplate

class DinstarSmsContractTest {

    /**
     * `prepare` صار دالة نسخة تعتمد على تسلسل قاعدة البيانات لتوليد `user_id`.
     * نُسقط الاستعلام إلى null فيسلك المسار الاحتياطي (عدّاد ذاكرة) — وهو ما
     * يضمن أن المعرّفات تبقى متمايزة حتى بلا قاعدة بيانات في الاختبار.
     */
    private fun contract(): DinstarSmsContract {
        val jdbc = mock<JdbcTemplate>()
        whenever(jdbc.queryForObject(any<String>(), eq(Long::class.java))).thenReturn(null)
        return DinstarSmsContract(jdbc)
    }

    @Test
    fun `prepare assigns distinct numeric user ids and preserves text parameters`() {
        val prepared = contract().prepare(
            listOf(
                mapOf("number" to "777123456", "text_param" to listOf("الأول")),
                mapOf("number" to "730123456")
            )
        )

        assertEquals(2, prepared.recipients.size)
        assertEquals(2, prepared.userIds.size)
        assertNotEquals(prepared.userIds[0], prepared.userIds[1])
        assertEquals(prepared.userIds[0].toInt(), prepared.recipients[0]["user_id"])
        assertEquals("777123456", prepared.recipients[0]["number"])
        assertEquals(listOf("الأول"), prepared.recipients[0]["text_param"])
    }

    @Test
    fun `prepare rejects non numeric or empty destinations`() {
        val contract = contract()
        assertThrows(IllegalArgumentException::class.java) {
            contract.prepare(listOf(mapOf("number" to "+967777123456")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            contract.prepare(emptyList())
        }
    }

    @Test
    fun `prepareSingle returns one recipient with its user id`() {
        val (recipient, userId) = contract().prepareSingle("777123456")

        assertEquals("777123456", recipient["number"])
        assertEquals(userId.toInt(), recipient["user_id"])
    }

    @Test
    fun `accepted response recognizes DINSTAR synchronous and queued codes`() {
        assertTrue(DinstarSmsContract.isAccepted(mapOf("error_code" to 200)))
        assertTrue(DinstarSmsContract.isAccepted(mapOf("error_code" to 202)))
    }
}
