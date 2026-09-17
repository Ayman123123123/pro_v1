package com.red.sovereign.core.workers

import android.content.Context
import android.net.Uri
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
import androidx.work.workDataOf
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.core.RedConnectionService
import com.red.sovereign.core.database.MediaUploadEntity
import com.red.sovereign.core.database.RedDatabase
import com.red.sovereign.media.EncryptedAttachmentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * LEGENDARY: عامل رفع الوسائط المتين — مزامنة كل الملفات الحديثة 1:1.
 *
 * المشكلة: [AttachmentViewModel.send] كان يشفر ويرفع على Main ثم يرسل مباشرة —
 * قتل العملية أثناء الرفع = فقدان الملف نهائياً (بلا outbox للوسائط).
 * الحل: الملف يُنسخ مرحلياً + صف PENDING في `media_uploads` + هذا العامل
 * (CONNECTED + مستأنف بعد reboot) يضغط/يشفر/يرفع/يرسل، ثم يحذف الصف والملف المرحلي.
 *
 * النطاق: محادثات 1:1 فقط (targetRedId != null). مرفقات المجموعات تبقى
 * مساراً مباشراً (تحتاج كائن Group الحي) — موثقة لا منسية.
 * الرسائل الصوتية تبقى مسار VoiceMessageViewModel المباشر (IO + بلا حذف عند الفشل).
 */
class MediaUploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = RedDatabase.getInstance(applicationContext)
        val dao = db.mediaUploadDao()
        val now = System.currentTimeMillis()

        // الأولوية للرسالة المطلوبة صراحةً، ثم مسح المعلق (مزامنة الحديثة كلها)
        val ordered = LinkedHashMap<String, MediaUploadEntity>()
        inputData.getString(KEY_MESSAGE_ID)?.let { id ->
            runCatching { dao.byId(id) }.getOrNull()?.let { ordered[it.messageId] = it }
        }
        runCatching { dao.due(now) }.getOrDefault(emptyList()).forEach { ordered.putIfAbsent(it.messageId, it) }
        if (ordered.isEmpty()) return@withContext Result.success()

        var needsRetry = false
        for (entity in ordered.values) {
            when (processOne(entity)) {
                Outcome.DONE -> Unit
                Outcome.RETRY_LATER -> needsRetry = true
            }
            if (runAttemptCount > 6) break // حماية البطارية
        }
        if (needsRetry && runAttemptCount < 6) Result.retry() else Result.success()
    }

    private suspend fun processOne(entity: MediaUploadEntity): Outcome {
        val db = RedDatabase.getInstance(applicationContext)
        val dao = db.mediaUploadDao()
        val target = entity.targetRedId
        if (target.isNullOrBlank()) {
            // مسار المجموعات مباشر — لا يجب أن يصل هنا؛ احذف لمنع الانسداد مع سجل
            Log.w(TAG, "skip non-1:1 upload ${entity.messageId} (group path is direct)")
            runCatching { dao.delete(entity.messageId) }
            return Outcome.DONE
        }
        val staged = File(entity.localPath)
        if (!staged.isFile || staged.length() <= 0) {
            // الملف المرحلي ضاع — لن ينجح أبداً؛ احذف لمنع الانسداد
            Log.w(TAG, "staged file gone for ${entity.messageId}, dropping")
            runCatching { dao.delete(entity.messageId) }
            return Outcome.DONE
        }
        return try {
            val tokens = TokenStore(applicationContext)
            val repo = EncryptedAttachmentRepository(applicationContext, AuthorizedApiClient(tokens))
            val uri = Uri.fromFile(staged)
            when (val prepared = repo.prepare(uri, target)) {
                is ApiResult.Error -> {
                    fail(entity, prepared.message)
                    Outcome.RETRY_LATER
                }
                is ApiResult.Success -> {
                    val type = when {
                        prepared.value.mimeType.startsWith("image/") -> "IMAGE"
                        prepared.value.mimeType.startsWith("video/") -> "VIDEO"
                        prepared.value.mimeType.startsWith("audio/") -> "AUDIO"
                        else -> "FILE"
                    }
                    // الإرسال عبر الخدمة (طابور WS + outbox نصي للحمولة) بمفتاح عدم التكرار نفسه
                    RedConnectionService.sendPayload(
                        applicationContext, target, entity.conversationId, type,
                        prepared.value.manifestJson.toByteArray(Charsets.UTF_8),
                        entity.messageId
                    )
                    runCatching { dao.delete(entity.messageId) }
                    runCatching { staged.delete() }
                    Log.i(TAG, "uploaded ${entity.messageId} ($type)")
                    Outcome.DONE
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "upload failed for ${entity.messageId}", t)
            fail(entity, t.message ?: "UPLOAD_FAILED")
            Outcome.RETRY_LATER
        }
    }

    private suspend fun fail(entity: MediaUploadEntity, error: String) {
        val dao = RedDatabase.getInstance(applicationContext).mediaUploadDao()
        val next = entity.retryCount + 1
        // حد المحاولات + DLQ: بلا هذا كانت الوسائط الفاشلة دائمًا تُعاد للأبد
        // (BACKOFF يقف عند 24h لكن retryCount بلا سقف فيملأ الطابور ويستنزف البطارية).
        if (next > MAX_ATTEMPTS) {
            Log.e(TAG, "upload ${entity.messageId} exceeded $MAX_ATTEMPTS attempts ($error) — DLQ drop")
            runCatching { dao.delete(entity.messageId) }
            runCatching { File(entity.localPath).delete() }
            return
        }
        val delay = BACKOFF.getOrElse(entity.retryCount) { 86_400_000L }
        Log.w(TAG, "upload ${entity.messageId} failed ($error), retry $next")
        runCatching {
            dao.mark(entity.messageId, "FAILED", next, System.currentTimeMillis() + delay, entity.objectKey, entity.url)
        }
    }

    private enum class Outcome { DONE, RETRY_LATER }

    // LEGENDARY FIX (مكتشف على الجهاز): setExpedited يتطلب getForegroundInfo وإلا انهيار
    // IllegalStateException: Not implemented عند أول تشغيل.
    override suspend fun getForegroundInfo(): androidx.work.ForegroundInfo {
        val channelId = "red_uploads"
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            val nm = applicationContext.getSystemService(android.app.NotificationManager::class.java)
            if (nm?.getNotificationChannel(channelId) == null) {
                nm?.createNotificationChannel(
                    android.app.NotificationChannel(channelId, "رفع الوسائط", android.app.NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
        val notif = androidx.core.app.NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("جارٍ رفع الوسائط…")
            .setOngoing(true)
            .build()
        return if (android.os.Build.VERSION.SDK_INT >= 29) {
            androidx.work.ForegroundInfo(NOTIF_ID, notif, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            androidx.work.ForegroundInfo(NOTIF_ID, notif)
        }
    }

    companion object {
        private const val NOTIF_ID = 2101
        private const val TAG = "MediaUploadWorker"
        private const val UNIQUE_WORK_NAME = "red-media-upload"
        private const val KEY_MESSAGE_ID = "messageId"
        /** سقف المحاولات للملف الواحد (موائم لعتبة outbox DLQ=10) قبل الإسقاط. */
        private const val MAX_ATTEMPTS = 10
        private val BACKOFF = longArrayOf(10_000L, 30_000L, 120_000L, 600_000L, 3_600_000L, 86_400_000L)

        /** يُستدعى بعد كل إدراج + عند الإقلاع لمسح المعلق (مزامنة الحديثة). */
        fun enqueue(context: Context, messageId: String? = null) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresStorageNotLow(true)
                .build()
            val builder = OneTimeWorkRequestBuilder<MediaUploadWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            if (messageId != null) builder.setInputData(workDataOf(KEY_MESSAGE_ID to messageId))
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                builder.build()
            )
        }
    }
}
