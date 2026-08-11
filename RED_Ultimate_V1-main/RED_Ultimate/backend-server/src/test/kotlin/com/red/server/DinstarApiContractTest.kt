package com.red.server

import com.red.server.services.DinstarHardwareService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * عقد واجهة DINSTAR HTTP API — القيم الموثقة رسميًا.
 *
 * المصدر: «Dinstar GSM Gateway HTTP API» الإصدار 1.1 (2019-10-16)،
 * الأقسام 2.2 و2.3 و7.3 و10.3.
 *
 * هذه ثوابت بروتوكول خارجي لا خيارات تصميم: تغييرها يكسر التفاهم مع
 * الجهاز. الاختبار يثبّتها حتى لا «تُبسَّط» لاحقًا بحسن نية.
 */
class DinstarApiContractTest {

    @Test
    fun `حد المستلمين 128 لا 32`() {
        // 32 هو حد query_sms_result لا send_sms. الخلط بينهما كان يرفض
        // دفعات مشروعة قبل أن تصل إلى البوابة أصلًا.
        assertEquals(128, DinstarHardwareService.MAX_SMS_RECIPIENTS)
    }

    @Test
    fun `حد نص الرسالة 1500 بايت`() {
        assertEquals(1500, DinstarHardwareService.MAX_SMS_TEXT_BYTES)
    }

    @Test
    fun `الحد يُقاس بالبايت لا بعدد الأحرف`() {
        // العربية بـ UTF-8 بايتان للحرف في النطاق الأساسي: 800 حرف =
        // 1600 بايت، تتجاوز الحد رغم أنها أقل من 1500 «حرف». القياس
        // بالأحرف كان سيمرّر طلبًا ترفضه البوابة بـ 413.
        val arabic = "س".repeat(800)
        assertEquals(1600, arabic.toByteArray(Charsets.UTF_8).size)
        assertTrue(
            arabic.toByteArray(Charsets.UTF_8).size > DinstarHardwareService.MAX_SMS_TEXT_BYTES,
            "800 حرف عربي يجب أن تتجاوز حد البايتات"
        )
        assertTrue(
            arabic.length < DinstarHardwareService.MAX_SMS_TEXT_BYTES,
            "لكنها أقل من الحد لو قِيس بالأحرف — وهذا سبب وجوب القياس بالبايت"
        )
    }
}
