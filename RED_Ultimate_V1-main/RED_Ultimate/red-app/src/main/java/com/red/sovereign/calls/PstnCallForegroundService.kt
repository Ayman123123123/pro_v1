package com.red.sovereign.calls

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

class PstnCallForegroundService : Service() {

    companion object {
        const val ACTION_STOP = "com.red.sovereign.pstn.STOP"

        fun start(context: Context, number: String, isIncoming: Boolean = false) {}
        fun stop(context: Context) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
