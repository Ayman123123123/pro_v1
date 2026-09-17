package com.red.server.calls

import com.red.server.auth.repository.UserAccountRepository
import com.red.server.database.RedisManager
import com.red.server.services.NotificationService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
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
 * - DELETE /api/calls/scheduled/{id} — حذف مجدولة أملكها (إلغاء)
 *
 * ملاحظات:
 * - التخزين in-memory (ConcurrentHashMap) عمداً: لا هجرة DB في P1-F، والعميل
 *   يبقى offline-first عبر AlarmManager. الترقية لـ Mongo/JPA لاحقاً دون كسر العقد.
 * - التسجيل الخادمي موجود مسبقاً: POST /api/recordings في CallRecordingController
 *   (metadata في Mongo `call_recordings` + بوابة consent في العميل).
 * - التذكير السيادي: ماسح {@link #remindDueScheduled} كل دقيقة يذكّر المستحق
 *   (نافذة الاستحقاق أدناه) عبر NotificationService.sendVoipPushNotification لكل
 *   مدعو + المالك، مع نسخة best-effort في طابور Redis (red:notify:queue) —
 *   نفس مسارَي /api/calls/push-notify في CallHistoryController. لا إرسال فوري
 *   عند الجدولة — الدعوة تُرسل عند الاستحقاق فقط لتفادي إزعاج مبكر.
 */
@RestController
@RequestMapping("/api/calls/scheduled")
class ScheduledCallController(
    private val users: UserAccountRepository,
    private val notifications: NotificationService
) {
    /**
     * Redis اختياري عمداً: المجدول يعمل بدونه (VoIP push فقط)، ومعه يضيف نسخة
     * طابور الإشعارات. حقن حقلي حتى لا يتغير المُنشئ (توافق مع CallsV1Controller).
     */
    @Autowired(required = false)
    private var redis: RedisManager? = null

    companion object {
        private val log = LoggerFactory.getLogger(ScheduledCallController::class.java)
        private val ROOM_ID = Regex("^[A-Za-z0-9_-]{4,128}$")
        private const val MAX_TITLE = 140
        private const val MAX_INVITEES = 100
        private const val MAX_FUTURE_MILLIS = 365L * 24 * 60 * 60 * 1000

        /** ذكّر بمن يستحق خلال الدقيقة القادمة (فحص الماسح كل دقيقة). */
        const val REMIND_LEAD_MILLIS = 60_000L

        /** سماح الفائت: ذكّر أيضاً بمن فات وقته منذ ≤5 دقائق (تفويت tick واحد). */
        const val MISSED_GRACE_MILLIS = 5 * 60_000L

        /** احتفاظ المنتهية 5 دقائق لفرصة التذكير المتأخر، ثم حذف نهائي. */
        const val RETAIN_EXPIRED_MILLIS = 5 * 60_000L

        /** مخزن العملية — مفتاح id. يُصفّى منطقياً بالمستقبل فقط عند القراءة. */
        private val store = ConcurrentHashMap<String, ScheduledCallRecord>()

        /** مستحق؟ داخل (now - سماح الفائت، now + دقيقة]. دوال زمنية خالصة للاختبار. */
        fun isDue(timeMillis: Long, now: Long): Boolean =
            timeMillis <= now + REMIND_LEAD_MILLIS && timeMillis > now - MISSED_GRACE_MILLIS

        /** منتهٍ للحذف؟ تجاوز فترة الاحتفاظ. */
        fun isExpired(timeMillis: Long, now: Long): Boolean =
            timeMillis <= now - RETAIN_EXPIRED_MILLIS

        /** مستقبلي (يُعرض في GET ويُقبل في POST)؟ */
        fun isFuture(timeMillis: Long, now: Long): Boolean = timeMillis > now

        /**
         * تحقق زمني خالص يطابق شروط schedule: مستقبلي + بحد أقصى سنة.
         * @return رمز الخطأ أو null إن صالح.
         */
        fun validateScheduleTime(timeMillis: Long, now: Long): String? = when {
            timeMillis <= now -> "SCHEDULED_TIME_MUST_BE_FUTURE"
            timeMillis - now > MAX_FUTURE_MILLIS -> "SCHEDULED_TOO_FAR"
            else -> null
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

        // عمداً لا إرسال فوري هنا — الدعوة تُرسل عند الاستحقاق فقط لتفادي إزعاج مبكر.
        schedulePushReminder(record)

        return ResponseEntity.ok(record.toResponse())
    }

    @GetMapping
    fun list(authentication: Authentication): List<ScheduledCallResponse> {
        val now = System.currentTimeMillis()
        // تنظيف المنتهية بعد فترة الاحتفاظ فقط — حتى لا نحذف فائتاً قبل تذكيره.
        // العرض يبقى «المستقبلية فقط» كما كان.
        store.entries.removeIf { isExpired(it.value.timeMillis, now) }
        return store.values
            .filter { it.ownerAccountId == authentication.name && isFuture(it.timeMillis, now) }
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
     * ماسح الاستحقاق: كل دقيقة يذكّر المستحق مرة واحدة وينظّف المنتهية.
     * idempotent عبر علامة reminded — لا تكرار عند كل tick.
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    fun remindDueScheduled() {
        runCatching { remindDue(System.currentTimeMillis()) }
            .onFailure { log.warn("scheduled.call sweeper failed err={}", it.message) }
    }

    /**
     * نواة الماسح — داخلية وقابلة للاختبار: تنظيف + تذكير.
     * @return عدد السجلات المذكّرة في هذه الجولة.
     */
    internal fun remindDue(now: Long): Int {
        // تنظيف المنتهية (تجاوزت الاحتفاظ) والملغاة تُحذف أصلاً عبر DELETE.
        store.entries.removeIf { isExpired(it.value.timeMillis, now) }
        var reminded = 0
        store.values
            .filter { !it.reminded && isDue(it.timeMillis, now) }
            .sortedBy { it.timeMillis }
            .forEach { record ->
                runCatching { notifyRecord(record) }
                    .onFailure { log.warn("scheduled.call remind failed id={} err={}", record.id, it.message) }
                store.computeIfPresent(record.id) { _, cur ->
                    if (!cur.reminded) cur.copy(reminded = true, remindedAt = now) else cur
                }
                reminded++
            }
        if (reminded > 0) log.info("scheduled.call reminders sent count={}", reminded)
        return reminded
    }

    /**
     * تذكير سجل واحد: VoIP push سيادي لكل مدعو + المالك (المسار الرئيسي، يوقظ
     * التطبيق المقتول)، ونسخة best-effort في طابور Redis (red:notify:queue —
     * نفس عقد RedisManager المستخدم في /api/calls/push-notify).
     */
    private fun notifyRecord(record: ScheduledCallRecord) {
        val mode = if (record.video) "VIDEO" else "VOICE"
        val targets = (record.invitees + record.ownerRedId).filter { it.isNotBlank() }.distinct()
        val payload = scheduledReminderJson(record)
        targets.forEach { target ->
            runCatching {
                notifications.sendVoipPushNotification(target, record.ownerRedId, record.roomId, mode)
            }.onFailure {
                log.warn("scheduled.call voip push failed target={} id={} err={}", target, record.id, it.message)
            }
            runCatching { redis?.pushNotification(target, payload) }
                .onFailure {
                    log.warn("scheduled.call redis queue failed target={} id={} err={}", target, record.id, it.message)
                }
        }
        log.info("scheduled.call reminded id={} room={} targets={}", record.id, record.roomId, targets.size)
    }

    private fun scheduledReminderJson(record: ScheduledCallRecord): String =
        "{\"v\":1,\"type\":\"SCHEDULED_CALL_REMINDER\"," +
            "\"id\":${jsonStr(record.id)},\"roomId\":${jsonStr(record.roomId)}," +
            "\"title\":${jsonStr(record.title)},\"ownerRedId\":${jsonStr(record.ownerRedId)}," +
            "\"mode\":${jsonStr(if (record.video) "VIDEO" else "VOICE")}," +
            "\"timeMillis\":${record.timeMillis}}"

    private fun jsonStr(value: String): String {
        val escaped = buildString(value.length + 8) {
            for (ch in value) when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch < ' ') append("\\u%04x".format(ch.code)) else append(ch)
            }
        }
        return "\"$escaped\""
    }

    /**
     * خطاف الجدولة — لوج توثيقي فقط؛ التذكير الفعلي عبر ماسح remindDueScheduled.
     */
    private fun schedulePushReminder(record: ScheduledCallRecord) {
        log.info(
            "scheduled.call reminder armed id={} at={} invitees={} (sweeper reminds at due)",
            record.id, record.timeMillis, record.invitees.size
        )
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
    val createdAt: Instant,
    val reminded: Boolean = false,
    val remindedAt: Long? = null
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
