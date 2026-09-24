package com.red.sovereign.calls

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.telecom.DisconnectCause
import android.util.Log
import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallControlScope
import androidx.core.telecom.CallsManager
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Registers YOUNES as a self-managed VoIP application for system call surfaces and routing.
 * Manages per-call [CallControlScope] so that the system can request hold/transfer/disconnect
 * and YOUNES can proactively put calls on hold.
 */
class TelecomBridge(context: Context) {
    private val contextRef = context.applicationContext
    private val callsManager = CallsManager(contextRef)
    private val scopes = ConcurrentHashMap<String, CallControlScope>()
    private val heldStates = ConcurrentHashMap<String, Boolean>()
    private val counter = AtomicInteger(0)
    // Debounce: يمنع بدء مكرر (نقرة مزدوجة/سباق كوروتين) من توليد مدخلي peer وpeer#n.
    private val lastAddMs = ConcurrentHashMap<String, Long>()
    private val startLock = Any()

    companion object {
        private const val TAG = "TelecomBridge"
        private const val START_DEBOUNCE_MS = 1500L
    }

    fun register() {
        runCatching {
            callsManager.registerAppWithTelecom(
                CallsManager.CAPABILITY_BASELINE or
                CallsManager.CAPABILITY_SUPPORTS_VIDEO_CALLING or
                CallsManager.CAPABILITY_SUPPORTS_CALL_STREAMING
            )
            Log.i(TAG, "Successfully registered app with Telecom CallsManager")
        }.onFailure { e ->
            Log.e(TAG, "Failed to register app with Telecom CallsManager", e)
        }
    }

    /**
     * Resolves the actual call key: either peer itself (single call) or
     * the compound key peer#n for subsequent calls from the same number.
     */
    private fun resolve(peer: String): String? {
        if (scopes.containsKey(peer)) return peer
        return scopes.keys.lastOrNull { it == peer || it.startsWith("$peer#") }
    }

    /**
     * Adds a call to the system. The returned [callId] can be used later to hold/resume/transfer/disconnect.
     */
    suspend fun addCall(
        peer: String,
        incoming: Boolean,
        video: Boolean,
        onAnswer: suspend () -> Unit,
        onDisconnect: suspend () -> Unit,
        onActive: suspend () -> Unit,
        onInactive: suspend () -> Unit
    ): String {
        // رفض بدء أثناء Connecting: أي نطاق قائم (peer أو peer#n) يعني المكالمة
        // ما زالت في الطريق — نعيد المفتاح القائم بدل توليد مدخل ثانٍ.
        // + Debounce زمني يغلق سباق البدء المتزامن قبل إدخال النطاق.
        val now = SystemClock.elapsedRealtime()
        synchronized(startLock) {
            resolve(peer)?.let { existing ->
                Log.w(TAG, "Duplicate start rejected during active/connecting: peer=$peer existing=$existing")
                return existing
            }
            val last = lastAddMs[peer] ?: 0L
            if (now - last < START_DEBOUNCE_MS) {
                Log.w(TAG, "Debounced rapid start: peer=$peer")
                return resolve(peer) ?: peer
            }
            lastAddMs[peer] = now
        }
        val callId = peer

        val attributes = CallAttributesCompat(
            displayName = peer,
            address = Uri.parse("younes:$peer"),
            direction = if (incoming) CallAttributesCompat.DIRECTION_INCOMING else CallAttributesCompat.DIRECTION_OUTGOING,
            callType = if (video) CallAttributesCompat.CALL_TYPE_VIDEO_CALL else CallAttributesCompat.CALL_TYPE_AUDIO_CALL,
            callCapabilities = CallAttributesCompat.SUPPORTS_SET_INACTIVE or
                CallAttributesCompat.SUPPORTS_TRANSFER
        )

        runCatching {
            callsManager.addCall(
                attributes,
                onAnswer = {
                    runCatching { onAnswer() }.onFailure { e ->
                        Log.e(TAG, "Error in onAnswer callback for callId=$callId", e)
                    }
                },
                onDisconnect = {
                    scopes.remove(callId)
                    heldStates.remove(callId)
                    runCatching { onDisconnect() }.onFailure { e ->
                        Log.e(TAG, "Error in onDisconnect callback for callId=$callId", e)
                    }
                },
                onSetActive = {
                    heldStates[callId] = false
                    runCatching { onActive() }.onFailure { e ->
                        Log.e(TAG, "Error in onSetActive callback for callId=$callId", e)
                    }
                },
                onSetInactive = {
                    heldStates[callId] = true
                    runCatching { onInactive() }.onFailure { e ->
                        Log.e(TAG, "Error in onSetInactive callback for callId=$callId", e)
                    }
                }
            ) {
                scopes[callId] = this
                heldStates[callId] = false
                Log.i(TAG, "Call added successfully to Telecom CallsManager: callId=$callId")
            }
        }.onFailure { e ->
            Log.e(TAG, "Failed to add call to Telecom CallsManager: callId=$callId", e)
        }

        return callId
    }

    /**
     * Sets the call as inactive (held). The peer connection stays alive; system surfaces show held state.
     */
    suspend fun hold(peer: String): Boolean {
        val key = resolve(peer) ?: return false
        val scope = scopes[key] ?: return false
        val ok = runCatching {
            scope.setInactive()
            heldStates[key] = true
            true
        }.getOrElse { e ->
            Log.e(TAG, "Failed to hold call: key=$key", e)
            false
        }
        return ok
    }

    /**
     * Resumes a previously held call.
     */
    suspend fun resume(peer: String): Boolean {
        val key = resolve(peer) ?: return false
        val scope = scopes[key] ?: return false
        val ok = runCatching {
            scope.setActive()
            heldStates[key] = false
            true
        }.getOrElse { e ->
            Log.e(TAG, "Failed to resume call: key=$key", e)
            false
        }
        return ok
    }

    /**
     * Ends the call with specified disconnect cause (default REMOTE or LOCAL).
     */
    suspend fun disconnect(peer: String, causeCode: Int = DisconnectCause.REMOTE): Boolean {
        val key = resolve(peer) ?: return false
        val scope = scopes.remove(key) ?: return false
        heldStates.remove(key)
        return runCatching {
            scope.disconnect(DisconnectCause(causeCode))
            true
        }.getOrElse { e ->
            Log.e(TAG, "Failed to disconnect call: key=$key, causeCode=$causeCode", e)
            false
        }
    }

    /**
     * Sends DTMF tone representation or signal.
     */
    suspend fun sendDtmf(peer: String, digit: Char): Boolean {
        val key = resolve(peer) ?: return false
        val exists = scopes.containsKey(key)
        if (exists) {
            Log.d(TAG, "DTMF digit $digit routed for call key=$key (handled in-band via ToneGenerator)")
        }
        return exists
    }

    fun hasCall(peer: String): Boolean = resolve(peer) != null

    fun isHeld(peer: String): Boolean {
        val key = resolve(peer) ?: return false
        return heldStates[key] == true
    }
}
