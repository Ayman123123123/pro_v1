package com.red.server.calls

import com.red.server.auth.repository.UserAccountRepository
import com.red.server.services.NotificationService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
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
 * - تذكير FCM: controller فقط + TODO أدناه (لا Scheduler فعلي في هذه الخطوة).
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

        /** مخزن العملية — مفتاح id. يُصفّى منطقياً بالمستقبل فقط عند القراءة. */
        private val store = ConcurrentHashMap<String, ScheduledCallRecord>()
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
        require(request.timeMillis > now) { "SCHEDULED_TIME_MUST_BE_FUTURE" }
        require(request.timeMillis - now <= MAX_FUTURE_MILLIS) { "SCHEDULED_TOO_FAR" }
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

        // TODO FCM تذكير: مجدول خادمي (Scheduler/Quartz) يفحص المستحق خلال 5 دقائق
        // ويرسل NotificationService.sendVoipPushNotification لكل مدعو + المالك.
        // عمداً لا إرسال فوري هنا — الدعوة تُرسل عند الاستحقاق فقط لتفادي إزعاج مبكر.
        // مثال عند التفعيل:
        //   @Scheduled(fixedDelay = 60_000) fun remindDue() {
        //     dueRecords(5.min).forEach { r ->
        //       (r.invitees + r.ownerRedId).distinct().forEach { redId ->
        //         notifications.sendVoipPushNotification(redId, r.ownerRedId, r.roomId, if (r.video) "VIDEO" else "VOICE")
        //       }
        //     }
        //   }
        scheduleFcmReminder(record)

        return ResponseEntity.ok(record.toResponse())
    }

    @GetMapping
    fun list(authentication: Authentication): List<ScheduledCallResponse> {
        val now = System.currentTimeMillis()
        // تنظيف كسول للماضي حتى لا ينمو المخزن بلا حد (in-memory فقط).
        store.entries.removeIf { it.value.timeMillis <= now }
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
        log.info("scheduled.call deleted id={}", id)
        return ResponseEntity.ok(mapOf("id" to id, "deleted" to true))
    }

    /**
     * خطاف التذكير — حالياً توثيق/لوج فقط.
     * TODO: اربطه بـ @Scheduled + NotificationService.sendVoipPushNotification عند الاستحقاق.
     */
    private fun scheduleFcmReminder(record: ScheduledCallRecord) {
        log.info(
            "scheduled.call reminder TODO id={} at={} invitees={} (wire to Scheduler + FCM)",
            record.id, record.timeMillis, record.invitees.size
        )
        @Suppress("unused")
        val fcmHook: NotificationService = notifications
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
