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

    // ═══════════════════════════════════════════════════════════════
    // IMSI → المشغّل (MCC/MNC)
    // ═══════════════════════════════════════════════════════════════

    /** رمز الدولة المتنقلة لليمن (ITU E.212). */
    const val YEMEN_MCC = "421"

    /**
     * رمز الشبكة (MNC) → المشغّل.
     *
     * ## لماذا هذا هنا لا في [com.red.server.services.DinstarHardwareService]
     *
     * كان الجدول مكتوبًا داخل `resolveOperatorName` كـ`when` مباشر، وهو
     * **المسار الوحيد** لتحديد المشغّل على واجهة HTTP API: `get_port_info`
     * **لا يُصدر حقل `operator` إطلاقًا** (مُثبت ميدانيًا — طلبه بأي اسم
     * `operator/carrier/oper/plmn/network` يردّ `error_code=400`). واجهة
     * الويب `WebGetPortInfoAll` وحدها تُصدره، وأحيانًا كرقم خام `"42103"`.
     *
     * فأي MNC غائب عن الجدول يعني «مشغّل غير معروف» فتفقد المكالمة مطابقة
     * «داخل الشبكة» وتُوجَّه عبر شريحة مشغّل آخر بتعرفة أعلى — بصمت.
     *
     * | MNC | المشغّل | التقنية |
     * |-----|---------|---------|
     * | 01  | سبأفون Sabafon | GSM + LTE |
     * | 02  | يو YOU (MTN حتى 2021) | GSM + LTE |
     * | 03  | يمن موبايل Yemen Mobile | CDMA2000 + LTE |
     * | 04  | واي Y Telecom (HiTel سابقًا) | GSM |
     */
    val OPERATORS_BY_MNC: Map<String, OperatorInfo> = mapOf(
        "01" to SABAFON,
        "02" to OperatorInfo("YOU", "يو", isMobile = true),
        "03" to YEMEN_MOBILE,
        "04" to OperatorInfo("YTelecom", "واي", isMobile = true)
    )

    /**
     * تصنيف شريحة من IMSI.
     *
     * @return المشغّل، أو `null` إن لم يكن IMSI يمنيًا صالحًا، أو
     *   [unmappedYemeniMnc] لرمز يمني غير مُخطَّط له.
     *
     * MNC غير معروف **لا يُطمَس إلى «UNKNOWN»**: يُعاد بمُعرِّف يحمل رقمه
     * (`YE-MNC-11`) كي يظهر في اللوحة والسجل فيُلاحَظ ويُصنَّف، بدل أن
     * يختفي بين كل مجهول آخر. رُصد فعليًا `42111` على شرائح LTE في هذا
     * النشر، وهو غائب عن قائمة MNC الكلاسيكية لليمن.
     */
    fun classifyImsi(imsi: String?): OperatorInfo? {
        val digits = imsi?.filter { it.isDigit() } ?: return null
        if (digits.length < 5 || !digits.startsWith(YEMEN_MCC)) return null
        val mnc = digits.substring(3, 5)
        return OPERATORS_BY_MNC[mnc] ?: unmappedYemeniMnc(mnc)
    }

    /**
     * مشغّل يمني برمز شبكة غير مُخطَّط له.
     *
     * `isMobile=true` عن قصد: الشريحة **مسجَّلة فعلًا** على شبكة محمول
     * (وإلا لم يكن لها IMSI مقروء)، فاستبعادها من الترشيح يُهدر منفذًا
     * صالحًا. ما يُفقَد هو مطابقة «داخل الشبكة» فقط، وهي تحسين تكلفة لا
     * شرط اتصال.
     */
    fun unmappedYemeniMnc(mnc: String): OperatorInfo =
        OperatorInfo("YE-MNC-$mnc", "مشغّل يمني ($mnc)", isMobile = true)
}
