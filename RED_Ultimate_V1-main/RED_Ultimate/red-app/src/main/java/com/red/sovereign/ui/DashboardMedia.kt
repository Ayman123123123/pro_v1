package com.red.sovereign.ui

/**
 * ════════════════════════════════════════════════════════════════════════
 *  DashboardMedia — التنسيقات المشتركة لفقاعات الوسائط والوقت (عربي)
 * ════════════════════════════════════════════════════════════════════════
 *
 *  استُخرج من `RedDashboard.kt` (كان 4309 أسطر) لتقليص الوحش:
 *  - فقاعات Image/Video/Audio/FileMessage الحيّة تعيش أصلًا في
 *    `MessageContent.kt` (مستخرجة سابقًا) — وما كان في اللوحة نسخًا
 *    ميتة مكررة حُذفت بدل نقلها.
 *  - ما بقي هنا هو المشترك الحي: تنسيق الحجم والوقت والتاريخ ودور
 *    المجموعة — بتمرير مباشر من اللوحة و`MessageContent.kt` معًا.
 *
 *  التعريب: كل المنسقات تستخدم `Locale("ar")` صراحةً (لا `Locale.US`
 *  ولا الافتراضي)، والوحدات عربية مطابقة لمفاتيح `strings.xml`:
 *  ‏`file_size_mb` / `file_size_kb` / `file_size_b` — عدّل النصوص هناك
 *  أولًا ثم طابق الليترالات هنا.
 */

internal val dashboardArabicLocale: java.util.Locale = java.util.Locale("ar")

/** حجم ملف بالعربية: «٢٫٣ م.ب» / «٤٥٫٦ ك.ب» / «٩٠٠ بايت». */
internal fun dashboardFormatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 ->
        String.format(dashboardArabicLocale, "%.1f م.ب", bytes / (1024.0 * 1024.0))
    bytes >= 1024 ->
        String.format(dashboardArabicLocale, "%.1f ك.ب", bytes / 1024.0)
    else -> String.format(dashboardArabicLocale, "%d بايت", bytes)
}

/** مدة m:ss — أرقام لاتينية ثابتة لتوافق مشغلات الصوت. */
internal fun dashboardFormatDuration(seconds: Int): String =
    String.format(dashboardArabicLocale, "%d:%02d", seconds / 60, seconds % 60)

internal fun dashboardIsSameDay(a: Long, b: Long): Boolean {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = a }
    val d1 = cal.get(java.util.Calendar.DAY_OF_YEAR)
    val y1 = cal.get(java.util.Calendar.YEAR)
    cal.timeInMillis = b
    return y1 == cal.get(java.util.Calendar.YEAR) && d1 == cal.get(java.util.Calendar.DAY_OF_YEAR)
}

/** تسمية اليوم: اليوم / أمس / dd/MM/yyyy بالعربية. */
internal fun dashboardDateLabel(timestamp: Long): String {
    val now = System.currentTimeMillis()
    return when {
        dashboardIsSameDay(timestamp, now) -> "اليوم"
        dashboardIsSameDay(timestamp, now - 86400000L) -> "أمس"
        else -> java.text.SimpleDateFormat("dd/MM/yyyy", dashboardArabicLocale)
            .format(java.util.Date(timestamp))
    }
}

/** وقت نسبي للقوائم: الآن / Nد / Nس / أمس / dd/MM بالعربية. */
internal fun dashboardRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val min = diff / 60000
    return when {
        diff < 60000 -> "الآن"
        diff < 3600000 -> String.format(dashboardArabicLocale, "%dد", min)
        diff < 86400000 -> String.format(dashboardArabicLocale, "%dس", diff / 3600000)
        diff < 172800000 -> "أمس"
        else -> java.text.SimpleDateFormat("dd/MM", dashboardArabicLocale)
            .format(java.util.Date(timestamp))
    }
}

/** وقت الساعة داخل الفقاعة (مثل واتساب: 4:20 م / 11:05 ص) — بالعربية. */
internal fun dashboardFormatClockTime(timestamp: Long): String =
    java.text.SimpleDateFormat("h:mm a", dashboardArabicLocale)
        .format(java.util.Date(timestamp))

/** دور عضو المجموعة بالعربية. */
internal fun dashboardGroupRoleLabel(role: String): String = when (role) {
    "OWNER" -> "المالك"
    "ADMIN" -> "مسؤول"
    else -> "عضو"
}
