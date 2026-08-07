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
        defaultPlaybackSpeed = preferences.getFloat("playback_speed", 1f)
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
    val defaultPlaybackSpeed: Float = 1f
)

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
            defaultPlaybackSpeed = preferences.getFloat("playback_speed", 1f)
        ))
    }

    fun update(value: YounesSettings) { current = value }
}
