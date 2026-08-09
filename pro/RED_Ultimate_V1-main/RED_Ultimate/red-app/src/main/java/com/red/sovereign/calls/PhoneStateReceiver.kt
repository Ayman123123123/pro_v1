package com.red.sovereign.calls

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

/**
 * يستقبل أحداث PSTN النظامية (مكالمة واردة على خط الهاتف).
 * لا يمكن لـ RED VoIP الرد عليها مباشرة، لكن نستفيد من:
 * 1. تهدئة/إيقاف الـ ringer الحالي عند مكالمة PSTN واردة (تجنب الضوضاء)
 * 2. تذكير المستخدم بوجود خط PSTN إذا كان في مكالمة RED نشطة
 */
class PhoneStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                // PSTN call incoming — log it; in real app we'd suppress RED ringer
                if (CallRuntime.state is CallUiState.Active) {
                    // User is in a RED call — show a system notification about PSTN
                    val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                    showPstnReminderNotification(context, incomingNumber)
                }
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                // User picked up PSTN — log only; we don't auto-end RED calls
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                // PSTN ended or never ringing
            }
        }
    }

    private fun showPstnReminderNotification(context: Context, number: String?) {
        // TODO: integrate with existing notification channel
        // For now, just a log entry
        android.util.Log.i("PhoneStateReceiver", "PSTN incoming during RED call: $number")
    }
}
