package com.red.server.pstn

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * سجلّ الربط بين مكالمة PSTN جارية وصاحبها، ومراحلها الحيّة.
 *
 * ## المشكلة التي يحلّها
 *
 * عند طلب رقم عبر البوابة يُولَّد `actionId` ويُعاد للتطبيق كـ`callId`.
 * لكن أحداث Asterisk اللاحقة (`Ringing`, `Up`, `Hangup`) تصل معرَّفةً
 * بـ**اسم القناة** لا بـ`actionId`. فبدون جسر بينهما لا يمكن معرفة
 * أيّ مستخدم يخصّه الحدث، ولذلك كانت أحداث AMI تُسجَّل في اللوق فقط
 * ولا تصل إلى صاحب المكالمة إطلاقًا.
 *
 * يربط هذا السجلّ الطرفين عبر `OriginateResponseEvent` الذي يحمل
 * `actionId` و`channel` معًا، فيصبح كل حدث لاحق قابلًا للتوجيه.
 *
 * ## لماذا المراحل هنا وليست في الشاشة
 *
 * المرحلة تُشتقّ من حدث فعلي واحد لا أكثر؛ لا تُخترع مراحل وسيطة
 * لتجميل الواجهة. هذا امتداد لقاعدة «لا حالة بلا منتِج» المطبَّقة
 * على آلة حالة المكالمة الفردية.
 */
@Component
class PstnCallProgressTracker {

    companion object {
        private val log = LoggerFactory.getLogger(PstnCallProgressTracker::class.java)

        /**
         * عمر أقصى للقيد قبل تنظيفه.
         *
         * لو ضاع حدث `Hangup` (انقطاع AMI مثلًا) لبقي القيد للأبد
         * وتسرّبت الذاكرة. المهلة أطول من أقصى مكالمة معقولة عبر
         * البوابة ولا تقطع مكالمة قائمة — التنظيف كسول عند كل كتابة.
         */
        private val ENTRY_TTL: Duration = Duration.ofHours(4)
    }

    /** مراحل مكالمة PSTN كما تُشتقّ من أحداث Asterisk حصرًا. */
    enum class Stage {
        /** أُرسل `Originate` وقُبل، ولم تُنشأ القناة بعد. */
        INVITING,

        /** وصل `Ringing` من القناة: الطرف البعيد يرنّ فعلًا. */
        RINGING,

        /** وصل `BridgeEnter`/`Bridge`: التقى مساران صوتيان. */
        BRIDGING,

        /** وصل `Up`: المكالمة مُجابة والصوت يمرّ. */
        ACTIVE,

        /** وصل `Hangup`. */
        ENDED
    }

    data class Entry(
        val callId: String,
        val redId: String,
        val number: String,
        val stage: Stage,
        val channel: String? = null,
        val updatedAt: Instant = Instant.now()
    )

    /** `callId` (actionId) → القيد. */
    private val byCallId = ConcurrentHashMap<String, Entry>()

    /** اسم القناة → `callId`، لتوجيه الأحداث التي لا تحمل إلا القناة. */
    private val channelToCallId = ConcurrentHashMap<String, String>()

    /**
     * يُسجّل مكالمة فور قبول Asterisk لطلب الإخراج.
     * تُستدعى من [PstnCallService.dial].
     */
    fun register(callId: String, redId: String, number: String) {
        purgeExpired()
        byCallId[callId] = Entry(callId, redId, number, Stage.INVITING)
        log.debug("PSTN tracker: registered call {} for {}", callId, redId)
    }

    /**
     * يربط اسم القناة بالمكالمة، من `OriginateResponseEvent`.
     *
     * هذا هو المفصل الذي يجعل كل حدث AMI لاحق قابلًا للتوجيه.
     */
    fun attachChannel(callId: String, channel: String): Entry? {
        val entry = byCallId[callId] ?: return null
        val updated = entry.copy(channel = channel, updatedAt = Instant.now())
        byCallId[callId] = updated
        channelToCallId[channel] = callId
        log.debug("PSTN tracker: channel {} ↔ call {}", channel, callId)
        return updated
    }

    /**
     * ينقل مكالمة إلى مرحلة جديدة إن كان الانتقال تقدّمًا فعليًّا.
     *
     * يُرجع القيد المحدَّث، أو `null` إذا لم تكن القناة معروفة أو كانت
     * المرحلة تراجعًا أو تكرارًا — فلا يُبثّ حدث بلا معنى. الحماية من
     * التراجع ضرورية لأن Asterisk قد يُرسل `Ringing` بعد `Up` على
     * قنوات فرعية، وكان ذلك سيُرجع الواجهة من «نشطة» إلى «يرنّ».
     */
    fun advanceByChannel(channel: String, stage: Stage): Entry? {
        val callId = channelToCallId[channel] ?: return null
        val entry = byCallId[callId] ?: return null
        if (stage.ordinal <= entry.stage.ordinal) return null
        val updated = entry.copy(stage = stage, channel = channel, updatedAt = Instant.now())
        byCallId[callId] = updated
        return updated
    }

    /**
     * ينقل مكالمة إلى مرحلة جديدة بمعرِّفها المباشر `callId`، لا بالقناة.
     *
     * مسار توجيه أحداث AMI الفعلي في `DinstarEventListener` يحلّ `callId`
     * بآلية مثبَّتة (مفتاح Redis channel→callId، ثم تخمين المنفذ كاحتياط)
     * أدقّ من مطابقة اسم القناة الخام: قناة الحالة (`Ringing`/`Up`) قد
     * تختلف عن القناة التي حُفظ عليها المتغيّر `RED_CALL_ID`. لذا يُقاد
     * التقدّم من `callId` المحلول، بنفس حماية عدم التراجع في [advanceByChannel].
     */
    fun advanceByCallId(callId: String, stage: Stage): Entry? {
        val entry = byCallId[callId] ?: return null
        if (stage.ordinal <= entry.stage.ordinal) return null
        val updated = entry.copy(stage = stage, updatedAt = Instant.now())
        byCallId[callId] = updated
        return updated
    }

    /** ينهي التتبّع ويحرّر القيود. يُرجع القيد الأخير قبل الإزالة. */
    fun finishByChannel(channel: String): Entry? {
        val callId = channelToCallId.remove(channel) ?: return null
        val entry = byCallId.remove(callId) ?: return null
        return entry.copy(stage = Stage.ENDED, updatedAt = Instant.now())
    }

    /** ينهي التتبّع بالـ`callId` — لمسار الإنهاء اليدوي من التطبيق. */
    fun finishByCallId(callId: String): Entry? {
        val entry = byCallId.remove(callId) ?: return null
        entry.channel?.let(channelToCallId::remove)
        return entry.copy(stage = Stage.ENDED, updatedAt = Instant.now())
    }

    fun find(callId: String): Entry? = byCallId[callId]

    /** عدد المكالمات المتتبَّعة — للتشخيص والاختبار. */
    fun activeCount(): Int = byCallId.size

    private fun purgeExpired() {
        val cutoff = Instant.now().minus(ENTRY_TTL)
        val stale = byCallId.values.filter { it.updatedAt.isBefore(cutoff) }
        stale.forEach { entry ->
            byCallId.remove(entry.callId)
            entry.channel?.let(channelToCallId::remove)
            log.warn("PSTN tracker: purged stale call {} (last update {})", entry.callId, entry.updatedAt)
        }
    }
}
