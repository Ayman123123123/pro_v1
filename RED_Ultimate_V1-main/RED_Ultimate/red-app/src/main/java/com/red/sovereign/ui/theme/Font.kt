package com.red.sovereign.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.red.sovereign.R

// IBM Plex Sans Arabic - الخط الرئيسي للعربية
val PlexArabicFamily = FontFamily(
    Font(R.font.plex_arabic_regular, FontWeight.Normal),
    Font(R.font.plex_arabic_regular, FontWeight.Light),
    Font(R.font.plex_arabic_medium, FontWeight.Medium),
    Font(R.font.plex_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.plex_arabic_bold, FontWeight.Bold),
    Font(R.font.plex_arabic_bold, FontWeight.ExtraBold),
    Font(R.font.plex_arabic_bold, FontWeight.Black),
)

// Noto Sans Arabic - fallback
val NotoArabicFamily = FontFamily(
    Font(R.font.noto_sans_arabic_regular, FontWeight.Normal),
    Font(R.font.noto_sans_arabic_medium, FontWeight.Medium),
    Font(R.font.noto_sans_arabic_bold, FontWeight.Bold),
)

// الخط العربي الرئيسي (RTL أولوية)
val ArabicTypographyFamily = PlexArabicFamily

// Fallback للرموز/الإيموجي
val EmojiFamily = FontFamily.Default