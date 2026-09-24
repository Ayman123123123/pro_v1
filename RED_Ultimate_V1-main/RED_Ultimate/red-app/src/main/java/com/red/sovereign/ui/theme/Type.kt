package com.red.sovereign.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val RedTypography = Typography(
    // Display
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 45.sp,
        lineHeight = 52.sp
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,
        fontSize = 36.sp,
        lineHeight = 46.sp
    ),

    // Headline
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp,
        lineHeight = 40.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),

    // Title
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),

    // Body
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),

    // Label
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
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