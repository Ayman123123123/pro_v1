package com.red.server.services

/**
 * تحويل قراءة الإشارة الخام من بوابات DINSTAR UC2000 إلى قيمة ذات معنى.
 *
 * البوابة تُعيد في `get_port_info` حقل `signal` كما تُرجعه وحدة الراديو
 * عبر الأمر `AT+CSQ`، وهو مُعرَّف في **3GPP TS 27.007 §8.5**:
 *
 * | القيمة الخام | المعنى                                    |
 * |--------------|-------------------------------------------|
 * | 0            | -113 dBm أو أقل                          |
 * | 1            | -111 dBm                                 |
 * | 2..30        | -109 dBm .. -53 dBm (خطوة 2 dBm)        |
 * | 31           | -51 dBm أو أعلى                          |
 * | **99**       | **غير معروفة أو غير قابلة للكشف**        |
 * | 100..191     | RSCP ممتد لـ TD-SCDMA (-116 .. -25 dBm) |
 * | 199          | غير معروفة (النطاق الممتد)                |
 *
 * ## الإصلاحات الحرجة المطبقة:
 *
 * 1. **عطل القراءة 99**: كانت `coerceIn(0,31)` تحوّل 99 إلى 31 = 100%، فتُختار شريحة ميتة.
 *    الآن: 99 → `Grade.UNUSABLE` مع `usable=false` و `percent=null`.
 *
 * 2. **العتبة الثنائية**: `usable = dbm >= -100` كان يُقصي كل المنافذ (قراءاتها 5..6 = -103..-101).
 *    الآن: نظام **ثلاثي الدرجات** (GOOD/WEAK/UNUSABLE) مع عتبات قابلة للضبط.
 *
 * 3. **إشارة قابلة للاستخدام للـ Load Balancer**: `usable = grade != UNUSABLE` — لا تُستبعد المنافذ الضعيفة فوراً.
 */
object DinstarSignal {

    const val UNKNOWN_BASIC = 99
    const val UNKNOWN_EXTENDED = 199
    const val MIN_VIABLE_DBM = -112       // أدنى إشارة يمكن أن تنجز مكالمة (طوارئ)
    const val DEFAULT_MIN_GOOD_DBM = -95  // عتبة "جيد" افتراضية (أكثر مرونة من -100)
    private const val MIN_DBM = -113
    private const val MAX_DBM = -51

    enum class Grade { GOOD, WEAK, UNUSABLE }

    data class Quality(
        val raw: Int?,
        val dbm: Int?,
        val percent: Int?,
        val grade: Grade,
        val label: String
    ) {
        /** يمكن استخدامها للمكالمات/الرسائل — ليس UNUSABLE */
        val usable: Boolean get() = grade != Grade.UNUSABLE

        /** مفضلة للاختيار — إشارة قوية */
        val preferred: Boolean get() = grade == Grade.GOOD

        /** للتصحيح والسجلات */
        fun toMap(): Map<String, Any?> = mapOf(
            "signalRaw" to raw,
            "signalDbm" to dbm,
            "signal" to percent,
            "signalUsable" to usable,
            "signalGrade" to grade.name,
            "signalLabel" to label
        )
    }

    @Deprecated("استخدم interpret(raw, minGoodDbm) مع عتبة مناسبة")
    const val MIN_USABLE_DBM = DEFAULT_MIN_GOOD_DBM

    /**
     * يفسر القراءة الخام مع عتبة "جيد" قابلة للضبط.
     *
     * @param rawValue القيمة الخام من البوابة (Number أو String)
     * @param minGoodDbm أقل dBm يُعتبر "جيد" (افتراضي -95).
     *        للاستخدام في Load Balancer نمرر -95، وللطوارئ -112.
     */
    fun interpret(rawValue: Any?, minGoodDbm: Int = DEFAULT_MIN_GOOD_DBM): Quality {
        val raw = when (rawValue) {
            is Number -> rawValue.toInt()
            is String -> rawValue.trim().toIntOrNull()
            else -> null
        }
        if (raw == null || raw == UNKNOWN_BASIC || raw == UNKNOWN_EXTENDED) {
            return Quality(raw, null, null, Grade.UNUSABLE, "NO_SIGNAL")
        }
        val dbm = when (raw) {
            in 0..31 -> 2 * raw - 113
            in 100..191 -> raw - 216
            else -> return Quality(raw, null, null, Grade.UNUSABLE, "OUT_OF_RANGE")
        }
        val percent = ((dbm - MIN_DBM).toDouble() / (MAX_DBM - MIN_DBM) * 100).coerceIn(0.0, 100.0).toInt()
        val grade = when {
            dbm >= minGoodDbm -> Grade.GOOD
            dbm >= MIN_VIABLE_DBM -> Grade.WEAK
            else -> Grade.UNUSABLE
        }
        return Quality(
            raw = raw, dbm = dbm, percent = percent, grade = grade,
            label = when {
                dbm >= -65 -> "EXCELLENT"
                dbm >= -80 -> "GOOD"
                dbm >= -95 -> "FAIR"
                dbm >= minGoodDbm -> "ACCEPTABLE"
                dbm >= MIN_VIABLE_DBM -> "WEAK"
                else -> "UNUSABLE"
            }
        )
    }

    /**
     * تفسير مخصص لموزع الأحمال: يعيد `signalPercent` للوزن + `usable` للفلترة.
     * يستخدم عتبة "جيد" -95 dBm للتصنيف، لكن `usable` يسمح بالضعيف (WEAK).
     */
    fun interpretForLoadBalancer(rawValue: Any?): Map<String, Any?> {
        val q = interpret(rawValue, DEFAULT_MIN_GOOD_DBM)
        return q.toMap()
    }
}
