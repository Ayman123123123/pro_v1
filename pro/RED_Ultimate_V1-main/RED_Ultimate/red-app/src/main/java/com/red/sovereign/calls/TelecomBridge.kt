package com.red.sovereign.calls

import android.content.Context
import android.net.Uri
import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallsManager

/** Registers YOUNES as a self-managed VoIP application for system call surfaces and routing. */
class TelecomBridge(context: Context) {
    private val callsManager = CallsManager(context.applicationContext)

    fun register() {
        callsManager.registerAppWithTelecom(
            CallsManager.CAPABILITY_BASELINE or CallsManager.CAPABILITY_SUPPORTS_VIDEO_CALLING
        )
    }

    suspend fun addCall(
        peer: String,
        incoming: Boolean,
        video: Boolean,
        onAnswer: suspend () -> Unit,
        onDisconnect: suspend () -> Unit,
        onActive: suspend () -> Unit,
        onInactive: suspend () -> Unit
    ) {
        val attributes = CallAttributesCompat(
            displayName = peer,
            address = Uri.parse("younes:$peer"),
            direction = if (incoming) CallAttributesCompat.DIRECTION_INCOMING else CallAttributesCompat.DIRECTION_OUTGOING,
            callType = if (video) CallAttributesCompat.CALL_TYPE_VIDEO_CALL else CallAttributesCompat.CALL_TYPE_AUDIO_CALL,
            callCapabilities = CallAttributesCompat.SUPPORTS_SET_INACTIVE
        )
        callsManager.addCall(
            attributes,
            onAnswer = { onAnswer() },
            onDisconnect = { onDisconnect() },
            onSetActive = { onActive() },
            onSetInactive = { onInactive() }
        ) { /* CallControlScope remains alive for Android, watches and Bluetooth surfaces. */ }
    }
}
