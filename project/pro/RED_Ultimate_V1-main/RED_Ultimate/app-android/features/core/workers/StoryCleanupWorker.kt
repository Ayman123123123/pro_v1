package com.red.core.workers

import android.content.Context
import androidx.work.*
import com.red.core.database.REDDatabase
import java.util.concurrent.TimeUnit

class StoryCleanupWorker(
    context: Context,
    workerParams: WorkerParameters,
    private val database: REDDatabase
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val currentTime = System.currentTimeMillis()
        database.storyDao().cleanupExpired(currentTime)
        return Result.success()
    }

    companion object {
        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<StoryCleanupWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "StoryCleanup",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
