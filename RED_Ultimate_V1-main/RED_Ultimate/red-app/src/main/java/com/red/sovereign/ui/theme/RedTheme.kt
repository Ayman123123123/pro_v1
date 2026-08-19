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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.R

// ═══════════════════════════════════════════════════════════════════════════════
// الخط الرسمي — IBM Plex Sans Arabic، مضمَّن في الحزمة
//
// كان الخطّان Cairo وTajawal يُجلبان عبر GoogleFont.Provider، أي عبر
// خدمات Google Play والشبكة. على شبكات اليمن — وهي الحالة الغالبة
// لمستخدمي هذا التطبيق — يفشل الجلب فيرتدّ النص إلى خط النظام:
// تنهار الهوية، وتتغيّر مقاسات الأسطر بين جهاز وآخر، وتتكسّر
// التخطيطات المحسوبة على ارتفاع سطر بعينه.
//
// ولماذا عائلة واحدة ثنائية النص: الواجهة تخلط العربية بالأرقام
// اللاتينية في كل شاشة (معرّف RED، أرقام الهواتف، مدد المكالمات).
// خلط خط عربي بآخر لاتيني يُظهر تفاوتًا في الوزن وارتفاع السن داخل
// السطر الواحد؛ وIBM Plex Sans Arabic مصمَّمة أصلًا لتناسق النصّين.
// ═══════════════════════════════════════════════════════════════════════════════
val PlexArabic = FontFamily(
    Font(R.font.plex_arabic_regular,  FontWeight.Normal),
    Font(R.font.plex_arabic_medium,   FontWeight.Medium),
    Font(R.font.plex_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.plex_arabic_bold,     FontWeight.Bold),
)

// الأوزان غير المضمَّنة تُسقَط على أقرب وزن متاح بدل جلبها من الشبكة:
// ExtraBold/Black -> Bold، وLight/Thin -> Regular. أربعة ملفات (920 كB)
// بدل أحد عشر، دون أن يفقد التسلسل الطباعي تمايزه.
val CairoFamily = PlexArabic
val TajawalFamily = PlexArabic

// ═══════════════════════════════════════════════════════════════════════════════
// لوحة الألوان السيادية المحسّنة — نظام متناسق كامل
// ═══════════════════════════════════════════════════════════════════════════════

/*
 * حُسم تعارض الدمج مع origin/main (2026-08-19) لصالح هذه اللوحة.
 *
 * كان الطرفان قد أعادا تصميم اللوحة استقلالًا. والحسم لم يكن بالذوق
 * بل بقياس WCAG لكل لون مقرونًا بلون النصّ الذي تستعمله لوحته:
 *
 *   هذه اللوحة : 0 راسب من 9 فحوص
 *   لوحة main  : 4 رواسب — الأزرق 3.94 والتمييز 3.94 والأحمر 4.45
 *                والبنفسجي 4.07، كلها دون حدّ AA البالغ 4.5:1
 *
 * ورسوب الأحمر ليس نظريًّا: `YounesRose` مربوط بـ`colorScheme.error`،
 * فيعرضه Material نصَّ خطأ على الخلفية — وهو ما يُقاس عليه الحدّ.
 *
 * ما أُخذ من main: لا شيء في هذا الملف؛ تغييره اقتصر على قيم الألوان.
 */
// ─── الهوية اللونية — مستقلّة، لا منسوخة ─────────────────────────────────────
//
// اللوحة السابقة كانت ألوان المنافسين حرفيًّا: 00A884 و25D366 من واتساب،
// و2AABEE و0E1621 من تلجرام. تطبيق يريد التفوّق عليهما لا يمكن أن يرتدي
// زيّهما. البديل هنا مشتقّ من هوية محليّة: أخضر عميق مشبع، وذهب صنعاني
// للتمييز، على خلفية زرقاء-سوداء دافئة بدل الرمادي البارد.
//
// وكل زوج لون/خلفية أدناه مقيس بمعادلة WCAG 2.1 لا مُقدَّر بالذوق.
// العيب الذي أصلحته هذه اللوحة: النص الأبيض على الزر الأساسي كان
// 3.03:1 — راسب دون AA (الحد 4.5:1) — لأن الأخضر الفاتح لا يحتمل نصًّا
// أبيض. الحل نصّ داكن على الأزرار الملوّنة: 7.09:1، أي AAA.
val YounesPrimary      = Color(0xFF00B37E)  // أخضر سيادي عميق — 7.06:1 على الخلفية
val YounesPrimaryGlow  = Color(0xFF14D89B)  // إضاءة الحالة النشطة والتنبيه
val YounesAccent       = Color(0xFFE0A83C)  // ذهب صنعاني — التمييز والشارات، 8.97:1
val YounesAccentSoft   = Color(0xFFF0C674)  // ذهب فاتح للحدود والتدرجات
val YounesCobalt       = Color(0xFF4FC3F7)  // سماوي للروابط والمعلومات، 9.55:1
val YounesPurple       = Color(0xFF00916A)  // أخضر ثانوي للحالات المساندة
val YounesRose         = Color(0xFFFF5A5F)  // خطر — 6.27:1 (كان 4.30:1 دون AA)

// ─── الأسطح — سلّم مقروء الفروق ──────────────────────────────────────────────
// كل درجة أفتح من سابقتها بقدر يُرى على شاشة رخيصة في ضوء النهار،
// ولا يكفي أن تختلف رقميًّا فقط.
val YounesVoid         = Color(0xFF0A1014)  // أعمق طبقة — خلف كل شيء
val YounesMidnight     = Color(0xFF0A1014)  // خلفية الشاشة والمحادثة
val YounesDeep         = Color(0xFF121A20)  // شريط الملاحة والقوائم
val YounesSurface1     = Color(0xFF121A20)  // البطاقات
val YounesSurface2     = Color(0xFF1A242C)  // العناصر النشطة
val YounesSurface3     = Color(0xFF223038)  // الحوارات وBottomSheet
/**
 * فاصل **زخرفي** بحت: خطوط التقسيم بين العناصر.
 *
 * تباينه 1.70:1 على الخلفية — دون 3:1 — وهذا مقبول هنا وحده، لأن
 * معيار WCAG 1.4.11 يستثني ما هو زخرفي صِرف: حذف الفاصل لا يُفقِد
 * المستخدم أي معلومة ولا يمنعه من إدراك حدود عنصر فعّال.
 *
 * ⚠️ لا يصلح حدًّا لعنصر تفاعلي — استعمل [YounesOutline] هناك.
 */
val YounesBorder       = Color(0xFF2C3D47)  // فواصل زخرفية فقط

/**
 * حدّ **العناصر الفعّالة**: إطار حقول الإدخال غير المركَّزة أساسًا.
 *
 * لونٌ واحد لا يكفي لدورين متناقضين في التباين، وهو العطب نفسه الذي
 * عولج سابقًا في `*Container`/`*Accent`. كان `YounesBorder` يخدم
 * `outline` و`outlineVariant` معًا، فيرث 79 `OutlinedTextField`
 * تباينًا قدره 1.56:1 على البطاقة — والحدّ هو الشيء الوحيد الذي
 * يُظهر حدود الحقل، فغيابه البصري يعني حقلًا غير مرئي لضعيف البصر.
 * وهذا ما يوجب WCAG 1.4.11 (تباين غير النصّ) فيه 3:1.
 *
 * `627A8C` أدنى قيمة في هذا التدرّج تحقق 3:1 على **كل** الأسطح
 * الأربعة: الخلفية 4.27 · البطاقة 3.92 · النشط 3.51 · الحوار 3.02.
 * ولم يُختَر أفتح منه إبقاءً على الطابع الهادئ.
 */
val YounesOutline      = Color(0xFF627A8C)  // حدود العناصر الفعّالة — ≥3:1
val YounesMuted        = Color(0xFF9AAEBB)  // نص ثانوي — 8.34:1 على الخلفية

// ─── فقاعات المحادثة ─────────────────────────────────────────────────────────
// الفقاعة الصادرة خضراء داكنة من عائلة اللون الأساسي لا زرقاء مستعارة،
// فتُقرأ الرسائل الصادرة كامتداد لهوية التطبيق.
val YounesBubbleOut    = Color(0xFF0E3B32)  // صادرة — 11.43:1 للنص عليها
val YounesBubbleOutGlow = Color(0xFF134A3E)  // تدرّج خفيف يعطي عمقًا
val YounesBubbleIn     = Color(0xFF16212A)  // واردة — 15.03:1
val YounesReadTick     = Color(0xFF4FC3F7)  // ✓✓ مقروء

// ─── النصوص ──────────────────────────────────────────────────────────────────
// الأبيض النقي على خلفية داكنة يُتعب العين في الجلسات الطويلة، فالنص
// الأساسي مائل قليلًا إلى البرودة بدل FFFFFF.
val YounesOnPrimary    = Color(0xFF06110D)  // على الأزرار الملوّنة — 7.09:1
val YounesOnAccent     = Color(0xFF06110D)  // على الذهب — 8.99:1
val YounesOnSurface    = Color(0xFFF2F6F8)  // النص الأساسي — 17.60:1
val YounesOnSurfaceDim = Color(0xFF9AAEBB)  // النص الثانوي — 8.34:1

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
    // outline يُرسم به حدّ الحقول غير المركَّزة في Material 3، فيلزمه
    // 3:1. أما outlineVariant فللفواصل الزخرفية، ويبقى خافتًا.
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
