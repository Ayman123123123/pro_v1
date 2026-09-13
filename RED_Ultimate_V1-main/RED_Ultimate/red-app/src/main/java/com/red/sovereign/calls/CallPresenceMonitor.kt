package com.red.sovereign.calls

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * مراقب حضور المستلم أثناء المكالمة الصادرة والواردة — Call Presence Monitor
 * مع تتبع حالات الرنين، الاستيقاظ، عدم الإجابة، الانشغال، الرفض، والقبول،
 * مع معالجة استثناءات آمنة وتسجيل تشخيصي متقدم.
 */
class CallPresenceMonitor(
    private val deliveryEngine: CallDeliveryEngine
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val monitoredCalls = ConcurrentHashMap<String, CallMonitorState>()

    companion object {
        private const val TAG = "CallPresenceMonitor"
        /** مهلة سماح الرنين قبل إعلان عدم الإجابة — ثابت سياسة معلن للاختبارات. */
        const val RINGING_GRACE_MS = 5_000L
        private const val PROGRESS_TICK_MS = 500L
    }

    interface Listener {
        fun onPresenceState(callId: String, state: PresenceState)
    }

    enum class PresenceState {
        CONNECTING,
        RINGING,
        WAKING_UP,
        NO_ANSWER,
        BUSY,
        UNAVAILABLE,
        ANSWERED,
        REJECTED
    }

    private data class CallMonitorState(
        val startedAt: Long = System.currentTimeMillis(),
        @Volatile var presenceState: PresenceState = PresenceState.CONNECTING,
        val ringingConfirmed: AtomicBoolean = AtomicBoolean(false),
        var job: Job? = null
    )

    fun start(callId: String, listener: Listener) {
        val state = CallMonitorState()
        monitoredCalls[callId] = state
        Log.d(TAG, "[$callId] Starting presence monitoring...")

        state.job = scope.launch {
            notifyListener(callId, PresenceState.CONNECTING, listener)
            delay(RINGING_GRACE_MS)
            if (!state.ringingConfirmed.get() && state.presenceState == PresenceState.CONNECTING) {
                state.presenceState = PresenceState.WAKING_UP
                Log.d(TAG, "[$callId] Ringing grace expired without confirmation; transitioning to WAKING_UP.")
                notifyListener(callId, PresenceState.WAKING_UP, listener)
            }
            while (isActive) {
                delay(PROGRESS_TICK_MS)
                val elapsed = System.currentTimeMillis() - state.startedAt
                if (elapsed >= CallRingPolicy.UNANSWERED_TIMEOUT_MS) {
                    if (state.presenceState == PresenceState.CONNECTING || state.presenceState == PresenceState.WAKING_UP || state.presenceState == PresenceState.RINGING) {
                        state.presenceState = PresenceState.NO_ANSWER
                        Log.w(TAG, "[$callId] Call unanswered timeout reached (${CallRingPolicy.UNANSWERED_TIMEOUT_MS}ms). Marking as NO_ANSWER.")
                        notifyListener(callId, PresenceState.NO_ANSWER, listener)
                    }
                    break
                }
            }
        }
    }

    fun onSignalReceived(callId: String, signalType: String, listener: Listener) {
        val state = monitoredCalls[callId] ?: run {
            Log.d(TAG, "[$callId] Received signal '$signalType' for unmonitored or expired call.")
            return
        }
        val newPresence = when (signalType) {
            CallSignal.RINGING, "RINGING" -> {
                state.ringingConfirmed.set(true)
                runCatching { deliveryEngine.onDeliveryAckReceived(callId) }
                PresenceState.RINGING
            }
            CallSignal.ANSWER, "ANSWER" -> PresenceState.ANSWERED
            CallSignal.REJECT, "REJECT" -> PresenceState.REJECTED
            CallSignal.BUSY, "BUSY" -> PresenceState.BUSY
            CallSignal.UNAVAILABLE, "UNAVAILABLE" -> PresenceState.UNAVAILABLE
            else -> {
                Log.d(TAG, "[$callId] Unhandled signal type for presence: $signalType")
                return
            }
        }
        state.presenceState = newPresence
        Log.d(TAG, "[$callId] Presence state updated via signal '$signalType' -> $newPresence")
        notifyListener(callId, newPresence, listener)

        if (newPresence == PresenceState.ANSWERED || newPresence == PresenceState.REJECTED ||
            newPresence == PresenceState.BUSY || newPresence == PresenceState.UNAVAILABLE ||
            newPresence == PresenceState.NO_ANSWER
        ) {
            stop(callId)
        }
    }

    private fun notifyListener(callId: String, state: PresenceState, listener: Listener) {
        runCatching {
            listener.onPresenceState(callId, state)
        }.getOrElse { e ->
            Log.w(TAG, "[$callId] Error in presence state listener callback: ${e.message}", e)
        }
    }

    fun stop(callId: String) {
        monitoredCalls.remove(callId)?.job?.cancel()
        Log.d(TAG, "[$callId] Presence monitor stopped and cleaned up.")
    }

    fun destroy() {
        scope.cancel()
        monitoredCalls.clear()
        Log.d(TAG, "CallPresenceMonitor destroyed.")
    }
}
