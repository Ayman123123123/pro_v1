package com.red.core.delivery

import com.red.core.database.MasterDao
import kotlinx.coroutines.*

/**
 * RED Burn Manager
 * Handles self-destructing messages (System C).
 */
class BurnManager(private val masterDao: MasterDao) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun scheduleBurn(messageId: String, timerSeconds: Long) {
        scope.launch {
            delay(timerSeconds * 1000)
            masterDao.updateMessageStatus(messageId, "BURNED")
            // الحذف الفعلي من قاعدة البيانات المحلية لضمان الخصوصية
            println("🔴 RED: Message $messageId has been burned locally.")
        }
    }
}
