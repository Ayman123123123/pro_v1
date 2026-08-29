package com.red.sovereign.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: OutboxMessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<OutboxMessageEntity>)

    @Query("SELECT * FROM outbox_messages WHERE status IN ('PENDING','FAILED') AND nextAttemptAt <= :now ORDER BY priority ASC, nextAttemptAt ASC LIMIT :limit")
    suspend fun getPending(now: Long = System.currentTimeMillis(), limit: Int = 20): List<OutboxMessageEntity>

    @Query("SELECT * FROM outbox_messages WHERE status IN ('PENDING','FAILED') ORDER BY priority ASC, nextAttemptAt ASC")
    fun observePending(): Flow<List<OutboxMessageEntity>>

    @Query("SELECT COUNT(*) FROM outbox_messages WHERE status IN ('PENDING','FAILED')")
    suspend fun countPending(): Int

    @Query("SELECT COUNT(*) FROM outbox_messages WHERE status IN ('PENDING','FAILED')")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM outbox_messages WHERE id = :id")
    suspend fun getById(id: String): OutboxMessageEntity?

    @Query("UPDATE outbox_messages SET status = :status, lastError = :error WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, error: String? = null)

    @Query("UPDATE outbox_messages SET retryCount = retryCount + 1, nextAttemptAt = :nextAttempt, lastError = :error, status = 'PENDING' WHERE id = :id")
    suspend fun scheduleRetry(id: String, nextAttempt: Long, error: String?)

    @Query("UPDATE outbox_messages SET status = 'SENDING' WHERE id = :id AND status = 'PENDING'")
    suspend fun markSending(id: String): Int

    @Query("DELETE FROM outbox_messages WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM outbox_messages WHERE status = 'SENT' AND createdAt < :before")
    suspend fun cleanupSent(before: Long): Int

    @Query("DELETE FROM outbox_messages WHERE status = 'FAILED' AND retryCount >= :maxRetries AND nextAttemptAt < :before")
    suspend fun cleanupFailed(before: Long, maxRetries: Int): Int

    // Dead Letter Queue queries
    @Query("SELECT * FROM outbox_messages WHERE status = 'DEAD_LETTER' ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getDeadLetterQueue(limit: Int = 50): List<OutboxMessageEntity>

    @Query("SELECT COUNT(*) FROM outbox_messages WHERE status = 'DEAD_LETTER'")
    suspend fun countDeadLetter(): Int

    // Priority-based queries
    @Query("SELECT * FROM outbox_messages WHERE status IN ('PENDING','FAILED') AND nextAttemptAt <= :now ORDER BY priority ASC, nextAttemptAt ASC LIMIT :limit")
    suspend fun getPendingWithPriority(now: Long = System.currentTimeMillis(), limit: Int = 20): List<OutboxMessageEntity>

    // Circuit Breaker state
    @Query("SELECT COUNT(*) FROM outbox_messages WHERE status = 'SENDING' AND nextAttemptAt < :now")
    suspend fun countStuckSending(now: Long = System.currentTimeMillis()): Int

    // Media cleanup
    @Query("DELETE FROM outbox_messages WHERE status = 'SENT' AND createdAt < :before AND (mediaType IS NOT NULL OR localMediaPath IS NOT NULL)")
    suspend fun cleanupMedia(before: Long): Int
}
