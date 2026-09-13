package com.red.sovereign.calls

import android.app.Application

class PstnIncomingCallCoordinator(private val application: Application) {

    companion object {
        @Volatile
        var active: PstnIncomingCallCoordinator? = null
            private set
    }

    init { active = this }

    @Volatile
    var connected: Boolean = false
        private set

    @Volatile
    var activeIncoming: ActiveIncoming? = null
        private set

    data class ActiveIncoming(val callId: String, val channel: String, val caller: String, val called: String?)

    fun acceptIncoming(): Boolean {
        val inc = activeIncoming ?: return false
        return true
    }

    fun rejectIncoming(): Boolean {
        val inc = activeIncoming ?: return false
        activeIncoming = null
        return true
    }

    fun start() {}
    fun stop() {}
    fun destroy() {
        if (active === this) active = null
    }

    fun onExternalRing(callId: String, caller: String, called: String?, channel: String?) {}
}
