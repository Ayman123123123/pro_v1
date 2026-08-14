package com.red.sovereign.features.pstn

import androidx.compose.ui.graphics.Color

// ─── Operator Info ─────────────────────────────────────────────────────────

data class OperatorInfo(
    val key: String,
    val name: String,
    val isMobile: Boolean,
    val brandColor: Color,
    val prefix2: String
)

/**
 * كاشف المشغل اليمني — نسخة pstn مُعاد تصديرها من core.
 * تُستخدَم في [DialPadScreen] و[PstnViewModel].
 */
object YemeniOperatorDetector {
    private val OPERATORS: Map<String, OperatorInfo> = mapOf(
        "70" to OperatorInfo("YTelecom", "واي", true, Color(0xFF1565C0), "70"),
        "71" to OperatorInfo("Sabafon", "سبأفون", true, Color(0xFFE53935), "71"),
        "72" to OperatorInfo("Sabafon", "سبأفون", true, Color(0xFFE53935), "72"),
        "73" to OperatorInfo("YOU", "يو", true, Color(0xFFFFC107), "73"),
        "74" to OperatorInfo("YOU", "يو", true, Color(0xFFFFC107), "74"),
        "75" to OperatorInfo("YemenMobile", "يمن موبايل", true, Color(0xFF43A047), "75"),
        "76" to OperatorInfo("YemenMobile", "يمن موبايل", true, Color(0xFF43A047), "76"),
        "77" to OperatorInfo("YemenMobile", "يمن موبايل", true, Color(0xFF43A047), "77"),
        "78" to OperatorInfo("YemenMobile", "يمن موبايل", true, Color(0xFF43A047), "78"),
        "10" to OperatorInfo("Yemen4G", "يمن فورجي", false, Color(0xFF7C4DFF), "10")
    )
    private val UNKNOWN = OperatorInfo("Unknown", "غير محدد", false, Color(0xFF616161), "")

    fun getOperatorInfo(number: String): OperatorInfo {
        if (number.isBlank()) return UNKNOWN
        val local = normalize(number)
        if (local.length < 2) return UNKNOWN
        return OPERATORS[local.take(2)] ?: UNKNOWN
    }

    fun normalize(value: String): String {
        val compact = value.filter { it.isDigit() || it == '+' }
        return when {
            compact.startsWith("+967") -> compact.removePrefix("+967")
            compact.startsWith("00967") -> compact.removePrefix("00967")
            compact.startsWith("967") && compact.length >= 12 -> compact.removePrefix("967")
            compact.startsWith("0") -> compact.removePrefix("0")
            else -> compact
        }
    }
}
