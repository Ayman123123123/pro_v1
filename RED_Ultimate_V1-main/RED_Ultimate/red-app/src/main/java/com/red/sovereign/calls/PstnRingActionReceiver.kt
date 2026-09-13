package com.red.sovereign.calls

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PstnRingActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {}

    companion object {
        const val ACTION_ACCEPT = "com.red.sovereign.pstn.ACTION_ACCEPT"
        const val ACTION_DECLINE = "com.red.sovereign.pstn.ACTION_DECLINE"
    }
}
