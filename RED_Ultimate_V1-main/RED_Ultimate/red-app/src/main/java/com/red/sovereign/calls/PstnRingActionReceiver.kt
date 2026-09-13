package com.red.sovereign.calls

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * مستقبِل أزرار (قبول/رفض) من إشعار الرنين بـ fullScreenIntent —
 * يعمل والتطبيق مقفول دون فتح الواجهة أولاً، ثم يطلق شاشة المكالمة عند القبول.
 */
class PstnRingActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val callId = intent.getStringExtra("callId") ?: return
        val coordinator = PstnIncomingCallCoordinator.active ?: return
        when (intent.action) {
            ACTION_ACCEPT -> {
                // اقبل عبر المنسق (PSTN_ACCEPT → AMI Redirect) ثم افتح الشاشة.
                coordinator.acceptIncoming()
                IncomingCallActivity.launchPstn(
                    context.applicationContext,
                    callId = callId,
                    peer = coordinator.activeIncoming?.caller ?: ""
                )
            }
            ACTION_DECLINE -> {
                coordinator.rejectIncoming()
                // أخفِ الإشعار بعد الرفض.
                androidx.core.app.NotificationManagerCompat.from(context)
                    .cancel(callId.hashCode())
            }
        }
    }

    companion object {
        const val ACTION_ACCEPT = "com.red.sovereign.pstn.ACTION_ACCEPT"
        const val ACTION_DECLINE = "com.red.sovereign.pstn.ACTION_DECLINE"
    }
}
