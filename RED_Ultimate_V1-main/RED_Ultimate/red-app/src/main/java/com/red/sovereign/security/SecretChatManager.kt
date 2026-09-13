package com.red.sovereign.security

import android.app.Activity
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * 🔐 SecretChatManager — مدير المحادثات السرية السيادية ذاتية التدمير
 *
 * يوفر:
 * 1. ضبط مؤقت التدمير الذاتي للرسائل (Self-Destruct Timers: 1s .. 7d)
 * 2. تفعيل الحماية من لقطات وتسجيل الشاشة (FLAG_SECURE)
 * 3. حظر إعادة التوجيه التلقائي للمحادثات السرية
 */
object SecretChatManager {

    /** مؤقتات التدمير الذاتي المتاحة (بالثواني) */
    enum class SelfDestructTimer(val seconds: Long, val labelAr: String) {
        OFF(0L, "معطّل"),
        SECONDS_1(1L, "ثانية واحدة"),
        SECONDS_5(5L, "5 ثوانٍ"),
        SECONDS_10(10L, "10 ثوانٍ"),
        SECONDS_30(30L, "30 ثانية"),
        MINUTES_1(60L, "دقيقة واحدة"),
        HOURS_1(3600L, "ساعة واحدة"),
        DAYS_1(86400L, "يوم واحد"),
        DAYS_7(604800L, "أسبوع واحد")
    }

    private val secretSessions = ConcurrentHashMap<String, SelfDestructTimer>()
    private val _activeSecretChat = MutableStateFlow<String?>(null)
    val activeSecretChat = _activeSecretChat.asStateFlow()

    fun setTimer(conversationId: String, timer: SelfDestructTimer) {
        secretSessions[conversationId] = timer
    }

    fun getTimer(conversationId: String): SelfDestructTimer {
        return secretSessions[conversationId] ?: SelfDestructTimer.OFF
    }

    fun isSecretChat(conversationId: String): Boolean {
        return secretSessions.containsKey(conversationId) && secretSessions[conversationId] != SelfDestructTimer.OFF
    }

    fun enterSecretChat(activity: Activity, conversationId: String) {
        _activeSecretChat.value = conversationId
        // حظر لقطات وتسجيل الشاشة
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    fun exitSecretChat(activity: Activity) {
        _activeSecretChat.value = null
        // إلغاء حظر الشاشة عند الخروج
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    fun calculateExpiryTimestamp(conversationId: String, readAtTimestamp: Long): Long? {
        val timer = getTimer(conversationId)
        if (timer == SelfDestructTimer.OFF) return null
        return readAtTimestamp + (timer.seconds * 1000L)
    }
}
