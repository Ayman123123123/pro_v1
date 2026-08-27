package com.red.server.services

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * اختيار ترميز الرسائل القصيرة.
 *
 * الخلفية: كان `sendSms` يفترض `GSM7BIT` لكل رسالة. الحرف العربي غير
 * موجود في أبجدية GSM 03.38 أصلًا، فكانت كل رسالة عربية تصل «?????» —
 * عطل كامل في السوق المستهدَف لا مجرد تدهور في الجودة.
 *
 * والحل ليس فرض UCS2 دائمًا: ذلك يهبط بسعة الجزء من 160 حرفًا إلى 70،
 * فتنقسم رسائل OTP الإنجليزية القصيرة وتتضاعف كلفتها. لذا يُشتق الترميز
 * من محتوى كل رسالة، وهذه الاختبارات تحرس طرفَي المقايضة معًا.
 */
@DisplayName("اشتقاق ترميز SMS من محتوى الرسالة")
class SmsEncodingDetectionTest {

    private fun detect(text: String) = DinstarHardwareService.detectEncoding(text)

    @Test
    @DisplayName("العربية تُرقَّى إلى UCS2 — العطل الأصلي")
    fun `arabic upgrades to ucs2`() {
        assertEquals("UCS2", detect("مرحبا بك في يونس"))
        assertEquals("UCS2", detect("رمز التحقق الخاص بك هو 4821"))
    }

    @Test
    @DisplayName("الإنجليزية تبقى GSM7BIT — حفظًا للسعة والكلفة")
    fun `ascii stays gsm7bit`() {
        assertEquals("GSM7BIT", detect("Your YOUNES code is 4821"))
        assertEquals("GSM7BIT", detect("Price: 50\$ (20%) @ 12:30"))
    }

    @Test
    @DisplayName("خلط حرف عربي واحد يكفي لترقية الرسالة كلها")
    fun `single non gsm char forces ucs2`() {
        assertEquals("UCS2", detect("Hello مرحبا"))
        assertEquals("UCS2", detect("Code: 1234 ✓"))
    }

    @Test
    @DisplayName("الإيموجي خارج الأبجدية")
    fun `emoji forces ucs2`() {
        assertEquals("UCS2", detect("Welcome 😀"))
    }

    @Test
    @DisplayName("حروف الهروب تبقى ضمن GSM — لا تُرقَّى بلا داعٍ")
    fun `gsm extension chars stay gsm7bit`() {
        // هذه ببايتين داخل gsm-7bit لكنها ليست UCS2.
        assertEquals("GSM7BIT", detect("[test]{x}|^~\\"))
        assertEquals("GSM7BIT", detect("Cost 10€"))
    }
    @Test
    @DisplayName("اللاتينية الممتدة الواردة في الأبجدية لا تُرقَّى، وما خرج عنها يُرقَّى")
    fun `latin extended in alphabet stays gsm7bit`() {
        // 3GPP TS 23.038 يُدرج مجموعة محدَّدة من اللاتينية الممتدة فقط:
        // é ù ì ò Ç Ø ø Å å Æ æ ß É Ä Ö Ñ Ü ä ö ñ ü à — وكلها 7-bit.
        assertEquals("GSM7BIT", detect("Café Ørsted åäöñü"))
        assertEquals("GSM7BIT", detect("Ærø ßeta École Çin"))

        // الحروف الكبيرة المُشكَّلة خارج تلك المجموعة: `À` (U+00C0) ليست في
        // الأبجدية — الموجود هو `à` الصغيرة عند 0x7F وحدها. فترقية النص
        // إلى UCS2 هي السلوك الصحيح لا عيب: إرسالها كـ7-bit يُفقد الحرف.
        assertEquals("UCS2", detect("Àande"))
        assertEquals("UCS2", detect("Œuvre"))
    }

    @Test
    @DisplayName("الأبجدية تطابق 3GPP TS 23.038 في الحجم والمحتوى")
    fun `alphabet matches spec`() {
        val alphabet = DinstarHardwareService.GSM_03_38_ALPHABET
        assertEquals(137, alphabet.size, "حجم الأبجدية تغيّر — راجع 3GPP TS 23.038")
        assertTrue('@' in alphabet && 'A' in alphabet && 'z' in alphabet && '9' in alphabet)
        assertTrue('€' in alphabet, "اليورو من جدول الهروب")
        assertFalse('ا' in alphabet, "العربية ليست في أبجدية GSM")
        assertFalse('م' in alphabet)
    }

    @Test
    @DisplayName("النص الفارغ لا يُرقَّى")
    fun `empty text is gsm7bit`() {
        // `sendSms` يرفض الفراغ قبل هذه النقطة، لكن الدالة يجب ألا تنهار.
        assertEquals("GSM7BIT", detect(""))
    }

    @Test
    @DisplayName("الثابت AUTO هو الافتراضي المعلن")
    fun `auto is the declared default`() {
        assertEquals("AUTO", DinstarHardwareService.AUTO_ENCODING)
    }
}