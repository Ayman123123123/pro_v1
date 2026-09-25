package com.red.sovereign.core.outbox

import com.red.sovereign.core.database.OutboxDao
import com.red.sovereign.core.database.OutboxMessageEntity
import com.red.sovereign.core.database.OutboxMessageEntity.Companion.PRIORITY_NORMAL
import com.red.sovereign.core.database.OutboxMessageEntity.Companion.DEAD_LETTER_THRESHOLD
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * مستودع صندوق الصادر — **الواجهة الوحيدة** للكتابة في الـ outbox.
 *
 * ## القاعدة الذهبية: معاملة واحدة
 * يجب استدعاء `enqueue` داخل نفس `withTransaction` التي تكتب `LocalHistoryEntity`:
 * ```kotlin
 * db.withTransaction {
 *   redDao.insertLocalHistory(history)
 *   outboxRepository.enqueue(outboxMessage)
 * }
 * ```
 * بهذا لا توجد رسالة بلا outbox (تضيع) ولا outbox بلا رسالة (شبح).
 */
class OutboxRepository(private val dao: OutboxDao) {

    // ─── Metrics ─────────────────────────────────────────────────────────────
    private val _enqueuedCount = MutableStateFlow(0L)
    private val _sentCount = MutableStateFlow(0L)
    private val _failedCount = MutableStateFlow(0L)
    private val _deadLetterCount = MutableStateFlow(0L)
    private val _circuitBreakerOpen = MutableStateFlow(false)
    private val _lastFailureTime = MutableStateFlow<Long?>(null)
    private val consecutiveFailures = MutableStateFlow(0)
    private val circuitBreakerThreshold = 5
    val circuitBreakerResetTimeout = 60_000L // 1 minute

    val enqueuedCount: Flow<Long> = _enqueuedCount
    val sentCount: Flow<Long> = _sentCount
    val failedCount: Flow<Long> = _failedCount
    val deadLetterCount: Flow<Long> = _deadLetterCount
    val circuitBreakerOpen: Flow<Boolean> = _circuitBreakerOpen
    val circuitBreakerOpenSnapshot: Boolean
        get() = synchronized(circuitBreakerLock) { _circuitBreakerOpen.value }

    // ─── Enqueue ─────────────────────────────────────────────────────────────
    suspend fun enqueue(message: OutboxMessageEntity) {
        dao.insert(message)
        _enqueuedCount.value++
    }

    suspend fun enqueueWithIdempotency(
        conversationId: String,
        payload: ByteArray,
        type: String = "CHAT",
        idempotencyKey: String
    ): OutboxMessageEntity {
        val existing = dao.getById(idempotencyKey)
        if (existing != null) return existing
        val entity = OutboxMessageEntity(
            id = idempotencyKey,
            conversationId = conversationId,
            payload = payload,
            type = type,
            idempotencyKey = idempotencyKey
        )
        dao.insert(entity)
        _enqueuedCount.value++
        return entity
    }

    // ─── Media-aware enqueue ─────────────────────────────────────────────────
    // LEGENDARY FIX: توحيد الدالة المكررة + احترام idempotencyKey الممرر (كان يتجاهله ويولد عشوائياً فيسبب تكراراً عند retry)
    suspend fun enqueueMedia(
        conversationId: String,
        mediaType: String, // IMAGE, VIDEO, AUDIO, FILE, VOICE
        payload: ByteArray,
        localMediaPath: String,
        mediaEncryptionKey: String,
        type: String = "MEDIA",
        priority: Int = PRIORITY_NORMAL,
        idempotencyKey: String
    ): OutboxMessageEntity {
        // منع التكرار: إن وجد نفس المفتاح أرجعه (idempotent)
        dao.getById(idempotencyKey)?.let { return it }
        val entity = OutboxMessageEntity(
            id = idempotencyKey,
            conversationId = conversationId,
            payload = payload,
            type = type,
            priority = priority,
            mediaType = mediaType,
            localMediaPath = localMediaPath,
            mediaEncryptionKey = mediaEncryptionKey,
            idempotencyKey = idempotencyKey
        )
        dao.insert(entity)
        _enqueuedCount.value++
        return entity
    }

    // ─── High-level enqueue helpers ──────────────────────────────────────────
    suspend fun enqueueText(
        conversationId: String,
        text: String,
        priority: Int = PRIORITY_NORMAL,
        idempotencyKey: String
    ): OutboxMessageEntity {
        // الإرسال حتى الإقرار: id = idempotencyKey (لا UUID عشوائي) — إعادة المحاولة
        // بذات المفتاح تُعيد الصف نفسه بدل صف مكرر يضيع/يتكرر سلكياً.
        dao.getById(idempotencyKey)?.let { return it }
        val entity = OutboxMessageEntity.create(
            id = idempotencyKey,
            conversationId = conversationId,
            payload = text.toByteArray(),
            type = "CHAT",
            priority = priority,
            idempotencyKey = idempotencyKey
        )
        dao.insert(entity)
        _enqueuedCount.value++
        return entity
    }

    suspend fun enqueueMedia(
        conversationId: String,
        mediaType: String, // IMAGE, VIDEO, AUDIO, FILE, VOICE
        payload: ByteArray,
        localMediaPath: String,
        mediaEncryptionKey: String,
        priority: Int = PRIORITY_NORMAL,
        idempotencyKey: String
    ): OutboxMessageEntity {
        // تفويض للموحدة أعلاه — إزالة التكرار الميت الذي كان يكسر idempotency
        return enqueueMedia(
            conversationId = conversationId,
            mediaType = mediaType,
            payload = payload,
            localMediaPath = localMediaPath,
            mediaEncryptionKey = mediaEncryptionKey,
            type = "MEDIA",
            priority = priority,
            idempotencyKey = idempotencyKey
        )
    }

    suspend fun enqueueVoice(
        conversationId: String,
        payload: ByteArray,
        localPath: String,
        encryptionKey: String,
        priority: Int = PRIORITY_NORMAL,
        idempotencyKey: String
    ): OutboxMessageEntity {
        return enqueueMedia(
            conversationId = conversationId,
            mediaType = "VOICE",
            payload = payload,
            localMediaPath = localPath,
            mediaEncryptionKey = encryptionKey,
            priority = priority,
            idempotencyKey = idempotencyKey
        )
    }

    suspend fun enqueueImage(
        conversationId: String,
        payload: ByteArray,
        localPath: String,
        encryptionKey: String,
        priority: Int = PRIORITY_NORMAL,
        idempotencyKey: String
    ): OutboxMessageEntity {
        return enqueueMedia(
            conversationId = conversationId,
            mediaType = "IMAGE",
            payload = payload,
            localMediaPath = localPath,
            mediaEncryptionKey = encryptionKey,
            priority = priority,
            idempotencyKey = idempotencyKey
        )
    }

    suspend fun enqueueVideo(
        conversationId: String,
        payload: ByteArray,
        localPath: String,
        encryptionKey: String,
        priority: Int = PRIORITY_NORMAL,
        idempotencyKey: String
    ): OutboxMessageEntity {
        return enqueueMedia(
            conversationId = conversationId,
            mediaType = "VIDEO",
            payload = payload,
            localMediaPath = localPath,
            mediaEncryptionKey = encryptionKey,
            priority = priority,
            idempotencyKey = idempotencyKey
        )
    }

    suspend fun enqueueFile(
        conversationId: String,
        payload: ByteArray,
        localPath: String,
        encryptionKey: String,
        priority: Int = PRIORITY_NORMAL,
        idempotencyKey: String
    ): OutboxMessageEntity {
        return enqueueMedia(
            conversationId = conversationId,
            mediaType = "FILE",
            payload = payload,
            localMediaPath = localPath,
            mediaEncryptionKey = encryptionKey,
            priority = priority,
            idempotencyKey = idempotencyKey
        )
    }

    // ─── Basic queries ───────────────────────────────────────────────────────
    suspend fun getPending(limit: Int = 20): List<OutboxMessageEntity> = dao.getPendingWithPriority(now = System.currentTimeMillis(), limit = limit)

    fun observePending(): Flow<List<OutboxMessageEntity>> = dao.observePending()
        // LEGENDARY FIX: الفرز المزدوج sortedBy().sortedBy() كان يلغي الأول — دمج صحيح: أولوية ثم موعد المحاولة
        .map { list -> list.sortedWith(compareBy<OutboxMessageEntity> { it.priority }.thenBy { it.nextAttemptAt }) }

    fun observePendingCount(): Flow<Int> = dao.observePendingCount()

    // ─── Circuit Breaker ─────────────────────────────────────────────────────
    // All check-then-act transitions are guarded by circuitBreakerLock so
    // concurrent workers cannot interleave read-modify-write on
    // consecutiveFailures / _circuitBreakerOpen / _lastFailureTime.
    // NOTE: instances are recreated per WorkManager run, so the authoritative
    // state lives in the companion global (shared in-process) + SharedPrefs
    // (survives process death). Instance flows stay as observable mirrors.
    private val circuitBreakerLock = Any()

    fun recordSuccess() {
        globalRecordSuccess()
        synchronized(circuitBreakerLock) {
            consecutiveFailures.value = 0
            if (_circuitBreakerOpen.value) {
                _circuitBreakerOpen.value = false
                _lastFailureTime.value = null
            }
        }
    }

    fun recordFailure() {
        globalRecordFailure()
        synchronized(circuitBreakerLock) {
            val count = consecutiveFailures.value + 1
            consecutiveFailures.value = count
            _lastFailureTime.value = System.currentTimeMillis()
            if (count >= circuitBreakerThreshold) {
                _circuitBreakerOpen.value = true
                _lastFailureTime.value = System.currentTimeMillis()
            }
        }
    }

    fun isCircuitBreakerOpen(): Boolean {
        // Global is authoritative (shared across worker instances); mirror it.
        if (globalIsOpen()) {
            synchronized(circuitBreakerLock) {
                if (!_circuitBreakerOpen.value) {
                    _circuitBreakerOpen.value = true
                    _lastFailureTime.value = globalLastFailureMs()
                }
            }
            return true
        }
        return synchronized(circuitBreakerLock) { _circuitBreakerOpen.value }
    }

    fun tryResetCircuitBreaker() {
        globalTryReset()
        synchronized(circuitBreakerLock) {
            if (_circuitBreakerOpen.value) {
                val lastFailure = _lastFailureTime.value ?: return@synchronized
                if (System.currentTimeMillis() - lastFailure > circuitBreakerResetTimeout) {
                    _circuitBreakerOpen.value = false
                    _lastFailureTime.value = null
                }
            }
        }
    }

    /** المهلة المتبقية للقاطع المشترك (ثوانٍ) — للمؤشر الحقيقي والجدولة. */
    fun breakerRemainingSec(): Long = globalRemainingSec()

    // ─── Dead Letter Queue ───────────────────────────────────────────────────
    suspend fun getDeadLetterQueue(limit: Int = 50): List<OutboxMessageEntity> =
        dao.getDeadLetterQueue(limit)

    suspend fun countDeadLetter(): Int = dao.countDeadLetter()

    // ─── Sending & Retry Logic ───────────────────────────────────────────────
    suspend fun markSent(id: String) {
        dao.updateStatus(id, "SENT")
        dao.cleanupSent(System.currentTimeMillis() - 24 * 60 * 60 * 1000L)
        _sentCount.value++
        recordSuccess()
    }

    suspend fun scheduleRetry(id: String, error: String?) {
        val current = dao.getById(id) ?: return
        val nextDelay = computeBackoff(current.retryCount)
        val nextAttempt = System.currentTimeMillis() + nextDelay
        if (current.retryCount >= DEAD_LETTER_THRESHOLD) {
            dao.updateStatus(id, "DEAD_LETTER", error)
            _deadLetterCount.value++
            recordFailure()
        } else {
            dao.scheduleRetry(id, nextAttempt, error)
            _failedCount.value++
            recordFailure()
        }
        tryResetCircuitBreaker()
    }

    suspend fun delete(id: String) = dao.delete(id)

    // ─── Backoff ─────────────────────────────────────────────────────────────
    internal fun computeBackoff(retryCount: Int): Long {
        val base = when (retryCount) {
            0 -> 10_000L
            1 -> 30_000L
            2 -> 120_000L
            3 -> 600_000L
            4 -> 3_600_000L
            else -> 86_400_000L // 24h max
        }
        val jitter = (base * 0.25 * (Math.random() * 2 - 1)).toLong()
        return (base + jitter).coerceAtLeast(5_000L)
    }

    companion object {
        private val globalLock = Any()
        @Volatile private var globalFailures = 0
        @Volatile private var globalOpen = false
        @Volatile private var globalLastFailureMs = 0L
        const val GLOBAL_THRESHOLD = 5
        const val GLOBAL_RESET_TIMEOUT_MS = 60_000L
        private const val PREFS = "red_outbox_breaker"
        private const val KEY_FAILURES = "failures"
        private const val KEY_OPEN = "open"
        private const val KEY_LAST = "lastFailure"

        /** نصف مفتوح: يُسمح بمسبار واحد بعد المهلة بدل الانتظار السلبي. */
        fun globalIsOpen(): Boolean = synchronized(globalLock) {
            if (!globalOpen) return false
            if (System.currentTimeMillis() - globalLastFailureMs > GLOBAL_RESET_TIMEOUT_MS) {
                // half-open probe: close optimistically, one attempt decides
                globalOpen = false
                return false
            }
            true
        }

        fun globalRemainingSec(): Long = synchronized(globalLock) {
            if (!globalOpen) return 0L
            ((GLOBAL_RESET_TIMEOUT_MS - (System.currentTimeMillis() - globalLastFailureMs)) / 1000L).coerceAtLeast(0L)
        }

        fun globalLastFailureMs(): Long = synchronized(globalLock) { globalLastFailureMs }

        fun globalRecordSuccess() = synchronized(globalLock) {
            globalFailures = 0
            globalOpen = false
            globalLastFailureMs = 0L
        }

        /** @return true إن فُتح القاطع بهذا الفشل. */
        fun globalRecordFailure(): Boolean = synchronized(globalLock) {
            globalFailures++
            globalLastFailureMs = System.currentTimeMillis()
            if (globalFailures >= GLOBAL_THRESHOLD) globalOpen = true
            globalOpen
        }

        fun globalTryReset() = synchronized(globalLock) {
            if (globalOpen && System.currentTimeMillis() - globalLastFailureMs > GLOBAL_RESET_TIMEOUT_MS) {
                globalOpen = false
                globalLastFailureMs = 0L
            }
        }

        /** تحميل الحالة المحفوظة (موت العملية) — يُستدعى أول doWork. */
        fun loadPersisted(context: android.content.Context) = synchronized(globalLock) {
            runCatching {
                val p = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
                globalFailures = p.getInt(KEY_FAILURES, 0)
                globalOpen = p.getBoolean(KEY_OPEN, false)
                globalLastFailureMs = p.getLong(KEY_LAST, 0L)
                // إن انتهت المهلة أثناء الموت → half-open فوراً
                if (globalOpen && System.currentTimeMillis() - globalLastFailureMs > GLOBAL_RESET_TIMEOUT_MS) {
                    globalOpen = false
                    globalLastFailureMs = 0L
                }
            }
        }

        /** حفظ الحالة (يُستدعى بعد كل record) — رخيص (3 ints). */
        fun persist(context: android.content.Context) {
            val f: Int; val o: Boolean; val l: Long
            synchronized(globalLock) { f = globalFailures; o = globalOpen; l = globalLastFailureMs }
            runCatching {
                context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit()
                    .putInt(KEY_FAILURES, f).putBoolean(KEY_OPEN, o).putLong(KEY_LAST, l).apply()
            }
        }
    }
}
