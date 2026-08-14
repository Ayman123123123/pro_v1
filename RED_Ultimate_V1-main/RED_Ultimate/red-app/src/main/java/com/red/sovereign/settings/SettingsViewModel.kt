package com.red.sovereign.settings

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import java.io.File

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("younes_user_preferences", 0)
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

    private fun sanitizeVisibility(value: String) = value.takeIf { it in VISIBILITY }.orEmpty().ifBlank { "CONTACTS" }

    fun clearCache() {
        getApplication<Application>().cacheDir.listFiles()?.forEach(::deleteRecursivelySafe)
        cacheBytes = cacheSize(getApplication<Application>().cacheDir)
    }

    private fun update(value: YounesSettings) {
        state = value
        preferences.edit()
            .putFloat("font_scale", value.fontScale)
            .putBoolean("high_contrast", value.highContrast)
            .putBoolean("compact_mode", value.compactMode)
            .putBoolean("reduce_motion", value.reduceMotion)
            .putBoolean("read_receipts", value.readReceipts)
            .putBoolean("typing_indicators", value.typingIndicators)
            .putBoolean("link_previews", value.linkPreviews)
            .putBoolean("auto_download_wifi", value.autoDownloadWifi)
            .putBoolean("auto_download_mobile", value.autoDownloadMobile)
            .putInt("auto_download_limit_mb", value.autoDownloadLimitMb)
            .putBoolean("notification_preview", value.notificationPreview)
            .putBoolean("message_notifications", value.messageNotifications)
            .putBoolean("call_notifications", value.callNotifications)
            .putBoolean("data_saver_calls", value.dataSaverCalls)
            .putFloat("playback_speed", value.defaultPlaybackSpeed)
            .putBoolean("app_lock_enabled", value.appLockEnabled)
            .putBoolean("hide_last_seen", value.hideLastSeen)
            .putString("last_seen_visibility", value.lastSeenVisibility)
            .putString("profile_photo_visibility", value.profilePhotoVisibility)
            .putString("about_visibility", value.aboutVisibility)
            .putString("who_can_add_groups", value.whoCanAddToGroups)
            .putString("who_can_call", value.whoCanCall)
            .putBoolean("enter_to_send", value.enterToSend)
            .putBoolean("save_media_gallery", value.saveMediaToGallery)
            .putBoolean("auto_archive_muted", value.autoArchiveMuted)
            .putBoolean("group_notifications", value.groupNotifications)
            .putInt("lock_timeout_seconds", value.lockTimeoutSeconds)
            .apply()
        SettingsRuntime.update(value)
    }

    private fun load() = YounesSettings(
        fontScale = preferences.getFloat("font_scale", 1f),
        highContrast = preferences.getBoolean("high_contrast", false),
        compactMode = preferences.getBoolean("compact_mode", false),
        reduceMotion = preferences.getBoolean("reduce_motion", true),
        readReceipts = preferences.getBoolean("read_receipts", true),
        typingIndicators = preferences.getBoolean("typing_indicators", true),
        linkPreviews = preferences.getBoolean("link_previews", false),
        autoDownloadWifi = preferences.getBoolean("auto_download_wifi", true),
        autoDownloadMobile = preferences.getBoolean("auto_download_mobile", false),
        autoDownloadLimitMb = preferences.getInt("auto_download_limit_mb", 25),
        notificationPreview = preferences.getBoolean("notification_preview", false),
        messageNotifications = preferences.getBoolean("message_notifications", true),
        callNotifications = preferences.getBoolean("call_notifications", true),
        dataSaverCalls = preferences.getBoolean("data_saver_calls", true),
        defaultPlaybackSpeed = preferences.getFloat("playback_speed", 1f),
        appLockEnabled = preferences.getBoolean("app_lock_enabled", false),
        hideLastSeen = preferences.getBoolean("hide_last_seen", false),
        lastSeenVisibility = preferences.getString("last_seen_visibility", if (preferences.getBoolean("hide_last_seen", false)) "NOBODY" else "EVERYONE") ?: "EVERYONE",
        profilePhotoVisibility = preferences.getString("profile_photo_visibility", "EVERYONE") ?: "EVERYONE",
        aboutVisibility = preferences.getString("about_visibility", "EVERYONE") ?: "EVERYONE",
        whoCanAddToGroups = preferences.getString("who_can_add_groups", "CONTACTS") ?: "CONTACTS",
        whoCanCall = preferences.getString("who_can_call", "CONTACTS") ?: "CONTACTS",
        enterToSend = preferences.getBoolean("enter_to_send", false),
        saveMediaToGallery = preferences.getBoolean("save_media_gallery", false),
        autoArchiveMuted = preferences.getBoolean("auto_archive_muted", false),
        groupNotifications = preferences.getBoolean("group_notifications", true),
        lockTimeoutSeconds = preferences.getInt("lock_timeout_seconds", 15)
    ).also(SettingsRuntime::update)

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
    val lockTimeoutSeconds: Int = 15
) {
    val notificationEnabled: Boolean get() = messageNotifications
}

object SettingsRuntime {
    var current by mutableStateOf(YounesSettings()); private set

    fun initialize(application: Application) {
        val preferences = application.getSharedPreferences("younes_user_preferences", 0)
        update(YounesSettings(
            fontScale = preferences.getFloat("font_scale", 1f),
            highContrast = preferences.getBoolean("high_contrast", false),
            compactMode = preferences.getBoolean("compact_mode", false),
            reduceMotion = preferences.getBoolean("reduce_motion", true),
            readReceipts = preferences.getBoolean("read_receipts", true),
            typingIndicators = preferences.getBoolean("typing_indicators", true),
            linkPreviews = preferences.getBoolean("link_previews", false),
            autoDownloadWifi = preferences.getBoolean("auto_download_wifi", true),
            autoDownloadMobile = preferences.getBoolean("auto_download_mobile", false),
            autoDownloadLimitMb = preferences.getInt("auto_download_limit_mb", 25),
            notificationPreview = preferences.getBoolean("notification_preview", false),
            messageNotifications = preferences.getBoolean("message_notifications", true),
            callNotifications = preferences.getBoolean("call_notifications", true),
            dataSaverCalls = preferences.getBoolean("data_saver_calls", true),
            defaultPlaybackSpeed = preferences.getFloat("playback_speed", 1f),
            appLockEnabled = preferences.getBoolean("app_lock_enabled", false),
            hideLastSeen = preferences.getBoolean("hide_last_seen", false),
            lastSeenVisibility = preferences.getString("last_seen_visibility", if (preferences.getBoolean("hide_last_seen", false)) "NOBODY" else "EVERYONE") ?: "EVERYONE",
            profilePhotoVisibility = preferences.getString("profile_photo_visibility", "EVERYONE") ?: "EVERYONE",
            aboutVisibility = preferences.getString("about_visibility", "EVERYONE") ?: "EVERYONE",
            whoCanAddToGroups = preferences.getString("who_can_add_groups", "CONTACTS") ?: "CONTACTS",
            whoCanCall = preferences.getString("who_can_call", "CONTACTS") ?: "CONTACTS",
            enterToSend = preferences.getBoolean("enter_to_send", false),
            saveMediaToGallery = preferences.getBoolean("save_media_gallery", false),
            autoArchiveMuted = preferences.getBoolean("auto_archive_muted", false),
            groupNotifications = preferences.getBoolean("group_notifications", true),
            lockTimeoutSeconds = preferences.getInt("lock_timeout_seconds", 15)
        ))
    }

    fun update(value: YounesSettings) { current = value }
}

private val VISIBILITY = setOf("EVERYONE", "CONTACTS", "NOBODY")
