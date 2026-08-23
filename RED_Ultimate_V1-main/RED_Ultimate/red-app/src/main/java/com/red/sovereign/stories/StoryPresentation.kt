package com.red.sovereign.stories

import kotlin.math.max

/** يعرض عمر الحالة بالعربية من timestamp خادم ISO-8601 أو milliseconds محلية. */
internal fun storyAgeLabel(createdAt: String, nowMs: Long = System.currentTimeMillis()): String {
    val createdAtMs = storyTimestamp(createdAt)
    if (createdAtMs <= 0L) return "منذ قليل"

    val elapsedSeconds = max(0L, (nowMs - createdAtMs) / 1_000L)
    return when {
        elapsedSeconds < 60L -> "الآن"
        elapsedSeconds < 3_600L -> "منذ ${elapsedSeconds / 60L} دقيقة"
        elapsedSeconds < 86_400L -> "منذ ${elapsedSeconds / 3_600L} ساعة"
        elapsedSeconds < 172_800L -> "أمس"
        else -> "منذ ${elapsedSeconds / 86_400L} يوم"
    }
}
