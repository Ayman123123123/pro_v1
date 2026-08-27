package com.red.server.pstn

/**
 * خطة الترقيم المحمول اليمنية — **المصدر الوحيد للحقيقة** في الباكند.
 *
 * كانت ثلاث نسخ متضاربة (DinstarLoadBalancer / PstnCallService /
 * PstnBridgeController) تفتقد بادئات واي 700-709 وحافة 789، فكان
 * التصنيف يعتمد على ثغرة `length>=9` بدل المطابقة الصحيحة.
 *
 * | البادئة | المشغل | الملاحظة |
 * |---------|--------|----------|
 * | 700-709 | واي Y Telecom | كانت مفقودة كلياً |
 * | 710-719 | سبأفون Sabafon | |
 * | 722     | سبأفون عدن (VoLTE/4G) | نطاق مستقل لا امتداد لـ71 |
 * | 730-739 | يو YOU (MTN سابقاً حتى 2021) | |
 * | 770-779 | يمن موبايل Yemen Mobile | |
 * | 780-789 | يمن موبايل Yemen Mobile | 789 كانت مفقودة |
 * | 10x     | يمن 4G (بيانات ثابتة) | ليس GSM محمول — تصنيف عرض فقط |
 *
 * ## لماذا تُطابَق البادئات الثلاثية أولاً
 *
 * `722` نطاق سبأفون عدن للجيل الرابع، أُطلق مستقلاً لا امتداداً لـ`71`.
 * المطابقة بخانتين وحدها تقرأه `72` — وهي غير مُخصَّصة — فيسقط الرقم في
 * «غير معروف» وتُرفض المكالمة. تطبيق أندرويد يعرفه صحيحاً
 * (`YemenOperator.SABAFON.prefixes = ["71", "722"]`) فكان الخادم يرفض ما
 * يقبله التطبيق: تعارض عقد بين الطرفين. لذلك يُجرَّب الثلاثي ثم الثنائي.
 */
object YemenNumberPlan {

    data class OperatorInfo(
        val apiName: String,
        val arabicName: String,
        /** شبكة محمول يمكن لشريحة البوابة العمل عليها؟ يمن4G خدمة بيانات لا. */
        val isMobile: Boolean
    )

    private val SABAFON = OperatorInfo("Sabafon", "سبأفون", isMobile = true)
    private val YEMEN_MOBILE = OperatorInfo("YemenMobile", "يمن موبايل", isMobile = true)

    /** بادئة رقمين → المشغل. */
    val OPERATORS: Map<String, OperatorInfo> = mapOf(
        "70" to OperatorInfo("YTelecom", "واي", isMobile = true),
        "71" to SABAFON,
        "73" to OperatorInfo("YOU", "يو", isMobile = true),
        "77" to YEMEN_MOBILE,
        "78" to YEMEN_MOBILE,
        "10" to OperatorInfo("Yemen4G", "يمن فورجي", isMobile = false)
    )

    /**
     * بادئات ثلاثية مستقلة لا تُشتقّ من الثنائية — تُطابَق قبلها.
     * `722` سبأفون عدن (VoLTE): بخانتين يُقرأ `72` غير المخصَّصة فيُرفض.
     */
    val OPERATORS_3: Map<String, OperatorInfo> = mapOf(
        "722" to SABAFON
    )

    /** كل بادئات المحمول الثلاثية الصالحة (700..789 حسب الجدول أعلاه). */
    val MOBILE_PREFIXES_3: Set<String> = buildSet {
        (700..709).forEach { add(it.toString()) }   // واي
        (710..719).forEach { add(it.toString()) }   // سبأفون
        addAll(OPERATORS_3.keys)                    // سبأفون عدن 722
        (730..739).forEach { add(it.toString()) }   // يو
        (770..779).forEach { add(it.toString()) }   // يمن موبايل
        (780..789).forEach { add(it.toString()) }   // يمن موبايل
    }

    /**
     * تطبيع رقم يمني إلى الشكل المحلي القياسي (بدون +967/00967/صفر).
     * يزيل الفواصل والمسافات؛ يُرجع null إن لم يبقَ ما يكفي.
     */
    fun normalizeLocal(raw: String): String? {
        val digits = raw.filter { it.isDigit() || it == '+' }
        val local = when {
            digits.startsWith("+967") -> digits.removePrefix("+967")
            digits.startsWith("00967") -> digits.removePrefix("00967")
            digits.startsWith("967") && digits.length > 3 -> digits.removePrefix("967")
            digits.startsWith("0") && digits.length > 1 -> digits.removePrefix("0")
            else -> digits
        }
        return local.takeIf { it.length in 6..12 }
    }

    /**
     * تصنيف رقم (أي صيغة) إلى مشغله، أو null لغير اليمني/غير صالح.
     * الثلاثي أولاً: `722` سبأفون عدن قبل أن يُقرأ `72` غير المخصَّصة.
     */
    fun classify(raw: String): OperatorInfo? {
        val local = normalizeLocal(raw) ?: return null
        if (local.length >= 3) {
            OPERATORS_3[local.substring(0, 3)]?.let { return it }
        }
        if (local.length < 2) return null
        return OPERATORS[local.substring(0, 2)]
    }

    /** هل الرقم محمول يمني صالح (بادئة ثلاثية معروفة أو طول كامل)؟ */
    fun isDialableMobile(local: String): Boolean =
        local.substring(0, minOf(3, local.length)) in MOBILE_PREFIXES_3 || local.length >= 9
}
