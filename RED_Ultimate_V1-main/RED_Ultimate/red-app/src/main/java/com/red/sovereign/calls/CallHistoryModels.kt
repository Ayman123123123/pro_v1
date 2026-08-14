package com.red.sovereign.calls

import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class CallHistoryItem(
    val id: String,
    val peerId: String,
    val peerLabel: String,
    val direction: String,        // OUTGOING / INCOMING
    val type: String,             // VOICE / VIDEO / GROUP / CONFERENCE / LIVE / SPACE
    val route: String,            // RED / DINSTAR
    val status: String,           // ANSWERED / MISSED / REJECTED / FAILED
    val startedAt: String,
    val answeredAt: String? = null,
    val endedAt: String? = null,
    // ── حقول التحسين الجديدة ──────────────────────────────────────────
    /** مدة المكالمة الفعلية بالثواني (0 إذا لم يُرد عليها) */
    val durationSeconds: Long = 0L,
    /** تقييم جودة الشبكة (0.0–5.0) بناءً على MOS */
    val qualityScore: Float = 0f,
    /** مصدر المكالمة: PRIVATE / GROUP / CONFERENCE / LIVE / SPACE */
    val callSource: String = "PRIVATE",
    /** معرف المجموعة/الغرفة إن كانت مكالمة جماعية أو مؤتمراً */
    val groupId: String? = null,
    val roomId: String? = null,
    /** معرفات المشاركين في المكالمات الجماعية */
    val participantIds: List<String> = emptyList(),
    /** هل كانت هناك مشاركة شاشة */
    val hadScreenShare: Boolean = false,
    /** هل تم تسجيل المكالمة */
    val wasRecorded: Boolean = false
)

/** Accepts epoch millis, epoch seconds, or ISO-8601 from the backend Instant serializer. */
fun parseCallTimestamp(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    value.toLongOrNull()?.let { raw ->
        return if (raw in 1_000_000_000L until 100_000_000_000L) raw * 1000L else raw
    }
    return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
}

/** حساب مدة المكالمة من الطوابع الزمنية */
fun CallHistoryItem.computedDurationSeconds(): Long {
    if (durationSeconds > 0) return durationSeconds
    val start = parseCallTimestamp(answeredAt) ?: return 0L
    val end   = parseCallTimestamp(endedAt)   ?: return 0L
    return (end - start).coerceAtLeast(0L) / 1000L
}

/** تنسيق المدة للعرض (مثل "1:23") */
fun Long.formatCallDuration(): String {
    if (this <= 0L) return ""
    val h = this / 3600
    val m = (this % 3600) / 60
    val s = this % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

