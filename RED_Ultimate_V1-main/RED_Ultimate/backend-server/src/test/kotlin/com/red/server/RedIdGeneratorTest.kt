package com.red.server

import com.red.server.auth.RedIdGenerator
import com.red.server.auth.repository.UserAccountRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * اختبارات مولّد معرّف يونس — الصيغة الخماسية والتفرد والتطبيع.
 */
class RedIdGeneratorTest {

    private val repository: UserAccountRepository = mock()
    private val generator = RedIdGenerator(repository)

    @Test
    fun `generated id is exactly five digits within range`() {
        repeat(200) {
            val id = generator.next()
            assertTrue(id.matches(Regex(RedIdGenerator.PATTERN)), "معرّف غير صالح: $id")
            assertEquals(5, id.length, "الطول يجب أن يكون خمسة بالضبط: $id")
            val numeric = id.toInt()
            assertTrue(
                numeric in RedIdGenerator.MIN_ID..RedIdGenerator.MAX_ID,
                "المعرّف خارج المدى المسموح: $id"
            )
        }
    }

    @Test
    fun `generated id never starts with zero`() {
        // الصفر البادئ يضيع عند نسخ المعرّف إلى حقل رقمي أو جدول بيانات،
        // فيتحوّل 01234 إلى 1234 ويصبح معرّفًا مختلفًا أو غير صالح.
        repeat(200) { assertFalse(generator.next().startsWith("0")) }
    }

    @Test
    fun `ids are unique across many persisted generations`() {
        val issued = linkedSetOf<String>()
        whenever(repository.existsByRedId(org.mockito.kotlin.any())).thenAnswer { invocation ->
            issued.contains(invocation.getArgument<String>(0))
        }

        repeat(500) { issued += generator.next() }
        assertEquals(500, issued.size)
    }

    @Test
    fun `generation is random not sequential`() {
        // التوليد التسلسلي يكشف ترتيب التسجيل وحجم قاعدة المستخدمين.
        whenever(repository.existsByRedId(org.mockito.kotlin.any())).thenReturn(false)
        val ids = (1..100).map { generator.next().toInt() }
        val ascending = ids.zipWithNext().count { (a, b) -> b == a + 1 }
        assertTrue(ascending < 5, "المعرّفات تبدو تسلسلية — $ascending زيادة متتالية")
    }

    @Test
    fun `collision is retried and eventually returns a fresh id`() {
        var called = false
        whenever(repository.existsByRedId(org.mockito.kotlin.any())).thenAnswer {
            if (!called) { called = true; true } else false
        }
        val id = generator.next()
        assertNotEquals("", id)
        assertTrue(RedIdGenerator.isValid(id))
    }

    @Test
    fun `total space matches the five digit range minus the reserved id`() {
        assertEquals(89_999, RedIdGenerator.TOTAL_SPACE)
        assertEquals(10_001, RedIdGenerator.MIN_ID)
        assertEquals(99_999, RedIdGenerator.MAX_ID)
        assertEquals("10000", RedIdGenerator.SYSTEM_ID)
    }

    @Test
    fun `system id is reserved and never generated`() {
        // مُرسِل رسائل التحكم يحمل هذا المعرّف؛ منحه لمستخدم حقيقي
        // يجعل رسائل الخادم منسوبة إليه.
        whenever(repository.existsByRedId(org.mockito.kotlin.any())).thenReturn(false)
        repeat(2_000) { assertNotEquals(RedIdGenerator.SYSTEM_ID, generator.next()) }
    }

    @Test
    fun `system id is not resolvable through normalize`() {
        assertNull(RedIdGenerator.normalize(RedIdGenerator.SYSTEM_ID))
    }

    @Test
    fun `isValid accepts only five digit ids`() {
        assertTrue(RedIdGenerator.isValid("10001"))
        assertTrue(RedIdGenerator.isValid("99999"))
        assertFalse(RedIdGenerator.isValid("09999"), "الصفر البادئ مرفوض")
        assertFalse(RedIdGenerator.isValid("1234"), "أربعة أرقام مرفوضة")
        assertFalse(RedIdGenerator.isValid("123456"), "ستة أرقام مرفوضة")
        assertFalse(RedIdGenerator.isValid("YNS-ABCD-EFGH"), "الصيغة القديمة لم تعد صالحة")
        assertFalse(RedIdGenerator.isValid("777123456"), "رقم هاتف ليس معرّفًا")
        assertFalse(RedIdGenerator.isValid(null))
        assertFalse(RedIdGenerator.isValid(""))
    }

    @Test
    fun `normalize accepts pasted legacy prefixes and spacing`() {
        // ما قد يلصقه المستخدم فعلًا من رسالة أو من نسخة قديمة من التطبيق.
        assertEquals("12345", RedIdGenerator.normalize("12345"))
        assertEquals("12345", RedIdGenerator.normalize("  12345  "))
        assertEquals("12345", RedIdGenerator.normalize("YNS-12345"))
        assertEquals("12345", RedIdGenerator.normalize("RED-12345"))
        assertEquals("12345", RedIdGenerator.normalize("yns-12345"))
    }

    @Test
    fun `normalize refuses to guess when input is not an id`() {
        // لا يُخمّن: إدخال ناقص أو زائد يعود null لا معرّفًا مبتورًا.
        assertNull(RedIdGenerator.normalize("1234"))
        assertNull(RedIdGenerator.normalize("123456"))
        assertNull(RedIdGenerator.normalize("ahmed"))
        assertNull(RedIdGenerator.normalize(""))
        assertNull(RedIdGenerator.normalize(null))
    }
}
