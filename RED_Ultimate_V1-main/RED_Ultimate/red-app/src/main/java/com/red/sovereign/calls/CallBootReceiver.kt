package com.red.sovereign.calls

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.red.sovereign.auth.AuthState
import com.red.sovereign.auth.AuthViewModel
import com.red.sovereign.auth.TokenStore

/**
 * يضمن استمرار عمل [YounesCallService] بعد reboot الجهاز أو تحديث التطبيق.
 * يستمع إلى:
 * - BOOT_COMPLETED: عند إعادة تشغيل الجهاز
 * - MY_PACKAGE_REPLACED: عند تحديث التطبيق
 * - QUICKBOOT_POWERON: بعض أجهزة HTC/Samsung
 *
 * يبدأ signaling فقط إذا كان المستخدم مسجلاً دخوله (لديه accessToken صالح).
 */
class CallBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in SUPPORTED_ACTIONS) return
        // تحقق سريع: هل في token صالح؟
        val token = runCatching { TokenStore(context).accessToken }.getOrNull()
        if (token.isNullOrBlank()) {
            // المستخدم ليس مسجلاً — لا داعي لبدء الـ service
            return
        }
        YounesCallService.listen(context)
        // سحب صندوق البريد بعد الإقلاع/التحديث: فوري + مجدول (KEEP يمنع التكرار).
        runCatching { PendingOfferPoller.pollNow(context) }
        runCatching { PendingOfferPoller.schedule(context) }
        // أعد تسجيل الدفع السيادي (UnifiedPush) بعد إعادة التشغيل أو تحديث التطبيق —
        // دون ذلك يتجمد التسجيل حتى يفتح المستخدم التطبيق يدوياً.
        // آمن بلا موزّع: لا يُرسَل شيء إن لم يوجد موزّع أو نقطة نهاية مخزّنة.
        runCatching { VoipPushRegistrar.register(context) }
        // (2026-09-15) أعد جدولة مزامنة سجل المكالمات المشفر — كانت تُجدول فقط من
        // CallSystemIntegration.completeInitialization (غير المستدعاة، أُرشفت). بعد
        // reboot لا تنجو أي WorkManager دورية سابقة، فبدون هذا تتعطل المزامنة.
        runCatching { com.red.sovereign.core.sync.CallLogSyncScheduler.schedulePeriodicSync(context) }
        // أعد جدولة منبهات المكالمات المجدولة — AlarmManager لا ينجو من reboot
        // (المخزن ينجو، المنبهات لا) فتضيع المواعيد بصمت دونه.
        runCatching { ScheduledCallScheduler.rescheduleAll(context) }
    }

    companion object {
        internal val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "android.intent.action.MY_PACKAGE_REPLACED",
            "android.intent.action.PACKAGE_REPLACED"
        )
    }
}
