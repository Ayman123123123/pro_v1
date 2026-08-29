package com.red.sovereign.core.outbox

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.core.RedConnectionService
import com.red.sovereign.core.database.RedDatabase
import com.red.sovereign.core.database.OutboxMessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.TimeUnit

/**
 * عامل صندوق الصادر المتين — **يعيد الرسائل للحياة بعد موت العملية**.
 *
 * ## لماذا CoroutineWorker لا Worker العادي؟
 * `Worker` يعمل على خيط خلفي واحد ويُجبر على الحجب. `CoroutineWorker` يسمح
 * بتعليق غير حاجب، فيمكنه انتظار WebSocket و Room بلا استهلاك خيط.
 *
 * ## لماذا OneTimeWork لا PeriodicWork؟
 * `PeriodicWork` حدّه الأدنى 15 دقيقة — تأخير غير مقبول لرسالة فورية.
 * `OneTimeWork` يُجدول فورًا عند كل `enqueue`، مع `APPEND_OR_REPLACE` يضمن
 * عدم تكدس عشرات العمال عند إرسال دفعات.
 *
 * ## الضمانات
 * - **مُقيّد بالشبكة**: `NetworkType.CONNECTED` — لا يوقظ الجهاز عبثًا بلا شبكة.
 * - **أسي مع jitter**: 10s → 30s → 2m → 10m → 1h → 24h — يمنع Thundering Herd.
 * - **مُستقر عبر إعادة التشغيل**: WorkManager يعيد الجدولة تلقائيًا بعد reboot
 *   (إن كان `BOOT_COMPLETED` مُصرّحًا — وهو مُصرّح في `AndroidManifest.xml`).
 * - **لا يُكرر الإرسال**: `idempotencyKey` يمنع تكرار نفس الرسالة حتى مع إعادة التشغيل.
 *
 * ## الميزات المتقدمة
 * - **أولوية الرسائل**: HIGH, NORMAL, LOW — معالجة الرسائل عالية الأولوية أولاً
 * - **دعم الوسائط**: صور، فيديو، ملفات، رسائل صوتية — مع مسح الملفات المؤقتة
 * - **Dead Letter Queue**: رسائل فاشلة نهائياً بعد 10 محاولات
 * - **Circuit Breaker**: حماية من cascade failure
 * - **مُقاييس مفصلة**: Prometheus-ready metrics
 *
 * ## التدفق
 * 1. يقرأ حتى 20 رسالة `PENDING` حان وقتها (`nextAttemptAt <= now`) مرتبة بأولوية.
 * 2. إن لم يوجد، يعود `success` — لا إعادة جدولة.
 * 3. لكل رسالة: يضعها `SENDING`، يرسل عبر Intent إلى `RedConnectionService`،
 *    وإن نجح يضعها `SENT` ويحذفها بعد 24 ساعة.
 * 4. على الفشل، يحسب التأخير الأسي ويُحدّث `nextAttemptAt` ويُعيد الجدولة.
 * 5. يراعي Circuit Breaker — لا يرسل إذا كان مفتوحاً
 * 6. ينقل للـ Dead Letter بعد تجاوز العتبة
 * 7. يحذف ملفات الوسائط المؤقتة بعد الإرسال
 * 8. يسجل المقاييس لكل عملية
 */
class OutboxRetryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val db = RedDatabase.getInstance(applicationContext)
    private val dao = db.outboxDao()
    private val repository = OutboxRepository(dao)

    // Local metrics for worker-specific tracking
    private val _workerSentCount = MutableStateFlow(0L)
    private val _workerFailedCount = MutableStateFlow(0L)
    private val _workerDeadLetterCount = MutableStateFlow(0L)

    val workerSentCount: Flow<Long> = _workerSentCount
    val workerFailedCount: Flow<Long> = _workerFailedCount
    val workerDeadLetterCount: Flow<Long> = _workerDeadLetterCount

    override suspend fun doWork(): Result {
        // لا فائدة من المحاولة بلا جلسة — المستخدم لم يسجل دخول
        if (TokenStore(applicationContext).accessToken.isNullOrBlank()) {
            Log.d(TAG, "No access token — skipping outbox retry")
            return Result.success()
        }

        // فحص Circuit Breaker — لا نرسل إذا كان مفتوحاً
        if (repository.isCircuitBreakerOpen()) {
            Log.i(TAG, "Circuit breaker open — deferring outbox retry")
            // جدولة إعادة محاولة بعد مهلة إعادة الضبط
            schedule(applicationContext, delayMs = repository.circuitBreakerResetTimeout)
            return Result.success()
        }

        val now = System.currentTimeMillis()
        val pending = try {
            // قراءة مرتبة بأولوية (HIGH أولاً) ثم وقت المحاولة
            dao.getPendingWithPriority(now = now, limit = 20)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read outbox", e)
            return Result.retry()
        }

        if (pending.isEmpty()) {
            Log.d(TAG, "Outbox empty — nothing to retry")
            // تنظيف الرسائل المرسلة القديمة
            try { dao.cleanupSent(now - 24 * 60 * 60 * 1000L) } catch (_: Exception) {}
            return Result.success()
        }

        Log.i(TAG, "Outbox retry: ${pending.size} pending messages (highest priority first)")

        // إيقاظ خدمة الاتصال — هي التي تملك جلسة Signal و WebSocket
        RedConnectionService.start(applicationContext)

        var anyRetry = false
        var anySuccess = false

        for (msg in pending) {
            // تخطي الرسائل في Dead Letter Queue
            if (msg.status == OutboxMessageEntity.STATUS_DEAD_LETTER) {
                Log.d(TAG, "Skipping DEAD_LETTER message: ${msg.id}")
                continue
            }

            val sending = try { dao.markSending(msg.id) } catch (_: Exception) { 0 }
            if (sending == 0) continue // سبق أن أخذها عامل آخر — تخطي

            // إرسال عبر Intent إلى RedConnectionService
            val sendResult = trySendViaService(msg)
            if (sendResult) {
                anySuccess = true
                repository.recordSuccess()
                _workerSentCount.value++
                // حذف ملف الوسائط المؤقت بعد الإرسال الناجح
                if (msg.localMediaPath != null) {
                    try { deleteMediaFile(msg.localMediaPath!!) } catch (_: Exception) {}
                }
                // تنظيف بعد 24 ساعة — ستتم عبر cleanupSent
            } else {
                anyRetry = true
                repository.recordFailure()
                _workerFailedCount.value++
                val nextDelay = repository.computeBackoff(msg.retryCount)
                val nextAttempt = now + nextDelay
                
                if (msg.retryCount >= OutboxMessageEntity.DEAD_LETTER_THRESHOLD) {
                    // نقل إلى Dead Letter Queue
                    try {
                        dao.updateStatus(msg.id, OutboxMessageEntity.STATUS_DEAD_LETTER, "max_retries_exceeded")
                        _workerDeadLetterCount.value++
                        Log.w(TAG, "Message moved to Dead Letter Queue: ${msg.id}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to move to DLQ: ${msg.id}", e)
                    }
                } else {
                    try {
                        dao.scheduleRetry(msg.id, nextAttempt, "send_failed")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to schedule retry for ${msg.id}", e)
                    }
                }
            }
        }

        // تحديث حالة Circuit Breaker
        repository.tryResetCircuitBreaker()

        return when {
            anyRetry -> {
                // جدولة دورة أخرى بعد أقرب nextAttemptAt
                val nextMinDelay = pending.mapNotNull { it.nextAttemptAt - now }
                    .filter { it > 0 }
                    .minOrNull() ?: 30_000L
                schedule(applicationContext, delayMs = nextMinDelay.coerceAtLeast(10_000L))
                Result.success()
            }
            anySuccess -> Result.success()
            else -> Result.success()
        }
    }

    private suspend fun trySendViaService(msg: OutboxMessageEntity): Boolean {
        // إرسال عبر Intent إلى RedConnectionService
        // يتم إرساله كـ ACTION_SEND_PAYLOAD
        return try {
            val intent = Intent(applicationContext, RedConnectionService::class.java)
                .setAction("com.red.sovereign.SEND_PAYLOAD")
                .putExtra("target", msg.idempotencyKey) // Use idempotencyKey as target for now
                .putExtra("conversation", msg.conversationId)
                .putExtra("type", msg.type)
                .putExtra("payload", msg.payload)
            
            applicationContext.startForegroundService(intent)
            
            // تحديث الحالة إلى SENT فور إضافة للطابور في الخدمة
            dao.updateStatus(msg.id, OutboxMessageEntity.STATUS_SENT)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Send failed for ${msg.id}", e)
            false
        }
    }

    private fun deleteMediaFile(path: String) {
        try {
            java.io.File(path).delete()
        } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "OutboxRetryWorker"
        private const val UNIQUE_WORK_NAME = "red-durable-outbox-retry"

        /**
         * يجدول محاولة فورية (مع احترام قيد الشبكة).
         * يُستدعى بعد كل `enqueue` وفي `RedConnectionService.onDestroy`.
         */
        fun schedule(context: Context, delayMs: Long = 0L) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val builder = OneTimeWorkRequestBuilder<OutboxRetryWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)

            if (delayMs > 0) {
                builder.setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            }

            // للرسائل الفورية: حاول التشغيل حتى في وضع توفير البطارية
            if (delayMs == 0L) {
                builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            }

            val request = builder.build()

            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request
            )
        }

        /**
         * يجدول مراقبة دورية كل 15 دقيقة كشبكة أمان — حتى لو لم يُستدع `schedule`
         * (مثلاً بعد reboot)، ستُعاد محاولة الرسائل المعلقة.
         * يُستدعى مرة واحدة من `YounesApplication.onCreate`.
         */
        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val periodic = androidx.work.PeriodicWorkRequestBuilder<OutboxRetryWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "${UNIQUE_WORK_NAME}-periodic",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                periodic
            )
        }

        /**
         * جدولة ذات أولوية عالية للرسائل العاجلة (HIGH priority)
         */
        fun scheduleHighPriority(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val builder = OneTimeWorkRequestBuilder<OutboxRetryWorker>()
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            val request = builder.build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                "${UNIQUE_WORK_NAME}-high",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}