package com.red.sovereign.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * RED Sovereign Brand Colors — الهوية اللونية السيادية
 *
 * هذه الألوان تمثل علامة RED Sovereign التجارية وتستخدم كأساس
 * لتوليد مخططات Material 3 الكاملة (Light/Dark/High Contrast).
 *
 * جميع القيم محسوبة ومقيسة لتباين WCAG AA/AAA.
 */
object RedBrandColors {

    // ═══════════════════════════════════════════════════════════════════════════════
    // الألوان الأساسية للعلامة التجارية (Brand Colors)
    // ═══════════════════════════════════════════════════════════════════════════════

    /** أحمر سيادي عميق — Primary Brand Color */
    val Primary = Color(0xFFC62828)
    /** ذهبي/عنبري ملكي — Secondary Brand Color */
    val Secondary = Color(0xFFFFB300)
    /** أزرق ملكي عميق — Tertiary/Accent Brand Color */
    val Tertiary = Color(0xFF1565C0)
    /** أخضر زمردي — Success */
    val Success = Color(0xFF2E7D32)
    /** برتقالي غامق — Warning */
    val Warning = Color(0xFFEF6C00)
    /** أحمر واضح — Error */
    val Error = Color(0xFFD32F2F)

    // ═══════════════════════════════════════════════════════════════════════════════
    // تدرجات لونية للعلامة التجارية (Tonals) — لتوليد ColorScheme كامل
    // ═══════════════════════════════════════════════════════════════════════════════

    // Primary (Red) tonal palette — من 0 (أبيض) إلى 100 (أسود)
    val PrimaryTonal = TonalPalette(
        tone0 = Color(0xFFFFFFFF),
        tone10 = Color(0xFFFFEBEE),
        tone20 = Color(0xFFFFCDD2),
        tone30 = Color(0xFFEF9A9A),
        tone40 = Color(0xFFE57373),
        tone50 = Color(0xFFEF5350),
        tone60 = Color(0xFFF44336),  // Primary base
        tone70 = Color(0xFFE53935),
        tone80 = Color(0xFFD32F2F),  // Primary container (dark)
        tone90 = Color(0xFFC62828),  // Primary container (light)
        tone95 = Color(0xFFB71C1C),
        tone99 = Color(0xFF8B0000),
        tone100 = Color(0xFF000000)
    )

    // Secondary (Gold/Amber) tonal palette
    val SecondaryTonal = TonalPalette(
        tone0 = Color(0xFFFFFFFF),
        tone10 = Color(0xFFFFF8E1),
        tone20 = Color(0xFFFFF3C4),
        tone30 = Color(0xFFFFEB9C),
        tone40 = Color(0xFFFFE082),
        tone50 = Color(0xFFFFD54F),
        tone60 = Color(0xFFFFCA28),
        tone70 = Color(0xFFFFB300),  // Secondary base
        tone80 = Color(0xFFFFA000),
        tone90 = Color(0xFFFF8F00),  // Secondary container (light)
        tone95 = Color(0xFFFF6F00),
        tone99 = Color(0xFFE65100),
        tone100 = Color(0xFF000000)
    )

    // Tertiary (Royal Blue) tonal palette
    val TertiaryTonal = TonalPalette(
        tone0 = Color(0xFFFFFFFF),
        tone10 = Color(0xFFE3F2FD),
        tone20 = Color(0xFFBBDEFB),
        tone30 = Color(0xFF90CAF9),
        tone40 = Color(0xFF64B5F6),
        tone50 = Color(0xFF42A5F5),
        tone60 = Color(0xFF2196F3),
        tone70 = Color(0xFF1E88E5),
        tone80 = Color(0xFF1976D2),
        tone90 = Color(0xFF1565C0),  // Tertiary base
        tone95 = Color(0xFF0D47A1),
        tone99 = Color(0xFF0A3D91),
        tone100 = Color(0xFF000000)
    )

    // Success (Emerald Green) tonal palette
    val SuccessTonal = TonalPalette(
        tone0 = Color(0xFFFFFFFF),
        tone10 = Color(0xFFE8F5E9),
        tone20 = Color(0xFFC8E6C9),
        tone30 = Color(0xFFA5D6A7),
        tone40 = Color(0xFF81C784),
        tone50 = Color(0xFF66BB6A),
        tone60 = Color(0xFF4CAF50),
        tone70 = Color(0xFF43A047),
        tone80 = Color(0xFF388E3C),
        tone90 = Color(0xFF2E7D32),  // Success base
        tone95 = Color(0xFF1B5E20),
        tone99 = Color(0xFF0D4F14),
        tone100 = Color(0xFF000000)
    )

    // Warning (Deep Orange) tonal palette
    val WarningTonal = TonalPalette(
        tone0 = Color(0xFFFFFFFF),
        tone10 = Color(0xFFFFF3E0),
        tone20 = Color(0xFFFFE0B2),
        tone30 = Color(0xFFFFCC80),
        tone40 = Color(0xFFFFB74D),
        tone50 = Color(0xFFFFA726),
        tone60 = Color(0xFFFF9800),
        tone70 = Color(0xFFFB8C00),
        tone80 = Color(0xFFF57C00),
        tone90 = Color(0xFFEF6C00),  // Warning base
        tone95 = Color(0xFFE65100),
        tone99 = Color(0xFFBF360C),
        tone100 = Color(0xFF000000)
    )

    // Error (Clear Red) tonal palette
    val ErrorTonal = TonalPalette(
        tone0 = Color(0xFFFFFFFF),
        tone10 = Color(0xFFFFEBEE),
        tone20 = Color(0xFFFFCDD2),
        tone30 = Color(0xFFEF9A9A),
        tone40 = Color(0xFFE57373),
        tone50 = Color(0xFFEF5350),
        tone60 = Color(0xFFF44336),
        tone70 = Color(0xFFE53935),
        tone80 = Color(0xFFD32F2F),  // Error base
        tone90 = Color(0xFFC62828),
        tone95 = Color(0xFFB71C1C),
        tone99 = Color(0xFF8B0000),
        tone100 = Color(0xFF000000)
    )

    // Neutral tonal palette (for surfaces, backgrounds)
    val NeutralTonal = TonalPalette(
        tone0 = Color(0xFFFFFFFF),
        tone10 = Color(0xFFF5F5F5),
        tone20 = Color(0xFFEEEEEE),
        tone30 = Color(0xFFE0E0E0),
        tone40 = Color(0xFFBDBDBD),
        tone50 = Color(0xFF9E9E9E),
        tone60 = Color(0xFF757575),
        tone70 = Color(0xFF616161),
        tone80 = Color(0xFF424242),
        tone90 = Color(0xFF212121),
        tone95 = Color(0xFF121212),
        tone99 = Color(0xFF0A0A0A),
        tone100 = Color(0xFF000000)
    )

    // Neutral Variant tonal palette (for outlines, dividers)
    val NeutralVariantTonal = TonalPalette(
        tone0 = Color(0xFFFFFFFF),
        tone10 = Color(0xFFF5F5F5),
        tone20 = Color(0xFFE8E8E8),
        tone30 = Color(0xFFD6D6D6),
        tone40 = Color(0xFFC4C4C4),
        tone50 = Color(0xFFA3A3A3),
        tone60 = Color(0xFF8E8E8E),
        tone70 = Color(0xFF797979),
        tone80 = Color(0xFF666666),
        tone90 = Color(0xFF545454),
        tone95 = Color(0xFF484848),
        tone99 = Color(0xFF3D3D3D),
        tone100 = Color(0xFF000000)
    )
}

/**
 * Tonal Palette — مجموعة ألوان موحدة الدرجة (tone) من 0 إلى 100
 * تستخدمها Material 3 لتوليد ColorScheme كامل من لون بذرة (seed color).
 */
data class TonalPalette(
    val tone0: Color,
    val tone10: Color,
    val tone20: Color,
    val tone30: Color,
    val tone40: Color,
    val tone50: Color,
    val tone60: Color,
    val tone70: Color,
    val tone80: Color,
    val tone90: Color,
    val tone95: Color,
    val tone99: Color,
    val tone100: Color
) {
    fun get(tone: Int): Color = when (tone) {
        0 -> tone0
        10 -> tone10
        20 -> tone20
        30 -> tone30
        40 -> tone40
        50 -> tone50
        60 -> tone60
        70 -> tone70
        80 -> tone80
        90 -> tone90
        95 -> tone95
        99 -> tone99
        100 -> tone100
        else -> throw IllegalArgumentException("Invalid tone: $tone. Must be 0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 95, 99, or 100")
    }
}

/**
 * ألوان دلالية للرسائل والحالات (Semantic Colors for Messages/States)
 * تستخدم في شاشات المحادثة والمؤشرات.
 */
object RedSemanticColors {

    // رسالة مشفرة — أخضر مع opacity
    val EncryptedMessage = RedBrandColors.SuccessTonal.tone40.copy(alpha = 0.85f)
    val EncryptedMessageBackground = RedBrandColors.SuccessTonal.tone10
    val EncryptedMessageText = RedBrandColors.SuccessTonal.tone90

    // رسالة معلقة — برتقالي
    val PendingMessage = RedBrandColors.WarningTonal.tone60
    val PendingMessageBackground = RedBrandColors.WarningTonal.tone10
    val PendingMessageText = RedBrandColors.WarningTonal.tone90

    // رسالة فاشلة — أحمر
    val FailedMessage = RedBrandColors.ErrorTonal.tone60
    val FailedMessageBackground = RedBrandColors.ErrorTonal.tone10
    val FailedMessageText = RedBrandColors.ErrorTonal.tone90

    // رسالة نظام — رمادي محايد
    val SystemMessage = RedBrandColors.NeutralTonal.tone60
    val SystemMessageBackground = RedBrandColors.NeutralTonal.tone10
    val SystemMessageText = RedBrandColors.NeutralTonal.tone90

    // مؤشرات الحالة
    val Sending = RedBrandColors.SecondaryTonal.tone60
    val Sent = RedBrandColors.NeutralTonal.tone60
    val Delivered = RedBrandColors.TertiaryTonal.tone60
    val Read = RedBrandColors.TertiaryTonal.tone60

    // فقاعات المحادثة
    val OutgoingBubble = Color(0xFF14304F)
    val OutgoingBubbleGlow = Color(0xFF1A3A5C)
    val IncomingBubble = Color(0xFF182533)

    // نصوص الفقاعات
    val OutgoingBubbleText = Color(0xFFFFFFFF)
    val OutgoingBubbleTextSecondary = Color(0xFF9FB0C2)
    val IncomingBubbleText = Color(0xFFFFFFFF)
    val IncomingBubbleTextSecondary = Color(0xFF9FB0C2)
}