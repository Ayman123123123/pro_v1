package com.red.sovereign.core.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.red.sovereign.core.RichMessage
import com.red.sovereign.core.database.RedDatabase
import java.util.concurrent.TimeUnit

/**
 * LEGENDARY: عامل الرسائل المؤقتة — يحذف المنتهية محلياً (expiresAt داخل RichMessage).
 * الخادم ينظف سحابياً بالجملة كل 5 دقائق؛ هذا ينظف SQLCipher المحلي كل ساعة
 * (المؤقتة تعيش ساعات/أيام فلا حاجة لإيقاظ كل 15 دقيقة).
 */
class MessageCleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        val dao = RedDatabase.getInstance(applicationContext).redDao()
        val now = System.currentTimeMillis()
        val all = dao.allRichHistory()
        var removed = 0
        for (entity in all) {
            val expiresAt = runCatching { RichMessage.decode(entity.encryptedPlaintext)?.expiresAt }.getOrNull()
            if (expiresAt != null && expiresAt <= now) {
                runCatching { dao.deleteLocalHistory(entity.id) }.onSuccess { removed++ }
            }
            // حماية البطارية: حد 500 فحص لكل دورة
            if (removed >= 500) break
        }
        if (removed > 0) Log.i(TAG, "حُذفت $removed رسالة مؤقتة منتهية")
        Result.success()
    }.getOrElse { error ->
        Log.w(TAG, "فشل تنظيف المؤقتة — ستُعاد المحاولة", error)
        Result.retry()
    }

    companion object {
        private const val TAG = "MessageCleanupWorker"
        private const val UNIQUE_NAME = "message-cleanup"
        private const val INTERVAL_HOURS = 1L

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<MessageCleanupWorker>(
                INTERVAL_HOURS, TimeUnit.HOURS
            ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
