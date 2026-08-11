package com.red.server.auth

import com.red.server.auth.repository.UserAccountRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.security.SecureRandom

/**
 * مولّد **معرّف يونس** — خمسة أرقام.
 *
 * ## الصيغة
 *
 * `10000` .. `99999` — خمسة أرقام عشرية، بلا بادئة ولا شرطة.
 * لا يبدأ بصفر حتى يبقى الطول خمسة دائمًا ولا يضيع الصفر عند نسخه
 * إلى حقل رقمي أو جدول بيانات.
 *
 * ## سعة الفضاء وحدوده — قيد يجب معرفته لا إخفاؤه
 *
 * الفضاء الكامل **90,000 معرّف فقط**. هذا يعني أمرين لا مفرّ منهما:
 *
 * 1. **الحد الأقصى للمستخدمين 90 ألفًا.** عند الاقتراب من الامتلاء
 *    يبطئ التوليد ثم يفشل. لذلك يراقب [remainingCapacity] المتبقي
 *    ويسجّل تحذيرًا عند تجاوز 80 %.
 *
 * 2. **المعرّف قابل للتعداد الكامل.** مهاجم يستطيع تجربة كل القيم
 *    الـ90 ألف في وقت قصير. لذلك **المعرّف ليس سرًّا ولا يصلح للمصادقة
 *    وحده**: الدليل العام محمي بتحديد معدل صارم في
 *    [PublicDirectoryController] (20 بحثًا/دقيقة لكل مستخدم عبر
 *    [RateLimitService])، والوصول لأي بيانات يتطلب رمزًا صالحًا
 *    وحسابًا معتمدًا. المعرّف معرِّف عرض فقط، تمامًا كرقم الهاتف.
 *
 * الصيغة السابقة `41382` من أبجدية 32 رمزًا كانت تعطي
 * ‎32^8 ≈ 1.1 × 10^12 احتمالًا — أي أن التعداد كان مستحيلًا عمليًا.
 * الانتقال إلى خمسة أرقام قرار قابلية استخدام (أسهل نطقًا وإدخالًا)
 * مقابل خسارة مناعة التعداد، والتعويض بضوابط الخادم لا بالمعرّف نفسه.
 *
 * ## التوليد
 *
 * عشوائي تشفيريًا عبر [SecureRandom] لا تسلسلي: المعرّف التسلسلي يكشف
 * ترتيب التسجيل وحجم القاعدة (المستخدم `10007` هو السابع) وهي بيانات
 * تجارية وأمنية لا داعي لتسريبها.
 */
@Component
class RedIdGenerator(private val users: UserAccountRepository) {

    private val random = SecureRandom()

    /**
     * توليد معرّف فريد غير مستخدم.
     *
     * @throws IllegalStateException إذا تعذّر إيجاد معرّف حر — مؤشر على
     *   اقتراب الفضاء من الامتلاء لا على عطل عابر.
     */
    fun next(): String {
        warnIfNearlyFull()
        repeat(MAX_ATTEMPTS) {
            val candidate = randomId()
            if (!users.existsByRedId(candidate)) return candidate
        }
        // الفشل بعد هذا العدد يعني امتلاءً فعليًا لا سوء حظ:
        // احتمال 200 اصطدام متتالٍ ضئيل ما لم يكن الفضاء شبه ممتلئ.
        error(
            "تعذّر تخصيص معرّف يونس بعد $MAX_ATTEMPTS محاولة — " +
                "فضاء المعرّفات ($TOTAL_SPACE) شارف على الامتلاء"
        )
    }

    /** رقم عشوائي في المدى المسموح. */
    private fun randomId(): String = (MIN_ID + random.nextInt(TOTAL_SPACE)).toString()

    /** عدد المعرّفات المتبقية — لمراقبة الاقتراب من الحد. */
    fun remainingCapacity(): Long = TOTAL_SPACE - users.count()

    /**
     * تحذير عند تجاوز عتبة الامتلاء.
     *
     * الصمت حتى الفشل الكامل خطأ تشغيلي: عند تجاوز 80 % يبدأ التوليد
     * بالتباطؤ (اصطدامات أكثر) وينبغي للمشغّل أن يعرف قبل أن يعجز
     * مستخدم جديد عن التسجيل.
     */
    private fun warnIfNearlyFull() {
        val used = users.count()
        if (used * 100 >= TOTAL_SPACE * WARN_THRESHOLD_PERCENT) {
            log.warn(
                "فضاء معرّفات يونس بلغ {}٪ ({}/{}) — الصيغة الخماسية تسع {} معرّفًا فقط",
                used * 100 / TOTAL_SPACE, used, TOTAL_SPACE, TOTAL_SPACE
            )
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(RedIdGenerator::class.java)

        /** عتبة التحذير من امتلاء الفضاء. */
        private const val WARN_THRESHOLD_PERCENT = 80

        /**
         * معرّف النظام — محجوز ولا يُخصَّص لأي مستخدم.
         *
         * رسائل التحكم الصادرة عن الخادم تحمل هذا المعرّف كمُرسِل.
         * بلا حجزه صراحةً يستطيع المولّد منحه لمستخدم حقيقي، فتصبح
         * رسائل التحكم منسوبة إليه — وهو انتحال كامل لهوية الخادم.
         * الصيغة القديمة لم تكن تحتاج هذا لأن مُرسِل النظام كان
         * `RED-SYST-EM22` وهي سلسلة يستحيل عمليًا أن يولّدها العشوائي
         * من فضاء 10¹²، أما في فضاء 90,000 فالاصطدام حتمي مع النمو.
         */
        const val SYSTEM_ID = "10000"

        /** أصغر معرّف يُخصَّص لمستخدم — 10000 محجوز للنظام. */
        const val MIN_ID = 10_001

        /** أكبر معرّف. */
        const val MAX_ID = 99_999

        /** سعة الفضاء المتاحة للمستخدمين: 89,999. */
        const val TOTAL_SPACE = MAX_ID - MIN_ID + 1

        /** محاولات إيجاد معرّف حر قبل الإقرار بالامتلاء. */
        private const val MAX_ATTEMPTS = 200

        /**
         * النمط المعياري لمعرّف يونس — **مصدر الحقيقة الوحيد**.
         *
         * كل تحقق في المشروع (الخادم، التطبيق، اللوحة، خادم التطوير)
         * يجب أن يطابق هذا النمط بالضبط. تكرار النمط بصياغات مختلفة
         * هو ما سمح سابقًا بقبول `RED-` في مكان ورفضها في آخر.
         */
        const val PATTERN = "^[1-9][0-9]{4}$"

        private val REGEX = Regex(PATTERN)

        /** هل النص معرّف يونس صالح؟ */
        fun isValid(value: String?): Boolean = value != null && REGEX.matches(value)

        /**
         * تطبيع مدخل المستخدم قبل التحقق.
         *
         * يقبل ما قد يلصقه المستخدم فعلًا: مسافات، شرطات، بادئات
         * قديمة. يُرجع `null` إذا لم يبقَ معرّف صالح — لا يُخمّن.
         */
        fun normalize(raw: String?): String? {
            if (raw.isNullOrBlank()) return null
            val digits = raw.trim().uppercase()
                .removePrefix("YNS-").removePrefix("RED-").removePrefix("YNS").removePrefix("RED")
                .filter { it.isDigit() }
            // معرّف النظام ليس مستخدمًا: لا يُبحث عنه ولا يُطابَق.
            return digits.takeIf { REGEX.matches(it) && it != SYSTEM_ID }
        }
    }
}
