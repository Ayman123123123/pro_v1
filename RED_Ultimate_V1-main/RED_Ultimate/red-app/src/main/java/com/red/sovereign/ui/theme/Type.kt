package com.red.sovereign.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.red.sovereign.R

/**
 * RED Sovereign Typography — نظام الطباعة Material 3 كامل
 *
 * عائلة واحدة ثنائية النص (IBM Plex Sans Arabic) — 15 نمطًا
 * يغطي كل الاستعمالات من العناوين الكبيرة للنصوص الصغيرة.
 *
 * Material 3 Type Scale كامل مع دعم RTL للعربية.
 */
object RedTypography {

    // ══════════════════════════════════════════════════════════════════════════════
    // عائلة الخطوط — IBM Plex Sans Arabic (مضمّن محليًا، لا خطوط شبكة)
    // ═══════════════════════════════════════════════════════════════════════════════

    val PlexArabicFamily = FontFamily(
        Font(R.font.plex_arabic_regular, FontWeight.Normal),
        Font(R.font.plex_arabic_regular, FontWeight.Light),
        Font(R.font.plex_arabic_medium, FontWeight.Medium),
        Font(R.font.plex_arabic_semibold, FontWeight.SemiBold),
        Font(R.font.plex_arabic_bold, FontWeight.Bold),
        Font(R.font.plex_arabic_bold, FontWeight.ExtraBold),
        Font(R.font.plex_arabic_bold, FontWeight.Black),
    )

    // ═══════════════════════════════════════════════════════════════════════════════
    // Material 3 Typography Scale — 15 نمطًا
    // ═══════════════════════════════════════════════════════════════════════════════

    val Default = Typography(
        // Display — عناوين كبيرة جداً (Hero, splash screens)
        displayLarge = TextStyle(
            fontFamily = PlexArabicFamily,
            fontSize = 57.sp,
            lineHeight = 64.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.sp
        ),
        displayMedium = TextStyle(
            fontFamily = PlexArabicFamily,
            fontSize = 45.sp,
            lineHeight = 52.sp,
            fontWeight = FontWeight.ExtraBold
        ),
        displaySmall = TextStyle(
            fontFamily = PlexArabicFamily,
            fontSize = 36.sp,
            lineHeight = 46.sp,
            fontWeight = FontWeight.Black
        ),

        // Headline — عناوين الشاشات والأقسام الرئيسية
        headlineLarge = TextStyle(
            fontFamily = PlexArabicFamily,
            fontSize = 30.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.ExtraBold
        ),
        headlineMedium = TextStyle(
            fontFamily = PlexArabicFamily,
            fontSize = 25.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.Bold
        ),
        headlineSmall = TextStyle(
            fontFamily = PlexArabicFamily,
            fontSize = 22.sp,
            lineHeight = 30.sp,
            fontWeight = FontWeight.Bold
        ),

        // Title — عناوين متوسطة (Cards, Dialogs, List items)
        titleLarge = TextStyle(
            fontFamily = PlexArabicFamily,
            fontSize = 21.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Bold
        ),
        titleMedium = TextStyle(
            fontFamily = PlexArabicFamily,
            fontSize = 17.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.SemiBold
        ),
        titleSmall = TextStyle(
            fontFamily = PlexArabicFamily,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.SemiBold
        ),

        // Body — نصوص القراءة والمحادثات
        bodyLarge = TextStyle(
            fontFamily = PlexArabicFamily,
            fontSize = 17.sp,
            lineHeight = 27.sp,
            fontWeight = FontWeight.Normal
        ),
        bodyMedium = TextStyle(
            fontFamily = PlexArabicFamily,
            fontSize = 15.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Normal
        ),
        bodySmall = TextStyle(
            fontFamily = PlexArabicFamily,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Normal
        ),

        // Label — أزرار، شرائح، تسميات حقول
        labelLarge = TextStyle(
            fontFamily = PlexArabicFamily,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.SemiBold
        ),
        labelMedium = TextStyle(
            fontFamily = PlexArabicFamily,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Medium
        ),
        labelSmall = TextStyle(
            fontFamily = PlexArabicFamily,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium
        ),
    )

    // ═══════════════════════════════════════════════════════════════════════════════
    // Typography مضبوطة للـ High Contrast (أحجام أكبر قليلاً، أوزان أثقل)
    // ═══════════════════════════════════════════════════════════════════════════════

    val HighContrast = Typography(
        displayLarge = Default.displayLarge.copy(fontWeight = FontWeight.Black, fontSize = 60.sp),
        displayMedium = Default.displayMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = 48.sp),
        displaySmall = Default.displaySmall.copy(fontWeight = FontWeight.Black, fontSize = 38.sp),
        headlineLarge = Default.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 32.sp),
        headlineMedium = Default.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 27.sp),
        headlineSmall = Default.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp),
        titleLarge = Default.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
        titleMedium = Default.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
        titleSmall = Default.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
        bodyLarge = Default.bodyLarge.copy(fontWeight = FontWeight.Medium, fontSize = 18.sp),
        bodyMedium = Default.bodyMedium.copy(fontWeight = FontWeight.Medium, fontSize = 16.sp),
        bodySmall = Default.bodySmall.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp),
        labelLarge = Default.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
        labelMedium = Default.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
        labelSmall = Default.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
    )

    // ═══════════════════════════════════════════════════════════════════════════════
    // دوال مساعدة لتكبير الخط ديناميكياً (Font Scale)
    // ═══════════════════════════════════════════════════════════════════════════════

    fun scaled(typography: Typography = Default, scale: Float): Typography {
        if (scale == 1f) return typography
        return Typography(
            displayLarge = typography.displayLarge.copy(
                fontSize = typography.displayLarge.fontSize * scale,
                lineHeight = typography.displayLarge.lineHeight * scale
            ),
            displayMedium = typography.displayMedium.copy(
                fontSize = typography.displayMedium.fontSize * scale,
                lineHeight = typography.displayMedium.lineHeight * scale
            ),
            displaySmall = typography.displaySmall.copy(
                fontSize = typography.displaySmall.fontSize * scale,
                lineHeight = typography.displaySmall.lineHeight * scale
            ),
            headlineLarge = typography.headlineLarge.copy(
                fontSize = typography.headlineLarge.fontSize * scale,
                lineHeight = typography.headlineLarge.lineHeight * scale
            ),
            headlineMedium = typography.headlineMedium.copy(
                fontSize = typography.headlineMedium.fontSize * scale,
                lineHeight = typography.headlineMedium.lineHeight * scale
            ),
            headlineSmall = typography.headlineSmall.copy(
                fontSize = typography.headlineSmall.fontSize * scale,
                lineHeight = typography.headlineSmall.lineHeight * scale
            ),
            titleLarge = typography.titleLarge.copy(
                fontSize = typography.titleLarge.fontSize * scale,
                lineHeight = typography.titleLarge.lineHeight * scale
            ),
            titleMedium = typography.titleMedium.copy(
                fontSize = typography.titleMedium.fontSize * scale,
                lineHeight = typography.titleMedium.lineHeight * scale
            ),
            titleSmall = typography.titleSmall.copy(
                fontSize = typography.titleSmall.fontSize * scale,
                lineHeight = typography.titleSmall.lineHeight * scale
            ),
            bodyLarge = typography.bodyLarge.copy(
                fontSize = typography.bodyLarge.fontSize * scale,
                lineHeight = typography.bodyLarge.lineHeight * scale
            ),
            bodyMedium = typography.bodyMedium.copy(
                fontSize = typography.bodyMedium.fontSize * scale,
                lineHeight = typography.bodyMedium.lineHeight * scale
            ),
            bodySmall = typography.bodySmall.copy(
                fontSize = typography.bodySmall.fontSize * scale,
                lineHeight = typography.bodySmall.lineHeight * scale
            ),
            labelLarge = typography.labelLarge.copy(
                fontSize = typography.labelLarge.fontSize * scale,
                lineHeight = typography.labelLarge.lineHeight * scale
            ),
            labelMedium = typography.labelMedium.copy(
                fontSize = typography.labelMedium.fontSize * scale,
                lineHeight = typography.labelMedium.lineHeight * scale
            ),
            labelSmall = typography.labelSmall.copy(
                fontSize = typography.labelSmall.fontSize * scale,
                lineHeight = typography.labelSmall.lineHeight * scale
            ),
        )
    }
}