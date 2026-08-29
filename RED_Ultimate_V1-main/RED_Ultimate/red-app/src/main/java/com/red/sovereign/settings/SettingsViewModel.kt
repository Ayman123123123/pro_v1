package com.red.sovereign.settings

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import java.io.File

/**
 * تخزين إعدادات المستخدم — **مصدر الحقيقة الوحيد** لأسماء المفاتيح
 * وقيمها الافتراضية.
 *
 * قبل هذا التوحيد كانت خريطة الـ27 مفتاحًا مكرّرة حرفيًّا ثلاث مرات
 * (قراءة في `load()`، وقراءة ثانية في `SettingsRuntime.initialize`،
 * وكتابة في `update()`) — أي 81 سلسلة نصّية تُصان يدويًّا. إضافة إعداد
 * واحد كانت تتطلّب تعديل ثلاثة مواضع، ونسيان أحدها يُنتج عيبًا صامتًا:
 * إعداد يُحفظ ولا يُقرأ، أو يُقرأ بقيمة افتراضية مخالفة عند الإقلاع.
 *
 * صار التعريف الآن في [Keys] وحدها، وتشتقّ منه القراءة والكتابة معًا،
 * فاستحال أن تتباعد النسخ.
 */
private const val PREFS_NAME = "younes_user_preferences"

/**
 * تعريف كل إعداد: مفتاح التخزين، وكيف يُقرأ، وكيف يُكتب.
 *
 * الاحتفاظ بالثلاثة في مكان واحد هو ما يمنع التباعد بنيويًّا بدل
 * الاعتماد على الانضباط اليدوي.
 */
private object Keys {
    const val FONT_SCALE = "font_scale"
    const val HIGH_CONTRAST = "high_contrast"
    const val COMPACT_MODE = "compact_mode"
    const val REDUCE_MOTION = "reduce_motion"
    const val READ_RECEIPTS = "read_receipts"
    const val TYPING_INDICATORS = "typing_indicators"
    const val LINK_PREVIEWS = "link_previews"
    const val AUTO_DOWNLOAD_WIFI = "auto_download_wifi"
    const val AUTO_DOWNLOAD_MOBILE = "auto_download_mobile"
    const val AUTO_DOWNLOAD_LIMIT_MB = "auto_download_limit_mb"
    const val NOTIFICATION_PREVIEW = "notification_preview"
    const val MESSAGE_NOTIFICATIONS = "message_notifications"
    const val CALL_NOTIFICATIONS = "call_notifications"
    const val DATA_SAVER_CALLS = "data_saver_calls"
    const val PLAYBACK_SPEED = "playback_speed"
    const val APP_LOCK_ENABLED = "app_lock_enabled"
    const val HIDE_LAST_SEEN = "hide_last_seen"
    const val LAST_SEEN_VISIBILITY = "last_seen_visibility"
    const val PROFILE_PHOTO_VISIBILITY = "profile_photo_visibility"
    const val ABOUT_VISIBILITY = "about_visibility"
    const val WHO_CAN_ADD_GROUPS = "who_can_add_groups"
    const val WHO_CAN_CALL = "who_can_call"
    const val ENTER_TO_SEND = "enter_to_send"
    const val SAVE_MEDIA_GALLERY = "save_media_gallery"
    const val AUTO_ARCHIVE_MUTED = "auto_archive_muted"
    const val GROUP_NOTIFICATIONS = "group_notifications"
    const val LOCK_TIMEOUT_SECONDS = "lock_timeout_seconds"
    const val THEME_PRESET = "theme_preset"
    const val THEME_MODE = "theme_mode"
    const val LIQUID_GLASS = "liquid_glass"
    const val CUSTOM_PRIMARY = "custom_primary"
}

/**
 * يقرأ الإعدادات المحفوظة. تعريف واحد يستعمله الـViewModel
 * و[SettingsRuntime] معًا، فلا تختلف القيم بين الإقلاع والتحرير.
 */
internal fun SharedPreferences.readSettings(): YounesSettings {
    val defaults = YounesSettings()
    val hideLastSeen = getBoolean(Keys.HIDE_LAST_SEEN, defaults.hideLastSeen)
    return YounesSettings(
        fontScale = getFloat(Keys.FONT_SCALE, defaults.fontScale),
        highContrast = getBoolean(Keys.HIGH_CONTRAST, defaults.highContrast),
        compactMode = getBoolean(Keys.COMPACT_MODE, defaults.compactMode),
        reduceMotion = getBoolean(Keys.REDUCE_MOTION, defaults.reduceMotion),
        readReceipts = getBoolean(Keys.READ_RECEIPTS, defaults.readReceipts),
        typingIndicators = getBoolean(Keys.TYPING_INDICATORS, defaults.typingIndicators),
        linkPreviews = getBoolean(Keys.LINK_PREVIEWS, defaults.linkPreviews),
        autoDownloadWifi = getBoolean(Keys.AUTO_DOWNLOAD_WIFI, defaults.autoDownloadWifi),
        autoDownloadMobile = getBoolean(Keys.AUTO_DOWNLOAD_MOBILE, defaults.autoDownloadMobile),
        autoDownloadLimitMb = getInt(Keys.AUTO_DOWNLOAD_LIMIT_MB, defaults.autoDownloadLimitMb),
        notificationPreview = getBoolean(Keys.NOTIFICATION_PREVIEW, defaults.notificationPreview),
        messageNotifications = getBoolean(Keys.MESSAGE_NOTIFICATIONS, defaults.messageNotifications),
        callNotifications = getBoolean(Keys.CALL_NOTIFICATIONS, defaults.callNotifications),
        dataSaverCalls = getBoolean(Keys.DATA_SAVER_CALLS, defaults.dataSaverCalls),
        defaultPlaybackSpeed = getFloat(Keys.PLAYBACK_SPEED, defaults.defaultPlaybackSpeed),
        appLockEnabled = getBoolean(Keys.APP_LOCK_ENABLED, defaults.appLockEnabled),
        hideLastSeen = hideLastSeen,
        // التوافق الرجعي: النسخ القديمة حفظت `hide_last_seen` فقط، فتُشتقّ
        // منها الرؤية حين لا يكون المفتاح الأحدث موجودًا.
        lastSeenVisibility = getString(
            Keys.LAST_SEEN_VISIBILITY,
            if (hideLastSeen) "NOBODY" else "EVERYONE"
        ) ?: "EVERYONE",
        profilePhotoVisibility = getString(Keys.PROFILE_PHOTO_VISIBILITY, defaults.profilePhotoVisibility)
            ?: defaults.profilePhotoVisibility,
        aboutVisibility = getString(Keys.ABOUT_VISIBILITY, defaults.aboutVisibility)
            ?: defaults.aboutVisibility,
        whoCanAddToGroups = getString(Keys.WHO_CAN_ADD_GROUPS, defaults.whoCanAddToGroups)
            ?: defaults.whoCanAddToGroups,
        whoCanCall = getString(Keys.WHO_CAN_CALL, defaults.whoCanCall) ?: defaults.whoCanCall,
        enterToSend = getBoolean(Keys.ENTER_TO_SEND, defaults.enterToSend),
        saveMediaToGallery = getBoolean(Keys.SAVE_MEDIA_GALLERY, defaults.saveMediaToGallery),
        autoArchiveMuted = getBoolean(Keys.AUTO_ARCHIVE_MUTED, defaults.autoArchiveMuted),
        groupNotifications = getBoolean(Keys.GROUP_NOTIFICATIONS, defaults.groupNotifications),
        lockTimeoutSeconds = getInt(Keys.LOCK_TIMEOUT_SECONDS, defaults.lockTimeoutSeconds),
        themePreset = getString(Keys.THEME_PRESET, defaults.themePreset) ?: defaults.themePreset,
        themeMode = getString(Keys.THEME_MODE, defaults.themeMode) ?: defaults.themeMode,
        liquidGlassEnabled = getBoolean(Keys.LIQUID_GLASS, defaults.liquidGlassEnabled),
        customPrimary = getInt(Keys.CUSTOM_PRIMARY, defaults.customPrimary)
    )
}

/** يكتب الإعدادات. يقابل [readSettings] مفتاحًا بمفتاح. */
internal fun SharedPreferences.writeSettings(value: YounesSettings) {
    edit()
        .putFloat(Keys.FONT_SCALE, value.fontScale)
        .putBoolean(Keys.HIGH_CONTRAST, value.highContrast)
        .putBoolean(Keys.COMPACT_MODE, value.compactMode)
        .putBoolean(Keys.REDUCE_MOTION, value.reduceMotion)
        .putBoolean(Keys.READ_RECEIPTS, value.readReceipts)
        .putBoolean(Keys.TYPING_INDICATORS, value.typingIndicators)
        .putBoolean(Keys.LINK_PREVIEWS, value.linkPreviews)
        .putBoolean(Keys.AUTO_DOWNLOAD_WIFI, value.autoDownloadWifi)
        .putBoolean(Keys.AUTO_DOWNLOAD_MOBILE, value.autoDownloadMobile)
        .putInt(Keys.AUTO_DOWNLOAD_LIMIT_MB, value.autoDownloadLimitMb)
        .putBoolean(Keys.NOTIFICATION_PREVIEW, value.notificationPreview)
        .putBoolean(Keys.MESSAGE_NOTIFICATIONS, value.messageNotifications)
        .putBoolean(Keys.CALL_NOTIFICATIONS, value.callNotifications)
        .putBoolean(Keys.DATA_SAVER_CALLS, value.dataSaverCalls)
        .putFloat(Keys.PLAYBACK_SPEED, value.defaultPlaybackSpeed)
        .putBoolean(Keys.APP_LOCK_ENABLED, value.appLockEnabled)
        .putBoolean(Keys.HIDE_LAST_SEEN, value.hideLastSeen)
        .putString(Keys.LAST_SEEN_VISIBILITY, value.lastSeenVisibility)
        .putString(Keys.PROFILE_PHOTO_VISIBILITY, value.profilePhotoVisibility)
        .putString(Keys.ABOUT_VISIBILITY, value.aboutVisibility)
        .putString(Keys.WHO_CAN_ADD_GROUPS, value.whoCanAddToGroups)
        .putString(Keys.WHO_CAN_CALL, value.whoCanCall)
        .putBoolean(Keys.ENTER_TO_SEND, value.enterToSend)
        .putBoolean(Keys.SAVE_MEDIA_GALLERY, value.saveMediaToGallery)
        .putBoolean(Keys.AUTO_ARCHIVE_MUTED, value.autoArchiveMuted)
        .putBoolean(Keys.GROUP_NOTIFICATIONS, value.groupNotifications)
        .putInt(Keys.LOCK_TIMEOUT_SECONDS, value.lockTimeoutSeconds)
        .putString(Keys.THEME_PRESET, value.themePreset)
        .putString(Keys.THEME_MODE, value.themeMode)
        .putBoolean(Keys.LIQUID_GLASS, value.liquidGlassEnabled)
        .putInt(Keys.CUSTOM_PRIMARY, value.customPrimary)
        .apply()
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    var state: YounesSettings by mutableStateOf(load()); private set
    var cacheBytes: Long by mutableStateOf(cacheSize(application.cacheDir)); private set

    fun setFontScale(value: Float) = update(state.copy(fontScale = value.coerceIn(.85f, 1.30f)))
    fun setHighContrast(value: Boolean) = update(state.copy(highContrast = value))
    fun setCompactMode(value: Boolean) = update(state.copy(compactMode = value))
    fun setReduceMotion(value: Boolean) = update(state.copy(reduceMotion = value))
    fun setReadReceipts(value: Boolean) = update(state.copy(readReceipts = value))
    fun setTypingIndicators(value: Boolean) = update(state.copy(typingIndicators = value))
    fun setLinkPreviews(value: Boolean) = update(state.copy(linkPreviews = value))
    fun setWifiDownload(value: Boolean) = update(state.copy(autoDownloadWifi = value))
    fun setMobileDownload(value: Boolean) = update(state.copy(autoDownloadMobile = value))
    fun setAutoDownloadLimit(value: Int) = update(state.copy(autoDownloadLimitMb = value.coerceIn(1, 99)))
    fun setNotificationPreview(value: Boolean) = update(state.copy(notificationPreview = value))
    fun setMessageNotifications(value: Boolean) = update(state.copy(messageNotifications = value))
    fun setCallNotifications(value: Boolean) = update(state.copy(callNotifications = value))
    fun setDataSaverCalls(value: Boolean) = update(state.copy(dataSaverCalls = value))
    fun setDefaultPlaybackSpeed(value: Float) = update(state.copy(defaultPlaybackSpeed = value.takeIf { it in setOf(1f, 1.5f, 2f) } ?: 1f))
    fun setAppLockEnabled(value: Boolean) = update(state.copy(appLockEnabled = value))
    fun setHideLastSeen(value: Boolean) = update(state.copy(hideLastSeen = value, lastSeenVisibility = if (value) "NOBODY" else "EVERYONE"))
    fun setLastSeenVisibility(value: String) = update(state.copy(lastSeenVisibility = sanitizeVisibility(value), hideLastSeen = sanitizeVisibility(value) == "NOBODY"))
    fun setProfilePhotoVisibility(value: String) = update(state.copy(profilePhotoVisibility = sanitizeVisibility(value)))
    fun setAboutVisibility(value: String) = update(state.copy(aboutVisibility = sanitizeVisibility(value)))
    fun setWhoCanAddToGroups(value: String) = update(state.copy(whoCanAddToGroups = sanitizeVisibility(value)))
    fun setWhoCanCall(value: String) = update(state.copy(whoCanCall = sanitizeVisibility(value)))
    fun setEnterToSend(value: Boolean) = update(state.copy(enterToSend = value))
    fun setSaveMediaToGallery(value: Boolean) = update(state.copy(saveMediaToGallery = value))
    fun setAutoArchiveMuted(value: Boolean) = update(state.copy(autoArchiveMuted = value))
    fun setGroupNotifications(value: Boolean) = update(state.copy(groupNotifications = value))
    fun setLockTimeoutSeconds(value: Int) = update(state.copy(lockTimeoutSeconds = value.coerceIn(5, 300)))
    fun setThemePreset(value: String) = update(state.copy(themePreset = value))
    fun setThemeMode(value: String) = update(state.copy(themeMode = value))
    fun setLiquidGlass(value: Boolean) = update(state.copy(liquidGlassEnabled = value))
    fun setCustomPrimary(value: Int) = update(state.copy(customPrimary = value))

    private fun sanitizeVisibility(value: String) = value.takeIf { it in VISIBILITY }.orEmpty().ifBlank { "CONTACTS" }

    fun clearCache() {
        getApplication<Application>().cacheDir.listFiles()?.forEach(::deleteRecursivelySafe)
        cacheBytes = cacheSize(getApplication<Application>().cacheDir)
    }

    private fun update(value: YounesSettings) {
        state = value
        preferences.writeSettings(value)
        SettingsRuntime.update(value)
    }

    private fun load() = preferences.readSettings().also(SettingsRuntime::update)

    private fun cacheSize(root: File): Long = root.walkBottomUp().filter(File::isFile).sumOf(File::length)
    private fun deleteRecursivelySafe(file: File) { runCatching { file.deleteRecursively() } }
}

data class YounesSettings(
    val fontScale: Float = 1f,
    val highContrast: Boolean = false,
    val compactMode: Boolean = false,
    val reduceMotion: Boolean = true,
    val readReceipts: Boolean = true,
    val typingIndicators: Boolean = true,
    val linkPreviews: Boolean = false,
    val autoDownloadWifi: Boolean = true,
    val autoDownloadMobile: Boolean = false,
    val autoDownloadLimitMb: Int = 25,
    val notificationPreview: Boolean = false,
    val messageNotifications: Boolean = true,
    val callNotifications: Boolean = true,
    val dataSaverCalls: Boolean = true,
    val defaultPlaybackSpeed: Float = 1f,
    val appLockEnabled: Boolean = false,
    val hideLastSeen: Boolean = false,
    val lastSeenVisibility: String = "EVERYONE",
    val profilePhotoVisibility: String = "EVERYONE",
    val aboutVisibility: String = "EVERYONE",
    val whoCanAddToGroups: String = "CONTACTS",
    val whoCanCall: String = "CONTACTS",
    val enterToSend: Boolean = false,
    val saveMediaToGallery: Boolean = false,
    val autoArchiveMuted: Boolean = false,
    val groupNotifications: Boolean = true,
    val lockTimeoutSeconds: Int = 15,
    val themePreset: String = "SOVEREIGN",
    val themeMode: String = "SYSTEM",
    val liquidGlassEnabled: Boolean = true,
    val customPrimary: Int = 0
) {
    val notificationEnabled: Boolean get() = messageNotifications
}

/**
 * الإعدادات الحاليّة كما يقرأها بقيّة التطبيق (12 ملفًا) خارج شاشة
 * الإعدادات — الإشعارات، جودة المكالمة، التنزيل التلقائي، القفل.
 *
 * حالة عامة للقراءة فقط من الخارج: التحديث يمرّ حصرًا عبر
 * [SettingsViewModel] فلا يكتب أحد قيمةً لا تُحفظ.
 */
object SettingsRuntime {
    var current by mutableStateOf(YounesSettings()); private set

    /**
     * تُستدعى مرّة عند إقلاع التطبيق. آمنة للاستدعاء المتكرّر: تعيد
     * القراءة من التخزين نفسه فتصل إلى الحالة ذاتها.
     */
    fun initialize(application: Application) {
        update(application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).readSettings())
    }

    fun update(value: YounesSettings) { current = value }
}

private val VISIBILITY = setOf("EVERYONE", "CONTACTS", "NOBODY")
