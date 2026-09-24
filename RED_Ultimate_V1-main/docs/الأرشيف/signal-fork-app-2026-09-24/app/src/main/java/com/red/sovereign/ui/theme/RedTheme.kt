package com.red.sovereign.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ════════════════════════════════════════════════════════════════════════
//  🔴 RED ULTIMATE — ULTRA-PREMIUM DESIGN SYSTEM 2026
//  مستوحى من: TikTok (أسود حقيقي + نيون) × Discord (عمق + طبقات)
//            × X/Twitter (بساطة + تباين عالٍ) × إضافة هوية RED الفريدة
// ════════════════════════════════════════════════════════════════════════

// ── ① TRUE OLED BLACKS (مثل TikTok — أسود حقيقي للـ OLED) ──────────────
val Black             = Color(0xFF000000)   // أسود نقي OLED
val BgDeep            = Color(0xFF050507)   // خلفية أعمق (دفء ضئيل)
val BgPrimary         = Color(0xFF0C0C0E)   // الخلفية الرئيسية
val BgSecondary       = Color(0xFF111114)   // خلفية ثانوية
val SurfaceDark       = Color(0xFF17171B)   // سطح البطاقات الداكن (كـ Discord BG Tertiary)
val SurfaceMid        = Color(0xFF1E1E24)   // سطح وسط (كـ Discord BG Secondary)
val SurfaceLight      = Color(0xFF26262E)   // سطح فاتح (كـ Discord BG Primary)
val SurfaceElevated   = Color(0xFF2E2E38)   // سطح مرتفع (للـ Dropdown/Dialogs)
val SurfaceOverlay    = Color(0xFF383844)   // سطح مرتفع جداً

// ── ② RED SIGNATURE BRAND (أحمر جريء فاخر) ─────────────────────────────
//    مستوحى من TikTok #FE2C55 لكن بهوية RED المميزة — أكثر نضجاً
val RedBrand          = Color(0xFFFF1744)   // أحمر العلامة التجارية (Electric Crimson)
val RedPrimary        = Color(0xFFE53935)   // أحمر أساسي للواجهة
val RedDark           = Color(0xFFC62828)   // أحمر غامق للحالات النشطة
val RedDeep           = Color(0xFFB71C1C)   // أحمر عميق للـ containers
val RedGlow           = Color(0xFFFF5252)   // أحمر متوهج للـ accents
val RedSoft           = Color(0xFFEF9A9A)   // أحمر ناعم للـ disabled states
val RedEnd            = Color(0xFFE53935)   // اسم بديل للاستخدام في ConferenceScreens
val RedAccent         = Color(0xFFFF1744)   // أحمر حاد للـ notifications

// ── ③ ELECTRIC ACCENTS (تأثير نيون مستوحى من TikTok) ──────────────────
//    TikTok يستخدم: #FE2C55 (pink-red) + #25F4EE (electric cyan)
//    نحن نستخدم RED + Electric Cyan مكمّلة تماماً
val AqyalCyanGlow     = Color(0xFF00E5FF)   // سيان كهربائي متوهج (TikTok-inspired)
val AqyalCyan         = Color(0xFF18FFFF)   // سيان نيون — للـ read receipts وإضاءة خاصة
val ElectricCyan      = Color(0xFF00BCD4)   // سيان أهدأ للأيقونات الثانوية

// ── ④ PURPLE AURORA (تأثير Aurora متقدم) ───────────────────────────────
val PurplePrimary     = Color(0xFF7C4DFF)   // بنفسجي أساسي
val PurpleGlow        = Color(0xFF651FFF)   // بنفسجي متوهج (أغمق)
val PurpleAccent      = Color(0xFFAA00FF)   // بنفسجي حاد للـ premium badges
val PurpleDeep        = Color(0xFF4A0080)   // بنفسجي عميق جداً للـ containers

// ── ⑤ SUPPORTING PALETTE ────────────────────────────────────────────────
val GoldenAccent      = Color(0xFFFFD600)   // ذهبي للـ VIP/Gold badges
val GoldenDim         = Color(0xFFFF9800)   // ذهبي أهدأ
val GreenAccent       = Color(0xFF00E676)   // أخضر نيون للـ online/active
val GreenCall         = Color(0xFF00C853)   // أخضر لزر قبول المكالمة
val OrangeAccent      = Color(0xFFFF6D00)   // برتقالي للـ warnings/notifications
val BlueAccent        = Color(0xFF448AFF)   // أزرق للمعلومات
val BlueElectric      = Color(0xFF2979FF)   // أزرق كهربائي

// ── ⑥ TEXT (87% opacity rule للـ body text) ─────────────────────────────
val TextPrimary       = Color(0xFFEEEEF0)   // أبيض دافئ (87% opacity على الأسود - WCAG AA)
val TextBright        = Color(0xFFFFFFFF)   // أبيض نقي للـ Headlines
val TextSecondary     = Color(0xFF8A8A96)   // رمادي متوسط (مثل Discord)
val TextTertiary      = Color(0xFF56566A)   // رمادي خافت
val TextDisabled      = Color(0xFF3A3A4A)   // رمادي معطل
val TextOnRed         = Color(0xFFFFFFFF)   // نص على خلفيات حمراء
val TextOnCyan        = Color(0xFF000000)   // نص على خلفيات سيان

// ── ⑦ SEMANTIC COLORS ────────────────────────────────────────────────────
val DividerColor      = Color(0xFF1C1C22)   // فاصل خفي جداً
val OnlineDot         = Color(0xFF23D160)   // أخضر متوهج للـ online (Discord-style)
val TypingColor       = AqyalCyanGlow       // سيان لـ "يكتب..."
val ReadReceipt       = AqyalCyanGlow       // سيان لعلامة القراءة
val PinColor          = GoldenAccent        // ذهبي للمثبت

// ── ⑧ CHAT BUBBLES (متأثر بـ Telegram Premium + iMessage على dark) ──────
//    فقاعة المرسِل: Gradient بنفسجي-أحمر (فاخر ومميز)
//    فقاعة المستقبِل: سطح رمادي عميق (نظيف)
val BubbleMeGradStart = Color(0xFF2D0B61)   // بنفسجي عميق جداً
val BubbleMeGradMid   = Color(0xFF4A1080)   // بنفسجي أخف
val BubbleMeGradEnd   = Color(0xFF8B0000)   // أحمر عميق جداً
val BubbleMe          = Color(0xFF2D0B61)   // لون ثابت بديل
val BubbleOther       = Color(0xFF1E1E24)   // سطح المستقبِل

// ── ⑨ GRADIENT PALETTES ─────────────────────────────────────────────────
val GradientRedPrimary  = listOf(RedDeep, RedBrand)
val GradientRedGlow     = listOf(RedDeep, RedGlow)
val GradientBrand       = listOf(Color(0xFF8B0000), RedBrand, Color(0xFFFF6B35))
val GradientCyanRed     = listOf(AqyalCyanGlow, RedBrand)
val GradientPurpleRed   = listOf(PurplePrimary, RedDeep)
val GradientPurpleCyan  = listOf(PurpleDeep, PurplePrimary, AqyalCyanGlow)
val GradientLive        = listOf(Color(0xFFFF1744), Color(0xFFFF6D00))   // مثل TikTok LIVE badge
val GradientGold        = listOf(Color(0xFFFF9800), Color(0xFFFFD600))
val GradientSurface     = listOf(SurfaceDark, BgPrimary)
val GradientSpace       = listOf(Color(0xFF0D0020), Color(0xFF0C0C0E))   // للـ Spaces/Conference
val GradientNight       = listOf(Color(0xFF0C0C0E), Color(0xFF050507))   // للخلفيات الداكنة

// ════════════════════════════════════════════════════════════════════════
//  TYPOGRAPHY — خطوط احترافية (Google Sans / Roboto style)
// ════════════════════════════════════════════════════════════════════════

val RedTypography = Typography(
    // Display — للأرقام الكبيرة والعناوين الرئيسية
    displayLarge  = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Black,    fontSize = 57.sp, letterSpacing = (-0.25).sp, lineHeight = 64.sp),
    displayMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.ExtraBold,fontSize = 45.sp, letterSpacing = (-0.15).sp, lineHeight = 52.sp),
    displaySmall  = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,     fontSize = 36.sp, letterSpacing = (-0.10).sp, lineHeight = 44.sp),
    // Headlines
    headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,     fontSize = 32.sp, letterSpacing = (-0.5).sp,  lineHeight = 40.sp),
    headlineMedium= TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, letterSpacing = (-0.3).sp,  lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    // Titles
    titleLarge    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,     fontSize = 22.sp, letterSpacing = 0.sp,       lineHeight = 28.sp),
    titleMedium   = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, letterSpacing = 0.15.sp,    lineHeight = 24.sp),
    titleSmall    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,   fontSize = 14.sp, letterSpacing = 0.1.sp,     lineHeight = 20.sp),
    // Body — 87% rule applied in actual composables
    bodyLarge     = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,   fontSize = 16.sp, letterSpacing = 0.5.sp,     lineHeight = 24.sp),
    bodyMedium    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,   fontSize = 14.sp, letterSpacing = 0.25.sp,    lineHeight = 20.sp),
    bodySmall     = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,   fontSize = 12.sp, letterSpacing = 0.4.sp,     lineHeight = 16.sp),
    // Labels
    labelLarge    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,   fontSize = 14.sp, letterSpacing = 0.1.sp),
    labelMedium   = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,   fontSize = 12.sp, letterSpacing = 0.5.sp),
    labelSmall    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,   fontSize = 11.sp, letterSpacing = 0.5.sp),
)

// ════════════════════════════════════════════════════════════════════════
//  MATERIAL 3 COLOR SCHEME (OLED Dark)
//  Contrast ratios: TextPrimary/BgPrimary ≈ 15:1 (exceeds WCAG AAA)
// ════════════════════════════════════════════════════════════════════════

private val DarkColorScheme = darkColorScheme(
    primary              = RedBrand,
    onPrimary            = TextOnRed,
    primaryContainer     = RedDeep.copy(alpha = 0.85f),
    onPrimaryContainer   = RedGlow,
    secondary            = AqyalCyanGlow,
    onSecondary          = TextOnCyan,
    secondaryContainer   = Color(0xFF003040),
    onSecondaryContainer = AqyalCyan,
    tertiary             = PurplePrimary,
    onTertiary           = TextOnRed,
    tertiaryContainer    = PurpleDeep,
    onTertiaryContainer  = PurpleAccent,
    error                = Color(0xFFFF6B6B),
    onError              = Color(0xFF3B0000),
    errorContainer       = Color(0xFF5C1212),
    onErrorContainer     = Color(0xFFFFB4AB),
    background           = BgPrimary,
    onBackground         = TextPrimary,
    surface              = SurfaceDark,
    onSurface            = TextPrimary,
    surfaceVariant       = SurfaceMid,
    onSurfaceVariant     = TextSecondary,
    outline              = DividerColor,
    outlineVariant       = SurfaceLight,
    scrim                = Color(0xCC000000),
    inverseSurface       = TextPrimary,
    inverseOnSurface     = BgPrimary,
    inversePrimary       = RedDeep,
    surfaceTint          = RedBrand.copy(alpha = 0.08f),
)

// ════════════════════════════════════════════════════════════════════════
//  SHAPES — زوايا مستديرة حديثة 2026
// ════════════════════════════════════════════════════════════════════════

val RedShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small      = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium     = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large      = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
)

// ════════════════════════════════════════════════════════════════════════
//  MAIN THEME
// ════════════════════════════════════════════════════════════════════════

@Composable
fun RedUltimateTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography  = RedTypography,
        shapes      = RedShapes,
        content     = content
    )
}
