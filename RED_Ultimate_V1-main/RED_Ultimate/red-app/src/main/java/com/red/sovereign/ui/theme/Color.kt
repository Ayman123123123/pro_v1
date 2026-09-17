package com.red.sovereign.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.OptIn
import androidx.compose.ui.graphics.Color

/**
 * RED Sovereign Material 3 Color Schemes
 *
 * نظام ألوان Material 3 كامل. نِسَب التباين أدناه مقيسة بمعادلة
 * WCAG 2.1 SC 1.4.3 (لا بتقريب):
 * - الداكن: onPrimary أبيض 4.98:1، الحاويات داكنة + نص فاتح (4.92–9.65:1)،
 *   onSecondary أسود 11.70:1، outline تفاعلي #7A8FA3 (5.13:1 على البطاقة).
 * - الفاتح (#F7F8FA/#0F1B2D): onPrimary أبيض 5.62:1 على #C62828،
 *   onSecondary أبيض 4.55:1، الحاويات فاتحة + نص داكن (6.15–7.11:1).
 * - outline تفاعلي حدّ ≥3:1 (WCAG 1.4.11)؛ outlineVariant فاصل زخرفي مستثنى.
 *
 * الأحمر لدور Danger/Error فقط (YounesRose #F25C5C) — ليس لون علامة؛
 * انظر RedBrandColors (مهمل: aliases نحو Younes).
 */
object RedColorScheme {

    // ═══════════════════════════════════════════════════════════════════════════════
    // Dark Theme (افتراضي)
    // ═══════════════════════════════════════════════════════════════════════════════

    val Dark = darkColorScheme(
        // Primary — أحمر سيادي داكن بما يكفي لنص أبيض AA
        primary = Color(0xFFD32F2F),
        onPrimary = Color.White,                            // 4.98:1 على primary
        primaryContainer = Color(0xFF8B0000),                // حاوية داكنة
        onPrimaryContainer = Color(0xFFFFEBEE),              // 4.92:1 على الحاوية

        // Secondary — ذهبي ملكي (نص أسود: الأبيض عليه 1.79:1 راسب)
        secondary = Color(0xFFFFB300),
        onSecondary = Color.Black,                           // 11.70:1
        secondaryContainer = Color(0xFF3D2E00),              // حاوية داكنة
        onSecondaryContainer = Color(0xFFF0D48C),            // 9.12:1 على الحاوية

        // Tertiary — أزرق ملكي (الأبيض عليه 3.68:1 راسب → نص داكن)
        tertiary = Color(0xFF4D9FE8),
        onTertiary = Color(0xFF001F2A),                      // 6.05:1
        tertiaryContainer = Color(0xFF1565C0),               // حاوية داكنة
        onTertiaryContainer = Color(0xFFE3F2FD),             // 5.03:1 على الحاوية

        // Error — أحمر الخطر YounesRose (الأبيض عليه 3.25:1 راسب → نص داكن)
        error = Color(0xFFF25C5C),
        onError = Color(0xFF3A0010),                         // 5.44:1
        errorContainer = Color(0xFF5C001A),                  // حاوية داكنة
        onErrorContainer = Color(0xFFFFB3B8),                // 8.43:1 على الحاوية

        // Background & Surface — Younes
        background = Color(0xFF0A0F18),                      // YounesVoid
        onBackground = Color(0xFFFFFFFF),
        surface = Color(0xFF131C29),                         // YounesSurface1
        onSurface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFF1B2635),                  // YounesSurface2
        onSurfaceVariant = Color(0xFF9FB0C2),                // YounesMuted
        surfaceTint = Color(0xFFD32F2F),

        // Surface containers (للارتفاعات Material 3)
        surfaceContainer = Color(0xFF131C29),
        surfaceContainerLow = Color(0xFF0A0F18),
        surfaceContainerHigh = Color(0xFF1B2635),
        surfaceContainerHighest = Color(0xFF212E40),

        // Outlines — تفاعلي مقابل زخرفي (WCAG 1.4.11)
        outline = Color(0xFF7A8FA3),                         // YounesOutline — 5.13:1 على البطاقة
        outlineVariant = Color(0xFF2A394A),                  // YounesBorder — فاصل زخرفي مستثنى

        // Inverse colors — أحمر داكن يُقرأ على الفاتح
        inversePrimary = Color(0xFFC62828),                  // 5.62:1 على الأبيض
        inverseSurface = Color(0xFFFFFFFF),
        inverseOnSurface = Color(0xFF0A0F18),

        // Scrim
        scrim = Color(0xE0000000),

        // Shadow
        shadow = Color(0xFF000000)
    )

    // ═══════════════════════════════════════════════════════════════════════════════
    // Light Theme — خلفية لؤلؤية #F7F8FA ونص كحلي #0F1B2D
    // ═══════════════════════════════════════════════════════════════════════════════

    val Light = lightColorScheme(
        // Primary — أحمر داكن يحمل أبيض AA (الفاتح #F44336 مع الأبيض 3.68:1 راسب)
        primary = Color(0xFFC62828),
        onPrimary = Color.White,                             // 5.62:1
        primaryContainer = Color(0xFFFFCDD2),                // حاوية فاتحة
        onPrimaryContainer = Color(0xFF8B0000),              // 7.11:1 على الحاوية

        // Secondary — كوبالت داكن AA على الفاتح
        secondary = Color(0xFF2E7DA8),
        onSecondary = Color.White,                           // 4.55:1
        secondaryContainer = Color(0xFFBFE6F7),
        onSecondaryContainer = Color(0xFF001F2A),

        // Tertiary — ذهب داكن AA على الفاتح
        tertiary = Color(0xFF8A6A0A),
        onTertiary = Color.White,                            // 5.06:1
        tertiaryContainer = Color(0xFFFFE08B),
        onTertiaryContainer = Color(0xFF221B00),

        // Error — أحمر الخطر (دور Danger فقط)
        error = Color(0xFFD32F2F),
        onError = Color.White,                               // 4.98:1
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),

        // Background & Surface
        background = Color(0xFFF7F8FA),                      // لؤلؤي فاتح
        onBackground = Color(0xFF0F1B2D),                    // كحلي داكن
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF0F1B2D),
        surfaceVariant = Color(0xFFE6E8EB),
        onSurfaceVariant = Color(0xFF5A6B7D),
        surfaceTint = Color(0xFFC62828),

        // Surface containers
        surfaceContainer = Color(0xFFF0F2F5),
        surfaceContainerLow = Color(0xFFF7F8FA),
        surfaceContainerHigh = Color(0xFFE6E8EB),
        surfaceContainerHighest = Color(0xFFDDE1E6),

        // Outlines — تفاعلي 3.34:1 على الأبيض (≥3:1)، والفاصل زخرفي
        outline = Color(0xFF7A8FA3),
        outlineVariant = Color(0xFFD0D7DE),

        // Inverse
        inversePrimary = Color(0xFFF25C5C),                  // 5.90:1 على الداكن
        inverseSurface = Color(0xFF0A0F18),
        inverseOnSurface = Color(0xFFF7F8FA),

        // Scrim
        scrim = Color(0x66000000),

        // Shadow
        shadow = Color(0xFF000000)
    )

    // ═══════════════════════════════════════════════════════════════════════════════
    // High Contrast Dark — للتباين العالي (WCAG AAA)
    // ═══════════════════════════════════════════════════════════════════════════════

    val HighContrastDark = Dark.copy(
        onBackground = Color(0xFFFFFFFF),
        onSurface = Color(0xFFFFFFFF),
        onSurfaceVariant = Color(0xFFE8E8E8),
        outline = Color(0xFF3DE8BC),                         // YounesPrimaryGlow — حدّ أظهر
        outlineVariant = Color(0xFF7A8FA3),                  // التفاعلي السابق يصبح فاصلًا
        surface = Color(0xFF0D1520),
        surfaceContainer = Color(0xFF0D1520),
        surfaceContainerLow = Color(0xFF050A12),
        background = Color(0xFF000000)
    )

    // ═══════════════════════════════════════════════════════════════════════════════
    // High Contrast Light
    // ═══════════════════════════════════════════════════════════════════════════════

    val HighContrastLight = Light.copy(
        onBackground = Color(0xFF000000),
        onSurface = Color(0xFF000000),
        onSurfaceVariant = Color(0xFF1A1A1A),
        outline = Color(0xFF0A7A5E),                         // زمرد داكن — حدّ أظهر على الفاتح
        outlineVariant = Color(0xFF5A6B7D),
        surface = Color(0xFFFFFFFF),
        surfaceContainer = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFFFFFF),
        background = Color(0xFFFFFFFF)
    )

    // ═══════════════════════════════════════════════════════════════════════════════
    // دالة مساعدة لبناء ColorScheme من لون مخصص (User-customizable accent)
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * يولد ColorScheme مخصص من لون بذرة (seed color) للمستخدم.
     * القاعدة نفسها: onPrimary أبيض، الحاويات الداكنة ليلًا (tone90 + نص tone10)،
     * onSecondary أسود، outline تفاعلي YounesOutline. الثالثي/الخطأ والأسطح
     * ثابتة على Younes — البذرة تؤثر في Primary/Secondary فقط.
     */
    fun fromSeed(seed: Color, isDark: Boolean): ColorScheme {
        val tonal = generateTonalPalette(seed)
        return if (isDark) {
            darkColorScheme(
                primary = tonal.tone80,
                onPrimary = Color.White,
                primaryContainer = tonal.tone90,
                onPrimaryContainer = tonal.tone10,
                secondary = tonal.tone70,
                onSecondary = Color.Black,
                secondaryContainer = tonal.tone90,
                onSecondaryContainer = tonal.tone10,
                tertiary = Color(0xFF4D9FE8),
                onTertiary = Color(0xFF001F2A),
                tertiaryContainer = Color(0xFF1565C0),
                onTertiaryContainer = Color(0xFFE3F2FD),
                error = Color(0xFFF25C5C),
                onError = Color(0xFF3A0010),
                errorContainer = Color(0xFF5C001A),
                onErrorContainer = Color(0xFFFFB3B8),
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
                inversePrimary = tonal.tone90,
                inverseSurface = Color(0xFFFFFFFF),
                inverseOnSurface = Color(0xFF0A0F18),
                scrim = Color(0xE0000000),
                shadow = Color(0xFF000000)
            )
        } else {
            lightColorScheme(
                primary = tonal.tone90,
                onPrimary = Color.White,
                primaryContainer = tonal.tone20,
                onPrimaryContainer = tonal.tone90,
                secondary = tonal.tone80,
                onSecondary = Color.Black,
                secondaryContainer = tonal.tone20,
                onSecondaryContainer = tonal.tone90,
                tertiary = Color(0xFF8A6A0A),
                onTertiary = Color.White,
                tertiaryContainer = Color(0xFFFFE08B),
                onTertiaryContainer = Color(0xFF221B00),
                error = Color(0xFFD32F2F),
                onError = Color.White,
                errorContainer = Color(0xFFFFDAD6),
                onErrorContainer = Color(0xFF410002),
                background = Color(0xFFF7F8FA),
                onBackground = Color(0xFF0F1B2D),
                surface = Color(0xFFFFFFFF),
                onSurface = Color(0xFF0F1B2D),
                surfaceVariant = Color(0xFFE6E8EB),
                onSurfaceVariant = Color(0xFF5A6B7D),
                surfaceTint = tonal.tone90,
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

        return TonalPalette(
            tone0 = Color.Hsv(hue, 0f, 1f),
            tone10 = Color.Hsv(hue, saturation * 0.1f, 0.95f),
            tone20 = Color.Hsv(hue, saturation * 0.2f, 0.9f),
            tone30 = Color.Hsv(hue, saturation * 0.4f, 0.85f),
            tone40 = Color.Hsv(hue, saturation * 0.6f, 0.8f),
            tone50 = Color.Hsv(hue, saturation * 0.8f, 0.75f),
            tone60 = Color.Hsv(hue, saturation, 0.7f),
            tone70 = Color.Hsv(hue, saturation, 0.6f),
            tone80 = Color.Hsv(hue, saturation, 0.5f),
            tone90 = Color.Hsv(hue, saturation * 0.9f, 0.35f),
            tone95 = Color.Hsv(hue, saturation, 0.25f),
            tone99 = Color.Hsv(hue, saturation, 0.15f),
            tone100 = Color.Hsv(hue, 0f, 0f)
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
    get() = YounesPrimary

@OptIn(ExperimentalMaterial3Api::class)
val ColorScheme.warningColor: Color
    get() = YounesAccent
