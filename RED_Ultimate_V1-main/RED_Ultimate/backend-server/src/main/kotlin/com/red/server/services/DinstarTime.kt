package com.red.server.services

import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * تحويل أوقات بوابة DINSTAR إلى [Instant] والعكس.
 *
 * ## العطل الذي يعالجه
 *
 * كل ردود الجهاز تحمل الوقت بصيغة `yyyy-MM-dd HH:mm:ss` بتوقيت الجهاز
 * المحلي — وهي **ليست** ISO-8601. الشيفرة كانت تنادي:
 *
 * ```kotlin
 * Instant.parse((m["time"] ?: m["datetime"] ?: m["timestamp"]).toString())
 * ```
 *
 * وهذا يرمي استثناءً **حتمًا** لكل قيمة يُرسلها الجهاز، لأن `Instant.parse`
 * تتطلّب `2025-08-25T21:12:40Z` بحرف `T` ولاحقة المنطقة. كان الاستثناء
 * يُلتقط بـ `getOrNull()` ثم يُستبدل بـ `Instant.now()`.
 *
 * النتيجة: **كل** رسالة واردة تُختَم بزمن لحظة القراءة لا زمن وصولها،
 * وكل تقرير تسليم كذلك. أثر ذلك:
 *
 * - ترتيب المحادثة مبني على وقت ملفَّق: رسالة وصلت أمس تظهر الآن.
 * - نافذة إزالة التكرار «خلال دقيقتين» تقارن أزمنة متطابقة تقريبًا
 *   لكل الرسائل، فتُسقط رسائل مشروعة.
 * - `time_after`/`time_before` في الاستعلامات تُبنى على أوقات خاطئة.
 *
 * ## المنطقة الزمنية
 *
 * الجهاز يكتب توقيته المحلي بلا إشارة إلى المنطقة. النشر في اليمن
 * (‎+03:00، بلا توقيت صيفي)، فتُفسَّر القيم على `Asia/Aden`. لو غُيِّر
 * توقيت الجهاز يُضبَط `red.dinstar.timezone`.
 */
object DinstarTime {

    private val log = LoggerFactory.getLogger(DinstarTime::class.java)

    private val FORMATTER: DateTimeFormatter =
        DateTimeFormatter.ofPattern(DinstarApiContract.TIME_PATTERN)

    /**
     * منطقة الجهاز. اليمن ‎UTC+3 ثابتة بلا توقيت صيفي، فالتحويل مستقر
     * ولا يخضع لغموض الساعة المكرّرة.
     */
    @Volatile
    var deviceZone: ZoneId = ZoneId.of("Asia/Aden")

    /**
     * صيغ بديلة ظهرت في بعض الإصدارات وواجهة الويب:
     * - `2025/08/25 21:12:40` — بشرطة مائلة (صندوق الصادر في الويب).
     * - `2025-08-25T21:12:40` — بحرف T بلا منطقة.
     */
    private val ALTERNATES: List<DateTimeFormatter> = listOf(
        DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    )

    /**
     * تحليل وقت من الجهاز.
     *
     * @return [Instant] عند النجاح، و`null` عند الفشل — **لا** `Instant.now()`.
     *   إعادة الآن تُخفي العطل وتُنتج بيانات كاذبة؛ إعادة `null` تجعل
     *   المستهلك يقرّر صراحةً (يسجّل تحذيرًا، أو يستخدم زمن القراءة
     *   معلومًا أنه تقديري).
     */
    fun parse(value: String?): Instant? {
        val raw = value?.trim()?.takeIf { it.isNotBlank() && it != "null" } ?: return null

        // ISO-8601 كامل إن أرسله إصدار حديث — يُقبل كما هو.
        runCatching { return Instant.parse(raw) }

        runCatching {
            return LocalDateTime.parse(raw, FORMATTER).atZone(deviceZone).toInstant()
        }

        for (alt in ALTERNATES) {
            runCatching {
                return LocalDateTime.parse(raw, alt).atZone(deviceZone).toInstant()
            }
        }

        // طابع زمني عددي (ثوان أو ميلي ثانية) — يظهر في بعض ردود CDR.
        raw.toLongOrNull()?.let { epoch ->
            return when {
                epoch > 1_000_000_000_000L -> Instant.ofEpochMilli(epoch)
                epoch > 1_000_000_000L -> Instant.ofEpochSecond(epoch)
                else -> null
            }
        }

        log.debug("تعذّر تحليل وقت من البوابة: '{}'", raw)
        return null
    }

    /**
     * تحليل مع بديل صريح. يُستخدم حين لا بدّ من زمن، مع تسجيل أن القيمة
     * تقديرية بدل ادّعاء أنها من الجهاز.
     */
    fun parseOr(value: String?, fallback: Instant): Instant = parse(value) ?: fallback

    /**
     * تنسيق للإرسال في `time_after` / `time_before`.
     *
     * البوابة تتوقع توقيتها المحلي، فتمرير سلسلة UTC يُزيح النافذة ثلاث
     * ساعات — أي أن استعلام «آخر ساعة» يعود فارغًا دائمًا.
     */
    fun format(instant: Instant): String =
        LocalDateTime.ofInstant(instant, deviceZone).format(FORMATTER)
}
