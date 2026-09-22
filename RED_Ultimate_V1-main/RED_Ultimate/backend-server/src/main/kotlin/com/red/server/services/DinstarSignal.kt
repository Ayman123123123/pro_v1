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
    // عتبات LTE RSRP — تختلف عن GSM RSSI: RSRP -105 صالح، -115 يعمل مع SINR جيد
    const val LTE_MIN_VIABLE_DBM = -120
    const val LTE_MIN_GOOD_DBM = -105
    // عتبات CDMA2000 RSSI/ECIO
    const val CDMA_MIN_VIABLE_DBM = -105
    const val CDMA_MIN_GOOD_DBM = -90
    private const val MIN_DBM = -113
    private const val MAX_DBM = -51

    enum class Grade { GOOD, WEAK, UNUSABLE }

    /** تقنية الراديو — تحدد التفسير والعتبات. */
    enum class Rat { GSM, LTE, CDMA, WCDMA, UNKNOWN }

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

    /**
     * تحديد تقنية الراديو من حقل `type` الخام للبوابة.
     * يمن موبايل: CDMA2000/EVDO/1X (BC0 800) + LTE — ليست GSM.
     */
    fun detectRat(typeRaw: Any?): Rat {
        val t = typeRaw?.toString()?.uppercase().orEmpty()
        // الترتيب مقصود: WCDMA قبل CDMA لأن "WCDMA" تحوي "CDMA" كنص فرعي
        return when {
            "LTE" in t || "4G" in t || "E-UTRAN" in t || "FDD" in t || "TDD" in t -> Rat.LTE
            "WCDMA" in t || "UMTS" in t || "HSPA" in t -> Rat.WCDMA
            "CDMA" in t || "EVDO" in t || "EV-DO" in t || "1X" in t || "EHRPD" in t || "BC0" in t -> Rat.CDMA
            "GSM" in t || "GPRS" in t || "EDGE" in t || "GERAN" in t -> Rat.GSM
            else -> Rat.UNKNOWN
        }
    }

    /**
     * تفسير CESQ / LTE (RSRP 0..97 → -140..-44dBm) و CDMA (RSSI/ECIO).
     * القيم الممددة 100..191 هي RSCP لـ TD-SCDMA وتُعالج في [interpret].
     *
     * @param rsrp RSRP خام 0..97 أو dBm مباشر -140..-44
     * @param rsrq RSRQ dB (اختياري للتشخيص)
     * @param sinr SINR dB (اختياري — يحسّن التصنيف)
     */
    fun interpretLte(rsrp: Any?, rsrq: Any? = null, sinr: Any? = null): Quality {
        val rawInt = when (rsrp) {
            is Number -> rsrp.toInt()
            is String -> rsrp.trim().toIntOrNull()
            else -> null
        } ?: return Quality(null, null, null, Grade.UNUSABLE, "NO_SIGNAL")
        // RSRP خام 0..97 → dBm، أو dBm سالب مباشر
        val dbm = when {
            rawInt in 0..97 -> rawInt - 140
            rawInt in -140..-44 -> rawInt
            rawInt == 99 || rawInt == 199 || rawInt == 255 -> return Quality(rawInt, null, null, Grade.UNUSABLE, "NO_SIGNAL")
            else -> return Quality(rawInt, null, null, Grade.UNUSABLE, "OUT_OF_RANGE")
        }
        val sinrDb = when (sinr) {
            is Number -> sinr.toDouble()
            is String -> sinr.trim().toDoubleOrNull()
            else -> null
        }
        // SINR ممتاز يعوّض RSRP حدّي (خلايا ريفية يمن موبايل)
        val effectiveGood = if (sinrDb != null && sinrDb >= 10.0) LTE_MIN_GOOD_DBM - 5 else LTE_MIN_GOOD_DBM
        val percent = ((dbm + 140).toDouble() / 96.0 * 100).coerceIn(0.0, 100.0).toInt()
        val grade = when {
            dbm >= effectiveGood -> Grade.GOOD
            dbm >= LTE_MIN_VIABLE_DBM -> Grade.WEAK
            else -> Grade.UNUSABLE
        }
        return Quality(rawInt, dbm, percent, grade, "LTE_RSRP")
    }

    /**
     * تفسير CDMA2000 RSSI/ECIO لمنافذ يمن موبايل غير-LTE.
     */
    fun interpretCdma(rssiDbm: Any?, ecioDb: Any? = null): Quality {
        val dbm = when (rssiDbm) {
            is Number -> rssiDbm.toInt()
            is String -> rssiDbm.trim().toIntOrNull()
            else -> null
        } ?: return Quality(null, null, null, Grade.UNUSABLE, "NO_SIGNAL")
        if (dbm == 99 || dbm == 199) return Quality(dbm, null, null, Grade.UNUSABLE, "NO_SIGNAL")
        if (dbm !in -120..-20) return Quality(dbm, null, null, Grade.UNUSABLE, "OUT_OF_RANGE")
        val percent = ((dbm + 120).toDouble() / 100.0 * 100).coerceIn(0.0, 100.0).toInt()
        val grade = when {
            dbm >= CDMA_MIN_GOOD_DBM -> Grade.GOOD
            dbm >= CDMA_MIN_VIABLE_DBM -> Grade.WEAK
            else -> Grade.UNUSABLE
        }
        return Quality(dbm, dbm, percent, grade, "CDMA_RSSI")
    }

    /**
     * تفسير موحد يختار المسار حسب RAT: LTE → [interpretLte]، CDMA → [interpretCdma]، غيرها → [interpret].
     * تُمرَّر حقول `rsrp/rsrq/sinr/rssi/ecio` عندما تصدرها البوابة؛ وإلا CSQ الكلاسيكي.
     */
    fun interpretRatAware(port: Map<String, Any?>, minGoodDbm: Int = DEFAULT_MIN_GOOD_DBM): Quality {
        val rat = detectRat(port["type"])
        // LTE: جرّب RSRP أولاً
        if (rat == Rat.LTE) {
            val rsrp = port["rsrp"] ?: port["RSRP"] ?: port["lte_rsrp"] ?: port["lteRsrp"]
            if (rsrp != null) return interpretLte(rsrp, port["rsrq"] ?: port["RSRQ"], port["sinr"] ?: port["SINR"])
        }
        if (rat == Rat.CDMA) {
            val rssi = port["rssi"] ?: port["RSSI"] ?: port["signal"]
            val ecio = port["ecio"] ?: port["ECIO"] ?: port["ecIo"]
            // CSQ الكلاسيكي 0..31 لا يصلح لـ CDMA — لكن إن كان rssi dBm سالباً فسّره مباشرة
            val rssiInt = (rssi as? Number)?.toInt() ?: (rssi?.toString()?.trim()?.toIntOrNull())
            if (rssiInt != null && rssiInt < 0) return interpretCdma(rssiInt, ecio)
        }
        return interpret(port["signal"], minGoodDbm)
    }
}
