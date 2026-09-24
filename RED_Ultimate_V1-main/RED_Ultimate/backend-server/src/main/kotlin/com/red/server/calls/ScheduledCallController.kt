package com.red.server.calls

import com.red.server.auth.repository.UserAccountRepository
import com.red.server.services.NotificationService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * المكالمات المجدولة خادمياً (P1-F).
 *
 * Endpoints:
 * - POST   /api/calls/scheduled      — جدولة مكالمة (title/roomId/video/invitees/timeMillis)
 * - GET    /api/calls/scheduled      — مجدولاتي المستقبلية فقط
 * - DELETE /api/calls/scheduled/{id} — حذف مجدولة أملكها
 *
 * ملاحظات:
 * - التخزين in-memory (ConcurrentHashMap) عمداً: لا هجرة DB في P1-F، والعميل
 *   يبقى offline-first عبر AlarmManager. الترقية لـ Mongo/JPA لاحقاً دون كسر العقد.
 * - التسجيل الخادمي موجود مسبقاً: POST /api/recordings في CallRecordingController
 *   (metadata في Mongo `call_recordings` + بوابة consent في العميل).
 * - المجدول الخادمي فعّال: @Scheduled كل دقيقة يرسل VoIP push عند الاستحقاق
 *   (نافذة ±دقيقة) وينظّف المنتهية بعد نافذة الاحتفاظ (5 دقائق).
 */
@RestController
@RequestMapping("/api/calls/scheduled")
class ScheduledCallController(
    private val users: UserAccountRepository,
    private val notifications: NotificationService
) {
    companion object {
        private val log = LoggerFactory.getLogger(ScheduledCallController::class.java)
        private val ROOM_ID = Regex("^[A-Za-z0-9_-]{4,128}$")
        private const val MAX_TITLE = 140
        private const val MAX_INVITEES = 100
        private const val MAX_FUTURE_MILLIS = 365L * 24 * 60 * 60 * 1000
        /** نافذة الاستحقاق: قبل الموعد بدقيقة وبعده بدقيقة (تغطي انحراف الجولة). */
        const val DUE_BEFORE_MILLIS = 60_000L
        const val DUE_AFTER_MILLIS = 60_000L
        /** الاحتفاظ بالمنتهية 5 دقائق بعد موعدها ثم تنظيفها نهائياً. */
        const val EXPIRE_AFTER_MILLIS = 5 * 60_000L

        /** مخزن العملية — مفتاح id. يُصفّى منطقياً بالمستقبل فقط عند القراءة. */
        private val store = ConcurrentHashMap<String, ScheduledCallRecord>()

        /** مُذكَّرات تم إشعارها فعلاً — لمنع التكرار في كل جولة (exactly-once لكل id). */
        private val reminded = ConcurrentHashMap.newKeySet<String>()

        /** تحقق زمني خالص (للوحدة + نقطة الجدولة): null = صالح. */
        @JvmStatic
        fun validateScheduleTime(timeMillis: Long, now: Long = System.currentTimeMillis()): String? {
            if (timeMillis <= now) return "SCHEDULED_TIME_MUST_BE_FUTURE"
            if (timeMillis - now > MAX_FUTURE_MILLIS) return "SCHEDULED_TOO_FAR"
            return null
        }

        /** مستحق خلال نافذة [now-دقيقة، now+دقيقة]. */
        @JvmStatic
        fun isDue(timeMillis: Long, now: Long = System.currentTimeMillis()): Boolean {
            return timeMillis <= now + DUE_BEFORE_MILLIS && timeMillis >= now - DUE_AFTER_MILLIS
        }

        /** منتهٍ فقط بعد تجاوز نافذة الاحتفاظ (now - time > 5 دقائق). */
        @JvmStatic
        fun isExpired(timeMillis: Long, now: Long = System.currentTimeMillis()): Boolean {
            return now - timeMillis > EXPIRE_AFTER_MILLIS
        }
    }

    @PostMapping
    fun schedule(
        @RequestBody request: ScheduleCallRequest,
        authentication: Authentication
    ): ResponseEntity<ScheduledCallResponse> {
        val accountId = UUID.fromString(authentication.name)
        val user = users.findById(accountId).orElseThrow { NoSuchElementException("User not found") }
        val now = System.currentTimeMillis()
        require(request.roomId.trim().matches(ROOM_ID)) { "Invalid roomId" }
        val timeError = validateScheduleTime(request.timeMillis, now)
        require(timeError == null) { timeError!! }
        require(request.title.length <= MAX_TITLE) { "TITLE_TOO_LONG" }
        val invitees = request.invitees.filter { it.isNotBlank() }.distinct().take(MAX_INVITEES)
        val id = request.id.trim().ifBlank { "sched_${UUID.randomUUID().toString().replace("-", "").take(12)}" }
        require(id.matches(Regex("^[A-Za-z0-9_-]{4,64}$"))) { "Invalid scheduled id" }

        val record = ScheduledCallRecord(
            id = id,
            ownerAccountId = user.id.toString(),
            ownerRedId = user.redId,
            title = request.title.trim(),
            roomId = request.roomId.trim(),
            video = request.video,
            invitees = invitees,
            timeMillis = request.timeMillis,
            createdAt = Instant.now()
        )
        store[id] = record
        log.info("scheduled.call created id={} room={} owner={} at={}", id, record.roomId, user.redId, record.timeMillis)

        // لا إرسال فوري هنا عمداً — الدعوة تُرسل عند الاستحقاق فقط عبر remindDue()
        // لتفادي إزعاج مبكر. (المجدول كل دقيقة + نافذة ±دقيقة).
        schedulePushReminder(record)

        return ResponseEntity.ok(record.toResponse())
    }

    @GetMapping
    fun list(authentication: Authentication): List<ScheduledCallResponse> {
        val now = System.currentTimeMillis()
        // تنظيف المنتهية فقط بعد نافذة الاحتفاظ حتى لا ينمو المخزن بلا حد.
        val expiredIds = store.entries.filter { isExpired(it.value.timeMillis, now) }.map { it.key }
        expiredIds.forEach { store.remove(it); reminded.remove(it) }
        return store.values
            .filter { it.ownerAccountId == authentication.name && it.timeMillis > now }
            .sortedBy { it.timeMillis }
            .map { it.toResponse() }
    }

    @DeleteMapping("/{id}")
    fun delete(
        @PathVariable id: String,
        authentication: Authentication
    ): ResponseEntity<Map<String, Any>> {
        val record = store[id] ?: throw NoSuchElementException("Scheduled call not found")
        require(record.ownerAccountId == authentication.name) { "ONLY_OWNER_CAN_DELETE" }
        store.remove(id)
        reminded.remove(id)
        log.info("scheduled.call deleted id={}", id)
        return ResponseEntity.ok(mapOf("id" to id, "deleted" to true))
    }

    /**
     * جولة المجدول الفعلية — كل دقيقة عبر Spring (@EnableScheduling في التطبيق).
     * ترسل VoIP push عند الاستحقاق ثم تنظّف المنتهية بعد نافذة الاحتفاظ.
     * @return عدد السجلات التي تم تذكيرها أول مرة في هذه الجولة.
     */
    @Scheduled(fixedDelay = 60_000)
    fun scheduledRemind() {
        runCatching { remindDue(System.currentTimeMillis()) }
            .onFailure { log.warn("scheduled.call tick failed", it) }
    }

    /**
     * يفحص المستحق خلال نافذة ±دقيقة ويرسل لكل مدعو + المالك مرة واحدة فقط،
     * ثم يحذف المنتهية بعد 5 دقائق. خالصة زمنياً عبر nowMillis لتكون قابلة للاختبار.
     */
    fun remindDue(nowMillis: Long = System.currentTimeMillis()): Int {
        // تنظيف المنتهية أولاً (مع تحرير reminded لمنع التسرب).
        val expiredIds = store.entries.filter { isExpired(it.value.timeMillis, nowMillis) }.map { it.key }
        expiredIds.forEach { store.remove(it); reminded.remove(it) }
        var remindedCount = 0
        store.values
            .filter { isDue(it.timeMillis, nowMillis) && reminded.add(it.id) }
            .forEach { record ->
                val targets = (record.invitees + record.ownerRedId).distinct()
                var ok = false
                targets.forEach { redId ->
                    runCatching {
                        notifications.sendVoipPushNotification(
                            redId,
                            record.ownerRedId,
                            record.roomId,
                            if (record.video) "VIDEO" else "VOICE"
                        )
                    }.onSuccess { ok = true }
                        .onFailure { log.warn("scheduled.call push failed id={} target={}", record.id, redId, it) }
                }
                if (ok) {
                    remindedCount++
                    log.info("scheduled.call reminded id={} room={} targets={}", record.id, record.roomId, targets.size)
                } else if (targets.isEmpty()) {
                    // بلا مدعوين: تُحتسب مذكّرة لتفادي إعادة الفحص كل دقيقة.
                    remindedCount++
                } else {
                    // فشل الكل: اسمح بإعادة المحاولة في الجولة القادمة.
                    reminded.remove(record.id)
                }
            }
        return remindedCount
    }
    /**
     * خطاف توثيق فقط — الإرسال الفعلي يتم عبر remindDue() كل دقيقة عند الاستحقاق.
     */
    private fun schedulePushReminder(record: ScheduledCallRecord) {
        log.info(
            "scheduled.call queued id={} at={} invitees={} (push on due via Scheduler)",
            record.id, record.timeMillis, record.invitees.size
        )
        @Suppress("unused")
        val pushHook: NotificationService = notifications
    }
}

data class ScheduleCallRequest(
    val id: String = "",
    val title: String = "",
    val roomId: String = "",
    val video: Boolean = false,
    val invitees: List<String> = emptyList(),
    val timeMillis: Long = 0L
)

data class ScheduledCallResponse(
    val id: String,
    val title: String,
    val roomId: String,
    val video: Boolean,
    val invitees: List<String>,
    val timeMillis: Long,
    val ownerRedId: String,
    val createdAt: Instant
)

private data class ScheduledCallRecord(
    val id: String,
    val ownerAccountId: String,
    val ownerRedId: String,
    val title: String,
    val roomId: String,
    val video: Boolean,
    val invitees: List<String>,
    val timeMillis: Long,
    val createdAt: Instant
) {
    fun toResponse() = ScheduledCallResponse(
        id = id,
        title = title,
        roomId = roomId,
        video = video,
        invitees = invitees,
        timeMillis = timeMillis,
        ownerRedId = ownerRedId,
        createdAt = createdAt
    )
}
