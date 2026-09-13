package com.red.sovereign.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.red.sovereign.ui.theme.CustomThemePackage
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * خلفية المحادثة — زخرفة هندسية خافتة (نجمة ثمانية) + صبغة لكل شات.
 *
 * على طريقة واتساب (doodle) لكن بهوية سيادية: الزخرفة 4% فقط حتى لا
 * تنافس النص، وفي الفاتح تتحول لورق لؤلؤي `#F2EFE9` بزخرفة كحلية خافتة.
 * رسم ثابت بلا أنيميشن — صفر تكلفة بعد التركيب الأول.
 *
 * @param package_ حزمة الثيم النشطة (null = الافتراضي السيادي).
 * @param chatTint صبغة خاصة بهذه المحادثة تتفوق على الحزمة.
 * @param isDark من ثيم النظام الحالي.
 */
@Composable
fun ChatWallpaper(
    modifier: Modifier = Modifier,
    package_: CustomThemePackage? = null,
    chatTint: Color? = null,
    isDark: Boolean = true
) {
    val patternAlpha = package_?.patternAlpha ?: 0.04f
    val dim = package_?.wallpaperDim ?: 0f
    val solid = package_?.wallpaperStyle == CustomThemePackage.WALLPAPER_SOLID
    val tint = chatTint
        ?: package_?.wallpaperTintArgb?.let { Color(it) }

    Box(modifier = modifier.fillMaxSize()) {
        if (!solid) {
            val lineColor = if (isDark) Color.White else Color(0xFF0F1B2D)
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cell = 148.dp.toPx()
                val r = cell * 0.30f
                val cols = (size.width / cell).toInt() + 1
                val rows = (size.height / cell).toInt() + 1
                for (cx in 0..cols) {
                    for (cy in 0..rows) {
                        val center = Offset(
                            cx * cell + (if (cy % 2 == 1) cell / 2f else 0f),
                            cy * cell
                        )
                        // نجمة ثمانية: مربعان متقاطعان بزاوية 45°.
                        for (rot in 0..1) {
                            val path = Path().apply {
                                for (k in 0..3) {
                                    val a = Math.toRadians((45.0 * k + rot * 45.0)).toFloat()
                                    val p = Offset(
                                        center.x + r * cos(a),
                                        center.y + r * sin(a)
                                    )
                                    if (k == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                                }
                                close()
                            }
                            drawPath(
                                path = path,
                                color = lineColor.copy(alpha = patternAlpha),
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }
                        drawCircle(
                            color = lineColor.copy(alpha = patternAlpha * 0.8f),
                            radius = min(size.width, size.height) * 0.0016f + 1.5f,
                            center = center,
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                }
            }
        }
        // صبغة المحادثة — هالة قطرية خافتة لا تمس التباين.
        if (tint != null) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.radialGradient(
                        colors = listOf(
                            tint.copy(alpha = if (isDark) 0.10f else 0.08f),
                            Color.Transparent
                        ),
                        radius = 900f
                    )
                )
            )
        }
        // تعتيم اختياري فوق الزخرفة لرفع مقروئية النص.
        if (dim > 0f) {
            Box(
                Modifier.fillMaxSize().background(
                    (if (isDark) Color.Black else MaterialTheme.colorScheme.background)
                        .copy(alpha = dim.coerceIn(0f, 0.6f))
                )
            )
        }
    }
}
