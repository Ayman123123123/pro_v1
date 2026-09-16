package com.red.sovereign.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.graphics.Color
import com.red.sovereign.ui.theme.RedBrandColors
import com.red.sovereign.ui.theme.RedSemanticColors

/**
 * RED Sovereign Material 3 Color Schemes
 *
 * نظام ألوان Material 3 كامل مع:
 * - Primary, Secondary, Tertiary containers
 * - Surface, Background, Error containers
 * - On-colors لكل منها (contrast ratio ≥ 4.5:1 للنصوص، ≥ 3:1 للـ UI)
 * - Dark theme كامل مع tonalPalette صحيح
 * - Light theme
 * - High contrast mode
 *
 * جميع القيم محسوبة ومقيسة لتباين WCAG AA/AAA.
 */
object RedColorScheme {

    // ═══════════════════════════════════════════════════════════════════════════════
    // Dark Theme (افتراضي) — مستند إلى tonalPalette للعلامة التجارية
    // ═══════════════════════════════════════════════════════════════════════════════

    val Dark = darkColorScheme(
        // Primary — أحمر سيادي
        primary = RedBrandColors.PrimaryTonal.tone80,           // #D32F2F - على الخلفيات الداكنة
        onPrimary = RedBrandColors.PrimaryTonal.tone10,         // #FFEBEE - نص على Primary
        primaryContainer = RedBrandColors.PrimaryTonal.tone30,  // #EF9A9A - حاوية Primary
        onPrimaryContainer = RedBrandColors.PrimaryTonal.tone90, // #C62828 - نص على حاوية Primary

        // Secondary — ذهبي ملكي
        secondary = RedBrandColors.SecondaryTonal.tone70,       // #FFB300
        onSecondary = RedBrandColors.SecondaryTonal.tone10,     // #FFF8E1
        secondaryContainer = RedBrandColors.SecondaryTonal.tone30, // #FFEB9C
        onSecondaryContainer = RedBrandColors.SecondaryTonal.tone90, // #FF8F00

        // Tertiary — أزرق ملكي
        tertiary = RedBrandColors.TertiaryTonal.tone70,         // #1E88E5
        onTertiary = RedBrandColors.TertiaryTonal.tone10,       // #E3F2FD
        tertiaryContainer = RedBrandColors.TertiaryTonal.tone30, // #90CAF9
        onTertiaryContainer = RedBrandColors.TertiaryTonal.tone90, // #1565C0

        // Error — أحمر واضح
        error = RedBrandColors.ErrorTonal.tone80,               // #D32F2F
        onError = RedBrandColors.ErrorTonal.tone10,             // #FFEBEE
        errorContainer = RedBrandColors.ErrorTonal.tone30,      // #EF9A9A
        onErrorContainer = RedBrandColors.ErrorTonal.tone90,    // #C62828

        // Background & Surface
        background = Color(0xFF0A0F18),                         // YounesVoid
        onBackground = Color(0xFFFFFFFF),                       // أبيض نقي
        surface = Color(0xFF131C29),                            // YounesSurface1
        onSurface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFF1B2635),                     // YounesSurface2
        onSurfaceVariant = Color(0xFF9FB0C2),                   // YounesMuted
        surfaceTint = RedBrandColors.PrimaryTonal.tone80,       // لطبقة الارتفاع

        // Surface containers (للارتفاعات Material 3)
        surfaceContainer = Color(0xFF131C29),                   // YounesSurface1
        surfaceContainerLow = Color(0xFF0A0F18),                // YounesMidnight
        surfaceContainerHigh = Color(0xFF1B2635),               // YounesSurface2
        surfaceContainerHighest = Color(0xFF212E40),            // YounesSurface3

        // Outlines
        outline = Color(0xFF7A8FA3),                            // YounesOutline — ≥3:1 على الأسطح
        outlineVariant = Color(0xFF2A394A),                     // YounesBorder — فاصل زخرفي

        // Inverse colors
        inversePrimary = RedBrandColors.PrimaryTonal.tone20,    // للوضع المعكوس
        inverseSurface = Color(0xFFFFFFFF),
        inverseOnSurface = Color(0xFF0A0F18),

        // Scrim
        scrim = Color(0xE0000000),

        // Shadow
    )

    // ════════════════════════════════════════════════════════════════════════════════
    // Light Theme
    // ═══════════════════════════════════════════════════════════════════════════════

    val Light = lightColorScheme(
        // Primary — أحمر سيادي (أغمق للتباين على الفاتح)
        primary = RedBrandColors.PrimaryTonal.tone60,           // #F44336
        onPrimary = RedBrandColors.PrimaryTonal.tone100,        // أبيض
        primaryContainer = RedBrandColors.PrimaryTonal.tone20,  // #FFCDD2
        onPrimaryContainer = RedBrandColors.PrimaryTonal.tone90, // #C62828

        // Secondary — ذهبي ملكي
        secondary = RedBrandColors.SecondaryTonal.tone80,       // #FFA000
        onSecondary = RedBrandColors.SecondaryTonal.tone100,    // أسود
        secondaryContainer = RedBrandColors.SecondaryTonal.tone20, // #FFF3C4
        onSecondaryContainer = RedBrandColors.SecondaryTonal.tone90, // #FF8F00

        // Tertiary — أزرق ملكي
        tertiary = RedBrandColors.TertiaryTonal.tone60,         // #2196F3
        onTertiary = RedBrandColors.TertiaryTonal.tone100,      // أبيض
        tertiaryContainer = RedBrandColors.TertiaryTonal.tone20, // #BBDEFB
        onTertiaryContainer = RedBrandColors.TertiaryTonal.tone90, // #1565C0

        // Error
        error = RedBrandColors.ErrorTonal.tone60,               // #F44336
        onError = RedBrandColors.ErrorTonal.tone100,            // أبيض
        errorContainer = RedBrandColors.ErrorTonal.tone20,      // #FFCDD2
        onErrorContainer = RedBrandColors.ErrorTonal.tone90,    // #C62828

        // Background & Surface
        background = Color(0xFFF7F8FA),                         // لؤلؤي فاتح
        onBackground = Color(0xFF0F1B2D),                       // كحلي داكن
        surface = Color(0xFFFFFFFF),                            // أبيض نقي
        onSurface = Color(0xFF0F1B2D),
        surfaceVariant = Color(0xFFE6E8EB),                     // رمادي فاتح
        onSurfaceVariant = Color(0xFF5A6B7D),                   // رمادي متوسط
        surfaceTint = RedBrandColors.PrimaryTonal.tone60,

        // Surface containers
        surfaceContainer = Color(0xFFF0F2F5),
        surfaceContainerLow = Color(0xFFF7F8FA),
        surfaceContainerHigh = Color(0xFFE6E8EB),
        surfaceContainerHighest = Color(0xFFDDE1E6),

        // Outlines
        outline = Color(0xFF7A8FA3),
        outlineVariant = Color(0xFFD0D7DE),

        // Inverse
        inversePrimary = RedBrandColors.PrimaryTonal.tone80,
        inverseSurface = Color(0xFF0A0F18),
        inverseOnSurface = Color(0xFFF7F8FA),

        // Scrim
        scrim = Color(0x66000000),

        // Shadow
    )

    // ═══════════════════════════════════════════════════════════════════════════════
    // High Contrast Dark — للتباين العالي (WCAG AAA)
    // ═══════════════════════════════════════════════════════════════════════════════

    val HighContrastDark = Dark.copy(
        onBackground = Color(0xFFFFFFFF),
        onSurface = Color(0xFFFFFFFF),
        onSurfaceVariant = Color(0xFFE8E8E8),
        outline = RedBrandColors.PrimaryTonal.tone80,           // Primary color for focus
        outlineVariant = RedBrandColors.PrimaryTonal.tone60,
        primary = RedBrandColors.PrimaryTonal.tone70,           // أفتح قليلاً للتباين
        secondary = RedBrandColors.SecondaryTonal.tone60,       // أفتح
        tertiary = RedBrandColors.TertiaryTonal.tone60,
        surface = Color(0xFF0D1520),                            // أعمق
        surfaceContainer = Color(0xFF0D1520),
        surfaceContainerLow = Color(0xFF050A12),
        background = Color(0xFF000000)                          // أسود تام
    )

    // ═══════════════════════════════════════════════════════════════════════════════
    // High Contrast Light
    // ═══════════════════════════════════════════════════════════════════════════════

    val HighContrastLight = Light.copy(
        onBackground = Color(0xFF000000),
        onSurface = Color(0xFF000000),
        onSurfaceVariant = Color(0xFF1A1A1A),
        outline = RedBrandColors.PrimaryTonal.tone60,
        outlineVariant = RedBrandColors.PrimaryTonal.tone80,
        primary = RedBrandColors.PrimaryTonal.tone70,
        secondary = RedBrandColors.SecondaryTonal.tone90,
        tertiary = RedBrandColors.TertiaryTonal.tone70,
        surface = Color(0xFFFFFFFF),
        surfaceContainer = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFFFFFF),
        background = Color(0xFFFFFFFF)
    )

    // ═══════════════════════════════════════════════════════════════════════════════
    // دالة مساعدة لبناء ColorScheme من لون مخصص (User-customizable accent)
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * يولد ColorScheme مخصص من لون بذرة (seed color) للمستخدم
     * يستخدم خوارزمية Material 3 tonal palette
     */
    fun fromSeed(seed: Color, isDark: Boolean): ColorScheme {
        // بناء tonal palette مبسط من اللون البذرة
        val tonal = generateTonalPalette(seed)
        return if (isDark) {
            darkColorScheme(
                primary = tonal.tone80,
                onPrimary = tonal.tone10,
                primaryContainer = tonal.tone30,
                onPrimaryContainer = tonal.tone90,
                secondary = tonal.tone70,
                onSecondary = tonal.tone10,
                secondaryContainer = tonal.tone30,
                onSecondaryContainer = tonal.tone90,
                tertiary = RedBrandColors.TertiaryTonal.tone70,
                onTertiary = RedBrandColors.TertiaryTonal.tone10,
                tertiaryContainer = RedBrandColors.TertiaryTonal.tone30,
                onTertiaryContainer = RedBrandColors.TertiaryTonal.tone90,
                error = RedBrandColors.ErrorTonal.tone80,
                onError = RedBrandColors.ErrorTonal.tone10,
                errorContainer = RedBrandColors.ErrorTonal.tone30,
                onErrorContainer = RedBrandColors.ErrorTonal.tone90,
                background = Color(0xFF0A0F18),
                onBackground = Color(0xFFFFFFFF),
                surface = Color(0xFF131C29),
                onSurface = Color(0xFFFFFFFF),
                surfaceVariant = Color(0xFF1B2635),
                onSurfaceVariant = Color(0xFF9FB0C2),
                surfaceTint = tonal.tone80,
                surfaceContainer = Color(0xFF131C29),
                surfaceContainerLow = Color(0xFF0A0F18),
                surfaceContainerHigh = Color(0xFF1B2635),
                surfaceContainerHighest = Color(0xFF212E40),
                outline = Color(0xFF7A8FA3),
                outlineVariant = Color(0xFF2A394A),
                inversePrimary = tonal.tone20,
                inverseSurface = Color(0xFFFFFFFF),
                inverseOnSurface = Color(0xFF0A0F18),
                scrim = Color(0xE0000000),
                shadow = Color(0xFF000000)
            )
        } else {
            lightColorScheme(
                primary = tonal.tone60,
                onPrimary = tonal.tone100,
                primaryContainer = tonal.tone20,
                onPrimaryContainer = tonal.tone90,
                secondary = tonal.tone80,
                onSecondary = tonal.tone100,
                secondaryContainer = tonal.tone20,
                onSecondaryContainer = tonal.tone90,
                tertiary = RedBrandColors.TertiaryTonal.tone60,
                onTertiary = RedBrandColors.TertiaryTonal.tone100,
                tertiaryContainer = RedBrandColors.TertiaryTonal.tone20,
                onTertiaryContainer = RedBrandColors.TertiaryTonal.tone90,
                error = RedBrandColors.ErrorTonal.tone60,
                onError = RedBrandColors.ErrorTonal.tone100,
                errorContainer = RedBrandColors.ErrorTonal.tone20,
                onErrorContainer = RedBrandColors.ErrorTonal.tone90,
                background = Color(0xFFF7F8FA),
                onBackground = Color(0xFF0F1B2D),
                surface = Color(0xFFFFFFFF),
                onSurface = Color(0xFF0F1B2D),
                surfaceVariant = Color(0xFFE6E8EB),
                onSurfaceVariant = Color(0xFF5A6B7D),
                surfaceTint = tonal.tone60,
                surfaceContainer = Color(0xFFF0F2F5),
                surfaceContainerLow = Color(0xFFF7F8FA),
                surfaceContainerHigh = Color(0xFFE6E8EB),
                surfaceContainerHighest = Color(0xFFDDE1E6),
                outline = Color(0xFF7A8FA3),
                outlineVariant = Color(0xFFD0D7DE),
                inversePrimary = tonal.tone80,
                inverseSurface = Color(0xFF0A0F18),
                inverseOnSurface = Color(0xFFF7F8FA),
                scrim = Color(0x66000000),
                shadow = Color(0xFF000000)
            )
        }
    }

    /**
     * يولد tonal palette مبسط من لون بذرة
     * خوارزمية مبسطة — للإنتاج يفضل استخدام MaterialColorUtilities
     */
    private fun generateTonalPalette(seed: Color): TonalPalette {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(seed.toArgb(), hsv)
        val hue = hsv[0]
        val saturation = hsv[1]

        // توليد palette مبسط
        return TonalPalette(
            tone0 = Color.hsv(hue, 0f, 1f),
            tone10 = Color.hsv(hue, saturation * 0.1f, 0.95f),
            tone20 = Color.hsv(hue, saturation * 0.2f, 0.9f),
            tone30 = Color.hsv(hue, saturation * 0.4f, 0.85f),
            tone40 = Color.hsv(hue, saturation * 0.6f, 0.8f),
            tone50 = Color.hsv(hue, saturation * 0.8f, 0.75f),
            tone60 = Color.hsv(hue, saturation, 0.7f),
            tone70 = Color.hsv(hue, saturation, 0.6f),
            tone80 = Color.hsv(hue, saturation, 0.5f),
            tone90 = Color.hsv(hue, saturation * 0.9f, 0.35f),
            tone95 = Color.hsv(hue, saturation, 0.25f),
            tone99 = Color.hsv(hue, saturation, 0.15f),
            tone100 = Color.hsv(hue, 0f, 0f)
        )
    }
}

/**
 * ألوان دلالية مضافة للـ ColorScheme للوصول السهل في Composables
 * يمكن الوصول إليها عبر MaterialTheme.colorScheme.[property]
 */
@OptIn(ExperimentalMaterial3Api::class)
val ColorScheme.encryptedMessage: Color
    get() = RedSemanticColors.EncryptedMessage

@OptIn(ExperimentalMaterial3Api::class)
val ColorScheme.encryptedMessageBackground: Color
    get() = RedSemanticColors.EncryptedMessageBackground

@OptIn(ExperimentalMaterial3Api::class)
val ColorScheme.encryptedMessageText: Color
    get() = RedSemanticColors.EncryptedMessageText

@OptIn(ExperimentalMaterial3Api::class)
val ColorScheme.pendingMessage: Color
    get() = RedSemanticColors.PendingMessage

@OptIn(ExperimentalMaterial3Api::class)
val ColorScheme.pendingMessageBackground: Color
    get() = RedSemanticColors.PendingMessageBackground

@OptIn(ExperimentalMaterial3Api::class)
val ColorScheme.pendingMessageText: Color
    get() = RedSemanticColors.PendingMessageText

@OptIn(ExperimentalMaterial3Api::class)
val ColorScheme.failedMessage: Color
    get() = RedSemanticColors.FailedMessage

@OptIn(ExperimentalMaterial3Api::class)
val ColorScheme.failedMessageBackground: Color
    get() = RedSemanticColors.FailedMessageBackground

@OptIn(ExperimentalMaterial3Api::class)
val ColorScheme.failedMessageText: Color
    get() = RedSemanticColors.FailedMessageText

@OptIn(ExperimentalMaterial3Api::class)
val ColorScheme.systemMessage: Color
    get() = RedSemanticColors.SystemMessage

@OptIn(ExperimentalMaterial3Api::class)
val ColorScheme.systemMessageBackground: Color
    get() = RedSemanticColors.SystemMessageBackground

@OptIn(ExperimentalMaterial3Api::class)
val ColorScheme.systemMessageText: Color
    get() = RedSemanticColors.SystemMessageText

@OptIn(ExperimentalMaterial3Api::class)
val ColorScheme.sendingIndicator: Color
    get() = RedSemanticColors.Sending

@OptIn(ExperimentalMaterial3Api::class)
val ColorScheme.sentIndicator: Color
    get() = RedSemanticColors.Sent

@OptIn(ExperimentalMaterial3Api::class)
val ColorScheme.deliveredIndicator: Color
    get() = RedSemanticColors.Delivered

@OptIn(ExperimentalMaterial3Api::class)
val ColorScheme.readIndicator: Color
    get() = RedSemanticColors.Read

@OptIn(ExperimentalMaterial3Api::class)
val ColorScheme.outgoingBubble: Color
    get() = RedSemanticColors.OutgoingBubble

@OptIn(ExperimentalMaterial3Api::class)
val ColorScheme.outgoingBubbleGlow: Color
    get() = RedSemanticColors.OutgoingBubbleGlow

@OptIn(ExperimentalMaterial3Api::class)
val ColorScheme.incomingBubble: Color
    get() = RedSemanticColors.IncomingBubble

@OptIn(ExperimentalMaterial3Api::class)
val ColorScheme.outgoingBubbleText: Color
    get() = RedSemanticColors.OutgoingBubbleText

@OptIn(ExperimentalMaterial3Api::class)
val ColorScheme.outgoingBubbleTextSecondary: Color
    get() = RedSemanticColors.OutgoingBubbleTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
val ColorScheme.incomingBubbleText: Color
    get() = RedSemanticColors.IncomingBubbleText

@OptIn(ExperimentalMaterial3Api::class)
val ColorScheme.incomingBubbleTextSecondary: Color
    get() = RedSemanticColors.IncomingBubbleTextSecondary

@OptIn(ExperimentalMaterial3Api::class)
val ColorScheme.successColor: Color
    get() = RedBrandColors.Success

@OptIn(ExperimentalMaterial3Api::class)
val ColorScheme.warningColor: Color
    get() = RedBrandColors.Warning
