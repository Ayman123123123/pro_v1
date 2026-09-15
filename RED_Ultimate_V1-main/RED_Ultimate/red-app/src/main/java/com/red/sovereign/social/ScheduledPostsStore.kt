package com.red.sovereign.social

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * ── منشور مجدول (2026-09-10 · يتفوق على المنافسين) ──
 *
 * X يتطلب Premium للجدولة، وواتساب بلا جدولة أصلًا. هنا الجدولة محلية
 * سيادية: تُحفظ في SharedPreferences + WorkManager OneTime بقيود CONNECTED.
 *
 * النطاق V2: نص + استطلاع + وسائط (صور/فيديو/ملفات/صوت).
 * Uri تُحفظ بإذن دائم + نسخة مرحلية في التخزين الخاص.
 */
@Serializable
data class ScheduledMedia(
    val uri: String,
    val mimeType: String,
    val fileName: String? = null
)

@Serializable
data class ScheduledPost(
    val id: String,
    val text: String,
    val visibility: String = "PUBLIC",
    val pollOptions: List<String> = emptyList(),
    val pollDurationHours: Int? = null,
    val scheduledAtMs: Long,
    val createdAtMs: Long = System.currentTimeMillis(),
    val media: List<ScheduledMedia> = emptyList()
)

class ScheduledPostsStore(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("younes_scheduled_posts", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    val items = mutableStateListOf<ScheduledPost>()

    init {
        items.addAll(loadAll().sortedBy { it.scheduledAtMs })
    }

    fun schedule(
        text: String,
        visibility: String = "PUBLIC",
        pollOptions: List<String> = emptyList(),
        pollDurationHours: Int? = null,
        scheduledAtMs: Long,
        media: List<ScheduledMedia> = emptyList()
    ): ScheduledPost? {
        val clean = text.trim()
        if (clean.isBlank() && media.isEmpty()) return null
        if (clean.length > 2000) return null
        if (scheduledAtMs <= System.currentTimeMillis() + 60_000L) return null // أقل من دقيقة → انشر فورًا
        val post = ScheduledPost(
            id = UUID.randomUUID().toString(),
            text = clean,
            visibility = visibility,
            pollOptions = pollOptions,
            pollDurationHours = pollDurationHours,
            scheduledAtMs = scheduledAtMs,
            media = media
        )
        items.add(post)
        items.sortBy { it.scheduledAtMs }
        if (!persist()) {
            // القرص ممتلئ/كتابة فشلت: لا نوهم المستخدم بالجدولة — نتراجع محليًا.
            items.removeAll { it.id == post.id }
            android.util.Log.e("ScheduledPosts", "schedule persist failed — post ${post.id} not stored")
            return null
        }
        enqueueWorker(post)
        return post
    }

    fun cancel(id: String) {
        items.removeAll { it.id == id }
        if (!persist()) android.util.Log.e("ScheduledPosts", "cancel persist failed for $id — may reappear after restart")
        WorkManager.getInstance(app).cancelUniqueWork(workerName(id))
    }

    fun removeDelivered(id: String) {
        if (items.removeAll { it.id == id } && !persist())
            android.util.Log.e("ScheduledPosts", "removeDelivered persist failed for $id — will retry on next reschedule")
    }

    /** تُستدعى عند بدء التطبيق: تعيد جدولة منشورات لم يُنشر عمّالها (بعد reboot). */
    fun rescheduleAll() {
        val now = System.currentTimeMillis()
        // منتهية أثناء الإغلاق → تُنشر فورًا عند أول refresh (الـ Worker فات موعده).
        items.toList().forEach { enqueueWorker(it, immediateIfDue = true) }
        // تنظيف منشورات أقدم من 30 يومًا (لم تُنشر لسبب ما).
        if (items.removeAll { now - it.scheduledAtMs > 30L * 24 * 60 * 60 * 1000 } && !persist())
            android.util.Log.e("ScheduledPosts", "rescheduleAll cleanup persist failed")
    }

    private fun enqueueWorker(post: ScheduledPost, immediateIfDue: Boolean = false) {
        val delay = (post.scheduledAtMs - System.currentTimeMillis()).coerceAtLeast(0L)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val mediaUris = post.media.map { it.uri }.toTypedArray()
        val mediaMimes = post.media.map { it.mimeType }.toTypedArray()
        val input = workDataOf(
            "scheduled_id" to post.id,
            "text" to post.text,
            "visibility" to post.visibility,
            "poll_options" to post.pollOptions.toTypedArray(),
            "poll_hours" to (post.pollDurationHours ?: -1),
            "media_uris" to mediaUris,
            "media_mimes" to mediaMimes
        )
        val request = OneTimeWorkRequestBuilder<com.red.sovereign.core.workers.ScheduledPostWorker>()
            .setConstraints(constraints)
            .setInputData(input)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .apply { if (delay > 0) setInitialDelay(delay, TimeUnit.MILLISECONDS) }
            .addTag("scheduled-post")
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork(
            workerName(post.id),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun persist(): Boolean {
        return runCatching {
            prefs.edit().putString("items", json.encodeToString(items.toList())).apply()
            true
        }.getOrElse { e ->
            android.util.Log.e("ScheduledPosts", "prefs write failed (disk full loses post): ${e.message}", e)
            false
        }
    }

    private fun loadAll(): List<ScheduledPost> = runCatching {
        prefs.getString("items", null)?.let { json.decodeFromString<List<ScheduledPost>>(it) } ?: emptyList()
    }.getOrElse { emptyList() }

    companion object {
        fun workerName(id: String) = "scheduled-post-$id"

        /** حذف تسليمي من الـ Worker (بلا Store instance — SharedPrefs مباشرة). */
        fun markDelivered(context: Context, id: String): Boolean {
            val prefs = context.applicationContext.getSharedPreferences("younes_scheduled_posts", Context.MODE_PRIVATE)
            return runCatching {
                val json = Json { ignoreUnknownKeys = true }
                val list = prefs.getString("items", null)?.let { json.decodeFromString<List<ScheduledPost>>(it) } ?: return true
                prefs.edit().putString("items", json.encodeToString(list.filterNot { it.id == id })).apply()
                true
            }.getOrElse { e ->
                // آمن لإعادة المحاولة: يُرجع false فيُعيد الـ Worker الجدولة (retry) بدل success الضائع.
                android.util.Log.e("ScheduledPosts", "markDelivered failed for $id — retry-safe: ${e.message}", e)
                false
            }
        }
    }
}

/** وسم زمني عربي مختصر لموعد النشر ("بعد 3 ساعات"، "غدًا 14:30"...). */
fun scheduledLabel(scheduledAtMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    val diff = scheduledAtMs - nowMs
    if (diff <= 0) return "حان موعده"
    val mins = diff / 60_000L
    return when {
        mins < 60 -> "بعد $mins دقيقة"
        mins < 24 * 60 -> "بعد ${mins / 60} ساعة"
        else -> {
            val fmt = java.text.SimpleDateFormat("dd MMM · HH:mm", java.util.Locale("ar"))
            fmt.format(java.util.Date(scheduledAtMs))
        }
    }
}
