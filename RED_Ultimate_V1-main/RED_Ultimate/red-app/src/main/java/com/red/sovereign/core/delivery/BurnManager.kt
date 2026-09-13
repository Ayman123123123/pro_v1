package com.red.sovereign.core.delivery

import android.content.Context
import android.util.Log
import com.red.sovereign.core.database.LocalRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * RED Burn Manager — الرسائل ذاتية التدمير (System C).
 * يجدول حذف رسالة من قاعدة البيانات الفعلية (Room) بعد مدة محددة لضمان الخصوصية.
 *
 * ## Lifecycle — يجب الربط بدورة حياة المالك
 * يملك هذا المدير [CoroutineScope] خاصاً غير مربوط بأي LifecycleOwner.
 * إن لم يُلغَ سيبقى حياً بعد تدمير الخدمة/النشاط ويؤخر جمع القمامة
 * للـ repository والـ context (تسرب).
 *
 * - [cancelAllBurns] يلغي الحروق المعلقة فقط ويُبقي المدير صالحاً للاستخدام
 *   (يلغي الأطفال عبر `cancelChildren` دون إغلاق الـ scope نفسه).
 * - [close] يلغي الـ scope نهائياً — يجب استدعاؤه من `Service.onDestroy`
 *   (مثال: `RedConnectionService.onDestroy` يستدعي `burnManager.close()`).
 *   بعد [close] لا يجوز إعادة استخدام نفس النسخة؛ أنشئ نسخة جديدة.
 */
class BurnManager(context: Context) {
    private val repository = LocalRepository(context.applicationContext)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun scheduleBurn(messageId: String, timerSeconds: Long) {
        if (timerSeconds <= 0) return
        scope.launch {
            delay(timerSeconds * 1000)
            runCatching {
                repository.deleteLocalMessage(messageId)
            }.onSuccess {
                Log.i(TAG, "RED: Message $messageId has been burned locally.")
            }.onFailure {
                Log.w(TAG, "RED: Burn failed for $messageId", it)
            }
        }
    }

    fun cancelAllBurns() {
        // إلغاء الأطفال فقط — الـ scope يبقى صالحاً لجدولة حروق جديدة.
        scope.coroutineContext.cancelChildren()
    }

    /**
     * إغلاق نهائي — يُستدعى من `Service.onDestroy`.
     * بعد هذا الاستدعاء لا تُجدول حروق جديدة على هذه النسخة.
     */
    fun close() {
        scope.cancel()
    }

    private companion object {
        const val TAG = "BurnManager"
    }
}
