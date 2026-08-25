package com.red.sovereign.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * حارس معماري لتماثل قراءة/كتابة الإعدادات.
 *
 * لا يتوفّر Robolectric في هذه الوحدة، فيتعذّر تشغيل `SharedPreferences`
 * حقيقية. لكن العيب المستهدف بنيويّ لا سلوكيّ: **إعداد يُكتب ولا يُقرأ**
 * (فيضيع عند إعادة التشغيل) أو **يُقرأ ولا يُكتب** (فلا يُحفظ أبداً).
 * يُكتشف ذلك بفحص المصدر نفسه.
 *
 * كانت خريطة المفاتيح مكرّرة ثلاث مرات (81 سلسلة نصّية)، فكان نسيان
 * أحد المواضع عند إضافة إعداد جديد عيباً صامتاً. هذا الاختبار يمنع
 * عودة ذلك حتى لو أُعيد التكرار مستقبلاً.
 */
class SettingsPersistenceTest {

    private fun source(): String {
        val candidates = listOf(
            "src/main/java/com/red/sovereign/settings/SettingsViewModel.kt",
            "red-app/src/main/java/com/red/sovereign/settings/SettingsViewModel.kt",
            "../red-app/src/main/java/com/red/sovereign/settings/SettingsViewModel.kt",
            "RED_Ultimate_V1-main/RED_Ultimate/red-app/src/main/java/com/red/sovereign/settings/SettingsViewModel.kt"
        )
        val file = candidates.map { File(it) }.firstOrNull { it.isFile }
        assertTrue(
            "تعذّر العثور على SettingsViewModel.kt من ${File(".").absolutePath}",
            file != null
        )
        return file!!.readText()
    }

    private fun section(text: String, start: String, end: String): String {
        val from = text.indexOf(start)
        val to = text.indexOf(end)
        assertTrue("تعذّر تحديد المقطع $start", from in 0 until to)
        return text.substring(from, to)
    }

    private fun keysIn(text: String): Set<String> =
        Regex("""Keys\.([A-Z_0-9]+)""").findAll(text).map { it.groupValues[1] }.toSet()

    @Test
    fun `كل إعداد يُقرأ يُكتب أيضاً والعكس`() {
        val src = source()
        val read = section(src, "fun SharedPreferences.readSettings", "fun SharedPreferences.writeSettings")
        val write = section(src, "fun SharedPreferences.writeSettings", "class SettingsViewModel")

        val readKeys = keysIn(read)
        val writeKeys = keysIn(write)

        assertEquals("إعدادات تُقرأ ولا تُحفظ: ${readKeys - writeKeys}", emptySet<String>(), readKeys - writeKeys)
        assertEquals("إعدادات تُحفظ ولا تُقرأ: ${writeKeys - readKeys}", emptySet<String>(), writeKeys - readKeys)
        assertTrue("عدد المفاتيح أقل من المتوقع", readKeys.size >= 27)
    }

    @Test
    fun `كل مفتاح معرَّف مستعمَل فعلاً`() {
        val src = source()
        val keysObject = section(src, "private object Keys", "internal fun SharedPreferences.readSettings")
        val declared = Regex("""const val ([A-Z_0-9]+)""").findAll(keysObject).map { it.groupValues[1] }.toSet()
        val used = keysIn(src)
        assertEquals("مفاتيح معرَّفة بلا استعمال: ${declared - used}", emptySet<String>(), declared - used)
    }

    @Test
    fun `لا مفاتيح نصية حرفية خارج تعريف Keys`() {
        val src = source()
        val body = src.substring(src.indexOf("internal fun SharedPreferences.readSettings"))
        // أي "snake_case" داخل نداء get/put يعني عودة التكرار. النمط
        // يتسامح مع الأسطر الجديدة لأن النداءات الطويلة تُلفّ على أسطر،
        // وبدون ذلك يمرّ المفتاح الحرفي دون كشف.
        // ملاحظة: تُستعمل سلاسل عادية بمحارف هروب لا سلاسل خام، لأن نمطاً
        // خاماً ينتهي بعلامة اقتباس يُنتج """" ولا يستطيع Kotlin تحديد
        // نهايته — خطأ ترجمة صامت المظهر.
        val literals = Regex("\\.(?:get|put)\\w+\\(\\s*\"([a-z][a-z0-9_]*)\"", RegexOption.DOT_MATCHES_ALL)
            .findAll(body).map { it.groupValues[1] }.toList()
        assertEquals("مفاتيح حرفية يجب نقلها إلى Keys: $literals", emptyList<String>(), literals)

        // حارس مكمّل: لا تظهر أي سلسلة تشبه مفتاح تخزين خارج object Keys
        val keysObject = section(src, "private object Keys", "internal fun SharedPreferences.readSettings")
        val declaredValues = Regex("\"([^\"]+)\"").findAll(keysObject).map { it.groupValues[1] }.toSet()
        val strayKeys = Regex("\"([a-z][a-z0-9]*(?:_[a-z0-9]+){1,})\"")
            .findAll(body).map { it.groupValues[1] }
            .filter { it in declaredValues }
            .toList()
        assertEquals(
            "مفاتيح تخزين مكتوبة حرفياً خارج Keys: $strayKeys",
            emptyList<String>(),
            strayKeys
        )
    }

    @Test
    fun `اسم ملف التخزين ثابت حفاظاً على إعدادات المستخدمين الحاليين`() {
        val src = source()
        val name = Regex("PREFS_NAME = \"([^\"]+)\"").find(src)?.groupValues?.get(1)
        assertEquals(
            "تغيير اسم ملف التفضيلات يفقد كل إعدادات المستخدمين الحاليين",
            "younes_user_preferences",
            name
        )
    }

    @Test
    fun `أسماء المفاتيح المخزَّنة لم تتغيّر`() {
        val src = source()
        val keysObject = section(src, "private object Keys", "internal fun SharedPreferences.readSettings")
        val values = Regex("const val [A-Z_0-9]+ = \"([^\"]+)\"")
            .findAll(keysObject).map { it.groupValues[1] }.toSet()

        // الأسماء كما كُتبت على أجهزة المستخدمين قبل التوحيد
        val expected = setOf(
            "font_scale", "high_contrast", "compact_mode", "reduce_motion",
            "read_receipts", "typing_indicators", "link_previews",
            "auto_download_wifi", "auto_download_mobile", "auto_download_limit_mb",
            "notification_preview", "message_notifications", "call_notifications",
            "data_saver_calls", "playback_speed", "app_lock_enabled",
            "hide_last_seen", "last_seen_visibility", "profile_photo_visibility",
            "about_visibility", "who_can_add_groups", "who_can_call",
            "enter_to_send", "save_media_gallery", "auto_archive_muted",
            "group_notifications", "lock_timeout_seconds"
        )
        assertEquals("مفاتيح مفقودة: ${expected - values}", emptySet<String>(), expected - values)
    }

    @Test
    fun `القيم الافتراضية تُشتقّ من YounesSettings لا من أرقام مكرّرة`() {
        val src = source()
        val read = section(src, "fun SharedPreferences.readSettings", "fun SharedPreferences.writeSettings")
        val derived = Regex("""defaults\.\w+""").findAll(read).count()
        assertTrue(
            "القيم الافتراضية يجب أن تأتي من YounesSettings() حتى لا تتباعد عن الـdata class (وُجد $derived)",
            derived >= 25
        )
    }

    @Test
    fun `التوافق الرجعي لرؤية آخر ظهور محفوظ`() {
        val src = source()
        val read = section(src, "fun SharedPreferences.readSettings", "fun SharedPreferences.writeSettings")
        assertTrue(
            "يجب اشتقاق lastSeenVisibility من hide_last_seen القديم عند غياب المفتاح الأحدث",
            read.contains("if (hideLastSeen) \"NOBODY\" else \"EVERYONE\"")
        )
    }
}
