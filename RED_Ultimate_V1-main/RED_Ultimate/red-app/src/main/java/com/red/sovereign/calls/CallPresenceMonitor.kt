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
 * مراقب حضور المستلم أثناء المكالمة الصادرة.
 */
class CallPresenceMonitor(
    private val deliveryEngine: CallDeliveryEngine
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val monitoredCalls = ConcurrentHashMap<String, CallMonitorState>()

    companion object {
        private const val RINGING_GRACE_MS = 5_000L
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
        state.job = scope.launch {
            listener.onPresenceState(callId, PresenceState.CONNECTING)
            delay(RINGING_GRACE_MS)
            if (!state.ringingConfirmed.get()) {
                state.presenceState = PresenceState.WAKING_UP
                listener.onPresenceState(callId, PresenceState.WAKING_UP)
            }
            while (isActive) {
                delay(PROGRESS_TICK_MS)
                val elapsed = System.currentTimeMillis() - state.startedAt
                if (elapsed >= CallRingPolicy.UNANSWERED_TIMEOUT_MS) {
                    if (state.presenceState == PresenceState.CONNECTING || state.presenceState == PresenceState.WAKING_UP) {
                        state.presenceState = PresenceState.NO_ANSWER
                        listener.onPresenceState(callId, PresenceState.NO_ANSWER)
                    }
                    break
                }
            }
        }
    }

    fun onSignalReceived(callId: String, signalType: String, listener: Listener) {
        val state = monitoredCalls[callId] ?: return
        val newPresence = when (signalType) {
            CallSignal.RINGING, "RINGING" -> {
                state.ringingConfirmed.set(true)
                deliveryEngine.onDeliveryAckReceived(callId)
                PresenceState.RINGING
            }
            CallSignal.ANSWER, "ANSWER" -> PresenceState.ANSWERED
            CallSignal.REJECT, "REJECT" -> PresenceState.REJECTED
            CallSignal.BUSY, "BUSY" -> PresenceState.BUSY
            CallSignal.UNAVAILABLE, "UNAVAILABLE" -> PresenceState.UNAVAILABLE
            else -> return
        }
        state.presenceState = newPresence
        listener.onPresenceState(callId, newPresence)
        if (newPresence == PresenceState.ANSWERED || newPresence == PresenceState.REJECTED ||
            newPresence == PresenceState.BUSY || newPresence == PresenceState.UNAVAILABLE
        ) {
            stop(callId)
        }
    }

    fun stop(callId: String) {
        monitoredCalls.remove(callId)?.job?.cancel()
        Log.d("CallPresenceMonitor", "[$callId] stopped")
    }

    fun destroy() {
        scope.cancel()
        monitoredCalls.clear()
    }
}
