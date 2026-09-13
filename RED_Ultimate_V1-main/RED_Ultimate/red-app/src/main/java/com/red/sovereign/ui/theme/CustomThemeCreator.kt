package com.red.sovereign.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.red.sovereign.core.parseJsonMap
import org.json.JSONObject

/**
 * 🎨 CustomThemeCreator — صانع ومصدّر الثيمات والتنسيقات المخصصة
 *
 * يتيح للمستخدم تصميم ثيم خاص به، اختيار ألوان فقاعات الدردشة، والخلفيات،
 * وتصدير/استيراد حزم الثيمات بصيغة JSON `.redtheme`.
 */
data class CustomThemePackage(
    val name: String,
    val primaryColor: Color,
    val backgroundColor: Color,
    val outgoingBubbleColor: Color,
    val incomingBubbleColor: Color,
    val accentColor: Color,
    /** نمط الخلفية: PATTERN (زخرفة هندسية خافتة) أو SOLID (سادة). */
    val wallpaperStyle: String = WALLPAPER_PATTERN,
    /** صبغة خلفية خاصة بهذه المحادثة (ARGB) — null تعني صبغة الثيم العامة. */
    val wallpaperTintArgb: Long? = null,
    /** تعتيم فوق الزخرفة 0..1 — يرفع مقروئية النص على الخلفيات المزدحمة. */
    val wallpaperDim: Float = 0f,
    /** شفافية الزخرفة — الافتراضي 4% حتى لا تنافس النص. */
    val patternAlpha: Float = 0.04f
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("name", name)
            put("primaryColor", primaryColor.toArgb())
            put("backgroundColor", backgroundColor.toArgb())
            put("outgoingBubbleColor", outgoingBubbleColor.toArgb())
            put("incomingBubbleColor", incomingBubbleColor.toArgb())
            put("accentColor", accentColor.toArgb())
            put("wallpaperStyle", wallpaperStyle)
            wallpaperTintArgb?.let { put("wallpaperTintArgb", it) }
            put("wallpaperDim", wallpaperDim.toDouble())
            put("patternAlpha", patternAlpha.toDouble())
        }.toString(2)
    }

    companion object {
        const val WALLPAPER_PATTERN = "PATTERN"
        const val WALLPAPER_SOLID = "SOLID"

        fun fromJson(jsonStr: String): CustomThemePackage? {
            return runCatching {
                val map = parseJsonMap(jsonStr)
                // الافتراضيات = اللوحة السيادية الموحدة (كانت قيمًا قديمة متباينة).
                CustomThemePackage(
                    name = map["name"]?.toString() ?: "ثيم مخصص",
                    primaryColor = Color((map["primaryColor"] as? Number)?.toLong() ?: 0xFF14C79A),
                    backgroundColor = Color((map["backgroundColor"] as? Number)?.toLong() ?: 0xFF0A0F18),
                    outgoingBubbleColor = Color((map["outgoingBubbleColor"] as? Number)?.toLong() ?: 0xFF14304F),
                    incomingBubbleColor = Color((map["incomingBubbleColor"] as? Number)?.toLong() ?: 0xFF182533),
                    accentColor = Color((map["accentColor"] as? Number)?.toLong() ?: 0xFFE0B551),
                    wallpaperStyle = map["wallpaperStyle"]?.toString() ?: WALLPAPER_PATTERN,
                    wallpaperTintArgb = (map["wallpaperTintArgb"] as? Number)?.toLong(),
                    wallpaperDim = (map["wallpaperDim"] as? Number)?.toFloat() ?: 0f,
                    patternAlpha = (map["patternAlpha"] as? Number)?.toFloat() ?: 0.04f
                )
            }.getOrNull()
        }
    }
}

object CustomThemeStore {
    private const val PREF_KEY_CUSTOM_THEMES = "sovereign_custom_themes_json"

    fun saveCustomTheme(context: Context, theme: CustomThemePackage) {
        val prefs = context.getSharedPreferences("sovereign_themes", Context.MODE_PRIVATE)
        prefs.edit().putString("active_custom_theme", theme.toJson()).apply()
    }

    fun loadActiveCustomTheme(context: Context): CustomThemePackage? {
        val prefs = context.getSharedPreferences("sovereign_themes", Context.MODE_PRIVATE)
        val json = prefs.getString("active_custom_theme", null) ?: return null
        return CustomThemePackage.fromJson(json)
    }
}
