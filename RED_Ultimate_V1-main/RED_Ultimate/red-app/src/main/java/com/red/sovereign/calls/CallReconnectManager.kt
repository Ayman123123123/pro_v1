package com.red.sovereign.calls

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Auto-reconnect for WebSocket signaling with exponential backoff, jitter,
 * robust exception handling, diagnostic logging, and thread safety.
 *
 * الـ WebSocket قد ينقطع بسبب:
 * - Network change (Wi-Fi → LTE)
 * - Server restart
 * - Sleep/wake of device
 *
 * الاستراتيجية:
 * 1. Exponential backoff: 1s → 2s → 4s → 8s → 16s → 30s (max)
 * 2. بعد 5 محاولات فاشلة: عرض "اتصال الإشارة منقطع" في الـ UI
 * 3. عند نجاح إعادة الاتصال: استئناف ICE gathering + flush pending candidates
 */
class CallReconnectManager(
    private val scope: CoroutineScope,
    private val onReconnect: () -> Boolean,
    private val onFailure: () -> Unit
) {
    companion object {
        private const val TAG = "CallReconnectManager"
        private const val MAX_ATTEMPTS = 5
        private const val MAX_BACKOFF_MS = 30_000L
        private const val MIN_JITTER_DELAY_MS = 250L
    }

    private var job: Job? = null
    private val attemptCounter = AtomicInteger(0)

    val currentAttempt: Int
        get() = attemptCounter.get()

    val isReconnecting: Boolean
        get() = job?.isActive == true

    fun start() {
        if (job?.isActive == true) {
            Log.d(TAG, "Reconnect manager already active, ignoring start request.")
            return
        }
        job = scope.launch(Dispatchers.IO) {
            attemptCounter.set(0)
            Log.d(TAG, "Starting WebSocket reconnection sequence (max attempts: $MAX_ATTEMPTS)...")
            while (isActive && attemptCounter.get() < MAX_ATTEMPTS) {
                val currentAttemptNum = attemptCounter.incrementAndGet()

                // Calculate exponential backoff with full jitter to prevent thundering herd
                val exponentialDelay = (1000L * (1 shl (currentAttemptNum - 1).coerceAtMost(5))).coerceAtMost(MAX_BACKOFF_MS)
                val jitteredDelay = (Math.random() * exponentialDelay).toLong().coerceAtLeast(MIN_JITTER_DELAY_MS)

                Log.d(TAG, "Reconnect attempt $currentAttemptNum/$MAX_ATTEMPTS in ${jitteredDelay}ms...")
                delay(jitteredDelay)

                if (!isActive) break

                val success = runCatching {
                    onReconnect()
                }.getOrElse { e ->
                    Log.w(TAG, "Exception during reconnect callback on attempt $currentAttemptNum: ${e.message}", e)
                    false
                }

                if (success) {
                    Log.d(TAG, "WebSocket reconnection succeeded on attempt $currentAttemptNum.")
                    attemptCounter.set(0)
                    return@launch
                } else {
                    Log.w(TAG, "Reconnect attempt $currentAttemptNum failed.")
                }
            }

            if (isActive) {
                Log.e(TAG, "All $MAX_ATTEMPTS reconnect attempts failed. Triggering failure callback.")
                onFailure()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        attemptCounter.set(0)
        Log.d(TAG, "Reconnect manager stopped and state reset.")
    }
}
