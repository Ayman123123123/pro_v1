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
    }

    companion object {
        private val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
