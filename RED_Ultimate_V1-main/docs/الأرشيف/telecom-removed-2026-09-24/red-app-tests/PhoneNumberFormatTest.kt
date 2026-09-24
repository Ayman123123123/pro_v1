package com.red.sovereign.features.pstn

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * حارس تنسيق أرقام الهاتف.
 *
 * فُصلت [formatPhoneNumber] عن `IncomingPstnCallScreen` قبل أرشفتها،
 * وهذه الاختبارات تثبّت سلوكها حتى لا يضيع بضياع الشاشة، وتحرس
 * الحدود العددية (4، 6، 7، 9) التي يسهل أن تنزلق عند أي تعديل.
 */
class PhoneNumberFormatTest {

    @Test
    fun `الرقم اليمني المحلي الكامل يُقسم ثلاثًا ثلاثًا`() {
        assertEquals("711 234 567", formatPhoneNumber("711234567"))
    }

    @Test
    fun `الصيغة الدولية تُعرض كما هي دون تقسيم`() {
        // تقسيم الدولي بقاعدة محلية واحدة يُنتج تنسيقًا خاطئًا لأن
        // التجميع يختلف باختلاف رمز الدولة.
        assertEquals("+967711234567", formatPhoneNumber("+967711234567"))
        assertEquals("+15551234567", formatPhoneNumber("+15551234567"))
    }

    @Test
    fun `حدود المجموعة القصيرة أربعة إلى ستة`() {
        assertEquals("123 4", formatPhoneNumber("1234"))
        assertEquals("123 456", formatPhoneNumber("123456"))
    }

    @Test
    fun `حدود المجموعة الطويلة سبعة إلى تسعة`() {
        assertEquals("123 456 7", formatPhoneNumber("1234567"))
        assertEquals("123 456 789", formatPhoneNumber("123456789"))
    }

    @Test
    fun `ما دون أربعة أرقام يبقى دون تغيير`() {
        assertEquals("", formatPhoneNumber(""))
        assertEquals("123", formatPhoneNumber("123"))
    }

    @Test
    fun `ما فوق تسعة أرقام يبقى دون تغيير`() {
        // أطول من الخطة المحلية، فأي تقسيم افتراضي سيكون تخمينًا.
        assertEquals("1234567890", formatPhoneNumber("1234567890"))
    }

    @Test
    fun `التنسيق لا يفقد أي رقم`() {
        // خاصية عامة: التنسيق يُدخل مسافات فقط ولا يحذف محارف.
        listOf("1234", "123456", "1234567", "123456789", "711234567").forEach { input ->
            assertEquals(input, formatPhoneNumber(input).replace(" ", ""))
        }
    }
}
