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

    fun register() {
        callsManager.registerAppWithTelecom(
            CallsManager.CAPABILITY_BASELINE or CallsManager.CAPABILITY_SUPPORTS_VIDEO_CALLING
        )
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
        val callId = peer
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
                onDisconnect()
            },
            onSetActive = { onActive() },
            onSetInactive = { onInactive() }
        ) { scope ->
            // Store the scope so we can later set inactive/active from within the app
            scopes[callId] = scope
        }
        return callId
    }

    /**
     * Sets the call as inactive (held). The peer connection stays alive; the system surfaces show held state.
     * Returns true if successful, false if no active scope exists for this peer.
     */
    suspend fun hold(peer: String): Boolean {
        val scope = scopes[peer] ?: return false
        return runCatching { scope.setInactive() }.isSuccess
    }

    /**
     * Resumes a previously held call.
     */
    suspend fun resume(peer: String): Boolean {
        val scope = scopes[peer] ?: return false
        return runCatching { scope.setActive() }.isSuccess
    }

    /**
     * Ends the call. Sends disconnect to the system.
     */
    suspend fun disconnect(peer: String): Boolean {
        val scope = scopes.remove(peer) ?: return false
        return runCatching { scope.disconnect() }.isSuccess
    }

    /**
     * Sends a DTMF tone. Used for IVR navigation and banking-grade phone menus.
     */
    suspend fun sendDtmf(peer: String, digit: Char): Boolean {
        val scope = scopes[peer] ?: return false
        return runCatching { scope.sendDtmf(digit.toString()[0]) }.isSuccess
    }

    fun hasCall(peer: String): Boolean = scopes.containsKey(peer)

    fun isHeld(peer: String): Boolean = false // Real impl would track from CallControlScope state; placeholder for future
}
