package com.red.server.services

import kotlin.math.ceil

/**
 * أبجدية GSM 03.38 وحساب الترميز والأجزاء — **المصدر الوحيد**.
 *
 * ## لماذا وُحِّد
 *
 * كانت هناك نسختان مستقلتان من هذا المنطق:
 *
 * 1. `DinstarHardwareService.detectEncoding` — تفحص العضوية في مجموعة
 *    أبجدية كاملة تشمل جدول الهروب.
 * 2. `SmsService.detectEncoding` — مجموعة مختلفة، وتضيف فحصًا صريحًا
 *    لنطاق العربية، ومجموعة هروب ناقصة تحتوي `€` مكرّرًا و`|` فقط.
 *
 * النسختان تختلفان في نتائجهما لنصوص حقيقية. المهمّ أن `SmsService` هي
 * التي تحسب `smsParts` المخزَّن وتُمرّر الترميز، بينما
 * `DinstarHardwareService` هي التي تُقرّر ما يُرسَل على السلك فعلًا —
 * فيُخزَّن عدد أجزاء لا يطابق ما أُرسل، وتُحاسَب حصة المستخدم على رقم
 * خاطئ.
 *
 * ## المرجع
 *
 * 3GPP TS 23.038 §6.2.1 (Basic + Extension table)، و§6.1.2.1.1 لحساب
 * أطوال الأجزاء المتسلسلة (UDH يستهلك 7 أحرف من الجزء).
 */
object GsmAlphabet {

    /** أبجدية GSM 03.38 الأساسية — كل حرف في 7 بتات. */
    private val BASIC: Set<Char> = buildSet {
        addAll("@£\$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./".toList())
        addAll("0123456789:;<=>?".toList())
        addAll("¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§".toList())
        addAll("¿abcdefghijklmnopqrstuvwxyzäöñüà".toList())
    }

    /**
     * جدول الهروب. هذه الحروف تبقى ضمن `gsm-7bit` لكنها تُرسَل ببايتين،
     * فتُحسَب حرفين في طول الجزء. تجاهل ذلك — كما كان — يُنتج عدد أجزاء
     * أقل من الحقيقي فتُقطَع الرسالة عند الإرسال.
     */
    private val EXTENSION: Set<Char> = "\u000C^{}\\[~]|€".toSet()

    /** الأبجدية الكاملة: أساسية + هروب. */
    val FULL: Set<Char> = BASIC + EXTENSION

    /** سعة الجزء الواحد في GSM-7bit المفرد. */
    private const val GSM7_SINGLE = 160

    /** سعة الجزء في رسالة GSM-7bit متسلسلة (UDH يأخذ 7 أحرف). */
    private const val GSM7_CONCAT = 153

    /** سعة الجزء الواحد في UCS2 المفرد. */
    private const val UCS2_SINGLE = 70

    /** سعة الجزء في رسالة UCS2 متسلسلة (UDH يأخذ 3 أحرف). */
    private const val UCS2_CONCAT = 67

    const val GSM7BIT = "GSM7BIT"
    const val UCS2 = "UCS2"

    /**
     * هل النص كله قابل للتمثيل في GSM 03.38؟
     *
     * لا يُفحَص نطاق العربية صراحةً كما كان: العربية خارج الأبجدية أصلًا
     * فيكفي فحص العضوية. الفحص الصريح كان زائدًا ومضلّلًا — يوحي بأن
     * لغات أخرى خارج الأبجدية (الفارسية، العبرية، الروسية) قد تمرّ.
     */
    fun isGsm7Compatible(text: String): Boolean = text.all { it in FULL }

    /**
     * اختيار الترميز من محتوى النص.
     *
     * الاشتقاق لا الفرض: تثبيت `UCS2` دائمًا يحلّ مشكلة العربية ويخلق
     * أخرى — رموز OTP الإنجليزية تفقد أكثر من نصف سعتها فتنقسم وتتضاعف
     * كلفتها. وتثبيت `GSM7BIT` — وهو ما كان — يجعل كل رسالة عربية تصل
     * «?????».
     */
    fun detectEncoding(text: String): String = if (isGsm7Compatible(text)) GSM7BIT else UCS2

    /**
     * الطول المحسوب في وحدات الترميز.
     *
     * في GSM-7bit يُحسَب حرف الهروب **حرفين**. الحساب القديم كان
     * `text.count { it !in GSM_7BIT_EXT || it in GSM_7BIT }` وهو عكس
     * المقصود: يُسقط حروف الهروب من العدّ بدل مضاعفتها.
     */
    fun unitLength(text: String, encoding: String): Int = when (encoding) {
        GSM7BIT -> text.sumOf { if (it in EXTENSION) 2 else 1 }
        // UCS2 يُحسَب بوحدات UTF-16: الرمز التعبيري خارج BMP يأخذ وحدتين.
        else -> text.length
    }

    /**
     * عدد الأجزاء التي ستُرسَل فعلًا.
     *
     * الحساب القديم كان `1 + ceil((len - 160) / 153)` وهو يخطئ عند
     * الحدود: نص 161 حرفًا يُحسَب جزءين (صحيح)، لكن نص 313 حرفًا يُحسَب
     * 2 بينما الحقيقة 3، لأن السعة المتسلسلة تنطبق على **كل** الأجزاء
     * لا على ما بعد الأول فقط.
     */
    fun countParts(text: String, encoding: String): Int {
        val length = unitLength(text, encoding)
        if (length == 0) return 1
        val single = if (encoding == GSM7BIT) GSM7_SINGLE else UCS2_SINGLE
        if (length <= single) return 1
        val concat = if (encoding == GSM7BIT) GSM7_CONCAT else UCS2_CONCAT
        return ceil(length.toDouble() / concat).toInt()
    }

    /** ترجمة الترميز الداخلي إلى القيمة التي تفهمها البوابة. */
    fun wireValue(encoding: String): String =
        if (encoding == GSM7BIT) DinstarApiContract.Sms.WIRE_GSM7BIT
        else DinstarApiContract.Sms.WIRE_UNICODE

    /** تطبيع أي صيغة مُدخلة إلى الصيغة الداخلية. */
    fun normalize(encoding: String?): String? = when (encoding?.trim()?.uppercase()) {
        null, "" -> null
        "AUTO" -> "AUTO"
        "GSM7BIT", "GSM-7BIT", "GSM7" -> GSM7BIT
        "UCS2", "UNICODE", "UTF16" -> UCS2
        else -> throw IllegalArgumentException(
            "Encoding must be AUTO, GSM7BIT, UCS2, gsm-7bit or unicode"
        )
    }
}
