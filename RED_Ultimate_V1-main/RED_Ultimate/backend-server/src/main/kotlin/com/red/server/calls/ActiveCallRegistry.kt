package com.red.server.calls

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * سجل المكالمات النشطة على مستوى الخادم.
 *
 * يخدم هدفين:
 * 1. كشف الحالة BUSY: إذا وصل OFFER لمستخدم يملك مكالمة نشطة تُرد BUSY للمتصل فوراً
 *    (خط مشغول حقيقي مثل واتساب/تلجرام) بدل الرنين.
 * 2. تزويد لوحة الأدمن بعداد المكالمات النشطة عبر Redis (`red:calls:active` ZSet
 *    بالدرجة = وقت آخر نشاط) — كان العداد صفراً دائماً لأن لا أحد كان يكتبه.
 *
 * الانتهاء يكون بـ unregister من إشارات END/REJECT/BUSY، وأي تسريب من أجهزة
 * منقطعة يُنظَّف تلقائياً بعد 15 دقيقة من آخر نشاط.
 */
@Service
class ActiveCallRegistry(private val redis: StringRedisTemplate) {
    private val active = ConcurrentHashMap<String, ActiveCallRecord>()

    /** يسجّل مكالمة (1:1 أو جماعية) وأعضاءها كـ "في مكالمة". */
    fun register(callId: String, participants: List<String>) {
        active[callId] = ActiveCallRecord(participants.filter { it.isNotBlank() }.toSet(), Instant.now(), Instant.now())
        touch(callId)
    }

    /** يُحدِّث وقت النشاط (يسجّل المكالمة نشطة في عدادات اللوحة ويؤجّل انتهاء صلاحيتها). */
    fun touch(callId: String) {
        active[callId]?.let { active[callId] = it.copy(lastActivityAt = Instant.now()) }
        redis.opsForZSet().add(CALLS_KEY, callId, System.currentTimeMillis().toDouble())
    }

    /** هل المستخدم في مكالمة نشطة حالياً (1:1 أو جماعية)؟ */
    fun isInCall(redId: String): Boolean =
        redId.isNotBlank() && active.values.any { redId in it.participants }

    /** هل المكالمة/الغرفة الجماعية مسجّلة ونشطة؟ تُستخدم للتحقق من صحة تذاكر SFU. */
    fun isActiveCall(callId: String): Boolean =
        callId.isNotBlank() && active.containsKey(callId)

    /** يُحرِّر عضواً واحداً (رفض الدعوة/غادر/لم يرد) — يصبح متاحاً لاستقبال المكالمات. */
    fun releaseMember(callId: String, redId: String) {
        val record = active[callId] ?: return
        val updated = record.participants - redId
        if (updated.isEmpty()) unregister(callId)
        else active[callId] = record.copy(participants = updated)
    }

    /** يُستدعى عند END/REJECT/BUSY لإزالة المكالمة كاملة من العدادات. */
    fun unregister(callId: String) {
        active.remove(callId)
        redis.opsForZSet().remove(CALLS_KEY, callId)
    }

    /** لقطة المكالمات النشطة الحالية لعرضها في لوحة الأدمن. */
    fun snapshot(): List<Map<String, Any>> = active.map { (callId, record) ->
        mapOf(
            "id" to callId,
            "type" to "VOIP",
            "room" to callId,
            "participants" to record.participants.size,
            "participantIds" to record.participants.toList(),
            "startedAt" to record.startedAt.toString()
        )
    }

    /** تنظيف دوري: أي مكالمة بلا نشاط منذ 15 دقيقة تُعتبر معلّقة وتُزال.
     *  (الحذف حسب آخر نشاط فعلّ — لا حسب بدء المكالمة، فالمكالمات الطويلة حية). */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    fun expireStale() {
        val cutoff = Instant.now().minusMillis(STALE_AFTER_MS)
        redis.opsForZSet().removeRangeByScore(CALLS_KEY, 0.0, cutoff.toEpochMilli().toDouble())
        active.entries.removeIf { it.value.lastActivityAt.isBefore(cutoff) }
    }

    private data class ActiveCallRecord(
        val participants: Set<String>,
        val startedAt: Instant,
        val lastActivityAt: Instant
    )

    companion object {
        private const val CALLS_KEY = "red:calls:active"
        private const val STALE_AFTER_MS = 15 * 60_000L
    }
}