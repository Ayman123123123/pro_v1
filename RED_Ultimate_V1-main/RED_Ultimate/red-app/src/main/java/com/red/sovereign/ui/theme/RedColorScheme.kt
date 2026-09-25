package com.red.sovereign.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * RED Sovereign Brand Colors — مهمل (Deprecated).
 *
 * المصدر الوحيد للهوية اللونية هو لوحة Younes في RedTheme.kt
 * (YounesPrimary #14C79A، YounesAccent #E0B551، YounesCobalt #4D9FE8…).
 * كل أعضاء هذا الكائن aliases نحو Younes — لا قيم مستقلة جديدة.
 *
 * الأحمر (YounesRose #F25C5C / YounesRuby) لدور Danger/Error فقط:
 * مؤشر البث، زر الإنهاء، حالات الفشل. ممنوع استعماله كلون علامة
 * (Primary/Secondary) — كان ذلك أصل عيوب التباين (أبيض على أحمر فاتح).
 */
@Deprecated(
    "المصدر الوحيد Younes في RedTheme.kt — هذا الكائن aliases للتوافق فقط",
    ReplaceWith("YounesPrimary")
)
object RedBrandColors {

    // ═══════════════════════════════════════════════════════════════════════════════
    // Aliases نحو Younes — لا قيم مستقلة
    // ═══════════════════════════════════════════════════════════════════════════════

    /** كان أحمرًا سياديًا — أصبح زمرد Younes (الأحمر لدور Danger فقط). */
    @Deprecated("استعمل YounesPrimary — الأحمر لدور Danger فقط", ReplaceWith("YounesPrimary"))
    val Primary = YounesPrimary
    /** ذهبي Younes. */
    @Deprecated("استعمل YounesAccent", ReplaceWith("YounesAccent"))
    val Secondary = YounesAccent
    /** كوبالت Younes. */
    @Deprecated("استعمل YounesCobalt", ReplaceWith("YounesCobalt"))
    val Tertiary = YounesCobalt
    /** النجاح زمرد Younes. */
    @Deprecated("استعمل YounesPrimary", ReplaceWith("YounesPrimary"))
    val Success = YounesPrimary
    /** التحذير ذهب Younes. */
    @Deprecated("استعمل YounesAccent", ReplaceWith("YounesAccent"))
    val Warning = YounesAccent
    /** الخطأ = الدور الشرعي الوحيد للأحمر (YounesRose). */
    @Deprecated("استعمل YounesRose — الأحمر لدور Danger فقط", ReplaceWith("YounesRose"))
    val Error = YounesRose

    // ═══════════════════════════════════════════════════════════════════════════════
    // Tonal palettes مجمّدة — للتوافق المصدري فقط، ممنوع الاستعمال الجديد.
    // الأحمر منها صالح لدور Danger/Error فقط (ErrorTonal).
    // ═══════════════════════════════════════════════════════════════════════════════

    // Primary (Sovereign Red) tonal palette — من 0 (أبيض) إلى 100 (أسود)
    @Deprecated("مجمّد للتوافق — استعمل Younes* مباشرة")
    val PrimaryTonal = TonalPalette(
        tone0 = Color(0xFFFFFFFF),
        tone10 = Color(0xFFFFEBEE),
        tone20 = Color(0xFFFFCDD2),
        tone30 = Color(0xFFEF9A9A),
        tone40 = Color(0xFFE57373),
        tone50 = Color(0xFFEF5350),
        tone60 = Color(0xFFF44336),
        tone70 = Color(0xFFE53935),
        tone80 = Color(0xFFD32F2F),
        tone90 = Color(0xFFC62828),
        tone95 = Color(0xFFB71C1C),
        tone99 = Color(0xFF8B0000),
        tone100 = Color(0xFF000000)
    )

    // Secondary (Royal Gold) tonal palette
    @Deprecated("مجمّد للتوافق — استعمل YounesAccent مباشرة")
    val SecondaryTonal = TonalPalette(
        tone0 = Color(0xFFFFFFFF),
        tone10 = Color(0xFFFFF8E1),
        tone20 = Color(0xFFFFF3C4),
        tone30 = Color(0xFFFFEB9C),
        tone40 = Color(0xFFFFE082),
        tone50 = Color(0xFFFFD54F),
        tone60 = Color(0xFFFFCA28),
        tone70 = Color(0xFFFFB300),
        tone80 = Color(0xFFFFA000),
        tone90 = Color(0xFFFF8F00),
        tone95 = Color(0xFFFF6F00),
        tone99 = Color(0xFFE65100),
        tone100 = Color(0xFF000000)
    )

    // Tertiary (Royal Blue) tonal palette
    @Deprecated("مجمّد للتوافق — استعمل YounesCobalt مباشرة")
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
        tone90 = Color(0xFF1565C0),
        tone95 = Color(0xFF0D47A1),
        tone99 = Color(0xFF0A3D91),
        tone100 = Color(0xFF000000)
    )

    // Success (Emerald Green) tonal palette
    @Deprecated("مجمّد للتوافق — استعمل YounesPrimary مباشرة")
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
        tone90 = Color(0xFF2E7D32),
        tone95 = Color(0xFF1B5E20),
        tone99 = Color(0xFF0D4F14),
        tone100 = Color(0xFF000000)
    )

    // Warning (Deep Orange) tonal palette
    @Deprecated("مجمّد للتوافق — استعمل YounesAccent مباشرة")
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
        tone90 = Color(0xFFEF6C00),
        tone95 = Color(0xFFE65100),
        tone99 = Color(0xFFBF360C),
        tone100 = Color(0xFF000000)
    )

    // Error (Clear Red) tonal palette — الدور الشرعي الوحيد للأحمر (Danger)
    @Deprecated("للتوافق — للدور الجديد استعمل YounesRose مباشرة")
    val ErrorTonal = TonalPalette(
        tone0 = Color(0xFFFFFFFF),
        tone10 = Color(0xFFFFEBEE),
        tone20 = Color(0xFFFFCDD2),
        tone30 = Color(0xFFEF9A9A),
        tone40 = Color(0xFFE57373),
        tone50 = Color(0xFFEF5350),
        tone60 = Color(0xFFF44336),
        tone70 = Color(0xFFE53935),
        tone80 = Color(0xFFD32F2F),
        tone90 = Color(0xFFC62828),
        tone95 = Color(0xFFB71C1C),
        tone99 = Color(0xFF8B0000),
        tone100 = Color(0xFF000000)
    )

    // Neutral tonal palette (for surfaces, backgrounds)
    @Deprecated("مجمّد للتوافق — استعمل أسطح Younes مباشرة")
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
    @Deprecated("مجمّد للتوافق — استعمل YounesOutline/YounesBorder مباشرة")
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
 * تستخدم في شاشات المحادثة والمؤشرات — مربوطة مباشرة بلوحة Younes
 * (زمرد/ذهب/كوبالت/وردة الخطر)، بلا اعتماد على RedBrandColors المهمل.
 */
object RedSemanticColors {

    // رسالة مشفرة — زمرد Younes
    val EncryptedMessage = YounesPrimary.copy(alpha = 0.85f)
    val EncryptedMessageBackground = Color(0xFF0D2B21)
    val EncryptedMessageText = YounesPrimaryGlow

    // رسالة معلقة — ذهب Younes
    val PendingMessage = YounesAccent
    val PendingMessageBackground = Color(0xFF2A2010)
    val PendingMessageText = YounesAccentSoft

    // رسالة فاشلة — وردة الخطر (الدور الشرعي للأحمر)
    val FailedMessage = YounesRose
    val FailedMessageBackground = Color(0xFF3A1216)
    val FailedMessageText = Color(0xFFFFB3B8)

    // رسالة نظام — رمادي محايد (نص #5A6B7D على #F5F5F5 ‏5.02:1 AA)
    val SystemMessage = Color(0xFF5A6B7D)
    val SystemMessageBackground = Color(0xFFF5F5F5)
    val SystemMessageText = Color(0xFF212121)

    // مؤشرات الحالة — Sent أيقونة UI (حد 3:1 لا 4.5): #868686 يحقق 3.68
    // على الفقاعة الصادرة #14304F و3.64 على الأبيض و5.27 على #0A0F18.
    // كان #757575 ‏2.91 على الصادرة (راسب حتى لحد الأيقونة) و4.16 على الخلفية.
    val Sending = YounesAccent
    val Sent = Color(0xFF868686)
    val Delivered = YounesCobalt
    val Read = YounesCobalt

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
