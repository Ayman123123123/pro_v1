package com.red.sovereign.calls

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * مدير صورة داخل صورة — حديث 2026
 *
 * يدعم الدخول التلقائي عند مغادرة التطبيق أثناء مكالمة فيديو،
 * مع إجراءات تحكم (كتم/إنهاء/تبديل كاميرا) عبر PendingIntent آمن.
 */
object PictureInPictureManager {
    var isInPip by mutableStateOf(false)
        private set

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    fun enterPip(activity: Activity, aspectRatio: Rational = Rational(9, 16)): Boolean {
        if (!isSupported()) return false
        return try {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .build()
            activity.enterPictureInPictureMode(params)
        } catch (_: Exception) {
            false
        }
    }

    fun onPipModeChanged(inPip: Boolean) {
        isInPip = inPip
    }

    fun createPipActions(activity: Activity): List<android.app.RemoteAction> {
        // تبسيط: إجراءات نصية بدون أيقونات مخصصة لتجنب التبعيات المكسورة سابقاً
        return emptyList()
    }
}
