package com.red.sovereign.calls

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.content.Intent
import android.os.Build
import android.util.Rational
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Picture-in-Picture Manager — RED Sovereign 2026
 *
 * Supports automatic entry on app leave during video calls,
 * with secure remote action controls (mute/end/camera) via CallNotificationActionReceiver.
 */
object PictureInPictureManager {
    var isInPip by mutableStateOf(false)
        private set

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    fun enterPip(
        activity: Activity,
        aspectRatio: Rational = Rational(9, 16),
        actions: List<android.app.RemoteAction>? = null
    ): Boolean {
        if (!isSupported()) return false
        if (activity.isFinishing) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed) return false

        return try {
            val builder = PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setSeamlessResizeEnabled(true)
            }
            if (!actions.isNullOrEmpty()) {
                builder.setActions(actions)
            }
            activity.enterPictureInPictureMode(builder.build())
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Enables or disables auto-enter PiP mode on Android 12+ (API 31+).
     */
    fun setAutoEnterPip(activity: Activity, enabled: Boolean, aspectRatio: Rational = Rational(9, 16)) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !activity.isFinishing) {
            runCatching {
                val params = PictureInPictureParams.Builder()
                    .setAutoEnterEnabled(enabled)
                    .setAspectRatio(aspectRatio)
                    .setSeamlessResizeEnabled(true)
                    .build()
                activity.setPictureInPictureParams(params)
            }
        }
    }

    fun onPipModeChanged(inPip: Boolean) {
        isInPip = inPip
    }

    fun createPipActions(activity: Activity): List<android.app.RemoteAction> {
        val muteIntent = CallNotificationActionReceiver.receiverIntent(
            context = activity,
            action = CallNotificationActionReceiver.ACTION_TOGGLE_MIC,
            callType = CallNotificationActionReceiver.CALL_TYPE_1TO1,
            notifId = 1001,
            isVideo = false
        )
        val endIntent = CallNotificationActionReceiver.receiverIntent(
            context = activity,
            action = CallNotificationActionReceiver.ACTION_END,
            callType = CallNotificationActionReceiver.CALL_TYPE_1TO1,
            notifId = 1002,
            isVideo = false
        )
        val cameraIntent = CallNotificationActionReceiver.receiverIntent(
            context = activity,
            action = CallNotificationActionReceiver.ACTION_TOGGLE_VIDEO,
            callType = CallNotificationActionReceiver.CALL_TYPE_1TO1,
            notifId = 1003,
            isVideo = true
        )

        val muteAction = android.app.RemoteAction(
            android.graphics.drawable.Icon.createWithResource(activity, android.R.drawable.ic_btn_speak_now),
            "كتم", "تبديل الميكروفون", muteIntent
        )
        val endAction = android.app.RemoteAction(
            android.graphics.drawable.Icon.createWithResource(activity, android.R.drawable.ic_menu_call),
            "إنهاء", "إنهاء المكالمة", endIntent
        )
        val cameraAction = android.app.RemoteAction(
            android.graphics.drawable.Icon.createWithResource(activity, android.R.drawable.ic_menu_camera),
            "الكاميرا", "تبديل الكاميرا", cameraIntent
        )
        return listOf(muteAction, endAction, cameraAction)
    }
}
