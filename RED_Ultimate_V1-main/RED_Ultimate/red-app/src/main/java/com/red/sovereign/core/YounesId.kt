package com.red.sovereign.core

/**
 * **معرّف يونس** — مصدر الحقيقة الوحيد في التطبيق.
 *
 * الصيغة: خمسة أرقام `10000`..`99999`، بلا بادئة ولا شرطة.
 * مطابقة تمامًا لـ `RedIdGenerator.PATTERN` على الخادم.
 *
 * ## لماذا ملف واحد
 *
 * كان النمط مكرّرًا في أربعة ملفات على الأقل (`QrScannerSheet`،
 * `RedDashboard`، `SafetyViewModel`، وخادم التطوير) بصياغات متباينة.
 * أحدها كان يقبل البادئة `YNS` فقط والآخر يقبل `RED` أيضًا، فكان
 * المعرّف يمر من الماسح ثم تظهر شاشة المحادثة بأزرار معطّلة بلا سبب
 * ظاهر. أي تحقق جديد يجب أن يستدعي [isValid] لا أن يكتب نمطًا محليًا.
 *
 * ## المعرّف ليس سرًّا
 *
 * خمسة أرقام = 90,000 احتمالًا فقط، أي قابلة للتعداد الكامل في وقت
 * قصير. المعرّف معرِّف عرض كرقم الهاتف: لا يُستخدم للمصادقة ولا يمنح
 * وصولًا. الحماية بتحديد المعدل على الخادم وبإلزام الرمز والاعتماد.
 */
object YounesId {

    /** أصغر معرّف. */
    /** معرّف النظام — محجوز، يطابق RedIdGenerator.SYSTEM_ID. */
    const val SYSTEM_ID = "10000"

    const val MIN = 10_001

    /** أكبر معرّف. */
    const val MAX = 99_999

    /** عدد أرقام المعرّف. */
    const val LENGTH = 5

    /** النمط المعياري — يطابق `RedIdGenerator.PATTERN` على الخادم. */
    const val PATTERN = "^[1-9][0-9]{4}$"

    private val REGEX = Regex(PATTERN)

    /** هل النص معرّف يونس صالح؟ */
    fun isValid(value: String?): Boolean = value != null && REGEX.matches(value)

    /**
     * تطبيع ما يكتبه أو يلصقه المستخدم.
     *
     * يزيل المسافات والشرطات والبادئات القديمة (`YNS-` / `RED-`) التي
     * قد ترد في رمز QR قديم أو جهة اتصال محفوظة، ويُبقي الأرقام فقط
     * محدودةً بطول المعرّف حتى لا يبتلع الحقل إدخالًا أطول.
     */
    fun normalizeInput(input: String): String =
        input.uppercase()
            .removePrefix("YNS-").removePrefix("RED-")
            .removePrefix("YNS").removePrefix("RED")
            .filter { it.isDigit() }
            .take(LENGTH)

    /**
     * تحويل مدخل حر إلى معرّف صالح، أو `null`.
     *
     * `null` مقصودة: تخمين معرّف من إدخال ناقص يعني فتح محادثة مع
     * شخص آخر تمامًا.
     */
    fun parseOrNull(input: String?): String? {
        if (input.isNullOrBlank()) return null
        return normalizeInput(input).takeIf { REGEX.matches(it) }
    }

    /**
     * نمط الإشارة داخل نص رسالة — `@12345`.
     *
     * بلا مرساتَي البداية والنهاية لأنه يُبحث عنه وسط النص، لكن مع
     * `(?![0-9])` كي لا يلتقط أول خمسة أرقام من رقم أطول: `@123456`
     * ليست إشارة إلى `12345`.
     */
    const val MENTION_PATTERN = "@[1-9][0-9]{4}(?![0-9])"

    /** نص إرشادي موحّد لحقول الإدخال. */
    const val PLACEHOLDER = "12345"

    /** رسالة الخطأ الموحّدة. */
    const val ERROR_MESSAGE = "معرّف يونس يتكوّن من خمسة أرقام (10000–99999)"
}
