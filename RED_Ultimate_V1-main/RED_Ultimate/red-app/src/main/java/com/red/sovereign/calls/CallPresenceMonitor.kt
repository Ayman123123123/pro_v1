package com.red.sovereign.calls

import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "CallPresenceMonitor"

/**
 * # CallPresenceMonitor — مراقبة تواجد المستلم أثناء المكالمة
 *
 * ## الغرض:
 * بعد إرسال OFFER، نراقب حالة المستلم لإعطاء المتصل تغذية راجعة دقيقة:
 * - **RINGING**: تأكيد أن الجهاز استلم الإشارة وبدأ يرن
 * - **UNAVAILABLE**: المستلم offline أو تجاوز حد الوقت
 * - **DELIVERING**: لا يزال يُحاول الوصول
 * - **ANSWERED**: قبل المستلم
 * - **BUSY**: الطرف مشغول بمكالمة أخرى
 * - **REJECTED**: رفض المكالمة
 *
 * ## خوارزمية المراقبة:
 *
 * ### الإشارات الواردة (من CallSignalingClient):
 * ```
 * RINGING     → المستلم استيقظ وبدأ يرن
 * ANSWER      → قبل المكالمة
 * REJECT      → رفض المكالمة
 * BUSY        → مشغول
 * UNAVAILABLE → offline
 * CANCELLED   → ألغى المتصل
 * ```
 *
 * ### مؤشرات التقدم للمستخدم:
 * نبث حالة "جارٍ الاتصال → جهاز المستلم → يرن" بدلاً من "جارٍ الاتصال" الثابتة.
 *
 * ### Smart Retry Logic:
 * إذا لم يصل RINGING خلال [RINGING_GRACE_MS]:
 * → نُشغّل CallDeliveryEngine لتجربة مسارات أخرى
 * → نُبلّغ المستخدم بـ "جارٍ إيقاظ الجهاز…"
 *
 * ## دورة الحياة:
 * ```
 * start(callId) → monitor() → stop(callId)
 * ```
 */
class CallPresenceMonitor(
    private val deliveryEngine: CallDeliveryEngine
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val monitoredCalls = ConcurrentHashMap<String, CallMonitorState>()

    companion object {
        /** مهلة ظهور إشارة RINGING قبل اعتبار التسليم فاشلاً */
        private const val RINGING_GRACE_MS = 5_000L

        /** تكرار تحديث واجهة المستخدم بمؤشر التقدم */
        private const val PROGRESS_TICK_MS = 500L
    }

    interface Listener {
        /** حدّث واجهة المستخدم بحالة الاتصال الجديدة */
        fun onPresenceState(callId: String, state: PresenceState)
    }

    enum class PresenceState {
        /** جارٍ الاتصال بخوادم التوجيه */
        CONNECTING,
        /** تم الوصول للجهاز وجارٍ الرنين */
        RINGING,
        /** جارٍ إيقاظ الجهاز (غير متصل مؤقتاً) */
        WAKING_UP,
        /** لا يوجد رد بعد المهلة */
        NO_ANSWER,
        /** الطرف مشغول */
        BUSY,
        /** الطرف غير متاح (offline) */
        UNAVAILABLE,
        /** تم القبول */
        ANSWERED,
        /** تم الرفض */
        REJECTED
    }

    private data class CallMonitorState(
        val startedAt: Long = System.currentTimeMillis(),
        @Volatile var presenceState: PresenceState = PresenceState.CONNECTING,
        val ringingConfirmed: AtomicBoolean = AtomicBoolean(false),
        var job: Job? = null
    )

    /**
     * ابدأ مراقبة مكالمة.
     * @param callId   معرف المكالمة
     * @param listener مستمع التغييرات
     */
    fun start(callId: String, listener: Listener) {
        val state = CallMonitorState()
        monitoredCalls[callId] = state

        state.job = scope.launch {
            listener.onPresenceState(callId, PresenceState.CONNECTING)

            // انتظر تأكيد RINGING
            delay(RINGING_GRACE_MS)

            if (!state.ringingConfirmed.get()) {
                // لم نستلم RINGING — الجهاز ربما في وضع Doze أو غير متصل
                Log.d(TAG, "[$callId] No RINGING signal after ${RINGING_GRACE_MS}ms — switching to WAKING_UP")
                state.presenceState = PresenceState.WAKING_UP
                listener.onPresenceState(callId, PresenceState.WAKING_UP)
            }

            // استمر في الانتظار حتى ينتهي العرض (CallRingPolicy يتحكم بالمهلة الكلية)
            while (isActive) {
                delay(PROGRESS_TICK_MS)
                val elapsed = System.currentTimeMillis() - state.startedAt
                if (elapsed >= CallRingPolicy.UNANSWERED_TIMEOUT_MS) {
                    if (state.presenceState == PresenceState.CONNECTING ||
                        state.presenceState == PresenceState.WAKING_UP) {
                        state.presenceState = PresenceState.NO_ANSWER
                        listener.onPresenceState(callId, PresenceState.NO_ANSWER)
                    }
                    break
                }
            }
        }
    }

    /**
     * استدعِها عند استلام إشارة من المستلم عبر `onSignal`.
     */
    fun onSignalReceived(callId: String, signalType: String, listener: Listener) {
        val state = monitoredCalls[callId] ?: return
        val newPresence = when (signalType) {
            "RINGING" -> {
                state.ringingConfirmed.set(true)
                deliveryEngine.onDeliveryAckReceived()
                PresenceState.RINGING
            }
            "ANSWER" -> PresenceState.ANSWERED
            "REJECT" -> PresenceState.REJECTED
            "BUSY" -> PresenceState.BUSY
            "UNAVAILABLE" -> PresenceState.UNAVAILABLE
            else -> return
        }
        state.presenceState = newPresence
        listener.onPresenceState(callId, newPresence)

        if (newPresence == PresenceState.ANSWERED || newPresence == PresenceState.REJECTED ||
            newPresence == PresenceState.BUSY || newPresence == PresenceState.UNAVAILABLE) {
            stop(callId)
        }
    }

    /**
     * أوقف مراقبة مكالمة (عند الانتهاء أو الرفض أو القبول).
     */
    fun stop(callId: String) {
        monitoredCalls.remove(callId)?.job?.cancel()
        Log.d(TAG, "[$callId] Presence monitor stopped")
    }

    fun destroy() {
        scope.cancel()
        monitoredCalls.clear()
    }
}
