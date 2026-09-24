package com.red.sovereign.ui.theme

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

/**
 * مخزن الثيمات المخصصة — يغلّف [AppThemeState] ويوفر واجهة مبسطة
 * للتحميل/الحفظ من SharedPreferences. يدعم:
 * - حفظ الحالة العامة (preset/mode/…) في "younes_custom_theme"
 * - حفظ حزمة الثيم الكاملة (ألوان فقاعات + خلفية) بنفس الملف + JSON قديم
 *   في "sovereign_themes" للتوافق مع إصدارات سابقة.
 */
object CustomThemeStore {

    private const val PREFS_NAME = "younes_custom_theme"
    private const val PREFS_OLD = "sovereign_themes"
    private const val KEY_OLD_JSON = "active_custom_theme"
    private const val KEY_PRESET = "preset_ordinal"
    private const val KEY_MODE = "mode_ordinal"
    private const val KEY_HIGH_CONTRAST = "high_contrast"
    private const val KEY_LIQUID_GLASS = "liquid_glass"
    private const val KEY_FONT_SCALE = "font_scale"
    private const val KEY_CUSTOM_PRIMARY = "custom_primary_argb"

    // ——— حالة الثيم العامة (Snapshot) ———

    fun loadAppThemeSnapshot(context: Context): AppThemeStateSnapshot {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val presetOrdinal = prefs.getInt(KEY_PRESET, AppThemePreset.SOVEREIGN.ordinal)
        val modeOrdinal = prefs.getInt(KEY_MODE, AppThemeMode.SYSTEM.ordinal)
        val highContrast = prefs.getBoolean(KEY_HIGH_CONTRAST, false)
        val liquidGlass = prefs.getBoolean(KEY_LIQUID_GLASS, true)
        val fontScale = prefs.getFloat(KEY_FONT_SCALE, 1.0f)
        val customPrimaryArgb = prefs.getInt(KEY_CUSTOM_PRIMARY, 0)

        val preset = AppThemePreset.values().getOrNull(presetOrdinal) ?: AppThemePreset.SOVEREIGN
        val mode = AppThemeMode.values().getOrNull(modeOrdinal) ?: AppThemeMode.SYSTEM
        val customPrimary = if (customPrimaryArgb != 0) Color(customPrimaryArgb.toLong() and 0xFFFFFFFFL) else null

        return AppThemeStateSnapshot(preset, mode, highContrast, liquidGlass, fontScale, customPrimary)
    }

    fun saveAppThemeSnapshot(context: Context, snapshot: AppThemeStateSnapshot) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        prefs.putInt(KEY_PRESET, snapshot.preset.ordinal)
        prefs.putInt(KEY_MODE, snapshot.mode.ordinal)
        prefs.putBoolean(KEY_HIGH_CONTRAST, snapshot.highContrast)
        prefs.putBoolean(KEY_LIQUID_GLASS, snapshot.liquidGlass)
        prefs.putFloat(KEY_FONT_SCALE, snapshot.fontScale)
        if (snapshot.customPrimary != null) {
            prefs.putInt(KEY_CUSTOM_PRIMARY, snapshot.customPrimary.toArgb())
        } else {
            prefs.remove(KEY_CUSTOM_PRIMARY)
        }
        prefs.apply()
    }

    // توافق قديم: كانت تُسمى loadActiveCustomTheme/saveActiveCustomTheme للـ Snapshot
    @Deprecated("Use loadAppThemeSnapshot", ReplaceWith("loadAppThemeSnapshot(context)"))
    fun loadSnapshot(context: Context): AppThemeStateSnapshot = loadAppThemeSnapshot(context)

    @Deprecated("Use saveAppThemeSnapshot", ReplaceWith("saveAppThemeSnapshot(context, snapshot)"))
    fun saveActiveCustomTheme(context: Context, snapshot: AppThemeStateSnapshot) = saveAppThemeSnapshot(context, snapshot)

    // توافق إضافي: اسم قديم للـ Snapshot كان loadActiveCustomTheme — نوفر alias باسم مختلف لتجنب تضارب overload
    @Deprecated("Use loadAppThemeSnapshot", ReplaceWith("loadAppThemeSnapshot(context)"))
    fun loadThemeSnapshot(context: Context): AppThemeStateSnapshot = loadAppThemeSnapshot(context)

    /**
     * يحفظ حزمة ثيم مخصصة كاملة (للخلفيات والزخارف).
     * يكتب في:
     * - "younes_custom_theme" مفاتيح منفصلة (الجديد)
     * - "sovereign_themes" JSON (القديم) للتوافق
     */
    fun saveCustomTheme(context: Context, themePackage: CustomThemePackage) {
        // الجديد: مفاتيح منفصلة
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
        prefs.putString("custom_theme_name", themePackage.name)
        prefs.putInt("custom_theme_primary", themePackage.primaryColor.toArgb())
        prefs.putInt("custom_theme_background", themePackage.backgroundColor.toArgb())
        prefs.putInt("custom_theme_outgoing_bubble", themePackage.outgoingBubbleColor.toArgb())
        prefs.putInt("custom_theme_incoming_bubble", themePackage.incomingBubbleColor.toArgb())
        prefs.putInt("custom_theme_accent", themePackage.accentColor.toArgb())
        prefs.putFloat("custom_theme_pattern_alpha", themePackage.patternAlpha)
        prefs.putFloat("custom_theme_wallpaper_dim", themePackage.wallpaperDim)
        prefs.putInt("custom_theme_wallpaper_style", themePackage.wallpaperStyle)
        if (themePackage.wallpaperTintArgb != null) {
            prefs.putLong("custom_theme_wallpaper_tint", themePackage.wallpaperTintArgb)
        } else {
            prefs.remove("custom_theme_wallpaper_tint")
        }
        prefs.apply()
        // القديم: JSON في sovereign_themes للتوافق مع نسخ قديمة تقرأ active_custom_theme
        runCatching {
            val oldPrefs = context.getSharedPreferences(PREFS_OLD, Context.MODE_PRIVATE).edit()
            oldPrefs.putString(KEY_OLD_JSON, themePackage.toJson())
            oldPrefs.apply()
        }
    }

    /**
     * يحمل حزمة ثيم مخصصة محفوظة أو يعيد null إذا لم توجد.
     * يجرّب أولًا المفاتيح الجديدة، ثم يسقط إلى JSON القديم.
     */
    fun loadCustomThemePackage(context: Context): CustomThemePackage? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString("custom_theme_name", null)
        if (name != null) {
            // قراءة من المفاتيح الجديدة — تدعم تخزين Int (toArgb) و Long (value) للتوافق
            val hasPrimary = prefs.contains("custom_theme_primary")
            if (hasPrimary) {
                fun readColor(key: String, fallback: Color): Color {
                    return try {
                        // جرّب Long أولًا (قديم كان value.toLong())
                        val longVal = prefs.getLong(key, Long.MIN_VALUE)
                        if (longVal != Long.MIN_VALUE) Color(longVal and 0xFFFFFFFFL)
                        else {
                            val intVal = prefs.getInt(key, fallback.toArgb())
                            Color(intVal.toLong() and 0xFFFFFFFFL)
                        }
                    } catch (_: ClassCastException) {
                        // المفتاح مخزن كـ Int
                        val intVal = prefs.getInt(key, fallback.toArgb())
                        Color(intVal.toLong() and 0xFFFFFFFFL)
                    }
                }
                return CustomThemePackage(
                    name = name,
                    primaryColor = readColor("custom_theme_primary", YounesPrimary),
                    backgroundColor = readColor("custom_theme_background", YounesVoid),
                    outgoingBubbleColor = readColor("custom_theme_outgoing_bubble", YounesBubbleOut),
                    incomingBubbleColor = readColor("custom_theme_incoming_bubble", YounesBubbleIn),
                    accentColor = readColor("custom_theme_accent", YounesAccent),
                    patternAlpha = prefs.getFloat("custom_theme_pattern_alpha", 0.04f),
                    wallpaperDim = prefs.getFloat("custom_theme_wallpaper_dim", 0f),
                    wallpaperStyle = prefs.getInt("custom_theme_wallpaper_style", CustomThemePackage.WALLPAPER_PATTERN),
                    wallpaperTintArgb = if (prefs.contains("custom_theme_wallpaper_tint")) prefs.getLong("custom_theme_wallpaper_tint", -1L).takeIf { it != -1L } else null
                )
            }
        }
        // سقوط إلى JSON القديم
        return runCatching {
            val oldPrefs = context.getSharedPreferences(PREFS_OLD, Context.MODE_PRIVATE)
            val json = oldPrefs.getString(KEY_OLD_JSON, null) ?: return null
            CustomThemePackage.fromJson(json)
        }.getOrNull()
    }

    /**
     * Alias للتوافق مع الكود القديم الذي كان يستدعي loadActiveCustomTheme():CustomThemePackage?
     */
    fun loadActiveCustomTheme(context: Context): CustomThemePackage? = loadCustomThemePackage(context)

    data class AppThemeStateSnapshot(
        val preset: AppThemePreset,
        val mode: AppThemeMode,
        val highContrast: Boolean,
        val liquidGlass: Boolean,
        val fontScale: Float,
        val customPrimary: Color?
    )
}

/**
 * حالة الثيم الحالية — نقطة الحقيقة الوحيدة لإعدادات المظهر.
 * مُعرَّفة هنا لتجنب التبعيات الدائرية ولتكون قابلة للاستيراد من أي مكان.
 */
object AppThemeState {
    var currentPreset by mutableStateOf(AppThemePreset.SOVEREIGN)
    var themeMode by mutableStateOf(AppThemeMode.SYSTEM)
    var highContrast by mutableStateOf(false)
    var liquidGlassEnabled by mutableStateOf(true)
    var reduceMotion by mutableStateOf(false)
    var fontScale by mutableStateOf(1.0f)
    var customPrimary by mutableStateOf<Color?>(null)

    // 6 ألوان مقترحة للمخصص تضمن ≥4.5:1
    val customPresets = listOf(
        YounesPrimary to "زمرد يونس",
        YounesAccent to "ذهب إمبراطوري",
        YounesCobalt to "أزرق ملكي",
        Color(0xFFE53935) to "أحمر حي",
        Color(0xFF8E24AA) to "بنفسجي ملكي",
        Color(0xFF00ACC1) to "تركواز"
    )
}

/**
 * أوضاع المظهر المدعومة.
 */
enum class AppThemeMode(val label: String) {
    LIGHT("فاتح"),
    DARK("ليلي"),
    SYSTEM("حسب النظام")
}

/**
 * القوالب الجاهزة للهوية البصرية.
 */
enum class AppThemePreset(val label: String, val description: String) {
    SOVEREIGN("يونس السيادي", "أسود ملكي مع أخضر زمردي ولمسات ذهبية — الهوية الأصلية"),
    TELEGRAM_DARK("تلجرام الكحلي", "أزرق تلجرام الأنيق مع كحلي داكن"),
    WHATSAPP_DARK("واتساب الزمردي", "أخضر واتساب الكلاسيكي المريح للعين"),
    OLED_BLACK("أوليد فائق السواد", "سواد تام 100% لتوفير الطاقة وأقصى تباين"),
    DYNAMIC("ديناميكي", "ألوان مستخرجة من خلفية الهاتف — Material You (أندرويد 12+)"),
    CUSTOM("مخصص", "اختر لونك بنفسك مع حارس تباين ذكي")
}

/**
 * مستويات الزجاج السائل — ضبابية 8/20/40 فقط على nav/sheets.
 */
object SovereignGlassTier {
    val NavBar = 8.dp
    val Card = 20.dp
    val Sheet = 40.dp

    @Composable
    fun fallback(isDark: Boolean, tier: androidx.compose.ui.unit.Dp): Color {
        val scheme = MaterialTheme.colorScheme
        return when (tier) {
            NavBar -> scheme.surface.copy(alpha = if (isDark) 0.85f else 0.88f)
            Card -> scheme.surfaceContainerHigh.copy(alpha = if (isDark) 0.92f else 0.95f)
            else -> scheme.surfaceContainerHighest.copy(alpha = 0.97f)
        }
    }
}
