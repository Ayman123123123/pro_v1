package com.red.sovereign.calls

/**
 * نماذج مكالمة البوابة (PSTN) — المصدر الوحيد لحالتها ومقاييسها.
 *
 * استُخرجت من `PstnCallScreen.kt` قبل أرشفته: كان ذلك الملف يحمل
 * شاشةً ميتة لا يستدعيها أحد، لكنه يحمل معها `PstnCallStatus` التي
 * يعتمد عليها 30 موضعًا في التطبيق — من `CallRuntime` إلى الشاشة
 * الحيّة `Material3ExpressivePstnCallScreen`. حذف الملف دون فصل
 * النماذج كان سيُسقط سلسلة PSTN كلها.
 */

/**
 * مراحل مكالمة PSTN كما تصل من الخادم عبر إشارة `PSTN_PROGRESS`.
 *
 * كل مرحلة هنا **تُشتقّ من حدث Asterisk فعلي** يلتقطه
 * `DinstarEventListener` ويوجّهه `PstnCallProgressTracker` إلى صاحب
 * المكالمة وحده:
 *
 * | المرحلة | حدث AMI المُنتِج |
 * |---|---|
 * | [INVITING] | `OriginateResponseEvent` (يربط القناة بالمكالمة) |
 * | [RINGING] | `NewStateEvent` بحالة `Ringing` |
 * | [BRIDGING] | `BridgeEvent` |
 * | [ACTIVE] | `NewStateEvent` بحالة `Up` |
 * | [ENDED] | `HangupEvent` |
 *
 * [REGISTERING] و[ERROR] لا يبثّهما الخادم حالياً: الأولى تخصّ تسجيل
 * البوابة على Asterisk وهو إجراء بدء تشغيل لا يخصّ مكالمة بعينها،
 * والثانية تُعالَج كخطأ REST متزامن من `dialPstn`. تُركتا في التعداد
 * لأن الشاشات تعرضهما بشكل صحيح إن وُجدتا مستقبلاً، ولا يعتمد أي كود
 * على ترتيب العناصر.
 */
enum class PstnCallStatus {
    IDLE,
    REGISTERING,
    BRIDGING,
    INVITING,
    RINGING,
    ACTIVE,
    ENDED,
    ERROR
}

/**
 * مقاييس جودة المكالمة الحيّة والعدّاد اليومي.
 *
 * `jitterMs` و`roundTripMs` بالمللي ثانية، و`packetLossPercent` نسبة
 * مئوية. تُستخدم لتصنيف الجودة المعروض للمستخدم.
 */
data class CallMetrics(
    val jitterMs: Float = 0f,
    val packetLossPercent: Float = 0f,
    val roundTripMs: Float = 0f,
    val errors: List<String> = emptyList(),
    val dailyLimit: Int = 1000,
    val usedToday: Int = 0
)

/** تنسيق مدّة المكالمة بصيغة دقائق:ثوانٍ. */
internal fun formatPstnDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
