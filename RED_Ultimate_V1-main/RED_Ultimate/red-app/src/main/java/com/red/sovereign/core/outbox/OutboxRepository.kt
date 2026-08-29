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
        get() = _circuitBreakerOpen.value

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
        val entity = OutboxMessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            payload = payload,
            type = "MEDIA",
            priority = priority,
            mediaType = mediaType,
            localMediaPath = localMediaPath,
            mediaEncryptionKey = mediaEncryptionKey,
            idempotencyKey = UUID.randomUUID().toString()
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
        val entity = OutboxMessageEntity.create(
            id = UUID.randomUUID().toString(),
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
        val entity = OutboxMessageEntity(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            payload = payload,
            type = "MEDIA",
            priority = priority,
            mediaType = mediaType,
            localMediaPath = localMediaPath,
            mediaEncryptionKey = mediaEncryptionKey,
            idempotencyKey = UUID.randomUUID().toString()
        )
        dao.insert(entity)
        _enqueuedCount.value++
        return entity
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
    suspend fun getPending(limit: Int = 20): List<OutboxMessageEntity> = dao.getPendingWithPriority(limit = limit)

    fun observePending(): Flow<List<OutboxMessageEntity>> = dao.observePending()
        .map { it.sortedBy { it.priority }.sortedBy { it.nextAttemptAt } }

    fun observePendingCount(): Flow<Int> = dao.observePendingCount()

    // ─── Circuit Breaker ─────────────────────────────────────────────────────
    internal fun recordSuccess() {
        consecutiveFailures.value = 0
        if (_circuitBreakerOpen.value) {
            _circuitBreakerOpen.value = false
            _lastFailureTime.value = null
        }
    }

    internal fun recordFailure() {
        val count = consecutiveFailures.value + 1
        consecutiveFailures.value = count
        _lastFailureTime.value = System.currentTimeMillis()
        if (count >= 5) {
            _circuitBreakerOpen.value = true
            _lastFailureTime.value = System.currentTimeMillis()
        }
    }

    fun isCircuitBreakerOpen(): Boolean = _circuitBreakerOpen.value

    internal fun tryResetCircuitBreaker() {
        if (_circuitBreakerOpen.value) {
            val lastFailure = _lastFailureTime.value ?: return
            if (System.currentTimeMillis() - lastFailure > circuitBreakerResetTimeout) {
                _circuitBreakerOpen.value = false
                _lastFailureTime.value = null
            }
        }
    }

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
}