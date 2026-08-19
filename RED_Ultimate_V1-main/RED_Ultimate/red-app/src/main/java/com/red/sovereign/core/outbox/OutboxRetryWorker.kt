package com.red.sovereign.core.outbox

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.core.RedConnectionService
import java.util.concurrent.TimeUnit

/**
 * يوقظ خدمة الاتصال لاستئناف Outbox بعد إنهاء العملية أو انقطاع الشبكة.
 * لا ينفذ التشفير أو النقل هنا؛ تبقى تلك العملية داخل الخدمة الأمامية التي
 * تملك جلسة Signal وWebSocket، ويضمن القيد الشبكي ألا يبدأ بلا اتصال.
 */
class OutboxRetryWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        if (TokenStore(applicationContext).accessToken.isNullOrBlank()) return Result.success()
        return runCatching {
            RedConnectionService.start(applicationContext)
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "red-durable-outbox-retry"

        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<OutboxRetryWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
