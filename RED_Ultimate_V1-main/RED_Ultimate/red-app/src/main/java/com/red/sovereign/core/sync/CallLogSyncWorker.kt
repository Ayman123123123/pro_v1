package com.red.sovereign.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.calls.CallHistoryItem
import com.red.sovereign.calls.CallLogCipher
import com.red.sovereign.calls.computedDurationSeconds
import com.red.sovereign.calls.parseCallTimestamp
import com.red.sovereign.core.database.CallLogEntity
import com.red.sovereign.core.database.LocalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.pow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

/**
 * CallLogSyncWorker — Offline-first call log synchronization with WorkManager.
 *
 * Features:
 * - Periodic sync (every 15 minutes when online)
 * - Incremental sync using lastSyncedAt timestamp
 * - Conflict resolution: server wins (authoritative)
 * - Optimistic local writes, background reconciliation
 * - Exponential backoff on failures
 * - Battery-aware scheduling (WorkManager)
 *
 * Architecture:
 * 1. Local DB (Room) is source of truth for UI
 * 2. Worker fetches server changes since lastSync
 * 3. Merges server changes into local DB (server wins)
 * 4. Pushes local pending changes to server
 * 5. Updates lastSyncedAt timestamp
 */
class CallLogSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val repository = LocalRepository(applicationContext)
    private val tokens = TokenStore(applicationContext)
    private val api = CallHistoryApi(tokens)
    private val json = Json { ignoreUnknownKeys = true }

    private val SYNC_INTERVAL_MINUTES = 15
    private val MAX_RETRY_COUNT = 3
    private val BASE_BACKOFF_SECONDS = 30

    override suspend fun doWork(): Result {
        val attempt = runAttempt
        val lastSync = lastSyncedAt

        return try {
            // Check if we should run (respect minimum interval)
            if (System.currentTimeMillis() - lastSync < TimeUnit.MINUTES.toMillis(SYNC_INTERVAL_MINUTES.toLong())) {
                Result.success() // Too soon, skip
            } else {
                performSync()
                Result.success()
            }
        } catch (e: Exception) {
            val nextAttempt = attempt + 1
            if (nextAttempt >= MAX_RETRY_COUNT) {
                // Max retries exceeded — reschedule with exponential backoff
                scheduleRetry(nextAttempt)
                Result.retry()
            } else {
                Result.retry()
            }
        }
    }

    private suspend fun performSync() {
        val since = lastSyncedAt

        // 1. Pull: Fetch server changes since last sync
        val serverLogs = fetchServerLogs(since)

        // 2. Merge: Apply server changes to local DB (server wins)
        if (serverLogs.isNotEmpty()) {
            mergeServerLogs(serverLogs)
        }

        // 3. Push: Upload local pending changes (created offline)
        pushLocalPendingChanges()

        // 4. Update sync timestamp
        updateLastSyncedAt(System.currentTimeMillis())
    }

    private suspend fun fetchServerLogs(since: Long): List<CallLogEntity> {
        return when (val result = api.getHistory(since = since)) {
            is ApiResult.Success -> runCatching {
                // AUTO-FIX (call log sync): the server returns CallHistoryItem JSON (startedAt is a
                // string), not CallLogEntity - decoding it as an entity always failed, so these sync
                // paths silently imported nothing. Decode the DTO, then map it.
                json.decodeFromString<List<CallHistoryItem>>(result.value).map { it.toSyncedEntity() }
            }.getOrElse { emptyList() }
            is ApiResult.Error -> {
                android.util.Log.w("CallLogSync", "Server fetch failed: ${result.message}")
                emptyList()
            }
        }
    }

    private suspend fun mergeServerLogs(serverLogs: List<CallLogEntity>) {
        // Server wins strategy: upsert all server logs
        // Local-only logs (pending sync) are preserved via separate flag
        repository.saveCallLogs(serverLogs)
    }

    private suspend fun pushLocalPendingChanges() {
        // Collect the Flow to get the list of unsynced logs
        val pendingLogs = repository.getCallLogs().first()
        if (pendingLogs.isEmpty()) return

        for (log in pendingLogs) {
            when (val result = api.push(log)) {
                is ApiResult.Success -> {
                    // Mark as synced
                    repository.saveCallLog(log)
                }
                is ApiResult.Error -> {
                    android.util.Log.w("CallLogSync", "Push failed for ${log.id}: ${result.message}")
                    // FIX: Break loop on rate limit or connection failure to prevent OkHttp pool exhaustion
                    if (result.message.contains("RATE_LIMIT") || result.message.contains("connect") || result.message.contains("timeout", ignoreCase = true)) {
                        android.util.Log.e("CallLogSync", "Halting sync loop due to network/rate-limit error")
                        break
                    }
                    // Keep pending for next sync
                }
            }
        }
    }

    // ── Persistence helpers ──
    private val runAttempt: Int
        get() = inputData.getInt("runAttempt", 0)

    private val lastSyncedAt: Long
        get() = applicationContext.getSharedPreferences("call_sync_prefs", Context.MODE_PRIVATE)
            .getLong("last_synced_at", 0)

    private fun updateLastSyncedAt(timestamp: Long) {
        applicationContext.getSharedPreferences("call_sync_prefs", Context.MODE_PRIVATE)
            .edit().putLong("last_synced_at", timestamp).apply()
    }

    private fun scheduleRetry(attempt: Int) {
        val backoff = BASE_BACKOFF_SECONDS * (2.0.pow(attempt - 1)).toLong()
        // WorkManager handles retry scheduling automatically via Result.retry()
        // This is just for logging
        android.util.Log.i("CallLogSync", "Scheduling retry $attempt in ${backoff}s")
    }
}

/**
 * Manual sync trigger (e.g. user pulls-to-refresh in CallHistoryScreen).
 */
class ManualCallLogSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val repository = LocalRepository(applicationContext)
    private val tokens = TokenStore(applicationContext)
    private val api = CallHistoryApi(tokens)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun doWork(): Result {
        return try {
            performFullSync()
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    private suspend fun performFullSync() {
        // 1. Full pull from server
        val serverLogs = when (val result = api.getHistory(since = 0)) {
            is ApiResult.Success -> runCatching {
                // AUTO-FIX (call log sync): the server returns CallHistoryItem JSON (startedAt is a
                // string), not CallLogEntity - decoding it as an entity always failed, so these sync
                // paths silently imported nothing. Decode the DTO, then map it.
                json.decodeFromString<List<CallHistoryItem>>(result.value).map { it.toSyncedEntity() }
            }.getOrElse { emptyList() }
            is ApiResult.Error -> emptyList()
        }

        // 2. Full replace (server is authoritative)
        if (serverLogs.isNotEmpty()) {
            repository.saveCallLogs(serverLogs)
        }

        // 3. Update timestamp
        applicationContext.getSharedPreferences("call_sync_prefs", Context.MODE_PRIVATE)
            .edit().putLong("last_synced_at", System.currentTimeMillis()).apply()
    }
}

/**
 * WorkManager scheduling helpers.
 */
object CallLogSyncScheduler {
    private const val PERIODIC_WORK_NAME = "call_log_periodic_sync"
    private const val MANUAL_WORK_NAME = "call_log_manual_sync"

    fun schedulePeriodicSync(context: Context) {
        val workManager = androidx.work.WorkManager.getInstance(context)
        
        val periodicWork = androidx.work.PeriodicWorkRequestBuilder<CallLogSyncWorker>(
            15, TimeUnit.MINUTES
        ).addTag(PERIODIC_WORK_NAME)
            .setBackoffCriteria(
                androidx.work.BackoffPolicy.EXPONENTIAL,
                30, TimeUnit.SECONDS
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            periodicWork
        )
    }

    fun triggerManualSync(context: Context) {
        val workManager = androidx.work.WorkManager.getInstance(context)
        val work = androidx.work.OneTimeWorkRequestBuilder<ManualCallLogSyncWorker>()
            .addTag(MANUAL_WORK_NAME)
            .build()
        workManager.enqueue(work)
    }

    fun cancelPeriodicSync(context: Context) {
        androidx.work.WorkManager.getInstance(context)
            .cancelUniqueWork(PERIODIC_WORK_NAME)
    }

    /**
     * حالة المزامنة الدورية الحالية.
     *
     * كانت تقرأ `getWorkInfosByTagLiveData(...).value` — وهذا خطأ مزدوج:
     * يجرّ `runtime-livedata` كاعتماد كامل من أجل استدعاء واحد، و`value`
     * على LiveData غير مُراقَب يكون `null` دائماً تقريباً فتُرجع الدالة
     * `null` مهما كانت الحالة الحقيقية. `getWorkInfosByTag` المتزامنة
     * تُرجع القيمة الفعلية.
     *
     * الوسم يُقرأ من [PERIODIC_WORK_NAME] لا من نص مكرر: النص الحرفي
     * السابق `"call_log_periodic_sync"` كان ينفصل بصمت لو تغيّر الثابت.
     */
    fun getSyncStatus(context: Context): androidx.work.WorkInfo.State? {
        return runCatching {
            androidx.work.WorkManager.getInstance(context)
                .getWorkInfosByTag(PERIODIC_WORK_NAME)
                .get()
                .firstOrNull()
                ?.state
        }.getOrNull()
    }
}

/**
 * CallHistoryApi extensions for sync.
 */
/** AUTO-FIX (call log sync): shared mapper used by both sync workers. */
private val callLogSyncCipher = CallLogCipher()

/** Maps a server DTO into the local Room entity, keeping the peer identity encrypted at rest. */
private fun CallHistoryItem.toSyncedEntity(): CallLogEntity = CallLogEntity(
    id = id,
    peerId = callLogSyncCipher.encryptPeerId(peerId),
    peerLabel = callLogSyncCipher.encryptLabel(peerLabel),
    type = type,
    direction = direction,
    route = route,
    status = status,
    timestamp = parseCallTimestamp(startedAt) ?: System.currentTimeMillis(),
    durationMs = computedDurationSeconds() * 1000L,
    answeredAt = parseCallTimestamp(answeredAt),
    endedAt = parseCallTimestamp(endedAt)
)

class CallHistoryApi(tokens: TokenStore) {
    private val client = AuthorizedApiClient(tokens)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getHistory(since: Long = 0, limit: Int = 100): ApiResult<String> {
        val sinceParam = if (since > 0) "&since=$since" else ""
        return client.request("GET", "/api/calls/history?limit=$limit$sinceParam", "")
    }

    suspend fun push(log: CallLogEntity): ApiResult<Boolean> {
        // AUTO-FIX (call log sync): the old body was an empty string, so offline-created call logs
        // were never uploaded. Send the real record (peer identity decrypted on the wire; the
        // server stores caller/callee ids in clear by design).
        val cipher = CallLogCipher()
        val payload = CallHistoryItem(
            id = log.id,
            peerId = cipher.decryptPeerId(log.peerId),
            peerLabel = cipher.decryptLabel(log.peerLabel),
            direction = log.direction,
            type = log.type,
            route = log.route,
            status = log.status,
            startedAt = log.timestamp.toString(),
            answeredAt = log.answeredAt?.toString(),
            endedAt = log.endedAt?.toString(),
            durationSeconds = log.durationMs / 1000L
        )
        val body = json.encodeToString(ListSerializer(CallHistoryItem.serializer()), listOf(payload))
        return when (val result = client.request("POST", "/api/calls/history/sync", body)) {
            is ApiResult.Success -> ApiResult.Success(result.code, true)
            is ApiResult.Error -> ApiResult.Error(result.code, result.message)
        }
    }
}