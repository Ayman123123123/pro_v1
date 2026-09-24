package com.red.sovereign.ui.theme

import androidx.compose.ui.text.font.FontFamily

/**
 * الخطوط — المصدر الوحيد لعائلة Plex Arabic هو RedTheme.kt.
 *
 * كان هذا الملف يعرّف `PlexArabicFamily` مرة ثانية (تكرار يمنع الترجمة:
 * duplicate top-level declaration) ويشير إلى `noto_sans_arabic_*`
 * غير الموجودة في res/font (فشل بناء مضمون). الآن كل الأسماء هنا
 * مرادفات آمنة: عربية Plex + احتياطي النظام للرموز فقط.
 */

// المصدر الوحيد: RedTheme.PlexArabicFamily — لا تعريف مكرر هنا.

// الخط العربي الرئيسي (RTL أولوية)
val ArabicTypographyFamily: FontFamily = PlexArabicFamily

// Fallback للرموز/الإيموجي — خط النظام لا ملف مفقود.
val EmojiFamily: FontFamily = FontFamily.Default
