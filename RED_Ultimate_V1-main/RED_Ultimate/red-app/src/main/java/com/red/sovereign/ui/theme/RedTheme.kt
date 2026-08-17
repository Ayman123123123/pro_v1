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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
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

// ─── الألوان الأساسية — أسلوب منظم وأنيق مثل تلجرام وواتساب ───────────────
val YounesPrimary      = Color(0xFF00A884)  // أخضر زمردي هادئ نقي (WhatsApp / Telegram Clean Accent)
val YounesPrimaryGlow  = Color(0xFF25D366)  // أخضر فاتح للتنبيهات
val YounesAccent       = Color(0xFF2AABEE)  // أزرق تلجرام الأنيق للروابط والشارات
val YounesAccentSoft   = Color(0xFF64B5F6)  // أزرق أفتح للحدود
val YounesCobalt       = Color(0xFF2AABEE)  // أزرق نقي
val YounesPurple       = Color(0xFF388E3C)  // أخضر إضافي
val YounesRose         = Color(0xFFE53935)  // أحمر تحذيري خفيف للإنهاء والبث
val YounesRuby         = Color(0xFFE03131)  // أحمر ياقوتي لأزرار إنهاء المكالمات

// ─── ألوان الخلفية — داكنة فائقة التباين والوضوح (Telegram Dark Theme) ──────
val YounesVoid         = Color(0xFF0E1621)  // داكن تلجرام الرئيسي — مريح وبلا تشويش
val YounesMidnight     = Color(0xFF0E1621)  // خلفية الشاشة والشات
val YounesDeep         = Color(0xFF17212B)  // أسطح القوائم وشريط الملاحة
val YounesSurface1     = Color(0xFF17212B)  // كروت القوائم
val YounesSurface2     = Color(0xFF202B36)  // أسطح العناصر النشطة
val YounesSurface3     = Color(0xFF242F3D)  // الحوارات والـ BottomSheet
val YounesBorder       = Color(0xFF2A394A)  // فواصل حدودية واضحة
val YounesMuted        = Color(0xFF8E9DAE)  // نص ثانوي عالي التباين وسهل القراءة

// ─── ألوان المحادثة والفقاعات ────────────────────────────────────────────────
val YounesBubbleOut    = Color(0xFF2B5278)  // فقاعة صادرة — أزرق تلجرام داكن مريح
val YounesBubbleOutGlow = Color(0xFF2B5278)
val YounesBubbleIn     = Color(0xFF182533)  // فقاعة واردة — داكنة عالية التباين
val YounesReadTick     = Color(0xFF2AABEE)  // ✓✓ مقروء — أزرق

// ─── ألوان النصوص والتباين المرتفع (WCAG AAA Standard) ───────────────────────
val YounesOnPrimary    = Color(0xFFFFFFFF)  // نص أبيض نقي على الأزرار
val YounesOnAccent     = Color(0xFFFFFFFF)  // نص أبيض
val YounesOnSurface    = Color(0xFFFFFFFF)  // نص أبيض نقي للمحاذاة والوضوح
val YounesOnSurfaceDim = Color(0xFF8E9DAE)  // نص ثانوي رمادي هادئ وسهل القراءة

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

enum class AppThemePreset(val label: String, val description: String) {
    SOVEREIGN("يونس السيادي", "أسود ملكي مع أخضر زمردي ولمسات ذهبية"),
    TELEGRAM_DARK("تلجرام الكحلي", "أزرق تلجرام الأنيق مع كحلي داكن"),
    WHATSAPP_DARK("واتساب الزمردي", "أخضر واتساب الكلاسيكي المريح للعين"),
    OLED_BLACK("أوليد فائق السواد", "سواد تام 100% لتوفير الطاقة وأقصى تباين")
}

object AppThemeState {
    var currentPreset by androidx.compose.runtime.mutableStateOf(AppThemePreset.WHATSAPP_DARK)
}

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

// ═══════════════════════════════════════════════════════════════════════════════
// ثيم يونس الرئيسي
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun YounesTheme(
    preset: AppThemePreset = AppThemeState.currentPreset,
    highContrast: Boolean = false,
    content: @Composable () -> Unit
) {
    val baseScheme = when (preset) {
        AppThemePreset.SOVEREIGN -> redColorScheme
        AppThemePreset.TELEGRAM_DARK -> telegramColorScheme
        AppThemePreset.WHATSAPP_DARK -> whatsAppColorScheme
        AppThemePreset.OLED_BLACK -> oledColorScheme
    }
    val finalScheme = if (highContrast) baseScheme.copy(
        onBackground = Color.White,
        onSurface = Color.White,
        outline = baseScheme.primary
    ) else baseScheme

    MaterialTheme(
        colorScheme = finalScheme,
        typography = redTypography,
        shapes = redShapes,
        content = content
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// خلفية الشاشة الرئيسية — تدرج داكن عميق
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun SovereignBackground(content: @Composable () -> Unit) {
    val bgBrush = when (AppThemeState.currentPreset) {
        AppThemePreset.OLED_BLACK    -> Brush.verticalGradient(listOf(Color(0xFF000000), Color(0xFF0A0A0A)))
        AppThemePreset.WHATSAPP_DARK -> Brush.verticalGradient(listOf(Color(0xFF0B141A), Color(0xFF111B21), Color(0xFF0B141A)))
        AppThemePreset.TELEGRAM_DARK -> Brush.verticalGradient(listOf(Color(0xFF0E1621), Color(0xFF17212B), Color(0xFF0E1621)))
        AppThemePreset.SOVEREIGN     -> GradientBackground
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(bgBrush)
    ) { content() }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ثوابت الأنيميشن المشتركة
// ═══════════════════════════════════════════════════════════════════════════════
val SpringSnappy  = spring<Float>(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioMediumBouncy)
val SpringSmooth  = spring<Float>(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioNoBouncy)
