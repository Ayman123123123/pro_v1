package com.red.sovereign.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * أنماط الزجاج السيادي 2026 — Liquid Glass Sovereign Design System.
 *
 * هذه الملف هو **مصدر الحقيقة الوحيد** لسياسة الزجاج: الأنماط، الشدة،
 * الأداء، ومعايير الوصولية. الجسر الحي [com.red.sovereign.ui.components.SovereignFrosted]
 * يبني HazeStyle من [SovereignGlass.recipe] حصرياً، فلا تتكرر الأرقام في موضعين.
 *
 * توثيق الوضع الفاتح: ألوان [SovereignColors.LightBase]/[LightSurface] تعرَّف في
 * SovereignThemeSystem وتحرسها ColorContrastTest.
 */
enum class SovereignGlassStyle(val id: String, val titleAr: String, val descriptionAr: String) {
    AUTO("AUTO", "تلقائي ذكي", "يختار النمط الأنسب حسب نوع العنصر ومستوى الأداء"),
    REGULAR("REGULAR", "زجاج عادي كثيف", "ضباب أعمق وتعتيم أعلى لحفظ تباين النصوص فوق المحتوى المتحرك"),
    CLEAR("CLEAR", "زجاج بلوري صافٍ", "شفافية عالية وضباب ناعم للبطاقات والمعاينات السريعة"),
    CRYSTAL("CRYSTAL", "بلوري فائق (تجريبي)", "انكسار ضوئي وإضاءة حقيقية عبر Haze 2 — أجمل وأثقل؛ للأجهزة القوية"),
    CYBER_FROST("CYBER_FROST", "زجاج سيبراني متوهج", "حواف زمردية خافتة مع انعكاس ضوئي نيون للأجهزة المتطورة");

    companion object {
        /** تحويل آمن من سلسلة محفوظة — قيمة غير معروفة تعود للتلقائي. */
        fun fromName(value: String?): SovereignGlassStyle =
            entries.firstOrNull { it.id == value?.trim()?.uppercase() } ?: AUTO
    }
}

/** مستويات الشدة الثلاثة — تُعرض في الإعدادات بهذه العناوين العربية. */
enum class GlassIntensityLevel(val id: String, val titleAr: String, val descriptionAr: String, val value: Float) {
    SUBTLE("SUBTLE", "خفيفة", "حضور هادئ يوفر البطارية على الأجهزة المتوسطة", 0.85f),
    BALANCED("BALANCED", "متوازنة", "التوازن الأمثل بين الجمال والأداء", 1.0f),
    DEEP("DEEP", "عميقة", "أقصى بلورة وإبهار — للأجهزة القوية", 1.2f);

    companion object {
        fun fromName(value: String?): GlassIntensityLevel =
            entries.firstOrNull { it.id == value?.trim()?.uppercase() } ?: BALANCED
    }
}

/**
 * محددات الأداء للزجاج.
 * ECO يستبدل البلور بلون معتم صلب — للأجهزة الضعيفة أو توفير البطارية.
 */
enum class GlassPerformance(val id: String, val titleAr: String, val descriptionAr: String) {
    FULL("FULL", "كامل", "أقصى جودة بصرية وضباب كامل للأجهزة القوية"),
    BALANCED("BALANCED", "متوازن", "ضباب مخفف 30% لتقليل حمل المعالج الرسومي"),
    ECO("ECO", "موفر البطارية", "بلا بلور — سطح صلب أنيق يحاكي الزجاج بلا تكلفة");

    companion object {
        fun fromName(value: String?): GlassPerformance =
            entries.firstOrNull { it.id == value?.trim()?.uppercase() } ?: FULL
    }
}

/** الوصفة البصرية المحسوبة لكل طبقة زجاجية — قيَم نقية قابلة للاختبار على JVM. */
@Immutable
data class GlassRecipe(
    val active: Boolean,
    val style: SovereignGlassStyle,
    val tier: Dp,
    val tint: Color,
    val fallback: Color,
    val borderBrush: Brush,
    val borderWidth: Dp = 1.dp
)

/**
 * سياسة حساب وحوكمة الزجاج السيادي — دوال نقية بلا حالة.
 * الوصولية أولًا: تباين مرتفع يرفع العتامة، وتقليل الحركة يهدئ الحدود.
 */
object SovereignGlass {

    const val MinIntensity = 0.6f
    const val MaxIntensity = 1.4f

    fun recipe(
        style: SovereignGlassStyle = SovereignGlassStyle.AUTO,
        intensity: Float = 1.0f,
        tier: Dp = SovereignGlassTier.NavBar,
        isDark: Boolean = true,
        enabled: Boolean = true,
        highContrast: Boolean = false,
        reduceMotion: Boolean = false,
        performance: GlassPerformance = GlassPerformance.FULL
    ): GlassRecipe {
        if (!enabled || performance == GlassPerformance.ECO) {
            val solidFallback = if (isDark) SovereignColors.ObsidianDeep else SovereignColors.LightBase
            return GlassRecipe(
                active = false,
                style = style,
                tier = 0.dp,
                tint = Color.Transparent,
                fallback = solidFallback,
                borderBrush = Brush.verticalGradient(listOf(SovereignColors.GlassBorder, Color.Transparent))
            )
        }

        // اختيار النمط الفعلي: التلقائي يوازن الكلفة — الأعماق تتحمل كثافة أكبر.
        val effectiveStyle = if (style == SovereignGlassStyle.AUTO) {
            if (tier >= SovereignGlassTier.Sheet) SovereignGlassStyle.REGULAR else SovereignGlassStyle.CLEAR
        } else {
            style
        }

        // معامل الشدة: حصر صارم يمنع القيم المكسورة من الإعدادات.
        val clampedIntensity = intensity.coerceIn(MinIntensity, MaxIntensity)
        val perfMultiplier = if (performance == GlassPerformance.BALANCED) 0.7f else 1.0f
        val effectiveBlur = (tier.value * clampedIntensity * perfMultiplier).dp

        // حساب لون التعتيم (Tint) والاحتياطي (Fallback) — الأرقام تحرسها الاختبارات.
        val tintColor: Color
        val fallbackColor: Color
        if (isDark) {
            val alpha = when {
                highContrast -> 0.85f
                effectiveStyle == SovereignGlassStyle.REGULAR -> 0.60f
                else -> 0.40f
            }
            tintColor = SovereignColors.SurfaceCard.copy(alpha = alpha)
            fallbackColor = SovereignColors.ObsidianDeep
        } else {
            val alpha = when {
                highContrast -> 0.90f
                effectiveStyle == SovereignGlassStyle.REGULAR -> 0.70f
                else -> 0.50f
            }
            tintColor = Color.White.copy(alpha = alpha)
            fallbackColor = SovereignColors.LightBase
        }

        val topHighlight = if (isDark) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.60f)
        val bottomShade = if (isDark) Color.White.copy(alpha = 0.03f) else Color.Black.copy(alpha = 0.05f)

        val border = Brush.verticalGradient(listOf(topHighlight, Color.Transparent, bottomShade))

        return GlassRecipe(
            active = true,
            style = effectiveStyle,
            tier = effectiveBlur,
            tint = tintColor,
            fallback = fallbackColor,
            borderBrush = border,
            borderWidth = if (highContrast) 1.5.dp else 1.dp
        )
    }

    /** تدرج الحد المضيء للوضع الفاتح — لازِم البلوري الفائق (مرآة liquidGlassBorder). */
    fun liquidGlassBorderLight(): Brush = Brush.linearGradient(
        listOf(
            Color(0x330F1B2D),
            Color(0x3314C79A),
            Color(0x330F1B2D)
        )
    )
}
