package com.red.sovereign.core.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.red.sovereign.core.database.RedDatabase
import java.util.concurrent.TimeUnit

/**
 * عامل دوري يحذف القصص المنتهية من القاعدة المحلية.
 *
 * لماذا يلزم: `RedDao.getActiveStories` يُخفي المنتهية بشرط
 * `expiresAt > now`، لكنه لا يحذف صفوفها. فبلا هذا العامل تتراكم كل
 * قصة شُوهدت يومًا في قاعدة SQLCipher إلى الأبد — يكبر ملف القاعدة
 * وتبقى وسائط منتهية الصلاحية مخزّنة على الجهاز.
 *
 * الدورة 6 ساعات لا 15 دقيقة: القصص تعيش 24 ساعة، فالحذف ليس عاجلًا،
 * وإيقاظ الجهاز كل ربع ساعة لعملية `DELETE` واحدة يستنزف البطارية بلا
 * مقابل. أندرويد نفسه لا يضمن دورية أقل من 15 دقيقة أصلًا.
 *
 * `KEEP` تُبقي الجدولة القائمة عند إعادة التشغيل فلا تُعاد الجدولة من
 * الصفر في كل إقلاع (وهو ما يؤخّر أول تشغيل إلى ما لا نهاية على
 * الأجهزة التي يُعاد تشغيل التطبيق فيها كثيرًا).
 */
class StoryCleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        val removed = RedDatabase.getInstance(applicationContext)
            .redDao()
            .cleanupExpiredStories(System.currentTimeMillis())
        if (removed > 0) Log.i(TAG, "حُذفت $removed قصة منتهية")
        Result.success()
    }.getOrElse { error ->
        // فشل القاعدة عابر غالبًا (قفل، أو إعادة إنشاء بعد تلف):
        // retry لا failure، وإلا أُسقطت الجدولة الدورية نهائيًا.
        Log.w(TAG, "فشل تنظيف القصص — ستُعاد المحاولة", error)
        Result.retry()
    }

    companion object {
        private const val TAG = "StoryCleanupWorker"
        private const val UNIQUE_NAME = "story-cleanup"
        private const val INTERVAL_HOURS = 6L

        /** يُستدعى مرة واحدة عند إقلاع التطبيق. */
        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<StoryCleanupWorker>(
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
