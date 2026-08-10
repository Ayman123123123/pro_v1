package com.red.core.delivery

import android.util.Log
import com.red.core.database.MasterDao
import kotlinx.coroutines.*

/**
 * RED Burn Manager
 * Handles self-destructing messages (System C).
 * After the timer expires, the message content is wiped from the local database
 * and the message status is set to BURNED to prevent any recovery.
 */
class BurnManager(private val masterDao: MasterDao) {
    companion object { private const val TAG = "RED.BurnManager" }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val activeTimers = mutableMapOf<String, Job>()

    fun scheduleBurn(messageId: String, timerSeconds: Long) {
        // Cancel existing timer if rescheduled
        activeTimers[messageId]?.cancel()

        activeTimers[messageId] = scope.launch {
            delay(timerSeconds * 1000)
            try {
                // First clear the content to prevent any forensic recovery
                masterDao.clearMessageContent(messageId)
                // Then mark as burned
                masterDao.updateMessageStatus(messageId, "BURNED")
                Log.i(TAG, "Message $messageId burned locally after ${timerSeconds}s")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to burn message $messageId", e)
            } finally {
                activeTimers.remove(messageId)
            }
        }
    }

    fun cancelBurn(messageId: String) {
        activeTimers.remove(messageId)?.cancel()
    }
}
