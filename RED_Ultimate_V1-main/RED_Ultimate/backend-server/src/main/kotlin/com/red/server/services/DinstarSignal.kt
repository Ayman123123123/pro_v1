package com.red.server.services

/**
 * تحويل قراءة الإشارة الخام من بوابات DINSTAR UC2000 إلى قيمة ذات معنى.
 *
 * البوابة تُعيد في `get_port_info` حقل `signal` كما تُرجعه وحدة الراديو
 * عبر الأمر `AT+CSQ`، وهو مُعرَّف في **3GPP TS 27.007 §8.5**:
 *
 * | القيمة الخام | المعنى                                    |
 * |--------------|-------------------------------------------|
 * | 0            | ‎-113 dBm أو أقل                          |
 * | 1            | ‎-111 dBm                                 |
 * | 2..30        | ‎-109 dBm .. ‎-53 dBm (خطوة 2 dBm)        |
 * | 31           | ‎-51 dBm أو أعلى                          |
 * | **99**       | **غير معروفة أو غير قابلة للكشف**        |
 * | 100..191     | RSCP ممتد لـ TD-SCDMA (‎-116 .. ‎-25 dBm) |
 * | 199          | غير معروفة (النطاق الممتد)                |
 *
 * **العطل الذي يعالجه هذا الملف:** الشيفرة السابقة كانت تحسب
 * `signal.coerceIn(0, 31) / 31.0 * 100`. القيمة 99 تعني «لا توجد شبكة»،
 * لكن `coerceIn` كان يحوّلها إلى 31 أي **100%**. النتيجة أن شريحة بلا
 * تغطية إطلاقًا تظهر في اللوحة بإشارة كاملة، والأسوأ أن موزّع الأحمال
 * `DinstarLoadBalancer` يرتّب المنافذ بالإشارة تنازليًا فيختار هذه
 * الشريحة الميتة أولًا لكل مكالمة صادرة.
 *
 * لذلك تُعاد هنا `null` صراحةً عند تعذّر القياس بدل تلفيق رقم.
 */
object DinstarSignal {

    /** القيمة الخام التي تعني «غير قابلة للكشف» في النطاق الأساسي. */
    const val UNKNOWN_BASIC = 99

    /** القيمة الخام التي تعني «غير قابلة للكشف» في نطاق RSCP الممتد. */
    const val UNKNOWN_EXTENDED = 199

    /**
     * جودة الإشارة بعد التفسير.
     *
     * @param raw القيمة الخام كما وردت من البوابة (للتشخيص والسجل).
     * @param dbm القوة بالـ dBm، أو `null` إذا تعذّر القياس.
     * @param percent نسبة 0..100 مشتقة من نطاق ‎-113..‎-51 dBm،
     *                أو `null` إذا تعذّر القياس. تُستخدم للعرض فقط.
     * @param usable هل المنفذ صالح لحمل مكالمة؟ يُشترط قياس فعلي
     *               وقوة لا تقل عن [MIN_USABLE_DBM].
     */
    data class Quality(
        val raw: Int?,
        val dbm: Int?,
        val percent: Int?,
        val usable: Boolean,
        val label: String
    ) {
        fun toMap(): Map<String, Any?> = mapOf(
            "signalRaw" to raw,
            "signalDbm" to dbm,
            "signal" to percent,
            "signalUsable" to usable,
            "signalLabel" to label
        )
    }

    /**
     * الحد الأدنى العملي لإجراء مكالمة صوتية مقبولة.
     * ‎-100 dBm ≈ قراءة خام 7. تحتها تكون المكالمة عرضة للانقطاع
     * وتدهور الترميز، فلا يختار الموزّع المنفذ إلا اضطرارًا.
     */
    const val MIN_USABLE_DBM = -100

    private const val MIN_DBM = -113
    private const val MAX_DBM = -51

    /**
     * تفسير القراءة الخام. يقبل `Number` أو نصًا رقميًا لأن البوابة
     * تُرسل الحقل أحيانًا كسلسلة حسب إصدار البرنامج الثابت.
     */
    fun interpret(rawValue: Any?): Quality {
        val raw = when (rawValue) {
            is Number -> rawValue.toInt()
            is String -> rawValue.trim().toIntOrNull()
            else -> null
        }

        // لا قراءة إطلاقًا، أو القيمتان المحجوزتان لـ «غير قابل للكشف»
        if (raw == null || raw == UNKNOWN_BASIC || raw == UNKNOWN_EXTENDED) {
            return Quality(raw, null, null, usable = false, label = "NO_SIGNAL")
        }

        val dbm = when (raw) {
            in 0..31 -> 2 * raw - 113
            // النطاق الممتد لـ TD-SCDMA: 100 ⇒ ‎-116 dBm، 191 ⇒ ‎-25 dBm
            in 100..191 -> raw - 216
            else -> return Quality(raw, null, null, usable = false, label = "OUT_OF_RANGE")
        }

        val percent = ((dbm - MIN_DBM).toDouble() / (MAX_DBM - MIN_DBM) * 100)
            .coerceIn(0.0, 100.0).toInt()

        return Quality(
            raw = raw,
            dbm = dbm,
            percent = percent,
            usable = dbm >= MIN_USABLE_DBM,
            label = when {
                dbm >= -65 -> "EXCELLENT"
                dbm >= -80 -> "GOOD"
                dbm >= -95 -> "FAIR"
                dbm >= MIN_USABLE_DBM -> "WEAK"
                else -> "UNUSABLE"
            }
        )
    }
}
