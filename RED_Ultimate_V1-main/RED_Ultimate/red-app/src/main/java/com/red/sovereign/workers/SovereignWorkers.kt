package com.red.sovereign.workers

import android.content.Context
import androidx.work.*
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.core.ConnectionStatusRepository
import com.red.sovereign.crypto.PreKeyPoolManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * عمال الخلفية السياديون لتطبيق RED.
 *
 * يشمل:
 * - [MediaCleanupWorker]      — تنظيف الوسائط المؤقتة القديمة
 * - [PreKeyRefreshWorker]     — تجديد مفاتيح التشفير Signal Protocol تلقائياً
 * - [ConnectionGuardWorker]   — حارس الاتصال: يعيد دفع الخدمة عند الانقطاع الطويل
 *
 * ─── قرار معماري موثق 2026-09-26 ───
 * أُزيل [MessageRetryWorker] الهيكلي (كان TODO فارغاً): إعادة إرسال الرسائل
 * نظام متكامل موجود في [OutboxRetryWorker] (قاطع دائرة + إحياء عالق SENDING +
 * foreground expedited) ويُجدول تلقائياً بعد كل enqueue ومن MessageRecoveryWorker —
 * نسخة دورية موازية كانت تكراراً يعمل بلا فائدة كل 15 دقيقة.
 *
 * أُزيل [PresenceHeartbeatWorker] الدوري أيضاً: الحضور على السيرفر مدار من
 * دورة WebSocket نفسها (RedMasterHandler.touchPresence لكل إطار + last_seen عند
 * الاتصال/الفصل فقط). نبضة عميلية دورية كانت ستكتب في red:presence:index دون
 * اتصال حي فتصنع حالة «متصل» زائفًا تخدع جهات الاتصال. بديلها الصحيح:
 * [ConnectionGuardWorker] الذي يراقب الحالة الحقيقية ويعيد إيقاظ الخدمة عند
 * الانقطاع الطويل دون لمس فهرس الحضور إطلاقاً.
 *
 * التسجيل عبر [SovereignWorkManager.scheduleAll] من YounesApplication.onCreate.
 */

// ─────────────────────────────────────────────────────────────
// 1. Media Cleanup Worker
// ─────────────────────────────────────────────────────────────

/** يحذف الوسائط المؤقتة الأقدم من 24 ساعة من مجلد cache التطبيق. */
class MediaCleanupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            val cacheDir = applicationContext.cacheDir
            val cutoffMs = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24)
            var deletedCount = 0
            cacheDir.walkTopDown()
                .filter { it.isFile && it.lastModified() < cutoffMs }
                .filter { it.extension in setOf("jpg", "jpeg", "png", "mp4", "webm", "tmp") }
                .forEach { file ->
                    if (file.delete()) deletedCount++
                }
            Result.success(workDataOf("deleted_files" to deletedCount))
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 2. PreKey Refresh Worker — موصول بمدير المخزون الحقيقي
// ─────────────────────────────────────────────────────────────

/**
 * يراقب مستوى مفاتيح One-Time PreKey (EC + Kyber) ويجدّدها عند نقصها.
 *
 * الربط الحقيقي: [PreKeyPoolManager.replenishIfNeeded] يفحص المخزون عبر
 * GET /api/devices/{id}/prekeys/stock ويولّد ويرفع الدفعة تلقائياً إن هبط
 * تحت الحد الموصى به — نفس المسار الذي تستدعيه الخدمة عند CONNECTED، وهنا
 * كشبكة أمان دورية كل 6 ساعات للحالات التي بقي الجهاز فيها بلا اتصال طويلاً.
 */
class PreKeyRefreshWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            when (val result = PreKeyPoolManager(applicationContext).replenishIfNeeded()) {
                is ApiResult.Success -> Result.success(
                    workDataOf(
                        "ec_available" to result.value.ecAvailable,
                        "kyber_available" to result.value.kyberAvailable
                    )
                )
                is ApiResult.Error -> if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 3. Connection Guard Worker — بديل نبضة الحضور المضللة
// ─────────────────────────────────────────────────────────────

/**
 * حارس الاتصال الدوري — لا يكتب في فهرس الحضور إطلاقاً.
 *
 * وظيفته الوحيدة: قراءة حالة الاتصال الحقيقية من [ConnectionStatusRepository]
 * (التي تغذيها RedConnectionService وحدها)، وإن كانت الخدمة OFFLINE رغم توفر
 * الشبكة يوقظ مسار الاستعادة [ACTION_REDRIVE_STUCK] الذي يدفع الرسائل العالقة
 * ويطلب socket.connect() — idempotent بذات clientId فلا تكرار إرسال.
 *
 * هذا يحل الحالة الوحيدة التي كان «النبض» يُفترض أن يعالجها فعلاً: بقاء الخدمة
 * ساكتة في OFFLINE بعد موت العملية دون مؤقت إعادة اتصال حي.
 */
class ConnectionGuardWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            val status = ConnectionStatusRepository.status.value
            if (status.state == ConnectionStatusRepository.ServerUiState.OFFLINE) {
                // إيقاظ آمن: onStartCommand(ACTION_REDRIVE_STUCK) يعيد الدفع
                // ويتصل إن لم يكن متصلاً — بلا إعادة إنشاء الخدمة.
                val intent = android.content.Intent(applicationContext, com.red.sovereign.core.RedConnectionService::class.java)
                    .setAction(com.red.sovereign.core.RedConnectionService.ACTION_REDRIVE_STUCK)
                runCatching { applicationContext.startService(intent) }
                Result.success(workDataOf("action" to "redrive_requested", "last_online_at" to status.lastOnlineAtMs))
            } else {
                Result.success(workDataOf("state" to status.state.name))
            }
        } catch (e: Exception) {
            // startService قد يُرفض في الخلفية على أندرويد الحديث — ليست فشلاً:
            // RedConnectionService نفسه START_STICKY وسيعود بنفسه.
            Result.success(workDataOf("action" to "skipped", "reason" to (e.message?.take(80) ?: "unknown")))
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 4. Sovereign Work Manager — المُجدوِل المركزي
// ─────────────────────────────────────────────────────────────

/**
 * يُجدوِل جميع الـ Workers الدورية عند إطلاق التطبيق.
 *
 * استدعِه من [Application.onCreate]:
 * ```kotlin
 * SovereignWorkManager.scheduleAll(applicationContext)
 * ```
 */
object SovereignWorkManager {

    fun scheduleAll(context: Context) {
        val wm = WorkManager.getInstance(context)

        // تنظيف الميديا — يومياً (بشرط عدم انخفاض التخزين)
        wm.enqueueUniquePeriodicWork(
            "sovereign_media_cleanup",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<MediaCleanupWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiresStorageNotLow(true).build())
                .build()
        )

        // تجديد PreKeys — كل 6 ساعات (شبكة متصلة)
        wm.enqueueUniquePeriodicWork(
            "sovereign_prekey_refresh",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PreKeyRefreshWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        )

        // حارس الاتصال — كل 15 دقيقة (بشرط الشبكة): يوقظ الخدمة عند الانقطاع الطويل
        wm.enqueueUniquePeriodicWork(
            "sovereign_connection_guard",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ConnectionGuardWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
        )

        // ملاحظة: إعادة إرسال الرسائل لا تُجدول هنا — OutboxRetryWorker يُجدول
        // تلقائياً بعد كل enqueue (APPEND_OR_REPLACE) ومن MessageRecoveryWorker،
        // فأي دورية موازية هنا كانت تكراراً.
    }

    /** إلغاء جميع المهام (عند تسجيل الخروج). */
    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelAllWork()
    }
}
