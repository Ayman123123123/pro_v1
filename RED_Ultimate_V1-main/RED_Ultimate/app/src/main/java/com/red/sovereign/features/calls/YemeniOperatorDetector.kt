package com.red.sovereign.features.calls

import androidx.compose.ui.graphics.Color

// ─── Operator Info ─────────────────────────────────────────────────────────

data class OperatorInfo(
    val key: String,           // اسم المشغل الإنجليزي
    val name: String,          // الاسم العربي
    val isMobile: Boolean,
    val brandColor: Color,
    val prefix2: String        // البادئة بعد +967 (رقمان)
)

/**
 * كاشف المشغل اليمني.
 *
 * يحدد المشغل من أول رقمين من الرقم المحلي (بدون 0 أو +967).
 *
 * مصدر البيانات: خطة الترقيم الوطنية اليمنية (وزارة الاتصالات)
 * + هيئة تنظيم الاتصالات (TRA Yemen).
 *
 * | البادئة | المشغل              | اللون       |
 * |---------|---------------------|-------------|
 * | 70      | Y Telecom (واي)    | أزرق زاهٍ  |
 * | 71      | Sabafon (سبأفون)   | أحمر        |
 * | 73      | YOU (يو / MTN سابق)| أصفر ذهبي  |
 * | 77, 78  | Yemen Mobile (يمن) | أخضر        |
 * | 10      | Yemen 4G (ثابت)    | بنفسجي      |
 * | 75, 76  | يمن موبايل (جديد)  | أخضر        |
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

    /**
     * يحدد معلومات المشغل من الرقم المُدخَل.
     *
     * يقبل:
     * - رقم دولي: +967XXXXXXXXX أو 967XXXXXXXXX
     * - رقم محلي: 0XXXXXXXXX أو XXXXXXXXX
     *
     * @return [OperatorInfo] أو [UNKNOWN] إذا لم يُعرَّف المشغل
     */
    fun getOperatorInfo(number: String): OperatorInfo {
        if (number.isBlank()) return UNKNOWN

        val local = normalize(number)
        if (local.length < 2) return UNKNOWN

        val prefix2 = local.take(2)
        return OPERATORS[prefix2] ?: UNKNOWN
    }

    /**
     * هل الرقم يمني صحيح؟ (mobile فقط)
     */
    fun isValidYemeniMobile(number: String): Boolean {
        val local = normalize(number)
        if (local.length !in 9..9) return false
        val prefix = local.take(2)
        return OPERATORS[prefix]?.isMobile == true
    }

    /**
     * يُطبّع الرقم إلى صيغة محلية بدون 0 (مثلاً: 777123456)
     */
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

    /**
     * جميع المشغلين المعروفين.
     */
    fun allOperators(): List<OperatorInfo> = OPERATORS.values.distinct()
}
