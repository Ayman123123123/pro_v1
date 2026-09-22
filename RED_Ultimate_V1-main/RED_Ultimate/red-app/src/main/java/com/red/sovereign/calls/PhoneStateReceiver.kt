package com.red.sovereign.calls

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * RED-only - لا RED نهائياً
 * كل المكالمات عبر WebRTC داخل التطبيق فقط
 * هذا المستقبل كان للـ RED وتم تعطيله نهائياً
 */
class PhoneStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // RED-only - لا تعامل مع RED
        android.util.Log.d("PhoneStateReceiver", "RED-only mode - ignoring telephony state")
    }
}
