package com.red.sovereign.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.LayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.R

// â•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گ
// ط§ظ„ط®ط· ط§ظ„ظ…ظˆط­ظ‘ط¯ â€” IBM Plex Sans Arabic ط«ظ†ط§ط¦ظٹ ط§ظ„ظ†طµ (SIL OFL 1.1)
// ظ…ط¶ظ…ظ‘ظ† ظپظٹ ط§ظ„ط­ط²ظ…ط© ظ„ط§ ظ…ط¬ظ„ظˆط¨ ظ…ظ† ط§ظ„ط´ط¨ظƒط©: 4 ط£ظˆط²ط§ظ† ظ…ط­ظ„ظٹط© ظپظٹ res/font
// plex_arabic.xml (400/500/600/700). ظٹظ†ظ‡ظٹ ط§ط±طھط¯ط§ط¯ ط®ط·ظˆط· Google ط§ظ„ط´ط¨ظƒظٹط©
// ظˆظٹظˆط­ظ‘ط¯ ظ‡ظˆظٹط© ط§ظ„طھط·ط¨ظٹظ‚ ظ…ط¹ admin_dashboard (IBM Plex) ظˆظٹط¶ظ…ظ† ط«ط¨ط§طھ ظ…ظ‚ط§ط³ط§طھ
// ط§ظ„ط£ط³ط·ط± ط­طھظ‰ ط¹ظ„ظ‰ ط´ط¨ظƒط§طھ ط§ظ„ظٹظ…ظ† ط§ظ„ط¶ط¹ظٹظپط©.
// â•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گ
val PlexArabicFamily = FontFamily(
    Font(R.font.plex_arabic_regular, FontWeight.Normal, FontStyle.Normal),
    Font(R.font.plex_arabic_regular, FontWeight.Light, FontStyle.Normal),
    Font(R.font.plex_arabic_medium, FontWeight.Medium, FontStyle.Normal),
    Font(R.font.plex_arabic_semibold, FontWeight.SemiBold, FontStyle.Normal),
    Font(R.font.plex_arabic_bold, FontWeight.Bold, FontStyle.Normal),
    Font(R.font.plex_arabic_bold, FontWeight.ExtraBold, FontStyle.Normal),
    Font(R.font.plex_arabic_bold, FontWeight.Black, FontStyle.Normal)
)

val NotoSansArabicFamily = PlexArabicFamily

// ط¹ط§ط¦ظ„ط© ط§ظ„ط®ط· ط§ظ„ط£ط³ط§ط³ظٹط© ظ„ظ„طھط·ط¨ظٹظ‚ - Plex Arabic ظ…ط¹ Noto ظƒط¨ط¯ظٹظ„
val AppFontFamily = PlexArabicFamily

// ط£ط³ظ…ط§ط، ظ…ط³طھط¹ط§ط±ط© ظ„ظ„طھظˆط§ظپظ‚ â€” ظƒظ„ظ‡ط§ Plex Arabic ظپط¹ظ„ظٹظ‹ط§.
// @deprecated ط§ط³طھط¹ظ…ظ„ PlexArabicFamily ظ…ط¨ط§ط´ط±ط©ط› Cairo/Tajawal ط¨ط§ظ‚ظٹط§ظ† ظ„ظ„طھظˆط§ظپظ‚ ظپظ‚ط·
// ط­طھظ‰ ظ„ط§ ظٹط¸ظ† ظ‚ط§ط±ط¦ ط§ظ„ظƒظˆط¯ ظˆط¬ظˆط¯ ط¹ط§ط¦ظ„طھظٹظ† ظ…ط®طھظ„ظپطھظٹظ† (no Cairo confusion).
@Deprecated("ط§ط³طھط¹ظ…ظ„ PlexArabicFamily ظ…ط¨ط§ط´ط±ط©", ReplaceWith("PlexArabicFamily"))
val CairoFamily: FontFamily = PlexArabicFamily
@Deprecated("ط§ط³طھط¹ظ…ظ„ PlexArabicFamily ظ…ط¨ط§ط´ط±ط©", ReplaceWith("PlexArabicFamily"))
val TajawalFamily: FontFamily = PlexArabicFamily

// â•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گ
// ظ„ظˆط­ط© ط§ظ„ط£ظ„ظˆط§ظ† ط§ظ„ط³ظٹط§ط¯ظٹط© ط§ظ„ظ…ط­ط³ظ‘ظ†ط© â€” ظ†ط¸ط§ظ… ظ…طھظ†ط§ط³ظ‚ ظƒط§ظ…ظ„
// â•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گ

// â•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گ
// ظ„ظˆط­ط© ط§ظ„ط£ظ„ظˆط§ظ† ط§ظ„ط³ظٹط§ط¯ظٹط© â€” ظ‡ظˆظٹط© ظ…ط³طھظ‚ظ„ط© + ط§ظ„طھط²ط§ظ… WCAG ظ‚ط§ط¨ظ„ ظ„ظ„ظ‚ظٹط§ط³
// â•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گ
//
// ## ظ„ظ…ط§ط°ط§ طھط؛ظٹظ‘ط±طھ ط§ظ„ظ„ظˆط­ط©
//
// ظƒط§ظ†طھ ط§ظ„ظ„ظˆط­ط© ط§ظ„ط³ط§ط¨ظ‚ط© طھظ†ط³ط® ط£ظ„ظˆط§ظ† ط§ظ„ظ…ظ†ط§ظپط³ظٹظ† ط­ط±ظپظٹظ‹ط§: `#00A884` ظˆ`#25D366` ظ…ظ†
// ظˆط§طھط³ط§ط¨طŒ ظˆ`#2AABEE` ظˆ`#0E1621` ظˆ`#2B5278` ظ…ظ† طھظ„ط¬ط±ط§ظ…. ظ‡ط°ط§ ظٹظڈظپظ‚ط¯ ط§ظ„طھط·ط¨ظٹظ‚
// ظ‡ظˆظٹطھظ‡طŒ ظˆظٹط¬ط¹ظ„ آ«ظٹظˆظ†ط³آ» ظٹط¨ط¯ظˆ ظ†ط³ط®ط© ظ„ط§ ظ…ظ†طھط¬ظ‹ط§ ط³ظٹط§ط¯ظٹظ‹ط§.
//
// ظˆظƒط§ظ†طھ طھط®ط§ظ„ظپ ط§ظ„طھط¨ط§ظٹظ† ظپظٹ ط£ط±ط¨ط¹ط© ظ…ظˆط§ط¶ط¹ ظ…ظ‚ظٹط³ط©:
//   â€¢ ظ†طµ ط£ط¨ظٹط¶ ط¹ظ„ظ‰ `#00A884` = 3.03:1 â€” ط¯ظˆظ† AAA (7:1) ظˆط¯ظˆظ† AA (4.5:1) ط£طµظ„ظ‹ط§طŒ
//     ط£ظٹ ط£ظ† ظ†طµ ظƒظ„ ط²ط± ط£ط³ط§ط³ظٹ ظƒط§ظ† ط؛ظٹط± ظ…ظ‚ط±ظˆط، ظپط¹ظ„ظٹظ‹ط§.
//   â€¢ ظ†طµ ط£ط¨ظٹط¶ ط¹ظ„ظ‰ `#2AABEE` = 2.57:1 â€” ط£ط³ظˆط£.
//   â€¢ `#E53935` ط¹ظ„ظ‰ ط§ظ„ط®ظ„ظپظٹط© = 4.30:1 â€” ط¯ظˆظ† AAطŒ ظˆظ‡ظˆ ظ„ظˆظ† ط³ط¨ط£ظپظˆظ† ظˆط²ط± ط§ظ„ط¥ظ†ظ‡ط§ط،.
//   â€¢ ط§ظ„ظ†طµ ط§ظ„ط«ط§ظ†ظˆظٹ ط¹ظ„ظ‰ ط§ظ„ظپظ‚ط§ط¹ط© ط§ظ„طµط§ط¯ط±ط© = 2.94:1 â€” ط§ظ„ط·ط§ط¨ط¹ ط§ظ„ط²ظ…ظ†ظٹ ظˆآ«âœ“âœ“آ» ط´ط¨ظ‡
//     ط؛ظٹط± ظ…ط±ط¦ظٹظٹظ† ط¹ظ„ظ‰ ظƒظ„ ط±ط³ط§ظ„ط© طµط§ط¯ط±ط©.
//
// ## ظ‚ط§ط¹ط¯ط© ط§ظ„ط­ظ„ظ‘
//
// ط§ظ„ط£ط²ط±ط§ط± ط§ظ„ظ…ظ„ظˆظ‘ظ†ط© طھط­ظ…ظ„ ظ†طµظ‹ط§ **ط¯ط§ظƒظ†ظ‹ط§** (`YounesOnBrand`) ظ„ط§ ط£ط¨ظٹط¶. ط±ظپط¹ طھط¨ط§ظٹظ†
// ط§ظ„ط£ط¨ظٹط¶ ط¹ظ„ظ‰ ظ„ظˆظ† ظ…ط´ط¨ط¹ ط¥ظ„ظ‰ 7:1 ظٹظپط±ط¶ طھط¹ط·ظٹظ… ط§ظ„ظ„ظˆظ† ط­طھظ‰ ظٹظپظ‚ط¯ ط­ظٹط§طھظ‡ط› ط£ظ…ط§ طھط؛ظ…ظٹظ‚
// ط§ظ„ظ†طµ ط¹ظ„ظ‰ ظ„ظˆظ† ظپط§طھط­ ظپظٹط­ظ‚ظ‚ 9:1+ ظˆظٹظڈط¨ظ‚ظٹ ط§ظ„ظ„ظˆظ† ظ†ط§ط¨ط¶ظ‹ط§. ظ‡ط°ط§ ظ…ط§ طھظپط¹ظ„ظ‡ Material 3
// ظپظٹ `onPrimary` ظ„ظ„ط£ظ„ظˆط§ظ† ط§ظ„ظپط§طھط­ط©.
//
// ظƒظ„ ظ‚ظٹظ…ط© ط£ط¯ظ†ط§ظ‡ ظ…ظ‚ظٹط³ط© ظˆظ…ط«ط¨ظژظ‘طھط© ط¨ط§ط®طھط¨ط§ط± ظپظٹ `ColorContrastTest`.

// â”€â”€â”€ ط§ظ„ط£ظ„ظˆط§ظ† ط§ظ„ط£ط³ط§ط³ظٹط© â€” ط²ظ…ط±ط¯ ط³ظٹط§ط¯ظٹ ظˆط°ظ‡ط¨ ط¥ظ…ط¨ط±ط§ط·ظˆط±ظٹطŒ ظ„ط§ ط£ظ„ظˆط§ظ† ظ…ظ†ط§ظپط³ظٹظ† â”€â”€â”€â”€â”€â”€â”€â”€
/** ط²ظ…ط±ط¯ ط³ظٹط§ط¯ظٹ. ظ†طµ ط¯ط§ظƒظ† ظپظˆظ‚ظ‡ = 9.17:1 (AAA)طŒ ظˆظ‡ظˆ ظ†ظپط³ظ‡ 8.83:1 ط¹ظ„ظ‰ ط§ظ„ط®ظ„ظپظٹط©. */
val YounesPrimary      = Color(0xFF14C79A)
/** ط²ظ…ط±ط¯ ظ…ط¶ظٹط، ظ„ظ„طھظ†ط¨ظٹظ‡ط§طھ ظˆط§ظ„طھظˆظ‡ظ‘ط¬. ظ†طµ ط¯ط§ظƒظ† ظپظˆظ‚ظ‡ = 12.78:1. */
val YounesPrimaryGlow  = Color(0xFF3DE8BC)
/** ط²ظ…ط±ط¯ ط¹ظ…ظٹظ‚ ظ„ط·ط±ظپ ط§ظ„طھط¯ط±ظ‘ط¬ ط§ظ„ط¯ط§ظƒظ† (ظ‡ظˆط§ظ…ط´ ط§ظ„ط£ط²ط±ط§ط± ظˆط§ظ„ظ‡ط§ظ„ط§طھ) â€” ظˆظ„ظٹط³ ظ„ظˆظ†ط§ظ‹ ظ…ظ†ط§ظپط³ط§ظ‹. */
val YounesPrimaryDeep  = Color(0xFF00674F)
/** ط°ظ‡ط¨ ط¥ظ…ط¨ط±ط§ط·ظˆط±ظٹ ظ„ظ„ط±ظˆط§ط¨ط· ظˆط§ظ„ط´ط§ط±ط§طھ. ظ†طµ ط¯ط§ظƒظ† ظپظˆظ‚ظ‡ = 10.34:1طŒ ظˆط¹ظ„ظ‰ ط§ظ„ط®ظ„ظپظٹط© 9.96:1. */
val YounesAccent       = Color(0xFFE0B551)
/** ط°ظ‡ط¨ ظپط§طھط­ ظ„ظ„ط­ط¯ظˆط¯ ط§ظ„ظ…ظ…ظٹظ‘ط²ط© ظˆط§ظ„طھط¯ط±ظ‘ط¬ط§طھ. */
val YounesAccentSoft   = Color(0xFFF0D48C)
/** ط£ط²ط±ظ‚ ظ…ظ„ظƒظٹ â€” ظ…طھظ…ط§ظٹط² ط¹ظ† ط£ط²ط±ظ‚ طھظ„ط¬ط±ط§ظ…طŒ 6.80:1 ط¹ظ„ظ‰ ط§ظ„ط®ظ„ظپظٹط©. */
val YounesCobalt       = Color(0xFF4D9FE8)
/** ط¨ظ†ظپط³ط¬ظٹ ط§ظ„ظ…ط³ط§ط­ط§طھ ط§ظ„طµظˆطھظٹط© â€” 6.30:1 ط¹ظ„ظ‰ ط§ظ„ط®ظ„ظپظٹط©. */
val YounesPurple       = Color(0xFFB07CE8)
/** ط¨ظ†ظپط³ط¬ظٹ ط¹ظ…ظٹظ‚ ظ„ط·ط±ظپ ط§ظ„طھط¯ط±ظ‘ط¬ ط§ظ„ط´ط¨ظƒظٹ â€” ظ„ط§ ظٹظڈط³طھط¹ظ…ظ„ ظƒظ†طµطŒ ط¨ظ„ ظƒظ‡ط§ظ„ط© ط®ظ„ظپظٹط© ظپظ‚ط·. */
val YounesVioletDeep   = Color(0xFF6D3FB5)
/** ظƒظˆط¨ط§ظ„طھ ط¹ظ…ظٹظ‚ ظ„ط·ط±ظپ ط§ظ„طھط¯ط±ظ‘ط¬ ط§ظ„ط´ط¨ظƒظٹ â€” ظ‡ط§ظ„ط© ط®ظ„ظپظٹط© ظپظ‚ط·. */
val YounesCobaltDeep   = Color(0xFF1E4E8A)
/** ط£ط­ظ…ط± طھط­ط°ظٹط±ظٹ ظ„ظ„ط¨ط« ظˆط§ظ„ط¥ظ†ظ‡ط§ط، â€” 5.90:1 ط¹ظ„ظ‰ ط§ظ„ط®ظ„ظپظٹط© (ظƒط§ظ† 4.30 ط¯ظˆظ† AA). */
val YounesRose         = Color(0xFFF25C5C)
/** ط£ط­ظ…ط± ظٹط§ظ‚ظˆطھظٹ ط£ط؛ظ…ظ‚ ظ„ط²ط± ط¥ظ†ظ‡ط§ط، ط§ظ„ظ…ظƒط§ظ„ظ…ط© â€” ظٹط­ظ…ظ„ ظ†طµظ‹ط§ ط£ط¨ظٹط¶. */
val YounesRuby         = Color(0xFFE03131)

// â”€â”€â”€ ط£ظ„ظˆط§ظ† ط§ظ„ط®ظ„ظپظٹط© â€” طھط¯ط±ظ‘ط¬ ط§ط±طھظپط§ط¹ طµط§ط¹ط¯ ظˆظ…ظ‚ظٹط³ â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
val YounesVoid         = Color(0xFF0A0F18)  // ط£ط¹ظ…ظ‚ ظ…ط³طھظˆظ‰ â€” ط®ظ„ظپظٹط© ط§ظ„ط´ط§ط´ط©
val YounesMidnight     = Color(0xFF0A0F18)  // ط®ظ„ظپظٹط© ط§ظ„ط´ط§ط´ط© ظˆط§ظ„ظ…ط­ط§ط¯ط«ط©
val YounesDeep         = Color(0xFF131C29)  // ط£ط³ط·ط­ ط§ظ„ظ‚ظˆط§ط¦ظ… ظˆط´ط±ظٹط· ط§ظ„ظ…ظ„ط§ط­ط©
val YounesSurface1     = Color(0xFF131C29)  // ظƒط±ظˆطھ ط§ظ„ظ‚ظˆط§ط¦ظ…
val YounesSurface2     = Color(0xFF1B2635)  // ط£ط³ط·ط­ ط§ظ„ط¹ظ†ط§طµط± ط§ظ„ظ†ط´ط·ط©
val YounesSurface3     = Color(0xFF212E40)  // ط§ظ„ط­ظˆط§ط±ط§طھ ظˆط§ظ„ظ€ BottomSheet
val YounesBorder       = Color(0xFF2A394A)  // ظپظˆط§طµظ„ ط²ط®ط±ظپظٹط© â€” ظ„ط§ ظٹط´طھط±ط· ظ„ظ‡ط§ طھط¨ط§ظٹظ†
/**
 * ط­ط¯ظ‘ ط§ظ„ظ…ظƒظˆظ‘ظ†ط§طھ ط§ظ„طھظپط§ط¹ظ„ظٹط© (ط­ظ‚ظˆظ„ ط§ظ„ط¥ط¯ط®ط§ظ„طŒ ط§ظ„ط£ط²ط±ط§ط± ط§ظ„ظ…ظڈط­ط¯ظژظ‘ط¯ط©طŒ ط§ظ„ط´ط±ط§ط¦ط­).
 *
 * `YounesBorder` ظ…ظ†ط§ط³ط¨ ظ„ظ„ظپظˆط§طµظ„ ط§ظ„ط²ط®ط±ظپظٹط©طŒ ظ„ظƒظ† ط§ط³طھط®ط¯ط§ظ…ظ‡ ظƒظ€ `outline` ط¬ط¹ظ„ ط­ط¯ظ‘
 * `OutlinedTextField` ط¹ظ†ط¯ 1.45:1 ظپظˆظ‚ ط³ط·ط­ ط§ظ„ظ‚ظˆط§ط¦ظ… â€” ط£ظٹ ط­ظ‚ظ„ ط¨ظ„ط§ ط­ط¯ظ‘ ظ…ط±ط¦ظٹ ظپط¹ظ„ظٹظ‹ط§طŒ
 * ظˆظ‡ظˆ ظ…ط§ ظٹط®ط§ظ„ظپ WCAG 1.4.11 (ط­ط¯ظ‘ ط£ط¯ظ†ظ‰ 3:1 ظ„ط¹ظ†ط§طµط± ظˆط§ط¬ظ‡ط© ط؛ظٹط± ظ†طµظٹط©).
 *
 * ظ‡ط°ظ‡ ط§ظ„ظ‚ظٹظ…ط© طھط­ظ‚ظ‚ â‰¥4:1 ط¹ظ„ظ‰ ط§ظ„ط£ط³ط·ط­ ط§ظ„ط£ط±ط¨ط¹ط© (Midnight 5.74طŒ Surface1 5.13طŒ
 * Surface2 4.57طŒ Surface3 4.11) ظˆطھط¨ظ‚ظ‰ ط£ط¨ظ‡طھ ظ…ظ† ط§ظ„ظ†طµ ط§ظ„ط£ط¨ظٹط¶ ظپظ„ط§ طھط³ط­ط¨ ط§ظ„ط§ظ†طھط¨ط§ظ‡.
 */
val YounesOutline      = Color(0xFF7A8FA3)
/** ظ†طµ ط«ط§ظ†ظˆظٹ â€” 6.19:1 ط¹ظ„ظ‰ ط£ط¹ظ„ظ‰ ط³ط·ط­طŒ 8.65:1 ط¹ظ„ظ‰ ط§ظ„ط®ظ„ظپظٹط©. */
val YounesMuted        = Color(0xFF9FB0C2)

// â”€â”€â”€ ط£ظ„ظˆط§ظ† ط§ظ„ظ…ط­ط§ط¯ط«ط© ظˆط§ظ„ظپظ‚ط§ط¹ط§طھ â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
/** ظپظ‚ط§ط¹ط© طµط§ط¯ط±ط© â€” ظƒط­ظ„ظٹ ط³ظٹط§ط¯ظٹ ظ…طھظ…ط§ظٹط² ط¹ظ† `#2B5278` ط§ظ„طھظ„ط¬ط±ط§ظ…ظٹ. ط£ط¨ظٹط¶ ظپظˆظ‚ظ‡ 13.41:1. */
val YounesBubbleOut    = Color(0xFF14304F)
val YounesBubbleOutGlow = Color(0xFF1A3A5C)
/** ظپظ‚ط§ط¹ط© ظˆط§ط±ط¯ط© â€” ط£ط¨ظٹط¶ ظپظˆظ‚ظ‡ط§ 15.54:1. */
val YounesBubbleIn     = Color(0xFF182533)
/** âœ“âœ“ ظ…ظ‚ط±ظˆط، â€” 4.75:1 ط¹ظ„ظ‰ ط§ظ„ظپظ‚ط§ط¹ط© ط§ظ„طµط§ط¯ط±ط©طŒ ظپظˆظ‚ ط­ط¯ظ‘ 3:1 ظ„ظ„ط£ظٹظ‚ظˆظ†ط§طھ. */
val YounesReadTick     = Color(0xFF4D9FE8)

// â”€â”€â”€ ط£ظ„ظˆط§ظ† ط§ظ„ظ†طµظˆطµ ظˆط§ظ„طھط¨ط§ظٹظ† ط§ظ„ظ…ط±طھظپط¹ (WCAG AAA) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
/**
 * ظ†طµ ط§ظ„ط£ط³ط·ط­ ط§ظ„ظ…ظ„ظˆظ‘ظ†ط© (ط§ظ„ط£ط²ط±ط§ط± ط§ظ„ط£ط³ط§ط³ظٹط©طŒ ط§ظ„ط´ط§ط±ط§طھطŒ ط§ظ„ط´ط±ط§ط¦ط­ ط§ظ„ظ…ظ…ظ„ظˆط،ط©).
 *
 * ط¯ط§ظƒظ† ظ„ط§ ط£ط¨ظٹط¶: ط§ظ„ط£ط¨ظٹط¶ ط¹ظ„ظ‰ ط²ظ…ط±ط¯ ط£ظˆ ط°ظ‡ط¨ ظ…ط´ط¨ط¹ ظ„ط§ ظٹط¨ظ„ط؛ 4.5:1 ط£ط¨ط¯ظ‹ط§ ط¯ظˆظ† طھط¹ط·ظٹظ…
 * ط§ظ„ظ„ظˆظ†. ط§ظ„ظ†طµ ط§ظ„ط¯ط§ظƒظ† ظٹط¨ظ„ط؛ 9:1+ ظˆظٹظڈط¨ظ‚ظٹ ط§ظ„ظ„ظˆظ† ظ†ط§ط¨ط¶ظ‹ط§.
 */
val YounesOnBrand      = Color(0xFF06090F)
val YounesOnPrimary    = YounesOnBrand  // ظ†طµ ط¹ظ„ظ‰ ط§ظ„ط²ظ…ط±ط¯ â€” 9.17:1
val YounesOnAccent     = YounesOnBrand  // ظ†طµ ط¹ظ„ظ‰ ط§ظ„ط°ظ‡ط¨ â€” 10.34:1
val YounesOnSurface    = Color(0xFFFFFFFF)  // ظ†طµ ط£ط³ط§ط³ظٹ â€” 19.19:1 ط¹ظ„ظ‰ ط§ظ„ط®ظ„ظپظٹط©
val YounesOnSurfaceDim = YounesMuted        // ظ†طµ ط«ط§ظ†ظˆظٹ â€” 8.65:1 ط¹ظ„ظ‰ ط§ظ„ط®ظ„ظپظٹط©

// â”€â”€â”€ Migration aliases â€” ظ„ظ„ط­ظپط§ط¸ ط¹ظ„ظ‰ ط§ظ„طھظˆط§ظپظ‚ ظ…ط¹ ط§ظ„ظƒظˆط¯ ط§ظ„ظ‚ط¯ظٹظ… â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
val YounesEmerald      = YounesPrimary
val YounesEmeraldGlow  = YounesPrimaryGlow
val YounesGold         = YounesAccent
val YounesGoldLight    = YounesAccentSoft
val YounesInk          = YounesVoid
val YounesCyan         = YounesCobalt
val YounesDanger       = YounesRose
val YounesSurface      = YounesSurface1
val YounesSurfaceHigh  = YounesSurface2
val YounesMutedText    = YounesMuted

// legacy aliases
val AqyalGold           = YounesAccent
val YounesImperialGold  = Color(0xFFD4A843)
val AqyalGoldLight      = YounesAccentSoft
val AqyalDarkObsidian   = YounesVoid
val AqyalRoyalBlue      = YounesMidnight
val AqyalSurfaceNavy    = YounesSurface1
val AqyalSurfaceRaised  = YounesSurface2
val AqyalCyanGlow       = YounesCobalt
val RedCrimson          = YounesRose
val RedCrimsonGlow      = YounesRose
val RedMutedText        = YounesMuted

// â•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گ
// طھط¯ط±ط¬ط§طھ ظ„ظˆظ†ظٹط© ط§ط­طھط±ط§ظپظٹط© â€” ط´ط¨ظƒظٹط© ط®ط§ظپطھط© (mesh) ظ„ط§ ط£ط³ظˆط¯ ظ…ط³ط·ظ‘ط­
// ط§ظ„ظ‚ط§ط¹ط¯ط©: ط§ظ„ط£ط³ظˆط¯ ط§ظ„ظ…ط³ط·ظ‘ط­ ظٹط³ط­ظ‚ ط§ظ„طھظپط§طµظٹظ„ ط¹ظ„ظ‰ ط´ط§ط´ط§طھ ط±ط®ظٹطµط© (OLED-crushing)
// ظˆظٹط¨ط¯ظˆ ط£ط±ط®طµ ظ…ظ† ظˆط§طھط³ط§ط¨/طھظ„ط¬ط±ط§ظ…. ظƒظ„ ط®ظ„ظپظٹط© ظ‡ظ†ط§ ط·ط¨ظ‚ط© ط£ط³ط§ط³ ظ…ط±ظپظˆط¹ط©
// (0A0F18 ظ„ط§ 000000) + ظ‡ط§ظ„ط§طھ ظ…ظ„ظˆظ‘ظ†ط© â‰¤12% ط£ظ„ظپط§ ظ„ط§ طھظڈط¶ط¹ظپ طھط¨ط§ظٹظ† ط§ظ„ظ†طµ.
// â•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گ
/** ط®ظ„ظپظٹط© ط¯ط§ظƒظ†ط© ط´ط¨ظƒظٹط©: ط²ظ…ط±ط¯ 8% ط£ط¹ظ„ظ‰ + ظƒظˆط¨ط§ظ„طھ 10% ط£ط³ظپظ„ + ط°ظ‡ط¨ 4% â€” طھط¨ط§ظٹظ† ط§ظ„ظ†طµ ظ…ط­ظپظˆط¸. */
val GradientBackground  = Brush.verticalGradient(
    listOf(Color(0xFF0D1622), YounesVoid, Color(0xFF0A1220))
)
val GradientMeshEmerald = Brush.radialGradient(
    colors = listOf(YounesPrimary.copy(alpha = 0.10f), Color.Transparent),
    radius = 900f
)
val GradientMeshCobalt = Brush.radialGradient(
    colors = listOf(YounesCobalt.copy(alpha = 0.12f), Color.Transparent),
    radius = 1000f
)
val GradientMeshViolet = Brush.radialGradient(
    colors = listOf(YounesPurple.copy(alpha = 0.09f), Color.Transparent),
    radius = 900f
)
val GradientMeshGold = Brush.radialGradient(
    colors = listOf(YounesAccent.copy(alpha = 0.06f), Color.Transparent),
    radius = 800f
)
/** ط®ظ„ظپظٹط© ظپط§طھط­ط© ط´ط¨ظƒظٹط© (ظ„ط¤ظ„ط¤ظٹ #F7F8FA + ط²ظ…ط±ط¯ 5% + ظƒظˆط¨ط§ظ„طھ 5%) â€” ظ„ط§ ط£ط¨ظٹط¶ طµط§ط±ط®. */
val GradientBackgroundLight = Brush.verticalGradient(
    listOf(Color(0xFFF7F8FA), Color(0xFFEFF3F5), Color(0xFFE9EFF2))
)
val GradientPrimary     = Brush.linearGradient(
    listOf(YounesPrimary, YounesPrimary) // Official: solid emerald, no clownish glow
)
val GradientAccent      = Brush.linearGradient(
    listOf(YounesAccent, YounesAccentSoft)
)
val GradientBubbleOut   = Brush.linearGradient(
    0f to YounesBubbleOut, 1f to YounesBubbleOutGlow,
    start = androidx.compose.ui.geometry.Offset.Zero,
    end = androidx.compose.ui.geometry.Offset(1000f, 0f)
)
val GradientTopBar      = Brush.verticalGradient(
    listOf(YounesMidnight, YounesMidnight.copy(alpha = 0f))
)
val GradientCallScreen  = Brush.radialGradient(
    colors = listOf(
        YounesPrimary.copy(alpha = 0.10f),
        YounesCobalt.copy(alpha = 0.06f),
        YounesVoid
    ),
    radius = 1100f
)
val GradientGold        = Brush.linearGradient(
    listOf(YounesAccent, YounesAccentSoft, YounesAccent)
)
val GradientNavBar      = Brush.verticalGradient(
    listOf(YounesMidnight.copy(alpha = 0f), YounesDeep)
)

// â•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گ
// ظ†ط¸ط§ظ… ط§ظ„ط·ط¨ط§ط¹ط© â€” ط¹ط§ط¦ظ„ط© ظˆط§ط­ط¯ط© ط«ظ†ط§ط¦ظٹط© ط§ظ„ظ†طµ (Plex Arabic)
// Material 3 Type Scale ظƒط§ظ…ظ„ â€” 15 ظ†ظ…ط·ظ‹ط§ طھط؛ط·ظٹ ظƒظ„ ط§ظ„ط§ط³طھط¹ظ…ط§ظ„ط§طھ
// â•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گ
private val redTypography = Typography(
    // Display - ظ„ظ„ط¹ظ†ط§ظˆظٹظ† ط§ظ„ظƒط¨ظٹط±ط© ط¬ط¯ط§ظ‹
    displayLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 0.sp
    ),
    displayMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = AppFontFamily,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 0.sp
    ),

    // Headline - ظ„ظ„ط¹ظ†ط§ظˆظٹظ† ط§ظ„ط±ط¦ظٹط³ظٹط©
    headlineLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp
    ),

    // Title - ظ„ظ„ط¹ظ†ط§ظˆظٹظ† ط§ظ„ظ…طھظˆط³ط·ط©
    titleLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp
    ),

    // Body - ظ„ظ„ظ†طµظˆطµ ط§ظ„ط£ط³ط§ط³ظٹط© (min 14spطŒ lineHeight â‰¥ 1.5)
    bodyLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontSize = 16.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontSize = 14.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp
    ),
    bodySmall = TextStyle(
        fontFamily = AppFontFamily,
        fontSize = 13.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp
    ),

    // Label - ظ„ظ„طھط³ظ…ظٹط§طھ ظˆط§ظ„ط£ط²ط±ط§ط±
    labelLarge = TextStyle(
        fontFamily = AppFontFamily,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp
    ),
    labelMedium = TextStyle(
        fontFamily = AppFontFamily,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = AppFontFamily,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp
    ),
)

// Typography ظ…ط­ط³ظ† ظ„ظ„ظˆط¶ط¹ ط§ظ„ظ„ظٹظ„ظٹ (ط£ظˆط²ط§ظ† ط£ط«ظ‚ظ„ ظ„ظ„طھط¨ط§ظٹظ†)
private val redTypographyDark = Typography(
    displayLarge = redTypography.displayLarge.copy(fontWeight = FontWeight.Black),
    displayMedium = redTypography.displayMedium.copy(fontWeight = FontWeight.ExtraBold),
    displaySmall = redTypography.displaySmall.copy(fontWeight = FontWeight.Black),
    headlineLarge = redTypography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
    headlineMedium = redTypography.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = redTypography.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = redTypography.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = redTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = redTypography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = redTypography.bodyLarge.copy(fontWeight = FontWeight.Medium, lineHeight = 28.sp),
    bodyMedium = redTypography.bodyMedium.copy(fontWeight = FontWeight.Normal, lineHeight = 24.sp),
    bodySmall = redTypography.bodySmall.copy(fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    labelLarge = redTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = redTypography.labelMedium.copy(fontWeight = FontWeight.Medium),
    labelSmall = redTypography.labelSmall.copy(fontWeight = FontWeight.Medium),
)

// â•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گ
// ظ†ط¸ط§ظ… ط§ظ„ط£ط´ظƒط§ظ„ â€” ط­ظˆط§ظپ ظ†ط§ط¹ظ…ط© ظˆط­ط¯ظٹط«ط©
// â•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گ
private val redShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small      = RoundedCornerShape(14.dp),
    medium     = RoundedCornerShape(20.dp),
    large      = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

// â•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گ
// ظ†ط¸ط§ظ… ط§ظ„ط£ظ„ظˆط§ظ† Material3 â€” ط¯ط§ظƒظ† ظƒط§ظ…ظ„
// â•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گ
private val redColorScheme = darkColorScheme(
    // ط§ظ„ط£ط³ط§ط³ظٹ â€” ط§ظ„ط£ط®ط¶ط± ط§ظ„ط³ظٹط§ط¯ظٹ
    primary               = YounesPrimary,
    onPrimary             = YounesOnPrimary,
    primaryContainer      = Color(0xFF004D3A),
    onPrimaryContainer    = YounesPrimaryGlow,

    // ط§ظ„ط«ط§ظ†ظˆظٹ â€” ط§ظ„ط³ظ…ط§ظˆظٹ
    secondary             = YounesCobalt,
    onSecondary           = Color(0xFF001F2A),
    secondaryContainer    = Color(0xFF003A48),
    onSecondaryContainer  = Color(0xFFB2EDFA),

    // ط§ظ„ط«ط§ظ„ط«ظٹ â€” ط§ظ„ط°ظ‡ط¨ظٹ
    tertiary              = YounesAccent,
    onTertiary            = YounesOnAccent,
    tertiaryContainer     = Color(0xFF3D2E00),
    onTertiaryContainer   = YounesAccentSoft,

    // ط§ظ„ط®ظ„ظپظٹط© ظˆط§ظ„ط£ط³ط·ط­
    background            = YounesMidnight,
    onBackground          = YounesOnSurface,
    surface               = YounesSurface1,
    onSurface             = YounesOnSurface,
    surfaceVariant        = YounesSurface2,
    onSurfaceVariant      = YounesMuted,
    surfaceTint           = YounesPrimary,

    // ط§ظ„ط­ط§ظˆظٹط§طھ ط§ظ„ط³ط·ط­ظٹط©
    surfaceContainer          = YounesSurface1,
    surfaceContainerLow       = YounesMidnight,
    surfaceContainerHigh      = YounesSurface2,
    surfaceContainerHighest   = YounesSurface3,

    // ط§ظ„ط­ط¯ظˆط¯
    // outline = ط­ط¯ظ‘ ط§ظ„ط¹ظ†ط§طµط± ط§ظ„طھظپط§ط¹ظ„ظٹط© (WCAG 1.4.11 â‰¥ 3:1)
    // outlineVariant = ط§ظ„ظپظˆط§طµظ„ ط§ظ„ط²ط®ط±ظپظٹط©طŒ ظ„ط§ ظٹط´طھط±ط· ظ„ظ‡ط§ ط­ط¯ظ‘ طھط¨ط§ظٹظ†
    outline               = YounesOutline,
    outlineVariant        = YounesBorder,

    // ط§ظ„ط®ط·ط£ ظˆط§ظ„طھط­ط°ظٹط±
    error                 = YounesRose,
    onError               = Color(0xFF3A0010),
    errorContainer        = Color(0xFF5C001A),
    onErrorContainer      = Color(0xFFFFB3B8),

    // ط¥ط¶ط§ظپظٹ
    inversePrimary        = YounesOnPrimary,
    inverseSurface        = YounesOnSurface,
    inverseOnSurface      = YounesMidnight,
    scrim                 = Color(0xE0000000),
)

private val redHighContrastColorScheme = redColorScheme.copy(
    onBackground      = Color.White,
    onSurface         = Color.White,
    onSurfaceVariant  = Color(0xFFDCEEF6),
    outline           = YounesPrimaryGlow,
)

// â”€â”€â”€ Light schemes â€” ظ†ظپط³ ط§ظ„ظ‡ظˆظٹط©طŒ ط®ظ„ظپظٹط© ظپط§طھط­ط© #F7F8FA (ظ„ط¤ظ„ط¤ظٹ) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
private val redLightColorScheme = lightColorScheme(
    primary               = Color(0xFF0A7A5E),
    onPrimary             = Color.White,
    primaryContainer      = Color(0xFFA8F0D8),
    onPrimaryContainer    = Color(0xFF002117),
    secondary             = Color(0xFF2E7DA8),
    onSecondary           = Color.White,
    secondaryContainer    = Color(0xFFBFE6F7),
    onSecondaryContainer  = Color(0xFF001F2A),
    tertiary              = Color(0xFF8A6A0A),
    onTertiary            = Color.White,
    tertiaryContainer     = Color(0xFFFFE08B),
    onTertiaryContainer   = Color(0xFF221B00),
    background            = Color(0xFFF7F8FA),
    onBackground          = Color(0xFF0F1B2D),
    surface               = Color(0xFFFFFFFF),
    onSurface             = Color(0xFF0F1B2D),
    surfaceVariant        = Color(0xFFE6E8EB),
    onSurfaceVariant      = Color(0xFF5A6B7D),
    surfaceTint           = Color(0xFF0A7A5E),
    surfaceContainer          = Color(0xFFF0F2F5),
    surfaceContainerLow       = Color(0xFFF7F8FA),
    surfaceContainerHigh      = Color(0xFFE6E8EB),
    surfaceContainerHighest   = Color(0xFFDDE1E6),
    outline               = Color(0xFF7A8FA3),
    outlineVariant        = Color(0xFFD0D7DE),
    error                 = Color(0xFFD32F2F),
    onError               = Color.White,
    errorContainer        = Color(0xFFFFDAD6),
    onErrorContainer      = Color(0xFF410002),
    inversePrimary        = YounesPrimary,
    inverseSurface        = Color(0xFF0A0F18),
    inverseOnSurface      = Color(0xFFF7F8FA),
    scrim                 = Color(0x66000000),
)

private val redHighContrastLight = redLightColorScheme.copy(
    onBackground = Color(0xFF0A0F18),
    onSurface = Color(0xFF0A0F18),
    outline = Color(0xFF0A7A5E),
)

val telegramColorScheme = redColorScheme.copy(
    primary = Color(0xFF2AABEE),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1E3A5F),
    secondary = Color(0xFF64B5F6),
    background = Color(0xFF0E1621),
    surface = Color(0xFF17212B),
    surfaceVariant = Color(0xFF232E3C),
    outline = Color(0xFF2B5278)
)
val telegramLightColorScheme = redLightColorScheme.copy(
    primary = Color(0xFF0A7DBF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBFE6F7),
    secondary = Color(0xFF2AABEE),
    background = Color(0xFFF7F8FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE6E8EB),
)

val whatsAppColorScheme = redColorScheme.copy(
    primary = Color(0xFF00A884),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF005C4B),
    secondary = Color(0xFF25D366),
    background = Color(0xFF0B141A),
    surface = Color(0xFF111B21),
    surfaceVariant = Color(0xFF202C33),
    outline = Color(0xFF2A3942)
)
val whatsAppLightColorScheme = redLightColorScheme.copy(
    primary = Color(0xFF008069),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA8F0D8),
    secondary = Color(0xFF25D366),
    background = Color(0xFFF7F8FA),
    surface = Color(0xFFFFFFFF),
)

val oledColorScheme = redColorScheme.copy(
    primary = Color(0xFF00E676),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF00381C),
    secondary = Color(0xFFFFD54F),
    background = Color(0xFF000000),
    surface = Color(0xFF0D0D0D),
    surfaceVariant = Color(0xFF161616),
    outline = Color(0xFF262626)
)
val oledLightColorScheme = redLightColorScheme // ط§ظ„ط£ظˆظ„ظٹط¯ ط§ظ„ظپط§طھط­ = ط§ظ„ظپط§طھط­ ط§ظ„ط¹ط§ط¯ظٹ

// â”€â”€â”€ ط£ظˆط¶ط§ط¹ ط§ظ„ظ…ط¸ظ‡ط± â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
enum class AppThemeMode(val label: String) {
    LIGHT("ظپط§طھط­"),
    DARK("ظ„ظٹظ„ظٹ"),
    SYSTEM("ط­ط³ط¨ ط§ظ„ظ†ط¸ط§ظ…")
}

enum class AppThemePreset(val label: String, val description: String) {
    SOVEREIGN("ظٹظˆظ†ط³ ط§ظ„ط³ظٹط§ط¯ظٹ", "ط£ط³ظˆط¯ ظ…ظ„ظƒظٹ ظ…ط¹ ط£ط®ط¶ط± ط²ظ…ط±ط¯ظٹ ظˆظ„ظ…ط³ط§طھ ط°ظ‡ط¨ظٹط© â€” ط§ظ„ظ‡ظˆظٹط© ط§ظ„ط£طµظ„ظٹط©"),
    TELEGRAM_DARK("طھظ„ط¬ط±ط§ظ… ط§ظ„ظƒط­ظ„ظٹ", "ط£ط²ط±ظ‚ طھظ„ط¬ط±ط§ظ… ط§ظ„ط£ظ†ظٹظ‚ ظ…ط¹ ظƒط­ظ„ظٹ ط¯ط§ظƒظ†"),
    WHATSAPP_DARK("ظˆط§طھط³ط§ط¨ ط§ظ„ط²ظ…ط±ط¯ظٹ", "ط£ط®ط¶ط± ظˆط§طھط³ط§ط¨ ط§ظ„ظƒظ„ط§ط³ظٹظƒظٹ ط§ظ„ظ…ط±ظٹط­ ظ„ظ„ط¹ظٹظ†"),
    OLED_BLACK("ط£ظˆظ„ظٹط¯ ظپط§ط¦ظ‚ ط§ظ„ط³ظˆط§ط¯", "ط³ظˆط§ط¯ طھط§ظ… 100% ظ„طھظˆظپظٹط± ط§ظ„ط·ط§ظ‚ط© ظˆط£ظ‚طµظ‰ طھط¨ط§ظٹظ†"),
    DYNAMIC("ط¯ظٹظ†ط§ظ…ظٹظƒظٹ", "ط£ظ„ظˆط§ظ† ظ…ط³طھط®ط±ط¬ط© ظ…ظ† ط®ظ„ظپظٹط© ط§ظ„ظ‡ط§طھظپ â€” Material You (ط£ظ†ط¯ط±ظˆظٹط¯ 12+)"),
    CUSTOM("ظ…ط®طµطµ", "ط§ط®طھط± ظ„ظˆظ†ظƒ ط¨ظ†ظپط³ظƒ ظ…ط¹ ط­ط§ط±ط³ طھط¨ط§ظٹظ† ط°ظƒظٹ")
}

object AppThemeState {
    var currentPreset by androidx.compose.runtime.mutableStateOf(AppThemePreset.SOVEREIGN)
    var themeMode by androidx.compose.runtime.mutableStateOf(AppThemeMode.SYSTEM)
    var highContrast by androidx.compose.runtime.mutableStateOf(false)
    var liquidGlassEnabled by androidx.compose.runtime.mutableStateOf(true)
    var reduceMotion by androidx.compose.runtime.mutableStateOf(false)
    var fontScale by androidx.compose.runtime.mutableStateOf(1.0f)
    var customPrimary by androidx.compose.runtime.mutableStateOf<Color?>(null)
    // 6 ط£ظ„ظˆط§ظ† ظ…ظ‚طھط±ط­ط© ظ„ظ„ظ…ط®طµطµ طھط¶ظ…ظ† â‰¥4.5:1
    val customPresets = listOf(
        YounesPrimary to "ط²ظ…ط±ط¯ ظٹظˆظ†ط³",
        YounesAccent to "ط°ظ‡ط¨ ط¥ظ…ط¨ط±ط§ط·ظˆط±ظٹ",
        YounesCobalt to "ط£ط²ط±ظ‚ ظ…ظ„ظƒظٹ",
        Color(0xFFE53935) to "ط£ط­ظ…ط± ط­ظٹ",
        Color(0xFF8E24AA) to "ط¨ظ†ظپط³ط¬ظٹ ظ…ظ„ظƒظٹ",
        Color(0xFF00ACC1) to "طھط±ظƒظˆط§ط²"
    )
}

// â•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گ
// ط«ظٹظ… ظٹظˆظ†ط³ ط§ظ„ط±ط¦ظٹط³ظٹ â€” ظٹط¯ط¹ظ… ظپط§طھط­/ظ„ظٹظ„ظٹ/ظ†ط¸ط§ظ… + ط¯ظٹظ†ط§ظ…ظٹظƒظٹ + ظ…ط®طµطµ + Liquid Glass
// â•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گ
@Composable
fun YounesTheme(
    preset: AppThemePreset = AppThemeState.currentPreset,
    mode: AppThemeMode = AppThemeState.themeMode,
    highContrast: Boolean = AppThemeState.highContrast,
    liquidGlass: Boolean = AppThemeState.liquidGlassEnabled,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemIsDark = isSystemInDarkTheme()
    val isDark = when (mode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> systemIsDark
    }

    // â”€â”€â”€ ط§ط®طھظٹط§ط± ط§ظ„ظ„ظˆط­ط© ط§ظ„ط£ط³ط§ط³ظٹط© â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
    val baseScheme = when (preset) {
        AppThemePreset.SOVEREIGN -> if (isDark) redColorScheme else redLightColorScheme
        AppThemePreset.TELEGRAM_DARK -> if (isDark) telegramColorScheme else telegramLightColorScheme
        AppThemePreset.WHATSAPP_DARK -> if (isDark) whatsAppColorScheme else whatsAppLightColorScheme
        AppThemePreset.OLED_BLACK -> if (isDark) oledColorScheme else oledLightColorScheme
        AppThemePreset.DYNAMIC -> {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (isDark) redColorScheme else redLightColorScheme
            }
        }
        AppThemePreset.CUSTOM -> {
            val custom = AppThemeState.customPrimary
            if (custom != null) {
                val base = if (isDark) redColorScheme else redLightColorScheme
                base.copy(primary = custom, primaryContainer = custom.copy(alpha = 0.18f))
            } else {
                if (isDark) redColorScheme else redLightColorScheme
            }
        }
    }

    val finalScheme = if (highContrast) {
        if (isDark) baseScheme.copy(
            onBackground = Color.White,
            onSurface = Color.White,
            onSurfaceVariant = Color(0xFFDCEEF6),
            outline = baseScheme.primary
        ) else baseScheme.copy(
            onBackground = Color(0xFF0A0F18),
            onSurface = Color(0xFF0A0F18),
            outline = baseScheme.primary
        )
    } else baseScheme

    // â”€â”€â”€ ظ…ظ‚ظٹط§ط³ ط§ظ„ط®ط·: Density ظپظٹ MainActivity ظٹظƒط¨ظ‘ط± ظƒظ„ sp â€” ظ„ط§ طھظƒط¨ظٹط± ط«ط§ظ†ظچ ظ‡ظ†ط§
    // (ط§ظ„طھظƒط¨ظٹط± ط§ظ„ظ…ط²ط¯ظˆط¬ ط§ظ„ط³ط§ط¨ظ‚ 1.3xâ†’1.69x ظƒط§ظ† ظٹظپط¬ظ‘ط± ط§ظ„ظپظ‚ط§ط¹ط§طھ). ظ†ط­طھط±ظ… fontScale
    // ط¹ط¨ط± Density ظپظ‚ط· ظˆظ†ظ…ط±ط± redTypography ظƒظ…ط§ ظ‡ظٹ.
    MaterialTheme(
        colorScheme = finalScheme,
        typography = redTypography,
        shapes = redShapes,
        content = content
    )
}

@Composable
private fun rememberScaledTypography(scale: Float): Typography {
    if (scale == 1f) return redTypography
    fun TextStyle.scaled() = copy(fontSize = fontSize * scale, lineHeight = lineHeight * scale)
    return Typography(
        displayLarge = redTypography.displayLarge.scaled(),
        displayMedium = redTypography.displayMedium.scaled(),
        displaySmall = redTypography.displaySmall.scaled(),
        headlineLarge = redTypography.headlineLarge.scaled(),
        headlineMedium = redTypography.headlineMedium.scaled(),
        headlineSmall = redTypography.headlineSmall.scaled(),
        titleLarge = redTypography.titleLarge.scaled(),
        titleMedium = redTypography.titleMedium.scaled(),
        titleSmall = redTypography.titleSmall.scaled(),
        bodyLarge = redTypography.bodyLarge.scaled(),
        bodyMedium = redTypography.bodyMedium.scaled(),
        bodySmall = redTypography.bodySmall.scaled(),
        labelLarge = redTypography.labelLarge.scaled(),
        labelMedium = redTypography.labelMedium.scaled(),
        labelSmall = redTypography.labelSmall.scaled(),
    )
}

// â•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گ
// ط®ظ„ظپظٹط© ط§ظ„ط´ط§ط´ط© ط§ظ„ط±ط¦ظٹط³ظٹط© â€” ط´ط¨ظƒظٹط© ط®ط§ظپطھط© + Liquid Glass (2026)
// ط·ط¨ظ‚ط© ط£ط³ط§ط³ ظ…ط±ظپظˆط¹ط© (ظ„ط§ 000000) + ظ‡ط§ظ„ط§طھ ط²ظ…ط±ط¯/ظƒظˆط¨ط§ظ„طھ/ط¨ظ†ظپط³ط¬ â‰¤12%.
// ظپظٹ ط§ظ„ظˆط¶ط¹ ط§ظ„ظپط§طھط­: ظ„ط¤ظ„ط¤ظٹ + ظ‡ط§ظ„ط§طھ 5% â€” ظƒظ„ ط´ط§ط´ط© ط°ط§طھ ط«ظٹظ… ظƒط§ظ…ظ„.
// â•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گ
@Composable
fun SovereignBackground(content: @Composable () -> Unit) {
    val isDark = when (AppThemeState.themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val bgBrush = when (AppThemeState.currentPreset) {
        AppThemePreset.OLED_BLACK    -> if (isDark) Brush.verticalGradient(listOf(Color(0xFF000000), Color(0xFF0A0A0A))) else GradientBackgroundLight
        AppThemePreset.WHATSAPP_DARK -> if (isDark) Brush.verticalGradient(listOf(Color(0xFF0B141A), Color(0xFF111B21), Color(0xFF0B141A))) else GradientBackgroundLight
        AppThemePreset.TELEGRAM_DARK -> if (isDark) Brush.verticalGradient(listOf(Color(0xFF0E1621), Color(0xFF17212B), Color(0xFF0E1621))) else GradientBackgroundLight
        AppThemePreset.DYNAMIC       -> if (isDark) GradientBackground else GradientBackgroundLight
        AppThemePreset.CUSTOM        -> if (isDark) GradientBackground else GradientBackgroundLight
        AppThemePreset.SOVEREIGN     -> if (isDark) GradientBackground else GradientBackgroundLight
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(bgBrush)
    ) {
        // ظ‡ط§ظ„ط§طھ ط´ط¨ظƒظٹط© ط®ط§ظپطھط© â€” ظپظˆظ‚ ط§ظ„ط£ط³ط§ط³طŒ طھط­طھ ط§ظ„ظ…ط­طھظˆظ‰. ط£ظ„ظپط§ ظ…ظ†ط®ظپط¶ ط¹ظ…ط¯ظ‹ط§
        // ط­طھظ‰ ظ„ط§ طھظڈط¶ط¹ظپ طھط¨ط§ظٹظ† ط§ظ„ظ†طµ (ط§ظ„ظ†طµ ظٹظڈظ‚ط§ط³ ط¹ظ„ظ‰ ط§ظ„ط£ط³ط§ط³ ظ„ط§ ط¹ظ„ظ‰ ط§ظ„ظ‡ط§ظ„ط©).
        if (AppThemeState.currentPreset == AppThemePreset.SOVEREIGN) {
            if (isDark) {
                Box(Modifier.fillMaxSize().background(GradientMeshEmerald))
                Box(Modifier.fillMaxSize().background(GradientMeshCobalt))
                Box(Modifier.fillMaxSize().background(GradientMeshViolet))
            } else {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.radialGradient(
                            colors = listOf(
                                YounesPrimary.copy(alpha = 0.05f),
                                Color.Transparent
                            ),
                            radius = 900f
                        )
                    )
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.radialGradient(
                            colors = listOf(
                                YounesCobalt.copy(alpha = 0.05f),
                                Color.Transparent
                            ),
                            radius = 1000f
                        )
                    )
                )
            }
        }
        content()
    }
}

// â•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گ
// ط«ظˆط§ط¨طھ ط§ظ„ط£ظ†ظٹظ…ظٹط´ظ† ط§ظ„ظ…ط´طھط±ظƒط© â€” ط°ظˆظ‚ ظ‡ط§ط¯ط¦ ظٹطھظپظˆظ‚ ط¹ظ„ظ‰ ظˆط§طھط³ط§ط¨/طھظ„ط¬ط±ط§ظ…
// ط§ظ„ظ‚ط§ط¹ط¯ط©: ظ†ط¨ط¶ط© ظˆط§ط­ط¯ط© ظƒط­ط¯ ط£ظ‚طµظ‰ ظ„ظƒظ„ ط´ط§ط´ط©طŒ ظ‡ط§ظ„ط© ط¨ط·ظٹط¦ط© 1800ms+طŒ ظƒظ„
// InfiniteTransition ظ…ط؛ظ„ظ‚ (gated) ط®ظ„ظپ isSpeaking/ظ…ظƒط§ظ„ظ…ط©/animatedطŒ
// ظˆظ…ط³ط§ط± ط«ط§ط¨طھ ظƒط§ظ…ظ„ ط¹ظ†ط¯ reduceMotion (prefers-reduced-motion).
// â•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گ
val SpringSnappy  = spring<Float>(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy)
val SpringSmooth  = spring<Float>(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioNoBouncy)

/** ظ…ط¯ط© ط§ظ„ظ‡ط§ظ„ط© ط§ظ„ظ†ط§ط¨ط¶ط© â€” 2000ms: ط¨ط·ظٹط¦ط© ظپط§ط®ط±ط©طŒ ظ„ط§ طھظ„ظ‡ط« (ظˆط§طھط³ط§ط¨ ~1000ms). */
const val SovereignPulseDurationMs = 2000
/** ظ…ط¯ط© ظ…ظˆط¬ ط§ظ„طµظˆطھ â€” 1900ms ظ…ط¹ Reverse ظ†ط§ط¹ظ…. */
const val SovereignWaveDurationMs = 1900
/** ظ…ط¯ط© طھظˆظ‡ط¬ ط§ظ„ط´ط§ط±ط© â€” 2000ms. */
const val SovereignBadgeGlowMs = 2000
/** ط£ظ‚طµظ‰ ظ†ط¨ط¶ ظ…طھط²ط§ظ…ظ† ظپظٹ ط§ظ„ط´ط§ط´ط© ط§ظ„ظˆط§ط­ط¯ط© = 1 (ظ„ط§ ظ…ظ‡ط±ط¬ط§ظ† ظ†ط¨ط¶). */
const val SovereignMaxPulsesPerScreen = 1

// â•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گ
// Liquid Glass tiers â€” ط¶ط¨ط§ط¨ظٹط© 8/20/40 ظپظ‚ط· ط¹ظ„ظ‰ nav/sheets (2026)
// ط§ظ„ظ‚ط§ط¹ط¯ط©: ط§ظ„ط¨ظ„ظˆط± ظ…ظƒظ„ظپ (GPU) ظپظٹظڈط­طµط± ظپظٹ ط´ط±ظٹط· ط§ظ„طھظ†ظ‚ظ„ ظˆط§ظ„ظ€ BottomSheet.
// fallback ظ…ط¹طھظ… طھظ„ظ‚ط§ط¦ظٹ ط¹ظ†ط¯ طھط¹ط·ظٹظ„ liquidGlass ط£ظˆ ط؛ظٹط§ط¨ Haze.
// â•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گâ•گ
object SovereignGlassTier {
    /** ط´ط±ظٹط· ط¹ظ„ظˆظٹ/ط³ظپظ„ظٹ â€” ط®ظپظٹظپ. */
    val NavBar = 8.dp
    /** ط¨ط·ط§ظ‚ط§طھ ط²ط¬ط§ط¬ظٹط© â€” ظ…طھظˆط³ط·. */
    val Card = 20.dp
    /** ط­ظˆط§ط±ط§طھ ظˆ Sheets â€” ط¹ظ…ظٹظ‚. */
    val Sheet = 40.dp

    /** ظ„ظˆظ† ط§ط­طھظٹط§ط·ظٹ ظ…ط¹طھظ… ظ„ظƒظ„ ط·ط¨ظ‚ط© ط¹ظ†ط¯ ط؛ظٹط§ط¨ ط§ظ„ط¨ظ„ظˆط± (isDark). */
    @Composable
    fun fallback(isDark: Boolean, tier: androidx.compose.ui.unit.Dp): Color {
        val scheme = MaterialTheme.colorScheme
        return when (tier) {
            NavBar -> scheme.surface.copy(alpha = if (isDark) 0.85f else 0.88f)
            Card -> scheme.surfaceContainerHigh.copy(alpha = if (isDark) 0.92f else 0.95f)
            else -> scheme.surfaceContainerHighest.copy(alpha = 0.97f)
        }
    }
}

