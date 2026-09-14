package com.red.sovereign.settings

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
    // تفعيل DeviceSettings الحقيقي (8 بنود): صوت/اهتزاز/LED + جودة الوسائط + وضع الاتصال.
    // تُحفظ في نفس Prefs (younes_user_preferences) — مصدر حقيقة واحد مع بقية الإعدادات.
    const val NOTIFICATION_SOUND = "notification_sound"
    const val NOTIFICATION_VIBRATION = "notification_vibration"
    const val NOTIFICATION_LED = "notification_led"
    const val MEDIA_QUALITY = "media_quality"
    const val CONNECTION_MODE = "connection_mode"
    // نغمة مكالمة RED + اهتزاز الرنين: يختارها المستخدم عبر RingtonePickerDialog
    // ويقرأها YounesCallService/GroupCallService قبل تشغيل الرنين.
    const val CALL_RINGTONE_URI = "call_ringtone_uri"
    const val CALL_VIBRATION = "call_vibration"
    // تفضيلات الصوت عند بدء المكالمة — كانت remember مؤقتة في CallSettingsScreen
    // وصارت حقولاً دائمة يقرأها AudioManager (prepareAudio) عند بدء كل مكالمة.
    const val AUTO_SPEAKER = "auto_speaker"
    const val BLUETOOTH_PRIORITY = "bluetooth_priority"
    const val AUTO_MUTE_ON_ENTRY = "auto_mute_on_entry"
    // سجل المكالمات: مدة الاحتفاظ بالأيام + المزامنة التلقائية مع الخادم.
    const val CALL_HISTORY_RETENTION = "call_history_retention"
    const val CALL_HISTORY_SYNC = "call_history_sync"
    // حد المدة الأقصى للمكالمة بالثواني (حد PSTN اليومي حُذف في المرحلة 8).
    const val MAX_CALL_DURATION = "max_call_duration"
    // التسجيل التلقائي للمكالمات — دائم (يُقرأ عند بدء التسجيل مع موافقة الطرفين).
    const val CALL_AUTO_RECORD = "call_auto_record"
    const val FONT_FAMILY = "font_family"
    const val BUBBLE_STYLE = "bubble_style"
    const val DND_ENABLED = "dnd_enabled"
    const val DND_START_MINUTES = "dnd_start_minutes"
    const val DND_END_MINUTES = "dnd_end_minutes"
    const val DND_ALLOW_FAVORITES = "dnd_allow_favorites"
    const val DND_ALLOW_REPEAT = "dnd_allow_repeat"
    const val DEV_TELEMETRY_ENABLED = "dev_telemetry_enabled"
    const val DEV_DEBUG_MODE = "dev_debug_mode"
    const val DEV_WEBRTC_LOGGING = "dev_webrtc_logging"
    const val DEV_FORCE_TURN = "dev_force_turn"
    const val DEV_VERBOSE_SIGNALING = "dev_verbose_signaling"
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
        customPrimary = getInt(Keys.CUSTOM_PRIMARY, defaults.customPrimary),
        notificationSound = getBoolean(Keys.NOTIFICATION_SOUND, defaults.notificationSound),
        notificationVibration = getBoolean(Keys.NOTIFICATION_VIBRATION, defaults.notificationVibration),
        notificationLed = getBoolean(Keys.NOTIFICATION_LED, defaults.notificationLed),
        mediaQuality = getString(Keys.MEDIA_QUALITY, defaults.mediaQuality) ?: defaults.mediaQuality,
        connectionMode = getString(Keys.CONNECTION_MODE, defaults.connectionMode) ?: defaults.connectionMode,
        callRingtoneUri = getString(Keys.CALL_RINGTONE_URI, defaults.callRingtoneUri)
            ?: defaults.callRingtoneUri,
        callVibration = getBoolean(Keys.CALL_VIBRATION, defaults.callVibration),
        autoSpeaker = getBoolean(Keys.AUTO_SPEAKER, defaults.autoSpeaker),
        bluetoothPriority = getBoolean(Keys.BLUETOOTH_PRIORITY, defaults.bluetoothPriority),
        autoMuteOnEntry = getBoolean(Keys.AUTO_MUTE_ON_ENTRY, defaults.autoMuteOnEntry),
        fontFamily = getString(Keys.FONT_FAMILY, defaults.fontFamily) ?: defaults.fontFamily,
        bubbleStyle = getString(Keys.BUBBLE_STYLE, defaults.bubbleStyle) ?: defaults.bubbleStyle,
        dndEnabled = getBoolean(Keys.DND_ENABLED, defaults.dndEnabled),
        dndStartMinutes = getInt(Keys.DND_START_MINUTES, defaults.dndStartMinutes),
        dndEndMinutes = getInt(Keys.DND_END_MINUTES, defaults.dndEndMinutes),
        dndAllowFavorites = getBoolean(Keys.DND_ALLOW_FAVORITES, defaults.dndAllowFavorites),
        dndAllowRepeat = getBoolean(Keys.DND_ALLOW_REPEAT, defaults.dndAllowRepeat),
        devTelemetryEnabled = getBoolean(Keys.DEV_TELEMETRY_ENABLED, defaults.devTelemetryEnabled),
        devDebugMode = getBoolean(Keys.DEV_DEBUG_MODE, defaults.devDebugMode),
        devWebrtcLogging = getBoolean(Keys.DEV_WEBRTC_LOGGING, defaults.devWebrtcLogging),
        devForceTurn = getBoolean(Keys.DEV_FORCE_TURN, defaults.devForceTurn),
        devVerboseSignaling = getBoolean(Keys.DEV_VERBOSE_SIGNALING, defaults.devVerboseSignaling),
        callHistoryRetentionDays = getInt(Keys.CALL_HISTORY_RETENTION, defaults.callHistoryRetentionDays),
        callHistorySync = getBoolean(Keys.CALL_HISTORY_SYNC, defaults.callHistorySync),
        maxCallDurationSeconds = getInt(Keys.MAX_CALL_DURATION, defaults.maxCallDurationSeconds),
        callAutoRecord = getBoolean(Keys.CALL_AUTO_RECORD, defaults.callAutoRecord)
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
        .putBoolean(Keys.NOTIFICATION_SOUND, value.notificationSound)
        .putBoolean(Keys.NOTIFICATION_VIBRATION, value.notificationVibration)
        .putBoolean(Keys.NOTIFICATION_LED, value.notificationLed)
        .putString(Keys.MEDIA_QUALITY, value.mediaQuality)
        .putString(Keys.CONNECTION_MODE, value.connectionMode)
        .putString(Keys.CALL_RINGTONE_URI, value.callRingtoneUri)
        .putBoolean(Keys.CALL_VIBRATION, value.callVibration)
        .putBoolean(Keys.AUTO_SPEAKER, value.autoSpeaker)
        .putBoolean(Keys.BLUETOOTH_PRIORITY, value.bluetoothPriority)
        .putBoolean(Keys.AUTO_MUTE_ON_ENTRY, value.autoMuteOnEntry)
        .putString(Keys.FONT_FAMILY, value.fontFamily)
        .putString(Keys.BUBBLE_STYLE, value.bubbleStyle)
        .putBoolean(Keys.DND_ENABLED, value.dndEnabled)
        .putInt(Keys.DND_START_MINUTES, value.dndStartMinutes)
        .putInt(Keys.DND_END_MINUTES, value.dndEndMinutes)
        .putBoolean(Keys.DND_ALLOW_FAVORITES, value.dndAllowFavorites)
        .putBoolean(Keys.DND_ALLOW_REPEAT, value.dndAllowRepeat)
        .putBoolean(Keys.DEV_TELEMETRY_ENABLED, value.devTelemetryEnabled)
        .putBoolean(Keys.DEV_DEBUG_MODE, value.devDebugMode)
        .putBoolean(Keys.DEV_WEBRTC_LOGGING, value.devWebrtcLogging)
        .putBoolean(Keys.DEV_FORCE_TURN, value.devForceTurn)
        .putBoolean(Keys.DEV_VERBOSE_SIGNALING, value.devVerboseSignaling)
        .putInt(Keys.CALL_HISTORY_RETENTION, value.callHistoryRetentionDays)
        .putBoolean(Keys.CALL_HISTORY_SYNC, value.callHistorySync)
        .putInt(Keys.MAX_CALL_DURATION, value.maxCallDurationSeconds)
        .putBoolean(Keys.CALL_AUTO_RECORD, value.callAutoRecord)
        .apply()
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    var state: YounesSettings by mutableStateOf(load()); private set
    var cacheBytes: Long by mutableStateOf(cacheSize(application.cacheDir)); private set
    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()
    fun clearSyncError() { _syncError.value = null }

    fun setFontScale(value: Float) = update(state.copy(fontScale = value.coerceIn(.85f, 1.30f)))
    fun setHighContrast(value: Boolean) = update(state.copy(highContrast = value))
    fun setCompactMode(value: Boolean) = update(state.copy(compactMode = value))
    fun setReduceMotion(value: Boolean) = update(state.copy(reduceMotion = value))
    fun setReadReceipts(value: Boolean) = update(state.copy(readReceipts = value)).also { pushPrivacy(PrivacySyncBody(readReceipts = if (value) "EVERYONE" else "NOBODY")) }
    fun setTypingIndicators(value: Boolean) = update(state.copy(typingIndicators = value)).also { pushPrivacy(PrivacySyncBody(typingIndicators = if (value) "EVERYONE" else "NOBODY")) }
    fun setLinkPreviews(value: Boolean) = update(state.copy(linkPreviews = value))
    fun setWifiDownload(value: Boolean) = update(state.copy(autoDownloadWifi = value))
    fun setMobileDownload(value: Boolean) = update(state.copy(autoDownloadMobile = value))
    fun setAutoDownloadLimit(value: Int) = update(state.copy(autoDownloadLimitMb = value.coerceIn(1, 99)))
    fun setNotificationPreview(value: Boolean) = update(state.copy(notificationPreview = value))
    fun setMessageNotifications(value: Boolean) = update(state.copy(messageNotifications = value))
    fun setCallNotifications(value: Boolean) = update(state.copy(callNotifications = value))
    fun setDataSaverCalls(value: Boolean) = update(state.copy(dataSaverCalls = value))
    // LEGENDARY FIX: قبول السرعات الست الموحدة (كانت ترفض 0.5/0.75/1.25 المختارة في المشغل فتعود للافتراضي)
    fun setDefaultPlaybackSpeed(value: Float) = update(state.copy(defaultPlaybackSpeed = value.takeIf { it in setOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f) } ?: 1f))
    fun setAppLockEnabled(value: Boolean) = update(state.copy(appLockEnabled = value))
    fun setHideLastSeen(value: Boolean) = update(state.copy(hideLastSeen = value, lastSeenVisibility = if (value) "NOBODY" else "EVERYONE")).also {
        val scope = if (value) "NOBODY" else "EVERYONE"
        pushPrivacy(PrivacySyncBody(lastSeen = scope, onlineStatus = scope))
    }
    fun setLastSeenVisibility(value: String) = update(state.copy(lastSeenVisibility = sanitizeVisibility(value), hideLastSeen = sanitizeVisibility(value) == "NOBODY")).also {
        val scope = sanitizeVisibility(value)
        pushPrivacy(PrivacySyncBody(lastSeen = scope, onlineStatus = scope))
    }
    fun setProfilePhotoVisibility(value: String) = update(state.copy(profilePhotoVisibility = sanitizeVisibility(value)))
    fun setAboutVisibility(value: String) = update(state.copy(aboutVisibility = sanitizeVisibility(value)))
    fun setWhoCanAddToGroups(value: String) = update(state.copy(whoCanAddToGroups = sanitizeVisibility(value)))
    fun setWhoCanCall(value: String) = update(state.copy(whoCanCall = sanitizeVisibility(value))).also {
        pushPrivacy(PrivacySyncBody(calls = sanitizeVisibility(value)))
    }
    fun setEnterToSend(value: Boolean) = update(state.copy(enterToSend = value))
    fun setSaveMediaToGallery(value: Boolean) = update(state.copy(saveMediaToGallery = value))
    fun setAutoArchiveMuted(value: Boolean) = update(state.copy(autoArchiveMuted = value))
    fun setGroupNotifications(value: Boolean) = update(state.copy(groupNotifications = value))
    fun setLockTimeoutSeconds(value: Int) = update(state.copy(lockTimeoutSeconds = value.coerceIn(5, 300)))
    fun setThemePreset(value: String) = update(state.copy(themePreset = value))
    fun setThemeMode(value: String) = update(state.copy(themeMode = value))
    fun setLiquidGlass(value: Boolean) = update(state.copy(liquidGlassEnabled = value))
    fun setCustomPrimary(value: Int) = update(state.copy(customPrimary = value))
    // ─── البنود الثمانية المفعّلة في DeviceSettingsScreen — كل setter يحفظ فوراً ──
    fun setNotificationSound(value: Boolean) = update(state.copy(notificationSound = value))
    fun setNotificationVibration(value: Boolean) = update(state.copy(notificationVibration = value))
    fun setNotificationLed(value: Boolean) = update(state.copy(notificationLed = value))
    fun setMediaQuality(value: String) = update(state.copy(mediaQuality = sanitizeMediaQuality(value)))
    fun setConnectionMode(value: String) = update(state.copy(connectionMode = sanitizeConnectionMode(value)))
    // ─── نغمة مكالمة RED + اهتزاز الرنين — تُحفظ فوراً ويقرأها YounesCallService/GroupCallService ──
    fun setCallRingtoneUri(value: String) = update(state.copy(callRingtoneUri = value.trim()))
    fun setCallVibration(value: Boolean) = update(state.copy(callVibration = value))
    // ─── تفضيلات الصوت عند بدء المكالمة — كانت remember في CallSettingsScreen وصارت دائمة ──
    fun setAutoSpeaker(value: Boolean) = update(state.copy(autoSpeaker = value))
    fun setBluetoothPriority(value: Boolean) = update(state.copy(bluetoothPriority = value))
    fun setAutoMuteOnEntry(value: Boolean) = update(state.copy(autoMuteOnEntry = value))
    // ─── سجل المكالمات: احتفاظ أيام (7/30/90/365) + مزامنة تلقائية — تُحفظ فوراً ───
    fun setCallHistoryRetention(value: Int) =
        update(state.copy(callHistoryRetentionDays = value.coerceIn(1, 365)))
    fun setCallHistorySync(value: Boolean) = update(state.copy(callHistorySync = value))
    // ─── حد مدة المكالمة بالثواني — يُحفظ فوراً ───
    fun setMaxCallDuration(value: Int) =
        update(state.copy(maxCallDurationSeconds = value.coerceIn(60, 7200)))
    // ─── التسجيل التلقائي — دائم عبر CALL_AUTO_RECORD ويُقرأ قبل CallRecordingManager.start ───
    fun setCallAutoRecord(value: Boolean) = update(state.copy(callAutoRecord = value))
    fun setFontFamily(value: String) = update(state.copy(fontFamily = sanitizeFontFamily(value)))
    fun setBubbleStyle(value: String) = update(state.copy(bubbleStyle = sanitizeBubbleStyle(value)))
    fun setDndEnabled(value: Boolean) = update(state.copy(dndEnabled = value))
    fun setDndSchedule(startMinutes: Int, endMinutes: Int) = update(state.copy(dndStartMinutes = startMinutes.coerceIn(0, 1439), dndEndMinutes = endMinutes.coerceIn(0, 1439)))
    fun setDndAllowFavorites(value: Boolean) = update(state.copy(dndAllowFavorites = value))
    fun setDndAllowRepeat(value: Boolean) = update(state.copy(dndAllowRepeat = value))
    fun setDevTelemetryEnabled(value: Boolean) = update(state.copy(devTelemetryEnabled = value))
    fun setDevDebugMode(value: Boolean) = update(state.copy(devDebugMode = value))
    fun setDevWebrtcLogging(value: Boolean) = update(state.copy(devWebrtcLogging = value))
    fun setDevForceTurn(value: Boolean) = update(state.copy(devForceTurn = value))
    fun setDevVerboseSignaling(value: Boolean) = update(state.copy(devVerboseSignaling = value))

    private fun sanitizeMediaQuality(value: String) =
        value.takeIf { it in MEDIA_QUALITIES }.orEmpty().ifBlank { "HIGH" }
    private fun sanitizeConnectionMode(value: String) =
        value.takeIf { it in CONNECTION_MODES }.orEmpty().ifBlank { "AUTO" }

    private fun sanitizeVisibility(value: String) = value.takeIf { it in VISIBILITY }.orEmpty().ifBlank { "CONTACTS" }
    private fun sanitizeFontFamily(value: String) = value.takeIf { it in FONT_FAMILIES }.orEmpty().ifBlank { "PLEX_ARABIC" }
    private fun sanitizeBubbleStyle(value: String) = value.takeIf { it in BUBBLE_STYLES }.orEmpty().ifBlank { "LUXURY" }

    /**
     * مرآة الخادم: المحلي يحكم العرض على هذا الجهاز، والخادم يحكم ما يراه
     * الآخرون — كان الاثنان يتباعدان بصمت (تختار "جهات الاتصال" محلياً
     * والخادم يبقى EVERYONE). دفع غير متزامن لا يعطل الواجهة.
     */
    @Serializable
    private data class PrivacySyncBody(
        val lastSeen: String? = null,
        val onlineStatus: String? = null,
        val readReceipts: String? = null,
        val calls: String? = null,
        val typingIndicators: String? = null
    )

    private val privacyJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private fun pushPrivacy(body: PrivacySyncBody) = viewModelScope.launch {
        runCatching {
            val client = AuthorizedApiClient(TokenStore(getApplication()))
            client.request("PUT", "/api/social/privacy", privacyJson.encodeToString(PrivacySyncBody.serializer(), body))
        }.onFailure {
            Log.e("SettingsViewModel", "PUT /api/social/privacy failed", it)
            _syncError.value = "تعذر مزامنة الخصوصية: ${it.message?.take(120)}"
        }.onSuccess {
            _syncError.value = null
        }
    }

    fun clearCache() {
        getApplication<Application>().cacheDir.listFiles()?.forEach { deleteRecursivelySafe(it) }
        cacheBytes = cacheSize(getApplication<Application>().cacheDir)
    }

    private fun update(value: YounesSettings) {
        state = value
        preferences.writeSettings(value)
        SettingsRuntime.update(value)
    }

    private fun load() = preferences.readSettings().also(SettingsRuntime::update)

    private fun cacheSize(root: File): Long = root.walkBottomUp().filter(File::isFile).sumOf(File::length)
    private fun deleteRecursivelySafe(file: File): Boolean =
        runCatching { file.deleteRecursively() }.onFailure {
            Log.w("SettingsViewModel", "deleteRecursively failed: ${file.absolutePath}", it)
        }.getOrDefault(false)
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
    val customPrimary: Int = 0,
    // الإشعارات: صوت/اهتزاز/LED تُطبق على قنوات red_messages/red_calls عبر NotificationPrefs.apply.
    val notificationSound: Boolean = true,
    val notificationVibration: Boolean = true,
    val notificationLed: Boolean = true,
    // جودة الوسائط: HIGH/BALANCED/SAVER — يقرأها MediaCompressor قبل الضغط والرفع.
    val mediaQuality: String = "HIGH",
    // وضع الاتصال: AUTO/WIFI/VPN — يقرأه ConnectionModePolicy قبل autoDiscover.
    val connectionMode: String = "AUTO",
    // نغمة مكالمة RED: Uri كنص ("": الافتراضية) — يختارها RingtonePickerDialog
    // ويقرأها YounesCallService.startRingtone و GroupCallService.startRingtone.
    val callRingtoneUri: String = "",
    // اهتزاز الرنين للمكالمات الواردة — false يُسكت Vibrator مع بقاء النغمة.
    val callVibration: Boolean = true,
    // مكبر الصوت التلقائي عند بدء المكالمة — يقرأه prepareAudio في الخدمتين.
    val autoSpeaker: Boolean = false,
    // أولوية البلوتوث: توجيه الصوت لجهاز BT متصل عند توفره قبل قرار المكبر.
    val bluetoothPriority: Boolean = true,
    // كتم الميكروفون تلقائياً عند دخول المكالمة — يُطبق بعد إنشاء محرك WebRTC.
    val autoMuteOnEntry: Boolean = false,
    val fontFamily: String = "PLEX_ARABIC",
    val bubbleStyle: String = "LUXURY",
    val dndEnabled: Boolean = false,
    val dndStartMinutes: Int = 1320,
    val dndEndMinutes: Int = 420,
    val dndAllowFavorites: Boolean = true,
    val dndAllowRepeat: Boolean = false,
    val devTelemetryEnabled: Boolean = true,
    val devDebugMode: Boolean = false,
    val devWebrtcLogging: Boolean = false,
    val devForceTurn: Boolean = false,
    val devVerboseSignaling: Boolean = false,
    // سجل المكالمات: احتفاظ أيام (يُطبَّق حذف الأقدم عبر CallHistoryViewModel.pruneExpired)
    // + مزامنة تلقائية مع GET /api/calls/history عند فتح السجل.
    val callHistoryRetentionDays: Int = 30,
    val callHistorySync: Boolean = true,
    // حد مدة المكالمة بالثواني (يُقرأ عبر CallLimitsPolicy أثناء المكالمة).
    val maxCallDurationSeconds: Int = 1800,
    // التسجيل التلقائي الدائم — يُقرأ في RecordingConsentDialog وقبل CallRecordingManager.start.
    val callAutoRecord: Boolean = false
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

/** قيم جودة الوسائط المسموحة — تُطابق MediaQualityPolicy.spec. */
val MEDIA_QUALITIES = setOf("HIGH", "BALANCED", "SAVER")
val FONT_FAMILIES = setOf("PLEX_ARABIC", "SYSTEM")
val BUBBLE_STYLES = setOf("LUXURY", "CLASSIC", "MINIMAL")

/** أوضاع الاتصال المسموحة — تُطابق ConnectionModePolicy. */
val CONNECTION_MODES = setOf("AUTO", "WIFI", "VPN")
