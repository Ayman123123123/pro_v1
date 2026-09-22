package com.red.sovereign.features.calls

import androidx.compose.ui.graphics.Color

// ─── Operator Info ─────────────────────────────────────────────────────────

data class OperatorInfo(
    val key: String,           // اسم المشغل الإنجليزي
    val name: String,          // الاسم العربي
    val isMobile: Boolean,
    val brandColor: Color,
    val prefix2: String,       // البادئة بعد +967 (رقمان)
    val technology: String = "",
    val apiName: String = key,
    val iconLetter: String = name.take(1)
)

/**
 * كاشف المشغل اليمني — مزامن مع Backend YemenNumberPlan (المصدر الوحيد للحقيقة).
 *
 * | البادئة | المشغل            |
 * |---------|-------------------|
 * | 700-709 | YTelecom (واي)    |
 * | 710-719 | Sabafon (سبأفون)  |
 * | 722     | Sabafon عدن VoLTE |
 * | 730-739 | YOU (يو)          |
 * | 770-789 | YemenMobile       |
 * | 10x     | Yemen4G (عرض فقط) |
 *
 * المطابقة الثلاثية (722) أولاً ثم الثنائية.
 * مصدر البيانات: خطة الترقيم الوطنية اليمنية + YemenNumberPlan في الباكند.
 */
object YemeniOperatorDetector {

    private val SABAFON = OperatorInfo("Sabafon", "سبأفون", true, Color(0xFFFDB913), "71", "GSM/3G/4G", "Sabafon", "س")

    private val OPERATORS_3: Map<String, OperatorInfo> = mapOf(
        "722" to SABAFON.copy(prefix2 = "722")
    )

    private val OPERATORS: Map<String, OperatorInfo> = mapOf(
        "70" to OperatorInfo("YTelecom", "واي", true, Color(0xFF00A1E4), "70", "GSM/4G", "YTelecom", "و"),
        "71" to SABAFON,
        "73" to OperatorInfo("YOU", "يو", true, Color(0xFFFFF200), "73", "GSM/4G", "YOU", "ي"),
        "77" to OperatorInfo("YemenMobile", "يمن موبايل", true, Color(0xFFE31E24), "77", "CDMA/4G/5G", "YemenMobile", "ي"),
        "78" to OperatorInfo("YemenMobile", "يمن موبايل", true, Color(0xFFE31E24), "78", "CDMA/4G/5G", "YemenMobile", "ي"),
        "10" to OperatorInfo("Yemen4G", "يمن فورجي", false, Color(0xFF009688), "10", "LTE", "Yemen4G", "4")
    )

    /** كل بادئات المحمول الثلاثية الصالحة — مطابقة لـ YemenNumberPlan.MOBILE_PREFIXES_3 */
    val MOBILE_PREFIXES_3: Set<String> = buildSet {
        (700..709).forEach { add(it.toString()) }
        (710..719).forEach { add(it.toString()) }
        addAll(OPERATORS_3.keys)
        (730..739).forEach { add(it.toString()) }
        (770..779).forEach { add(it.toString()) }
        (780..789).forEach { add(it.toString()) }
    }

    private val UNKNOWN = OperatorInfo("Unknown", "غير محدد", false, Color(0xFF616161), "", "", "Unknown", "?")

    /** تصنيف nullable — الثلاثي أولاً ثم الثنائي (مطابق للباكند). */
    fun classify(raw: String): OperatorInfo? {
        val local = normalize(raw).takeIf { it.length in 6..12 } ?: return null
        if (local.length >= 3) {
            OPERATORS_3[local.substring(0, 3)]?.let { return it }
        }
        if (local.length < 2) return null
        return OPERATORS[local.substring(0, 2)]
    }

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
        return classify(number) ?: UNKNOWN
    }

    fun getOperatorName(number: String): String = getOperatorInfo(number).name

    fun getOperatorColor(number: String): Color = getOperatorInfo(number).brandColor

    fun isMobileNumber(number: String): Boolean = getOperatorInfo(number).isMobile

    /**
     * هل الرقم يمني صحيح؟ (mobile فقط — البادئة الثلاثية إلزامية)
     */
    fun isValidYemeniMobile(number: String): Boolean {
        val local = normalize(number)
        if (local.length != 9) return false
        return local.substring(0, 3) in MOBILE_PREFIXES_3
    }

    /**
     * يُطبّع الرقم إلى صيغة محلية بدون 0 (مثلاً: 777123456)
     * مطابق لـ YemenNumberPlan.normalizeLocal().
     */
    fun normalize(value: String): String {
        val compact = value.filter { it.isDigit() || it == '+' }
        return when {
            compact.startsWith("+967") -> compact.removePrefix("+967")
            compact.startsWith("00967") -> compact.removePrefix("00967")
            compact.startsWith("967") && compact.length > 3 -> compact.removePrefix("967")
            compact.startsWith("0") && compact.length > 1 -> compact.removePrefix("0")
            else -> compact
        }
    }

    /**
     * جميع المشغلين المعروفين.
     */
    fun allOperators(): List<OperatorInfo> = OPERATORS.values.distinctBy { it.key }
}
