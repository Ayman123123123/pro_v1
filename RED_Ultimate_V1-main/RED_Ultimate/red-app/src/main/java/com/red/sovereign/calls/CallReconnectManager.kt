package com.red.sovereign.calls

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Auto-reconnect for WebSocket signaling.
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
    private var job: Job? = null
    private var attempt: Int = 0
    private val maxAttempts: Int = 5

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            attempt = 0
            while (isActive && attempt < maxAttempts) {
                attempt++
                val delayMs = (1000L * (1 shl (attempt - 1))).coerceAtMost(30_000L)
                delay(delayMs)
                if (onReconnect()) {
                    // نجحت إعادة الاتصال فعلاً (الـ socket مُعاد فتحه)
                    return@launch
                }
                // فشلت المحاولة — نستمر بالتراجع الأسي
            }
            onFailure()
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        attempt = 0
    }
}
