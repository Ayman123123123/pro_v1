package com.red.sovereign.settings

import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.media.RingtoneManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.red.sovereign.core.ServerEndpoint

/**
 * منطق البنود الثمانية المفعّلة في DeviceSettingsScreen.
 * كل كائن يقرأ من [SettingsRuntime.current] (المحفوظ في younes_user_preferences)
 * ويُطبّق أثراً حقيقياً — لا شارات ولا Snackbar كاذب.
 */

/** 1+3) الإشعارات: صوت/اهتزاز/LED تُطبق فوراً على قنوات النظام الفعلية. */
object NotificationPrefs {
    fun apply(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val s = SettingsRuntime.current
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        // الأهمية تُشتق من تفعيل الرسائل/المكالمات — التعطيل يخفضها لا يحذف القناة
        // (حذف القناة يفقد ترتيب أندرويد الثابت؛ الخفض يحترم نية المستخدم ويُبقي القناة).
        val msgImportance = if (s.messageNotifications) NotificationManager.IMPORTANCE_HIGH
            else NotificationManager.IMPORTANCE_LOW
        val callImportance = if (s.callNotifications) NotificationManager.IMPORTANCE_HIGH
            else NotificationManager.IMPORTANCE_LOW
        applyToChannel(nm, "red_messages", msgImportance, s)
        applyToChannel(nm, "red_calls", callImportance, s)
        applyToChannel(nm, "red_calls_incoming", NotificationManager.IMPORTANCE_MAX, s)
    }

    private fun applyToChannel(
        nm: NotificationManager,
        channelId: String,
        importance: Int,
        s: YounesSettings
    ) {
        val existing = nm.getNotificationChannel(channelId) ?: return
        val updated = android.app.NotificationChannel(channelId, existing.name, importance).apply {
            description = existing.description
            enableVibration(s.notificationVibration)
            vibrationPattern = if (s.notificationVibration) longArrayOf(0, 250, 250, 250) else longArrayOf(0)
            setSound(
                if (s.notificationSound) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) else null,
                if (s.notificationSound) android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION).build() else null
            )
            enableLights(s.notificationLed)
            lightColor = Color.YELLOW
            lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            if (channelId == "red_calls_incoming") setBypassDnd(true)
        }
        runCatching { nm.createNotificationChannel(updated) }
            .onFailure { Log.e("NotificationPrefs", "createNotificationChannel($channelId) failed", it) }
    }
}

/** 6) جودة الوسائط: تُقرأ من Prefs ويستهلكها MediaCompressor فعلياً قبل الرفع. */
object MediaQualityPolicy {
    data class Spec(val maxDimension: Int, val jpegQuality: Int, val labelAr: String)

    fun spec(quality: String): Spec = when (quality) {
        "BALANCED" -> Spec(1280, 75, "متوازنة (1280px · JPEG 75)")
        "SAVER" -> Spec(640, 60, "توفير بيانات (640px · JPEG 60)")
        else -> Spec(2048, 85, "عالية (2048px · JPEG 85)")
    }

    fun current(): Spec = spec(SettingsRuntime.current.mediaQuality)
}

/** 4) التنزيل التلقائي: WiFi/Mobile/Never مخزنة كـ (wifi,mobile) ويستهلكها MediaApi. */
object MediaAutoDownloadPolicy {
    /** يعيد الوضع النصي المشتق من البولينيين المحفوظين. */
    fun modeOf(wifi: Boolean, mobile: Boolean): String = when {
        wifi && mobile -> "MOBILE"
        wifi -> "WIFI"
        else -> "NEVER"
    }

    /** هل يُسمح بالتنزيل التلقائي الآن حسب الشبكة الحالية؟ يستدعيها MediaApi. */
    fun shouldAutoDownloadNow(context: Context): Boolean {
        val s = SettingsRuntime.current
        if (!s.autoDownloadWifi && !s.autoDownloadMobile) return false
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return s.autoDownloadWifi
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        val isWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        val isCell = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        return (isWifi && s.autoDownloadWifi) || (isCell && s.autoDownloadMobile)
    }
}

/** 7) وضع الاتصال: AUTO/WIFI/VPN مخزن ويُستخدم قبل autoDiscover وفي واجهة الشبكة. */
object ConnectionModePolicy {
    const val AUTO = "AUTO"
    const val WIFI = "WIFI"
    const val VPN = "VPN"

    fun labelAr(mode: String): String = when (mode) {
        WIFI -> "واي فاي فقط"
        VPN -> "VPN فقط"
        else -> "تلقائي"
    }

    fun describeAr(mode: String): String = when (mode) {
        WIFI -> "يُوقف الاكتشاف التلقائي على بيانات الهاتف — يُستخدم ServerEndpoint الحالي فقط"
        VPN -> "يتطلب نفق VPN نشطاً قبل أي اكتشاف أو اتصال جديد"
        else -> "يكتشف الخادم المحلي تلقائياً عبر ServerEndpoint.autoDiscover عند الفشل"
    }

    /** البوابة الفعلية: هل يُسمح بالاكتشاف/إعادة الاتصال الآن؟ */
    fun allowsNetworkOp(context: Context, mode: String = SettingsRuntime.current.connectionMode): Boolean {
        if (mode == AUTO) return true
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return when (mode) {
            WIFI -> caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            VPN -> caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            else -> true
        }
    }

    /** تنفيذ الربط: اكتشاف مشروط بالوضع — يُستدعى من زر "اختبار الاتصال" في الشاشة. */
    fun discoverRespectingMode(context: Context, onComplete: ((Boolean) -> Unit)? = null) {
        if (!allowsNetworkOp(context)) {
            onComplete?.invoke(false)
            return
        }
        ServerEndpoint.autoDiscover(context, onComplete)
    }
}

/** حد مدة المكالمة — يُقرأ من SettingsRuntime (مصدر الحقيقة الوحيد). */
object CallLimitsPolicy {
    fun maxDurationMs(): Long = SettingsRuntime.current.maxCallDurationSeconds.coerceIn(60, 7200) * 1000L

    fun maxDurationLabelAr(): String {
        val s = SettingsRuntime.current.maxCallDurationSeconds.coerceIn(60, 7200)
        return if (s % 3600 == 0) "${s / 3600} ساعة" else "${s / 60} دقيقة"
    }

    /** هل تجاوزت المكالمة حد المدة؟ يُستدعى دورياً من شاشة المكالمة النشطة. */
    fun isDurationExceeded(elapsedMs: Long): Boolean = elapsedMs >= maxDurationMs()
}

/** سياسة احتفاظ سجل المكالمات: تُشتق من CALL_HISTORY_RETENTION — تُطبَّق عبر CallHistoryViewModel.pruneExpired. */
object CallHistoryRetentionPolicy {
    fun retentionDays(): Int = SettingsRuntime.current.callHistoryRetentionDays.coerceIn(1, 365)
    fun cutoffEpochMs(now: Long = System.currentTimeMillis()): Long =
        now - retentionDays() * 24L * 60L * 60L * 1000L
    fun labelAr(): String = "${retentionDays()} يوم"
}

/** 5) استخدام التخزين: أحجام حقيقية من القرص + مسح فعلي. */
object StorageStats {
    data class Stats(
        val cacheBytes: Long,
        val mediaBytes: Long,
        val dbBytes: Long,
        val totalBytes: Long
    )

    fun compute(context: Context): Stats {
        val cache = dirSize(context.cacheDir)
        val media = dirSize(java.io.File(context.cacheDir, "encrypted_media")) +
            dirSize(java.io.File(context.cacheDir, "story_media")) +
            dirSize(java.io.File(context.cacheDir, "decrypted_attachments"))
        val dbFile = context.getDatabasePath("red_sovereign.db")
        val db = (if (dbFile.exists()) dbFile.length() else 0L) +
            java.io.File("${dbFile.absolutePath}-wal").lengthOrZero() +
            java.io.File("${dbFile.absolutePath}-shm").lengthOrZero()
        return Stats(cache, media, db, cache + media + db)
    }

    /** مسح فعلي: الكاش العام + الوسائط المؤقتة + كاش القصص — يُستدعى من زر "مسح التخزين". @return بايتات تم تحريرها. */
    fun clearCaches(context: Context): Long {
        val before = dirSize(context.cacheDir)
        runCatching { context.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
            .onFailure { Log.e("StorageStats", "clearCaches cacheDir failed", it) }
        runCatching { com.red.sovereign.media.EncryptedMediaCache(context).clear() }
            .onFailure { Log.e("StorageStats", "clearCaches EncryptedMediaCache failed", it) }
        val after = dirSize(context.cacheDir)
        return (before - after).coerceAtLeast(0L)
    }

    private fun dirSize(root: java.io.File): Long {
        if (!root.exists()) return 0L
        if (root.isFile) return root.length()
        return root.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
    }

    private fun java.io.File.lengthOrZero(): Long =
        if (exists() && isFile) length() else 0L

    fun formatAr(bytes: Long): String = when {
        bytes >= 1024L * 1024 -> "%.1f م.ب".format(bytes / 1048576.0)
        bytes >= 1024 -> "%.1f ك.ب".format(bytes / 1024.0)
        else -> "$bytes بايت"
    }
}
