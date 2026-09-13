package com.red.sovereign.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.red.sovereign.ui.theme.AppThemeState
import com.red.sovereign.ui.theme.SovereignColors
import com.red.sovereign.ui.theme.SovereignGlassTier
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/**
 * الزجاج المثلج الحقيقي — Haze 1.x (ضبابية خلفية مُسرّعة عتاديًا).
 *
 * القاعدة (2026): البلور للطبقة العلوية فقط (أشرطة/حوارات)، وطبقة المحتوى
 * تحته موسومة بـ [sovereignHazeSource]. عند تعطيل liquidGlass يعود كل شيء
 * تلقائيًا للسطح المعتم عبر [SovereignGlassTier.fallback] — لا شاشة مكسورة.
 */

/** حالة ضباب واحدة لكل شاشة — تُمرَّر للشريط والمحتوى معًا. */
@Composable
fun rememberSovereignHaze(): HazeState = remember { HazeState() }

/** نمط Haze لكل طبقة: نصف قطر البلور من [SovereignGlassTier] + صبغة زجاجية. */
fun sovereignHazeStyle(tier: Dp, isDark: Boolean): HazeStyle {
    val radius = when (tier) {
        SovereignGlassTier.NavBar -> SovereignGlassTier.NavBar
        SovereignGlassTier.Card -> SovereignGlassTier.Card
        else -> SovereignGlassTier.Sheet
    }
    val tint = if (isDark) {
        SovereignColors.GlassBgLiquid.copy(alpha = 0.55f)
    } else {
        Color.White.copy(alpha = 0.55f)
    }
    // LEGENDARY FIX (انهيار دخول مثبت على الجهاز): Haze يتطلب backgroundColor صريحاً
    // لمسار البلور الموسع — غيابه يرمي IllegalArgumentException عند أول رسم بعد الدخول.
    val background = if (isDark) {
        SovereignColors.ObsidianDeep.copy(alpha = 0.72f)
    } else {
        Color.White.copy(alpha = 0.72f)
    }
    return HazeStyle(backgroundColor = background, tint = HazeTint(tint), blurRadius = radius)
}

/** وسم محتوى الخلفية كمصدر للضبابية — يُوضع على حاوية المحتوى تحت الشريط. */
fun Modifier.sovereignHazeSource(
    state: HazeState?,
    enabled: Boolean = AppThemeState.liquidGlassEnabled
): Modifier = if (enabled && state != null) hazeSource(state) else this

/**
 * تأثير الضبابية على الشريط نفسه — يُوضع على Surface الشريط.
 * بلا حالة (null) أو مع تعطيل الزجاج: يُعيد المُعدِّل كما هو (السطح المعتم الأصلي).
 */
fun Modifier.sovereignHazeEffect(
    state: HazeState?,
    tier: Dp = SovereignGlassTier.NavBar,
    isDark: Boolean = true,
    enabled: Boolean = AppThemeState.liquidGlassEnabled
): Modifier = if (enabled && state != null) {
    hazeEffect(state = state, style = sovereignHazeStyle(tier, isDark))
} else {
    this
}
