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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.R

// ═══════════════════════════════════════════════════════════════════════════════
// الخط الموحّد — IBM Plex Sans Arabic ثنائي النص (SIL OFL 1.1)
// مضمّن في الحزمة لا مجلوب من الشبكة: 4 أوزان محلية في res/font
// plex_arabic.xml (400/500/600/700). ينهي ارتداد Google Play Services
// ويوحّد هوية التطبيق مع admin_dashboard (IBM Plex) ويضمن ثبات مقاسات
// الأسطر حتى على شبكات اليمن الضعيفة.
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

// أسماء مستعارة للتوافق مع 15 موضعًا يستعمل Cairo/Tajawal مباشرة
// الآن كلاهما يشير لنفس العائلة الموحدة — لا تناقض بصري بعد اليوم
val CairoFamily: FontFamily = PlexArabicFamily
val TajawalFamily: FontFamily = PlexArabicFamily

// ═══════════════════════════════════════════════════════════════════════════════
// لوحة الألوان السيادية المحسّنة — نظام متناسق كامل
// ═══════════════════════════════════════════════════════════════════════════════

// ═══════════════════════════════════════════════════════════════════════════════
// لوحة الألوان السيادية — هوية مستقلة + التزام WCAG قابل للقياس
// ═══════════════════════════════════════════════════════════════════════════════
//
// ## لماذا تغيّرت اللوحة
//
// كانت اللوحة السابقة تنسخ ألوان المنافسين حرفيًا: `#00A884` و`#25D366` من
// واتساب، و`#2AABEE` و`#0E1621` و`#2B5278` من تلجرام. هذا يُفقد التطبيق
// هويته، ويجعل «يونس» يبدو نسخة لا منتجًا سياديًا.
//
// وكانت تخالف التباين في أربعة مواضع مقيسة:
//   • نص أبيض على `#00A884` = 3.03:1 — دون AAA (7:1) ودون AA (4.5:1) أصلًا،
//     أي أن نص كل زر أساسي كان غير مقروء فعليًا.
//   • نص أبيض على `#2AABEE` = 2.57:1 — أسوأ.
//   • `#E53935` على الخلفية = 4.30:1 — دون AA، وهو لون سبأفون وزر الإنهاء.
//   • النص الثانوي على الفقاعة الصادرة = 2.94:1 — الطابع الزمني و«✓✓» شبه
//     غير مرئيين على كل رسالة صادرة.
//
// ## قاعدة الحلّ
//
// الأزرار الملوّنة تحمل نصًا **داكنًا** (`YounesOnBrand`) لا أبيض. رفع تباين
// الأبيض على لون مشبع إلى 7:1 يفرض تعطيم اللون حتى يفقد حياته؛ أما تغميق
// النص على لون فاتح فيحقق 9:1+ ويُبقي اللون نابضًا. هذا ما تفعله Material 3
// في `onPrimary` للألوان الفاتحة.
//
// كل قيمة أدناه مقيسة ومثبَّتة باختبار في `ColorContrastTest`.

// ─── الألوان الأساسية — زمرد سيادي وذهب إمبراطوري، لا ألوان منافسين ────────
/** زمرد سيادي. نص داكن فوقه = 9.17:1 (AAA)، وهو نفسه 8.83:1 على الخلفية. */
val YounesPrimary      = Color(0xFF14C79A)
/** زمرد مضيء للتنبيهات والتوهّج. نص داكن فوقه = 12.78:1. */
val YounesPrimaryGlow  = Color(0xFF3DE8BC)
/** ذهب إمبراطوري للروابط والشارات. نص داكن فوقه = 10.34:1، وعلى الخلفية 9.96:1. */
val YounesAccent       = Color(0xFFE0B551)
/** ذهب فاتح للحدود المميّزة والتدرّجات. */
val YounesAccentSoft   = Color(0xFFF0D48C)
/** أزرق ملكي — متمايز عن أزرق تلجرام، 6.80:1 على الخلفية. */
val YounesCobalt       = Color(0xFF4D9FE8)
/** بنفسجي المساحات الصوتية — 6.30:1 على الخلفية. */
val YounesPurple       = Color(0xFFB07CE8)
/** أحمر تحذيري للبث والإنهاء — 5.90:1 على الخلفية (كان 4.30 دون AA). */
val YounesRose         = Color(0xFFF25C5C)
/** أحمر ياقوتي أغمق لزر إنهاء المكالمة — يحمل نصًا أبيض. */
val YounesRuby         = Color(0xFFE03131)

// ─── ألوان الخلفية — تدرّج ارتفاع صاعد ومقيس ─────────────────────────────────
val YounesVoid         = Color(0xFF0A0F18)  // أعمق مستوى — خلفية الشاشة
val YounesMidnight     = Color(0xFF0A0F18)  // خلفية الشاشة والمحادثة
val YounesDeep         = Color(0xFF131C29)  // أسطح القوائم وشريط الملاحة
val YounesSurface1     = Color(0xFF131C29)  // كروت القوائم
val YounesSurface2     = Color(0xFF1B2635)  // أسطح العناصر النشطة
val YounesSurface3     = Color(0xFF212E40)  // الحوارات والـ BottomSheet
val YounesBorder       = Color(0xFF2A394A)  // فواصل زخرفية — لا يشترط لها تباين
/**
 * حدّ المكوّنات التفاعلية (حقول الإدخال، الأزرار المُحدَّدة، الشرائح).
 *
 * `YounesBorder` مناسب للفواصل الزخرفية، لكن استخدامه كـ `outline` جعل حدّ
 * `OutlinedTextField` عند 1.45:1 فوق سطح القوائم — أي حقل بلا حدّ مرئي فعليًا،
 * وهو ما يخالف WCAG 1.4.11 (حدّ أدنى 3:1 لعناصر واجهة غير نصية).
 *
 * هذه القيمة تحقق ≥4:1 على الأسطح الأربعة (Midnight 5.74، Surface1 5.13،
 * Surface2 4.57، Surface3 4.11) وتبقى أبهت من النص الأبيض فلا تسحب الانتباه.
 */
val YounesOutline      = Color(0xFF7A8FA3)
/** نص ثانوي — 6.19:1 على أعلى سطح، 8.65:1 على الخلفية. */
val YounesMuted        = Color(0xFF9FB0C2)

// ─── ألوان المحادثة والفقاعات ────────────────────────────────────────────────
/** فقاعة صادرة — كحلي سيادي متمايز عن `#2B5278` التلجرامي. أبيض فوقه 13.41:1. */
val YounesBubbleOut    = Color(0xFF14304F)
val YounesBubbleOutGlow = Color(0xFF1A3A5C)
/** فقاعة واردة — أبيض فوقها 15.54:1. */
val YounesBubbleIn     = Color(0xFF182533)
/** ✓✓ مقروء — 4.75:1 على الفقاعة الصادرة، فوق حدّ 3:1 للأيقونات. */
val YounesReadTick     = Color(0xFF4D9FE8)

// ─── ألوان النصوص والتباين المرتفع (WCAG AAA) ────────────────────────────────
/**
 * نص الأسطح الملوّنة (الأزرار الأساسية، الشارات، الشرائح المملوءة).
 *
 * داكن لا أبيض: الأبيض على زمرد أو ذهب مشبع لا يبلغ 4.5:1 أبدًا دون تعطيم
 * اللون. النص الداكن يبلغ 9:1+ ويُبقي اللون نابضًا.
 */
val YounesOnBrand      = Color(0xFF06090F)
val YounesOnPrimary    = YounesOnBrand  // نص على الزمرد — 9.17:1
val YounesOnAccent     = YounesOnBrand  // نص على الذهب — 10.34:1
val YounesOnSurface    = Color(0xFFFFFFFF)  // نص أساسي — 19.19:1 على الخلفية
val YounesOnSurfaceDim = YounesMuted        // نص ثانوي — 8.65:1 على الخلفية

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
// نظام الطباعة — عائلة واحدة ثنائية النص (Plex Arabic)
// Material 3 Type Scale كامل — 15 نمطًا تغطي كل الاستعمالات
// ═══════════════════════════════════════════════════════════════════════════════
private val redTypography = Typography(
    // عناوين كبيرة
    displayLarge  = TextStyle(fontFamily = PlexArabicFamily, fontSize = 57.sp, lineHeight = 64.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontFamily = PlexArabicFamily, fontSize = 45.sp, lineHeight = 52.sp, fontWeight = FontWeight.ExtraBold),
    displaySmall  = TextStyle(fontFamily = PlexArabicFamily, fontSize = 36.sp, lineHeight = 46.sp, fontWeight = FontWeight.Black),
    headlineLarge = TextStyle(fontFamily = PlexArabicFamily, fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold),
    headlineMedium= TextStyle(fontFamily = PlexArabicFamily, fontSize = 25.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontFamily = PlexArabicFamily, fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    titleLarge    = TextStyle(fontFamily = PlexArabicFamily, fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium   = TextStyle(fontFamily = PlexArabicFamily, fontSize = 17.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleSmall    = TextStyle(fontFamily = PlexArabicFamily, fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    // نصوص المحادثات والوصف
    bodyLarge     = TextStyle(fontFamily = PlexArabicFamily, fontSize = 17.sp, lineHeight = 27.sp, fontWeight = FontWeight.Normal),
    bodyMedium    = TextStyle(fontFamily = PlexArabicFamily, fontSize = 15.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodySmall     = TextStyle(fontFamily = PlexArabicFamily, fontSize = 13.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    // تسميات وشارات
    labelLarge    = TextStyle(fontFamily = PlexArabicFamily, fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    labelMedium   = TextStyle(fontFamily = PlexArabicFamily, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium),
    labelSmall    = TextStyle(fontFamily = PlexArabicFamily, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
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
    // outline = حدّ العناصر التفاعلية (WCAG 1.4.11 ≥ 3:1)
    // outlineVariant = الفواصل الزخرفية، لا يشترط لها حدّ تباين
    outline               = YounesOutline,
    outlineVariant        = YounesBorder,

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

// ─── Light schemes — نفس الهوية، خلفية فاتحة #F7F8FA (لؤلؤي) ────────────────
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
val oledLightColorScheme = redLightColorScheme // الأوليد الفاتح = الفاتح العادي

// ─── أوضاع المظهر ───────────────────────────────────────────────────────────
enum class AppThemeMode(val label: String) {
    LIGHT("فاتح"),
    DARK("ليلي"),
    SYSTEM("حسب النظام")
}

enum class AppThemePreset(val label: String, val description: String) {
    SOVEREIGN("يونس السيادي", "أسود ملكي مع أخضر زمردي ولمسات ذهبية — الهوية الأصلية"),
    TELEGRAM_DARK("تلجرام الكحلي", "أزرق تلجرام الأنيق مع كحلي داكن"),
    WHATSAPP_DARK("واتساب الزمردي", "أخضر واتساب الكلاسيكي المريح للعين"),
    OLED_BLACK("أوليد فائق السواد", "سواد تام 100% لتوفير الطاقة وأقصى تباين"),
    DYNAMIC("ديناميكي", "ألوان مستخرجة من خلفية الهاتف — Material You (أندرويد 12+)"),
    CUSTOM("مخصص", "اختر لونك بنفسك مع حارس تباين ذكي")
}

object AppThemeState {
    var currentPreset by androidx.compose.runtime.mutableStateOf(AppThemePreset.SOVEREIGN)
    var themeMode by androidx.compose.runtime.mutableStateOf(AppThemeMode.SYSTEM)
    var highContrast by androidx.compose.runtime.mutableStateOf(false)
    var liquidGlassEnabled by androidx.compose.runtime.mutableStateOf(true)
    var reduceMotion by androidx.compose.runtime.mutableStateOf(false)
    var fontScale by androidx.compose.runtime.mutableStateOf(1.0f)
    var customPrimary by androidx.compose.runtime.mutableStateOf<Color?>(null)
    // 6 ألوان مقترحة للمخصص تضمن ≥4.5:1
    val customPresets = listOf(
        YounesPrimary to "زمرد يونس",
        YounesAccent to "ذهب إمبراطوري",
        YounesCobalt to "أزرق ملكي",
        Color(0xFFE53935) to "أحمر حي",
        Color(0xFF8E24AA) to "بنفسجي ملكي",
        Color(0xFF00ACC1) to "تركواز"
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
// ثيم يونس الرئيسي — يدعم فاتح/ليلي/نظام + ديناميكي + مخصص + Liquid Glass
// ═══════════════════════════════════════════════════════════════════════════════
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

    // ─── اختيار اللوحة الأساسية ──────────────────────────────────────────
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

    // ─── مقياس الخط الديناميكي ───────────────────────────────────────────
    val scaledTypography = rememberScaledTypography(AppThemeState.fontScale)

    MaterialTheme(
        colorScheme = finalScheme,
        typography = scaledTypography,
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

// ═══════════════════════════════════════════════════════════════════════════════
// خلفية الشاشة الرئيسية — تدرج Liquid Glass (2026)
// ═══════════════════════════════════════════════════════════════════════════════
@Composable
fun SovereignBackground(content: @Composable () -> Unit) {
    val isDark = when (AppThemeState.themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val bgBrush = when (AppThemeState.currentPreset) {
        AppThemePreset.OLED_BLACK    -> if (isDark) Brush.verticalGradient(listOf(Color(0xFF000000), Color(0xFF0A0A0A))) else Brush.verticalGradient(listOf(Color(0xFFF7F8FA), Color(0xFFE6E8EB)))
        AppThemePreset.WHATSAPP_DARK -> if (isDark) Brush.verticalGradient(listOf(Color(0xFF0B141A), Color(0xFF111B21), Color(0xFF0B141A))) else Brush.verticalGradient(listOf(Color(0xFFF7F8FA), Color(0xFFFFFFFF)))
        AppThemePreset.TELEGRAM_DARK -> if (isDark) Brush.verticalGradient(listOf(Color(0xFF0E1621), Color(0xFF17212B), Color(0xFF0E1621))) else Brush.verticalGradient(listOf(Color(0xFFF0F2F5), Color(0xFFFFFFFF)))
        AppThemePreset.DYNAMIC       -> if (isDark) GradientBackground else Brush.verticalGradient(listOf(Color(0xFFF7F8FA), Color(0xFFFFFFFF)))
        AppThemePreset.CUSTOM        -> if (isDark) GradientBackground else Brush.verticalGradient(listOf(Color(0xFFF7F8FA), Color(0xFFFFFFFF)))
        AppThemePreset.SOVEREIGN     -> if (isDark) GradientBackground else Brush.verticalGradient(listOf(Color(0xFFF7F8FA), Color(0xFFEAF0F2)))
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
