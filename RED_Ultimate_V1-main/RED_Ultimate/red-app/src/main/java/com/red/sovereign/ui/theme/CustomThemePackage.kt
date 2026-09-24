package com.red.sovereign.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.red.sovereign.core.parseJsonMap
import org.json.JSONObject

/**
 * حزمة ثيم مخصصة — تُخزَّن في SharedPreferences وتُطبّق على شاشات المحادثة.
 * تُنشأ عبر `CustomThemeStore.loadCustomThemePackage()` / `loadActiveCustomTheme()` وتُحفظ بـ `saveCustomTheme()`.
 * تدعم نمط الخلفية كـ Int (0/1) داخليًا مع توافق JSON كـ String "PATTERN"/"SOLID".
 */
data class CustomThemePackage(
    val name: String = "سيادي",
    val primaryColor: Color = YounesPrimary,
    val backgroundColor: Color = YounesVoid,
    val outgoingBubbleColor: Color = YounesBubbleOut,
    val incomingBubbleColor: Color = YounesBubbleIn,
    val accentColor: Color = YounesAccent,
    val patternAlpha: Float = 0.04f,
    val wallpaperDim: Float = 0f,
    val wallpaperStyle: Int = WALLPAPER_PATTERN,
    val wallpaperTintArgb: Long? = null
) {
    /**
     * يحوّل الحزمة إلى JSON قابل للتصدير `.redtheme`.
     * يحفظ wallpaperStyle كـ String للتوافق القديم + كـ Int جديد.
     */
    fun toJson(): String {
        return JSONObject().apply {
            put("name", name)
            // تخزن كـ UInt→Long موجبة حتى لا تُحفظ سالبة (0xFF... → 427…)
            put("primaryColor", primaryColor.toArgb().toUInt().toLong())
            put("backgroundColor", backgroundColor.toArgb().toUInt().toLong())
            put("outgoingBubbleColor", outgoingBubbleColor.toArgb().toUInt().toLong())
            put("incomingBubbleColor", incomingBubbleColor.toArgb().toUInt().toLong())
            put("accentColor", accentColor.toArgb().toUInt().toLong())
            // String للقديم، Int للجديد — كلاهما يُكتب لضمان قراءة أي إصدار
            put("wallpaperStyle", styleToString(wallpaperStyle))
            put("wallpaperStyleInt", wallpaperStyle)
            wallpaperTintArgb?.let { put("wallpaperTintArgb", it) }
            put("wallpaperDim", wallpaperDim.toDouble())
            put("patternAlpha", patternAlpha.toDouble())
        }.toString(2)
    }

    companion object {
        const val WALLPAPER_PATTERN = 0
        const val WALLPAPER_SOLID = 1

        /** String aliases للتوافق مع JSON القديم */
        const val WALLPAPER_PATTERN_STR = "PATTERN"
        const val WALLPAPER_SOLID_STR = "SOLID"

        // Deprecated String names بنفس الأسماء القديمة (كانت String) — نوفرها كـ别名 منفصلة لتجنب تضارب النوع
        @Deprecated("استعمل WALLPAPER_PATTERN (Int)", ReplaceWith("WALLPAPER_PATTERN"))
        const val WALLPAPER_PATTERN_STRING = "PATTERN"
        @Deprecated("استعمل WALLPAPER_SOLID (Int)", ReplaceWith("WALLPAPER_SOLID"))
        const val WALLPAPER_SOLID_STRING = "SOLID"

        /** تحويل Int ↔ String للنمط */
        fun styleToString(style: Int): String = if (style == WALLPAPER_SOLID) WALLPAPER_SOLID_STR else WALLPAPER_PATTERN_STR
        fun stringToStyle(str: String): Int = if (str == WALLPAPER_SOLID_STR) WALLPAPER_SOLID else WALLPAPER_PATTERN

        /** الحزمة الافتراضية السيادية — تُستعمل عند عدم وجود حزمة محفوظة. */
        val default = CustomThemePackage()

        fun fromJson(jsonStr: String): CustomThemePackage? {
            return runCatching {
                val map = parseJsonMap(jsonStr)
                // wallpaperStyle قد يكون String ("PATTERN"/"SOLID") أو Int (0/1)
                val rawStyle = map["wallpaperStyle"]
                val styleFromStringOrInt = when (rawStyle) {
                    is Number -> rawStyle.toInt().let { if (it == WALLPAPER_SOLID) WALLPAPER_SOLID else WALLPAPER_PATTERN }
                    is String -> when (rawStyle) {
                        WALLPAPER_SOLID_STR, "SOLID", "1" -> WALLPAPER_SOLID
                        else -> WALLPAPER_PATTERN
                    }
                    else -> WALLPAPER_PATTERN
                }
                // إذا وجد wallpaperStyleInt خذه كأولوية (الجديد)
                val finalStyle = (map["wallpaperStyleInt"] as? Number)?.toInt()?.let {
                    if (it == WALLPAPER_SOLID) WALLPAPER_SOLID else WALLPAPER_PATTERN
                } ?: styleFromStringOrInt

                CustomThemePackage(
                    name = map["name"]?.toString() ?: "ثيم مخصص",
                    // نقرأ كـ Long موجبة أو سالبة (توافق) — نحوّلها لـ ULong عبر & 0xFFFFFFFF
                    primaryColor = (map["primaryColor"] as? Number)?.let { Color(it.toLong() and 0xFFFFFFFFL) } ?: YounesPrimary,
                    backgroundColor = (map["backgroundColor"] as? Number)?.let { Color(it.toLong() and 0xFFFFFFFFL) } ?: YounesVoid,
                    outgoingBubbleColor = (map["outgoingBubbleColor"] as? Number)?.let { Color(it.toLong() and 0xFFFFFFFFL) } ?: YounesBubbleOut,
                    incomingBubbleColor = (map["incomingBubbleColor"] as? Number)?.let { Color(it.toLong() and 0xFFFFFFFFL) } ?: YounesBubbleIn,
                    accentColor = (map["accentColor"] as? Number)?.let { Color(it.toLong() and 0xFFFFFFFFL) } ?: YounesAccent,
                    wallpaperStyle = finalStyle,
                    wallpaperTintArgb = (map["wallpaperTintArgb"] as? Number)?.toLong(),
                    wallpaperDim = (map["wallpaperDim"] as? Number)?.toFloat() ?: 0f,
                    patternAlpha = (map["patternAlpha"] as? Number)?.toFloat() ?: 0.04f
                )
            }.getOrNull()
        }
    }
}
