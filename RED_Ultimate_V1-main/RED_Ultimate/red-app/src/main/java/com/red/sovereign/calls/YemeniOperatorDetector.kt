package com.red.sovereign.calls

import androidx.compose.ui.graphics.Color
import com.red.sovereign.features.dinstar.YemenOperator

data class OperatorInfo(
    val name: String,
    val brandColor: Color,
    val technology: String,
    /** الاسم كما تُعيده واجهة البوابة (DINSTAR) — للمطابقة مع منطق الخادم. */
    val apiName: String = "",
    /**
     * هل هذا المشغل شبكة محمول يمكن لشريحة في البوابة أن تكون عليها؟
     * `Yemen4G` خدمة بيانات ثابتة لاسلكية، والهاتف الثابت ليس محمولًا،
     * فمطابقة «داخل الشبكة» (on-net) لهما بلا معنى في اختيار المنفذ.
     */
    val isMobile: Boolean = true
)

/**
 * كاشف المشغّل اليمني — يحدّد شبكة الرقم من بادئته لعرضها في الواجهة.
 *
 * ⚠️ تصحيح حرج (2026-08-19):
 *   كانت هذه الأداة تحمل **جدول بادئات ثانيًا** يخالف الجدول المعتمد،
 *   فتعكس 71 و73 وتسمّي 70 «يو (YOU)». النتيجة: التطبيق يعرض «سبأفون»
 *   لأرقام YOU والعكس، بينما الخادم
 *   (`DinstarLoadBalancer.OPERATOR_PREFIXES`) يوجّه المكالمة بالجدول
 *   الصحيح — فيرى المستخدم مشغّلًا غير الذي تمرّ عبره مكالمته فعلًا.
 *
 *   ولأن توجيه بوابة DINSTAR يقوم على مطابقة «داخل الشبكة» — اختيار
 *   شريحة من مشغّل الوجهة نفسه لأن المكالمة داخل الشبكة أرخص
 *   (Least Cost Routing) — فإن خطأ التصنيف يعني عرض مسار وكلفة
 *   مضلّلين.
 *
 *   الإصلاح الجذري: أُلغي الجدول الثاني نهائيًا. البادئات تأتي الآن من
 *   [YemenOperator] وحده — وهو المصدر الوحيد للحقيقة في التطبيق،
 *   والمطابق حرفيًا لجدول الخادم. أي تغيير مستقبلي يجري في مكان واحد،
 *   فلا يعود ممكنًا أن يتفرّع الجدولان مرة أخرى.
 *
 * الخريطة المعتمدة (الخطة الوطنية للترقيم — وزارة الاتصالات وتقنية المعلومات):
 *
 * | البادئة | المشغّل                          | التقنية        |
 * |---------|----------------------------------|----------------|
 * | 70      | واي (Y Telecom)                  | GSM / 4G       |
 * | 71      | سبأفون (SabaFon)                 | GSM / 4G       |
 * | 73      | يو (YOU — كانت MTN حتى 2021)     | GSM / 4G       |
 * | 77, 78  | يمن موبايل (Yemen Mobile)        | CDMA2000 / 4G  |
 * | 10      | يمن فورجي (Yemen 4G)             | LTE ثابت       |
 *
 * الهاتف الثابت: رمز محافظة من رقم واحد (1 صنعاء، 2 عدن، 3 الحديدة،
 * 4 تعز وإب، 5 حضرموت، 6 مأرب، 7 صعدة وعمران). لا يتعارض رمز 7 مع
 * المحمول لأن بادئة المحمول رقمان (7X) ويُطابَق قبله.
 *
 * أمثلة:
 *  - "+967711234567" → "سبأفون"
 *  - "731234567"     → "يو"
 *  - "00967771234567"→ "يمن موبايل"
 *  - "1234567"       → "هاتف ثابت (صنعاء)"
 */
object YemeniOperatorDetector {

    /**
     * التقنية لكل مشغّل — بيان عرض لا يخصّ التوجيه، فيبقى هنا ولا
     * يُثقل [YemenOperator] المشترك مع منطق اختيار المنفذ.
     */
    private val TECHNOLOGY: Map<YemenOperator, String> = mapOf(
        YemenOperator.Y_TELECOM to "GSM/4G",
        YemenOperator.SABAFON to "GSM/4G",
        YemenOperator.YOU to "GSM/4G",
        YemenOperator.YEMEN_MOBILE to "CDMA2000/4G"
    )

    /** رموز محافظات الهاتف الثابت — رقم واحد. */
    private val LANDLINE_AREAS: Map<Char, String> = mapOf(
        '1' to "صنعاء",
        '2' to "عدن",
        '3' to "الحديدة",
        '4' to "تعز وإب",
        '5' to "حضرموت",
        '6' to "مأرب",
        '7' to "صعدة وعمران"
    )

    private val LANDLINE_COLOR = Color(0xFF607D8B)
    private val YEMEN_4G_COLOR = Color(0xFF009688)

    /** أقل طول وطني يُقبل للهاتف الثابت — دونه لا يُصنَّف تخمينًا. */
    private const val MIN_LANDLINE_LENGTH = 6

    /**
     * تطبيع الرقم إلى صيغته الوطنية: تُزال الرموز والمسافات، ثم البادئة
     * الدولية، ثم صفر الاتصال المحلي.
     *
     * ترتيب `00967` قبل `967` مقصود، وإلا اقتُطع جزء منها فقط.
     */
    private fun normalize(number: String): String {
        val digits = number.filter { it.isDigit() }
        return when {
            digits.startsWith("00967") -> digits.removePrefix("00967")
            digits.startsWith("967") -> digits.removePrefix("967")
            digits.startsWith("0") -> digits.removePrefix("0")
            else -> digits
        }
    }

    fun getOperatorInfo(number: String): OperatorInfo? {
        if (number.isBlank()) return null
        val local = normalize(number)
        if (local.isEmpty()) return null

        // ─── المحمول: البادئة من المصدر الموحّد، الأطول أولًا ───
        // fromNumber يفحص ثلاثة أرقام قبل رقمين، فيلتقط 722 (سبأفون عدن 4G)
        // قبل أن يُقرأ 72 ويسقط الرقم إلى فرع الهاتف الثابت خطأً.
        if (local.length >= 2) {
            val operator = YemenOperator.fromNumber(local)
            if (operator != YemenOperator.UNKNOWN) {
                return OperatorInfo(
                    name = operator.arabicName,
                    brandColor = operator.color,
                    technology = TECHNOLOGY[operator] ?: "GSM",
                    apiName = operator.englishName,
                    isMobile = true
                )
            }
            // يمن فورجي: بيانات ثابتة لاسلكية، ليست في enum التوجيه
            // لأن البوابة لا تحمل شريحة عليها ولا تصلح لمطابقة on-net.
            if (local.startsWith("10")) {
                return OperatorInfo("يمن فورجي", YEMEN_4G_COLOR, "LTE", "Yemen4G", isMobile = false)
            }
        }

        // ─── الهاتف الثابت: رمز محافظة من رقم واحد ───
        if (local.length >= MIN_LANDLINE_LENGTH) {
            LANDLINE_AREAS[local[0]]?.let {
                return OperatorInfo("هاتف ثابت ($it)", LANDLINE_COLOR, "PSTN", "Landline", isMobile = false)
            }
        }

        return null
    }
}
