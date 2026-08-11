package com.red.sovereign.core.delivery

import android.content.Context
import android.util.Log
import com.red.sovereign.core.database.LocalRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * RED Burn Manager — الرسائل ذاتية التدمير (System C).
 * يجدول حذف رسالة من قاعدة البيانات الفعلية (Room) بعد مدة محددة لضمان الخصوصية.
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
        scope.cancel()
    }

    private companion object {
        const val TAG = "BurnManager"
    }
}
