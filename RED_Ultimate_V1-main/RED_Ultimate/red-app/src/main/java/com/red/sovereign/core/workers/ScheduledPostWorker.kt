package com.red.sovereign.core.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.red.sovereign.MainActivity
import com.red.sovereign.R
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.social.CreatePostRequest
import com.red.sovereign.social.FeedApi
import com.red.sovereign.social.ScheduledPostsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * عامل النشر المجدول (2026-09-10 · يتفوق على المنافسين).
 *
 * ينشر منشورًا نصيًا/استطلاعًا في موعده عبر FeedApi مباشرة (بلا ViewModel —
 * الـ Worker يعيش خارج دورة الواجهة). القيود: CONNECTED + backoff أُسّي
 * 30s. عند النجاح يحذف الإدخال من SharedPreferences عبر
 * [ScheduledPostsStore.markDelivered]؛ عند الفشل retry (لا failure — وإلا
 * ضاع المنشور المجدول نهائيًا).
 *
 * ملاحظة: الوسائط غير مدعومة V1 (تتطلب persistable Uri) — الجدولة نص/استطلاع.
 */
class ScheduledPostWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getString("scheduled_id") ?: run {
            Log.e(TAG, "missing scheduled_id — failing without retry")
            notifyFailure(applicationContext, "؟", "بيانات الجدولة ناقصة — أعد إنشاء المنشور")
            return@withContext Result.failure()
        }
        val rawText = inputData.getString("text")?.trim().orEmpty()
        val hasMedia = (inputData.getStringArray("media_uris")?.isNotEmpty() == true)
        // المخزن يسمح بنص فارغ مع وسائط — لا تسقطه بصمت، بل انشر بعنصر نائب
        // (نفس سلوك createWithMedia). فارغ بلا وسائط = لا شيء للنشر.
        val text = rawText.ifBlank { if (hasMedia) "📷" else "" }
        if (text.isBlank()) {
            if (!ScheduledPostsStore.markDelivered(applicationContext, id)) {
                Log.w(TAG, "markDelivered failed for blank $id — retrying")
                return@withContext Result.retry()
            }
            return@withContext Result.success()
        }
        val visibility = inputData.getString("visibility")?.ifBlank { "PUBLIC" } ?: "PUBLIC"
        val pollOptions = inputData.getStringArray("poll_options")?.filter { it.trim().length >= 2 } ?: emptyList()
        val pollHours = inputData.getInt("poll_hours", -1).takeIf { it > 0 }

        return@withContext runCatching {
            val client = AuthorizedApiClient(TokenStore(applicationContext))
            val api = FeedApi(client)
            val request = if (pollOptions.size >= 2) {
                CreatePostRequest(
                    text = text,
                    visibility = visibility,
                    pollOptions = pollOptions.take(6),
                    pollDurationHours = pollHours?.coerceIn(1, 168)
                )
            } else {
                CreatePostRequest(text = text, visibility = visibility)
            }
            when (val result = api.create(request)) {
                is com.red.sovereign.auth.ApiResult.Success -> {
                    if (!ScheduledPostsStore.markDelivered(applicationContext, id)) {
                        // الحذف التسليمي فشل (قرص) — retry آمن بدل success يفقد الأثر.
                        Log.w(TAG, "markDelivered failed for $id — retrying")
                        return@withContext Result.retry()
                    }
                    Log.i(TAG, "نُشر المنشور المجدول $id")
                    Result.success()
                }
                is com.red.sovereign.auth.ApiResult.Error -> {
                    Log.w(TAG, "فشل النشر المجدول $id: ${result.message} — إعادة")
                    // تنبيه واحد فقط عند أول فشل؛ تكرار التنبيه كل retry = إزعاج.
                    if (runAttemptCount == 0) notifyFailure(applicationContext, text, result.message)
                    Result.retry()
                }
            }
        }.getOrElse { e ->
            Log.w(TAG, "استثناء النشر المجدول $id — إعادة", e)
            if (runAttemptCount == 0) notifyFailure(applicationContext, text, e.message ?: "خطأ غير متوقع")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "ScheduledPostWorker"
        private const val CHANNEL_ID = "red_scheduled_posts"

        /** إشعار فشل النشر المجدول — حتى لا يضيع الفشل بصمت عند تجاهل الإشعار السابق. */
        fun notifyFailure(context: Context, text: String, reason: String) {
            runCatching {
                val manager = NotificationManagerCompat.from(context)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    manager.createNotificationChannel(
                        NotificationChannel(CHANNEL_ID, "المنشورات المجدولة", NotificationManager.IMPORTANCE_DEFAULT)
                    )
                }
                val content = PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.sym_action_chat)
                    .setContentTitle("تعذر نشر منشور مجدول")
                    .setContentText(reason)
                    .setStyle(NotificationCompat.BigTextStyle().bigText("${text.take(140)}\n$reason"))
                    .setContentIntent(content)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .build()
                if (Build.VERSION.SDK_INT < 33 ||
                    ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                ) {
                    manager.notify(text.hashCode(), notif)
                }
            }
        }
    }
}
