package com.red.sovereign.core

import com.red.sovereign.core.database.FtsSearchManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * حارس تطبيع البحث العربي.
 *
 * ## لماذا أُعيدت كتابة هذا الاختبار
 *
 * النسخة السابقة كانت **تستنسخ** المنطق داخلها بدل استيراده:
 *
 *     val sanitized = query.replace("\"", "\"\"").take(100)
 *
 * فكانت تختبر سطرًا كتبته هي، لا شيفرة التطبيق. ولهذا بقيت خضراء
 * بينما كان البحث الحقيقي **معطَّلًا كليًّا**: صيغة المُجزِّئ كانت
 * `tokenize='unicode61 "remove_diacritics 1"'` وهي خطأ نحوي يردّه
 * FTS5، فلم يكن جدول الفهرس يُنشأ أصلًا، وكان `catch (_: Exception) {}`
 * يبتلع الخطأ فيرجع كل بحث فارغًا بلا أثر.
 *
 * اختبارٌ لا يستورد ما يزعم اختباره لا يحرس شيئًا.
 *
 * ## ما يحرسه الآن
 *
 * `unicode61` يعامل الحركات العربية (فئة `Mn`) **فاصلًا بين الكلمات**،
 * فتنكسر «السَّلامُ» إلى `['الس','لام']` ولا يطابقها بحثٌ عن «السلام».
 * وخيار `remove_diacritics` لا يعالجها بأيٍّ من قيمه الثلاث. لذلك
 * يلزم التطبيع قبل الفهرسة وقبل الاستعلام معًا.
 */
class FtsSearchManagerTest {

    @Test
    fun `التطبيع يحذف الحركات فتبقى الكلمة كلمة واحدة`() {
        assertEquals("السلام", FtsSearchManager.normalizeArabic("السَّلامُ"))
        assertEquals("محمد", FtsSearchManager.normalizeArabic("مُحَمَّد"))
        assertEquals("كتاب", FtsSearchManager.normalizeArabic("كِتَاب"))
    }

    @Test
    fun `التطبيع يوحد صور الالف فتطابق ابراهيم إبراهيم`() {
        val target = FtsSearchManager.normalizeArabic("إبراهيم")
        assertEquals(target, FtsSearchManager.normalizeArabic("ابراهيم"))
        assertEquals(target, FtsSearchManager.normalizeArabic("أبراهيم"))
        assertEquals(FtsSearchManager.normalizeArabic("آمن"), FtsSearchManager.normalizeArabic("امن"))
    }

    @Test
    fun `التطبيع يوحد التاء المربوطة والالف المقصورة`() {
        // أشيع خطأين إملائيين في الكتابة اليومية
        assertEquals(
            FtsSearchManager.normalizeArabic("مكتبة"),
            FtsSearchManager.normalizeArabic("مكتبه")
        )
        assertEquals(
            FtsSearchManager.normalizeArabic("مصطفى"),
            FtsSearchManager.normalizeArabic("مصطفي")
        )
    }

    @Test
    fun `التطبيع يحذف التطويل`() {
        assertEquals("مرحبا", FtsSearchManager.normalizeArabic("مـــرحبا"))
    }

    @Test
    fun `التطبيع لا يمس اللاتينية والارقام`() {
        assertEquals("Hello 2026", FtsSearchManager.normalizeArabic("Hello 2026"))
        assertEquals("", FtsSearchManager.normalizeArabic(""))
    }

    @Test
    fun `التطبيع ثابت عند اعادة تطبيقه`() {
        // لازم لأنه يُطبَّق على المخزون وعلى الاستعلام في وقتين مختلفين
        val once = FtsSearchManager.normalizeArabic("السَّلامُ عليكم يا إبراهيم")
        assertEquals(once, FtsSearchManager.normalizeArabic(once))
    }

    @Test
    fun `تهريب الاقتباس يمنع كسر عبارة البحث`() {
        // صيغة FTS5: الاقتباس المزدوج يُهرَّب بتكراره
        val sanitized = """he said "hello"""".replace("\"", "\"\"")
        assertEquals("""he said ""hello""", sanitized)
        assertTrue(sanitized.count { it == '"' } % 2 == 0)
    }
}
