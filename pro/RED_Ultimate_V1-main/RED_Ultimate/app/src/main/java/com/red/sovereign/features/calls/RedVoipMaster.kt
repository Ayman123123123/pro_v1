package com.red.sovereign.features.calls

import javax.inject.Inject
import javax.inject.Singleton

/**
 * RED VoIP Master — manages WebRTC calls (System A: 1080p AV1).
 */
@Singleton
class RedVoipMaster @Inject constructor() {

    private var activeCall: CallSession? = null

    data class CallSession(
        val targetId: String,
        val isVideo: Boolean,
        val startTime: Long = System.currentTimeMillis()
    )

    fun startSecureCall(target: String, videoEnabled: Boolean = true): CallSession {
        val session = CallSession(target, videoEnabled)
        activeCall = session
        return session
    }

    fun endCall() {
        activeCall = null
    }

    fun getActiveCall(): CallSession? = activeCall

    fun isCallActive(): Boolean = activeCall != null
}
