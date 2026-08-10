package com.red.core.theme

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 🎨 YOUNES Sovereign Theme System
 * نظام المظهر السيادي — ثيمات متعددة + Dark/Light/OLED + ألوان مخصصة
 */

// ━━━━━━━━━━━━ أنواع الثيمات ━━━━━━━━━━━━

enum class AppTheme(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    SOVEREIGN_DARK("سيادي داكن", Icons.Rounded.DarkMode),
    SOVEREIGN_LIGHT("سيادي فاتح", Icons.Rounded.LightMode),
    OLED_BLACK("OLED أسود حقيقي", Icons.Rounded.Brightness2),
    AUTO("تلقائي", Icons.Rounded.BrightnessAuto),
    YEMENI_GOLD("يمني ذهبي", Icons.Rounded.AutoAwesome),
    OCEAN_BLUE("أزرق محيطي", Icons.Rounded.Water),
    ROYAL_PURPLE("أرجواني ملكي", Icons.Rounded.Stars),
    EMERALD("زمردي", Icons.Rounded.Nature)
}

enum class AccentColor(val label: String, val color: Color, val emoji: String) {
    CYAN("سيادي سماوي", SovereignColors.Cyan, "💎"),
    GOLD("ذهبي ملكي", SovereignColors.Gold, "👑"),
    RED("أحمر سيادي", SovereignColors.LiveRed, "🔴"),
    PURPLE("أرجواني", SovereignColors.SpacePurple, "🟣"),
    GREEN("أخضر زمرد", SovereignColors.Success, "🟢"),
    ORANGE("برتقالي يمني", Color(0xFFF57C00), "🟠"),
    PINK("وردي", Color(0xFFEC407A), "🩷")
}

enum class FontSize(val label: String, val scale: Float) {
    SMALL("صغير", 0.85f),
    NORMAL("عادي", 1f),
    LARGE("كبير", 1.15f),
    EXTRA_LARGE("كبير جداً", 1.3f)
}

data class SovereignThemeConfig(
    val theme: AppTheme = AppTheme.SOVEREIGN_DARK,
    val accent: AccentColor = AccentColor.CYAN,
    val fontSize: FontSize = FontSize.NORMAL,
    val isAmoledBlack: Boolean = false,
    val isRtl: Boolean = true,
    val useDynamicColor: Boolean = false,
    val chatBubbleStyle: ChatBubbleStyle = ChatBubbleStyle.ROUNDED
)

enum class ChatBubbleStyle(val label: String) {
    ROUNDED("مدور"),
    SHARP("حاد"),
    TAIL("ذيل (واتساب)"),
    IOS("iOS")
}

// ━━━━━━━━━━━━ Color Schemes ━━━━━━━━━━━━

object SovereignSchemes {
    val sovereignDark = darkColorScheme(
        primary = SovereignColors.Cyan,
        secondary = SovereignColors.Gold,
        background = SovereignColors.Obsidian,
        surface = SovereignColors.SurfaceNavy,
        onPrimary = Color.Black,
        onSecondary = Color.Black,
        onBackground = Color.White,
        onSurface = Color.White,
        error = SovereignColors.Danger,
        onError = Color.White
    )

    val sovereignLight = lightColorScheme(
        primary = Color(0xFF0284C7),
        secondary = Color(0xFFD97706),
        background = Color(0xFFF8FAFC),
        surface = Color.White,
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color(0xFF0F172A),
        onSurface = Color(0xFF0F172A),
        error = SovereignColors.Danger,
        onError = Color.White
    )

    val oledBlack = darkColorScheme(
        primary = SovereignColors.Cyan,
        secondary = SovereignColors.Gold,
        background = Color.Black,
        surface = Color(0xFF0A0A0A),
        onPrimary = Color.Black,
        onSecondary = Color.Black,
        onBackground = Color.White,
        onSurface = Color.White,
        error = SovereignColors.Danger,
        onError = Color.White
    )

    val yemeniGold = darkColorScheme(
        primary = SovereignColors.Gold,
        secondary = SovereignColors.Cyan,
        background = Color(0xFF1A1000),
        surface = Color(0xFF2A1F00),
        onPrimary = Color.Black,
        onSecondary = Color.Black,
        onBackground = SovereignColors.Gold,
        onSurface = SovereignColors.Gold,
        error = SovereignColors.Danger,
        onError = Color.White
    )

    val oceanBlue = darkColorScheme(
        primary = Color(0xFF60A5FA),
        secondary = SovereignColors.Cyan,
        background = Color(0xFF0C1445),
        surface = Color(0xFF152268),
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color.White,
        onSurface = Color.White,
        error = SovereignColors.Danger,
        onError = Color.White
    )

    val royalPurple = darkColorScheme(
        primary = Color(0xFFA78BFA),
        secondary = Color(0xFFF472B6),
        background = Color(0xFF1A0A2E),
        surface = Color(0xFF2D1B4E),
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color.White,
        onSurface = Color.White,
        error = SovereignColors.Danger,
        onError = Color.White
    )

    val emerald = darkColorScheme(
        primary = Color(0xFF34D399),
        secondary = SovereignColors.Gold,
        background = Color(0xFF022C22),
        surface = Color(0xFF064E3B),
        onPrimary = Color.Black,
        onSecondary = Color.Black,
        onBackground = Color.White,
        onSurface = Color.White,
        error = SovereignColors.Danger,
        onError = Color.White
    )

    fun getScheme(theme: AppTheme): ColorScheme = when (theme) {
        AppTheme.SOVEREIGN_DARK -> sovereignDark
        AppTheme.SOVEREIGN_LIGHT -> sovereignLight
        AppTheme.OLED_BLACK -> oledBlack
        AppTheme.AUTO -> sovereignDark // يتبع النظام
        AppTheme.YEMENI_GOLD -> yemeniGold
        AppTheme.OCEAN_BLUE -> oceanBlue
        AppTheme.ROYAL_PURPLE -> royalPurple
        AppTheme.EMERALD -> emerald
    }
}

// ━━━━━━━━━━━━ شاشة إعدادات المظهر ━━━━━━━━━━━━

@Composable
fun SovereignThemeSettingsScreen(
    currentConfig: SovereignThemeConfig = SovereignThemeConfig(),
    onConfigChange: (SovereignThemeConfig) -> Unit = {},
    onBack: () -> Unit = {}
) {
    var config by remember { mutableStateOf(currentConfig) }

    Column(
        modifier = Modifier.fillMaxSize().background(SovereignColors.Obsidian).padding(16.dp)
    ) {
        // الرأس
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, null, tint = Color.White) }
            Spacer(Modifier.width(8.dp))
            Text("المظهر والثيم", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ─── الثيم ───
            item {
                SectionTitle("الثيم")
                Spacer(Modifier.height(8.dp))
                ThemeGrid(config.theme) { theme ->
                    config = config.copy(theme = theme)
                    onConfigChange(config)
                }
            }

            // ─── لون التمييز ───
            item {
                SectionTitle("لون التمييز")
                Spacer(Modifier.height(8.dp))
                AccentRow(config.accent) { accent ->
                    config = config.copy(accent = accent)
                    onConfigChange(config)
                }
            }

            // ─── حجم الخط ───
            item {
                SectionTitle("حجم الخط")
                Spacer(Modifier.height(8.dp))
                FontSizeRow(config.fontSize) { size ->
                    config = config.copy(fontSize = size)
                    onConfigChange(config)
                }
            }

            // ─── شكل فقاعة المحادثة ───
            item {
                SectionTitle("شكل فقاعة المحادثة")
                Spacer(Modifier.height(8.dp))
                ChatBubbleStyleRow(config.chatBubbleStyle) { style ->
                    config = config.copy(chatBubbleStyle = style)
                    onConfigChange(config)
                }
            }

            // ─── خيارات إضافية ───
            item {
                SectionTitle("خيارات إضافية")
                Spacer(Modifier.height(8.dp))

                SettingSwitch(
                    "OLED أسود حقيقي",
                    "خلفية سوداء #000000 لتوفير طاقة شاشات OLED",
                    config.isAmoledBlack,
                    Icons.Rounded.Brightness2
                ) {
                    config = config.copy(isAmoledBlack = it)
                    onConfigChange(config)
                }

                Spacer(Modifier.height(8.dp))

                SettingSwitch(
                    "من اليمين لليسار (RTL)",
                    "واجهة عربية كاملة",
                    config.isRtl,
                    Icons.Rounded.FormatTextdirectionRToL
                ) {
                    config = config.copy(isRtl = it)
                    onConfigChange(config)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = SovereignColors.Cyan)
}

@Composable
private fun ThemeGrid(selected: AppTheme, onSelect: (AppTheme) -> Unit) {
    androidx.compose.foundation.lazy.LazyVerticalGrid(
        columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
        modifier = Modifier.height(240.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(AppTheme.entries.size) { index ->
            val theme = AppTheme.entries[index]
            val isSelected = theme == selected
            val scheme = SovereignSchemes.getScheme(theme)

            Surface(
                onClick = { onSelect(theme) },
                shape = RoundedCornerShape(12.dp),
                color = scheme.surface,
                border = if (isSelected) BorderStroke(2.dp, SovereignColors.Cyan) else BorderStroke(1.dp, Color.Gray.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // معاينة مصغرة
                    Row(modifier = Modifier.height(32.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(scheme.primary))
                        Box(modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(scheme.secondary))
                        Box(modifier = Modifier.weight(1.5f).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(scheme.surface))
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(theme.icon, null, tint = if (isSelected) SovereignColors.Cyan else scheme.onSurface, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(theme.label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) SovereignColors.Cyan else scheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun AccentRow(selected: AccentColor, onSelect: (AccentColor) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll()) {
        AccentColor.entries.forEach { accent ->
            val isSelected = accent == selected
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(accent.color)
                        .border(3.dp, if (isSelected) Color.White else Color.Transparent, CircleShape)
                        .clickable { onSelect(accent) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) Icon(Icons.Rounded.Check, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(accent.emoji, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun FontSizeRow(selected: FontSize, onSelect: (FontSize) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FontSize.entries.forEach { size ->
            val isSelected = size == selected
            Surface(
                onClick = { onSelect(size) },
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) SovereignColors.Cyan.copy(alpha = 0.15f) else SovereignColors.SurfaceNavy,
                border = BorderStroke(1.dp, if (isSelected) SovereignColors.Cyan else Color.Gray.copy(alpha = 0.3f))
            ) {
                Text(
                    size.label,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    fontSize = (14.sp * size.scale),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) SovereignColors.Cyan else Color.White
                )
            }
        }
    }
}

@Composable
private fun ChatBubbleStyleRow(selected: ChatBubbleStyle, onSelect: (ChatBubbleStyle) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChatBubbleStyle.entries.forEach { style ->
            val isSelected = style == selected
            Surface(
                onClick = { onSelect(style) },
                shape = RoundedCornerShape(8.dp),
                color = if (isSelected) SovereignColors.Cyan.copy(alpha = 0.15f) else SovereignColors.SurfaceNavy,
                border = BorderStroke(1.dp, if (isSelected) SovereignColors.Cyan else Color.Gray.copy(alpha = 0.3f))
            ) {
                Text(
                    style.label,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) SovereignColors.Cyan else Color.White
                )
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    isChecked: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onToggle: (Boolean) -> Unit
) {
    Surface(shape = RoundedCornerShape(12.dp), color = SovereignColors.SurfaceNavy) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = SovereignColors.Cyan, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
                Text(description, fontSize = 11.sp, color = Color.Gray)
            }
            Switch(checked = isChecked, onCheckedChange = onToggle, colors = SwitchDefaults.colors(checkedTrackColor = SovereignColors.Cyan))
        }
    }
}

private fun Modifier.horizontalScroll() = this.then(
    androidx.compose.foundation.rememberScrollState().let { 
        androidx.compose.foundation.horizontalScroll(it) 
    }
)
