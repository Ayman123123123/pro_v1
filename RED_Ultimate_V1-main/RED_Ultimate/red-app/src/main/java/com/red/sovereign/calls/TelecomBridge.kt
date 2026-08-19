package com.red.sovereign.calls

import android.content.Context
import android.net.Uri
import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallControlScope
import androidx.core.telecom.CallsManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Registers YOUNES as a self-managed VoIP application for system call surfaces and routing.
 * Manages per-call [CallControlScope] so that the system can request hold/transfer/disconnect
 * and YOUNES can proactively put calls on hold.
 */
class TelecomBridge(context: Context) {
    private val callsManager = CallsManager(context.applicationContext)
    private val scopes = ConcurrentHashMap<String, CallControlScope>()
    private val heldStates = ConcurrentHashMap<String, Boolean>()
    private val counter = java.util.concurrent.atomic.AtomicInteger(0)

    fun register() {
        callsManager.registerAppWithTelecom(
            CallsManager.CAPABILITY_BASELINE or CallsManager.CAPABILITY_SUPPORTS_VIDEO_CALLING
        )
    }

    /**
     * يحلّ المفتاح الفعلي لأي مكالمة: إما peer نفسه (مكالمة واحدة) أو
     * المفتاح المركب peer#n لأي مكالمة لاحقة من نفس الرقم — كان مفتاح
     * addCall = peer فيستبدل المكالمة الثانية مكان الأولى ويمسح scope الأولى.
     */
    private fun resolve(peer: String): String? =
        scopes.keys.lastOrNull { it == peer || it.startsWith("$peer#") }

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
        val callId = if (scopes.containsKey(peer)) "$peer#${counter.incrementAndGet()}" else peer
        val attributes = CallAttributesCompat(
            displayName = peer,
            address = Uri.parse("younes:$peer"),
            direction = if (incoming) CallAttributesCompat.DIRECTION_INCOMING else CallAttributesCompat.DIRECTION_OUTGOING,
            callType = if (video) CallAttributesCompat.CALL_TYPE_VIDEO_CALL else CallAttributesCompat.CALL_TYPE_AUDIO_CALL,
            callCapabilities = CallAttributesCompat.SUPPORTS_SET_INACTIVE or CallAttributesCompat.SUPPORTS_TRANSFER
        )
        callsManager.addCall(
            attributes,
            onAnswer = { onAnswer() },
            onDisconnect = {
                scopes.remove(callId)
                heldStates.remove(callId)
                onDisconnect()
            },
            onSetActive = {
                heldStates[callId] = false
                onActive()
            },
            onSetInactive = {
                heldStates[callId] = true
                onInactive()
            }
        ) {
            // Store the scope so we can later set inactive/active from within the app
            scopes[callId] = this
            heldStates[callId] = false
        }
        return callId
    }

    /**
     * Sets the call as inactive (held). The peer connection stays alive; the system surfaces show held state.
     * Returns true if successful, false if no active scope exists for this peer.
     */
    suspend fun hold(peer: String): Boolean {
        val key = resolve(peer) ?: return false
        val scope = scopes[key] ?: return false
        val ok = runCatching { scope.setInactive() }.isSuccess
        if (ok) heldStates[key] = true
        return ok
    }

    /**
     * Resumes a previously held call.
     */
    suspend fun resume(peer: String): Boolean {
        val key = resolve(peer) ?: return false
        val scope = scopes[key] ?: return false
        val ok = runCatching { scope.setActive() }.isSuccess
        if (ok) heldStates[key] = false
        return ok
    }

    /**
     * Ends the call. Sends disconnect to the system.
     */
    suspend fun disconnect(peer: String): Boolean {
        val key = resolve(peer) ?: return false
        val scope = scopes.remove(key) ?: return false
        heldStates.remove(key)
        return runCatching { scope.disconnect(android.telecom.DisconnectCause(android.telecom.DisconnectCause.REMOTE)) }.isSuccess
    }

    /**
     * Sends a DTMF tone. Used for IVR navigation and banking-grade phone menus.
     * CallControlScope في core-telecom 1.1.0-alpha04 لا يوفر sendDtmf؛
     * النغمة الفعلية تُولَّد محلياً (In-band) في YounesCallService عبر
     * ToneGenerator على قناة المكالمة، فلا تُمرَّر هنا عبر النظام.
     */
    suspend fun sendDtmf(peer: String, digit: Char): Boolean {
        val key = resolve(peer) ?: return false
        return scopes.containsKey(key)
    }

    fun hasCall(peer: String): Boolean = resolve(peer) != null

    fun isHeld(peer: String): Boolean {
        val key = resolve(peer) ?: return false
        return heldStates[key] == true
    }
}
