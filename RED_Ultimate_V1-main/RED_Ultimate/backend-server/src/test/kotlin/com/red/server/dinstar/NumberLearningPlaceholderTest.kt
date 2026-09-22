package com.red.server.dinstar

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * حماية المجمّع من الأرقام الاصطناعية.
 *
 * ## لماذا هذا اختبار وليس تعليقًا
 *
 * المجمّع كان يحمل `777123456` و`712345678` و`701234567` — أرقام توثيق
 * لا وجود لها على الشبكة. المحرك كان يتصل بها كل دقيقة أربعة أيام: كل
 * محاولة تُطلب من الشبكة فعلًا (فتُحاسَب) ثم تفشل، والفشل كان بلا كبح
 * فيُعاد فورًا. 1682 محاولة مسجَّلة.
 *
 * الاختبار يثبّت التمييز: نمط اصطناعي يُرفض، ورقم يمني حقيقي من هذا
 * النشر يُقبل. الخطأ في الاتجاه الثاني أخطر — رفض رقم صالح يُعطّل الميزة
 * صامتًا، فكل رقم من الأسطول الفعلي مذكور صريحًا.
 */
class NumberLearningPlaceholderTest {

    @Test
    @DisplayName("الأنماط التسلسلية والمكرّرة تُرفض قبل أي إنفاق")
    fun rejectsPlaceholders() {
        // الثلاثة التي وُجدت فعلًا في المجمّع
        assertTrue(NumberLearningService.looksLikePlaceholder("777123456"))
        assertTrue(NumberLearningService.looksLikePlaceholder("712345678"))
        assertTrue(NumberLearningService.looksLikePlaceholder("701234567"))
        // خانة واحدة مكرّرة
        assertTrue(NumberLearningService.looksLikePlaceholder("777777777"))
        assertTrue(NumberLearningService.looksLikePlaceholder("000000000"))
        // أقصر من أن يكون رقمًا يمنيًا
        assertTrue(NumberLearningService.looksLikePlaceholder("12345"))
        assertTrue(NumberLearningService.looksLikePlaceholder(""))
    }

    @Test
    @DisplayName("أرقام الأسطول الحقيقية الثمانية تمرّ كلها")
    fun acceptsRealFleetNumbers() {
        // مستخرجة من الجهاز نفسه عبر بصمة الشبكة — لا واحدة منها اصطناعية
        listOf(
            "712065805", "712065242", "712068639", "712065191",
            "712065388", "712064924", "712065754", "712065586"
        ).forEach { number ->
            assertFalse(
                NumberLearningService.looksLikePlaceholder(number),
                "رقم أسطول حقيقي رُفض خطأً: $number"
            )
        }
    }

    @Test
    @DisplayName("الصيغ الدولية لا تُربك الفحص")
    fun toleratesDialingPrefixes() {
        // التنقية تعمل على الأرقام فقط، فالبادئة لا تُغيّر الحكم
        assertFalse(NumberLearningService.looksLikePlaceholder("+967712065805"))
        assertFalse(NumberLearningService.looksLikePlaceholder("00967712065805"))
    }
}
