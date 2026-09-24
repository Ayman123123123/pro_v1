package com.red.sovereign.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * RED Sovereign Typography — عربية RTL سليمة.
 *
 * قواعد ملزمة (تتفوق على واتساب/تلجرام/سيجنال):
 * - عائلة واحدة ثنائية النص [PlexArabicFamily] — لا Default.
 * - letterSpacing = 0.sp دائمًا للعربية: أي تباعد موجب يفكك
 *   اتصال الحروف ويكسر الكلمات (كان 0.5/0.25/0.4sp هنا).
 * - حد أدنى مقروء 12sp لكل نص مرئي (labelSmall كان 11sp).
 * - ارتفاعات سخية للعربية (1.5–1.6×) لاستيعاب التشكيل والألف المقصورة.
 */
val RedTypography = Typography(
    // Display
    displayLarge = TextStyle(
        fontFamily = PlexArabicFamily,
        fontWeight = FontWeight.Black,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = 0.sp
    ),
    displayMedium = TextStyle(
        fontFamily = PlexArabicFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = PlexArabicFamily,
        fontWeight = FontWeight.Black,
        fontSize = 36.sp,
        lineHeight = 46.sp,
        letterSpacing = 0.sp
    ),

    // Headline
    headlineLarge = TextStyle(
        fontFamily = PlexArabicFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 42.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = PlexArabicFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 38.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = PlexArabicFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.sp
    ),

    // Title
    titleLarge = TextStyle(
        fontFamily = PlexArabicFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = PlexArabicFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 25.sp,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = PlexArabicFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    ),

    // Body — حد أدنى 13sp
    bodyLarge = TextStyle(
        fontFamily = PlexArabicFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 27.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = PlexArabicFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    ),
    bodySmall = TextStyle(
        fontFamily = PlexArabicFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp
    ),

    // Label — حد أدنى 12sp، بلا تباعد ضار
    labelLarge = TextStyle(
        fontFamily = PlexArabicFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp
    ),
    labelMedium = TextStyle(
        fontFamily = PlexArabicFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = PlexArabicFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp
    ),
)

// Responsive Typography - سيتعامل معها RedTheme مع fontScale
fun Typography.scaled(scale: Float): Typography {
    if (scale == 1f) return this
    return Typography(
        displayLarge = displayLarge.copy(
            fontSize = displayLarge.fontSize * scale,
            lineHeight = displayLarge.lineHeight * scale
        ),
        displayMedium = displayMedium.copy(
            fontSize = displayMedium.fontSize * scale,
            lineHeight = displayMedium.lineHeight * scale
        ),
        displaySmall = displaySmall.copy(
            fontSize = displaySmall.fontSize * scale,
            lineHeight = displaySmall.lineHeight * scale
        ),
        headlineLarge = headlineLarge.copy(
            fontSize = headlineLarge.fontSize * scale,
            lineHeight = headlineLarge.lineHeight * scale
        ),
        headlineMedium = headlineMedium.copy(
            fontSize = headlineMedium.fontSize * scale,
            lineHeight = headlineMedium.lineHeight * scale
        ),
        headlineSmall = headlineSmall.copy(
            fontSize = headlineSmall.fontSize * scale,
            lineHeight = headlineSmall.lineHeight * scale
        ),
        titleLarge = titleLarge.copy(
            fontSize = titleLarge.fontSize * scale,
            lineHeight = titleLarge.lineHeight * scale
        ),
        titleMedium = titleMedium.copy(
            fontSize = titleMedium.fontSize * scale,
            lineHeight = titleMedium.lineHeight * scale
        ),
        titleSmall = titleSmall.copy(
            fontSize = titleSmall.fontSize * scale,
            lineHeight = titleSmall.lineHeight * scale
        ),
        bodyLarge = bodyLarge.copy(
            fontSize = bodyLarge.fontSize * scale,
            lineHeight = bodyLarge.lineHeight * scale
        ),
        bodyMedium = bodyMedium.copy(
            fontSize = bodyMedium.fontSize * scale,
            lineHeight = bodyMedium.lineHeight * scale
        ),
        bodySmall = bodySmall.copy(
            fontSize = bodySmall.fontSize * scale,
            lineHeight = bodySmall.lineHeight * scale
        ),
        labelLarge = labelLarge.copy(
            fontSize = labelLarge.fontSize * scale,
            lineHeight = labelLarge.lineHeight * scale
        ),
        labelMedium = labelMedium.copy(
            fontSize = labelMedium.fontSize * scale,
            lineHeight = labelMedium.lineHeight * scale
        ),
        labelSmall = labelSmall.copy(
            fontSize = labelSmall.fontSize * scale,
            lineHeight = labelSmall.lineHeight * scale
        ),
    )
}
