package com.red.sovereign.ui.theme

import androidx.compose.ui.text.font.FontFamily

// PlexArabicFamily is defined once in RedTheme.kt. Reuse it for the
// compatibility aliases below; a second declaration makes every UI import
// ambiguous and prevents the app from compiling.

// Do not reference a Noto resource that is not bundled. Android will use the
// system fallback for glyphs that IBM Plex Sans Arabic does not contain.
// الخط العربي الرئيسي (RTL أولوية)
val ArabicTypographyFamily = PlexArabicFamily

// Fallback للرموز/الإيموجي
val EmojiFamily = FontFamily.Default