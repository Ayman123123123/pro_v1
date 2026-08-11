package com.red.sovereign.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.R

// ═══════════════════════════════════════════════════════════════════════════════
// Google Fonts Provider — Cairo (العناوين) + Tajawal (نصوص المحادثات)
// ═══════════════════════════════════════════════════════════════════════════════
private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private val CairoFont   = GoogleFont("Cairo")
private val TajawalFont = GoogleFont("Tajawal")

val CairoFamily = FontFamily(
    Font(googleFont = CairoFont, fontProvider = provider, weight = FontWeight.Light),
    Font(googleFont = CairoFont, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = CairoFont, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = CairoFont, fontProvider = provider, weight = FontWeight.SemiBold),
    Font(googleFont = CairoFont, fontProvider = provider, weight = FontWeight.Bold),
    Font(googleFont = CairoFont, fontProvider = provider, weight = FontWeight.ExtraBold),
    Font(googleFont = CairoFont, fontProvider = provider, weight = FontWeight.Black),
)

val TajawalFamily = FontFamily(
    Font(googleFont = TajawalFont, fontProvider = provider, weight = FontWeight.Light),
    Font(googleFont = TajawalFont, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = TajawalFont, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = TajawalFont, fontProvider = provider, weight = FontWeight.Bold),
)

// ═══════════════════════════════════════════════════════════════════════════════
// لوحة الألوان السيادية المحسّنة — نظام متناسق كامل
// ═══════════════════════════════════════════════════════════════════════════════

// ─── الألوان الأساسية ─────────────────────────────────────────────────────────
val YounesPrimary      = Color(0xFF00C98C)  // أخضر سيادي — زر الإرسال والحضور
val YounesPrimaryGlow  = Color(0xFF6DFFD3)  // أخضر متوهج — التأثيرات
val YounesAccent       = Color(0xFFF5C842)  // ذهبي يوناني — الاسم والنجوم
val YounesAccentSoft   = Color(0xFFFFE27A)  // ذهبي فاتح — تأثيرات ثانوية
val YounesCobalt       = Color(0xFF38D4F0)  // سماوي — التواريخ والـ ID
val YounesPurple       = Color(0xFFA78BFA)  // بنفسجي — المساحات والاجتماعات
val YounesRose         = Color(0xFFF43F5E)  // أحمر وردي — التنبيهات والخطر

// ─── ألوان الخلفية — تدرج داكن عميق ─────────────────────────────────────────
val YounesVoid         = Color(0xFF030710)  // أسود فضائي — أعمق خلفية
val YounesMidnight     = Color(0xFF080F1C)  // منتصف الليل — الشاشة الرئيسية
val YounesDeep         = Color(0xFF0D1829)  // عميق — NavigationBar
val YounesSurface1     = Color(0xFF101E2E)  // السطح الأول — قوائم الدردشات
val YounesSurface2     = Color(0xFF162334)  // السطح الثاني — كروت المنشورات
val YounesSurface3     = Color(0xFF1C2B3F)  // السطح الثالث — BottomSheet والديالوج
val YounesBorder       = Color(0xFF253548)  // الحدود — فواصل خفية
val YounesMuted        = Color(0xFF8FA7B8)  // نص ثانوي — التوقيت والوصف

// ─── ألوان المحادثة ──────────────────────────────────────────────────────────
val YounesBubbleOut    = Color(0xFF006B4F)  // فقاعة صادرة — أخضر داكن غني
val YounesBubbleOutGlow = Color(0xFF007D5C) // فقاعة صادرة — أفتح قليلاً
val YounesBubbleIn     = Color(0xFF152234)  // فقاعة واردة — أزرق داكن
val YounesReadTick     = Color(0xFF00C98C)  // ✓✓ مقروء — أخضر

// ─── عكوس الألوان الديناميكية ────────────────────────────────────────────────
val YounesOnPrimary    = Color(0xFF003823)  // نص على الأخضر
val YounesOnAccent     = Color(0xFF2A1F00)  // نص على الذهبي
val YounesOnSurface    = Color(0xFFE8F2F8)  // نص رئيسي
val YounesOnSurfaceDim = Color(0xFFA8BBC7)  // نص ثانوي

// ─── Migration aliases — للحفاظ على التوافق مع الكود القديم ──────────────────
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
val AqyalGoldLight      = YounesAccentSoft
val AqyalDarkObsidian   = YounesVoid
val AqyalRoyalBlue      = YounesMidnight
val AqyalSurfaceNavy    = YounesSurface1
val AqyalSurfaceRaised  = YounesSurface2
val AqyalCyanGlow       = YounesCobalt
val RedCrimson          = YounesRose
val RedCrimsonGlow      = YounesRose
val RedMutedText        = YounesMuted

// ═══════════════════════════════════════════════════════════════════════════════
// تدرجات لونية احترافية
// ═══════════════════════════════════════════════════════════════════════════════
val GradientBackground  = Brush.verticalGradient(
    listOf(YounesVoid, YounesMidnight) // Official: solid deep, no rainbow — professional, not clownish
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
    listOf(YounesPrimary.copy(alpha = 0.15f), YounesVoid)
)
val GradientGold        = Brush.linearGradient(
    listOf(YounesAccent, YounesAccentSoft, YounesAccent)
)
val GradientNavBar      = Brush.verticalGradient(
    listOf(YounesMidnight.copy(alpha = 0f), YounesDeep)
)

// ═══════════════════════════════════════════════════════════════════════════════
// نظام الطباعة — Cairo للعناوين، Tajawal للمحادثات
// ═══════════════════════════════════════════════════════════════════════════════
private val redTypography = Typography(
    // Cairo للعناوين الكبيرة والشاشات
    displaySmall  = TextStyle(fontFamily = CairoFamily, fontSize = 36.sp, lineHeight = 46.sp, fontWeight = FontWeight.Black),
    headlineLarge = TextStyle(fontFamily = CairoFamily, fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold),
    headlineMedium= TextStyle(fontFamily = CairoFamily, fontSize = 25.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontFamily = CairoFamily, fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    titleLarge    = TextStyle(fontFamily = CairoFamily, fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium   = TextStyle(fontFamily = CairoFamily, fontSize = 17.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleSmall    = TextStyle(fontFamily = CairoFamily, fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    // Tajawal لنصوص المحادثات والوصف
    bodyLarge     = TextStyle(fontFamily = TajawalFamily, fontSize = 17.sp, lineHeight = 27.sp, fontWeight = FontWeight.Normal),
    bodyMedium    = TextStyle(fontFamily = TajawalFamily, fontSize = 15.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodySmall     = TextStyle(fontFamily = TajawalFamily, fontSize = 13.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    // Cairo للتسميات
    labelLarge    = TextStyle(fontFamily = CairoFamily, fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    labelMedium   = TextStyle(fontFamily = CairoFamily, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium),
    labelSmall    = TextStyle(fontFamily = CairoFamily, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

// ═══════════════════════════════════════════════════════════════════════════════
// نظام الأشكال — حواف ناعمة وحديثة
// ═══════════════════════════════════════════════════════════════════════════════
private val redShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small      = RoundedCornerShape(14.dp),
    medium     = RoundedCornerShape(20.dp),
    large      = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

// ═══════════════════════════════════════════════════════════════════════════════
// نظام الألوان Material3 — داكن كامل
// ═══════════════════════════════════════════════════════════════════════════════
private val redColorScheme = darkColorScheme(
    // الأساسي — الأخضر السيادي
    primary               = YounesPrimary,
    onPrimary             = YounesOnPrimary,
    primaryContainer      = Color(0xFF004D3A),
    onPrimaryContainer    = YounesPrimaryGlow,

    // الثانوي — السماوي
    secondary             = YounesCobalt,
    onSecondary           = Color(0xFF001F2A),
    secondaryContainer    = Color(0xFF003A48),
    onSecondaryContainer  = Color(0xFFB2EDFA),

    // الثالثي — الذهبي
    tertiary              = YounesAccent,
    onTertiary            = YounesOnAccent,
    tertiaryContainer     = Color(0xFF3D2E00),
    onTertiaryContainer   = YounesAccentSoft,

    // الخلفية والأسطح
    background            = YounesMidnight,
    onBackground          = YounesOnSurface,
    surface               = YounesSurface1,
    onSurface             = YounesOnSurface,
    surfaceVariant        = YounesSurface2,
    onSurfaceVariant      = YounesMuted,
    surfaceTint           = YounesPrimary,

    // الحاويات السطحية
    surfaceContainer          = YounesSurface1,
    surfaceContainerLow       = YounesMidnight,
    surfaceContainerHigh      = YounesSurface2,
    surfaceContainerHighest   = YounesSurface3,

    // الحدود
    outline               = YounesBorder,
    outlineVariant        = YounesBorder.copy(alpha = 0.5f),

    // الخطأ والتحذير
    error                 = YounesRose,
    onError               = Color(0xFF3A0010),
    errorContainer        = Color(0xFF5C001A),
    onErrorContainer      = Color(0xFFFFB3B8),

    // إضافي
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

// ═══════════════════════════════════════════════════════════════════════════════
// ثيم يونس الرئيسي
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun YounesTheme(
    highContrast: Boolean = false,
    content: @Composable () -> Unit
) = MaterialTheme(
    colorScheme = if (highContrast) redHighContrastColorScheme else redColorScheme,
    typography  = redTypography,
    shapes      = redShapes,
    content     = content
)

// ═══════════════════════════════════════════════════════════════════════════════
// خلفية الشاشة الرئيسية — تدرج داكن عميق
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun SovereignBackground(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(GradientBackground)
    ) { content() }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ثوابت الأنيميشن المشتركة
// ═══════════════════════════════════════════════════════════════════════════════
val SpringSnappy  = spring<Float>(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy)
val SpringSmooth  = spring<Float>(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioNoBouncy)
