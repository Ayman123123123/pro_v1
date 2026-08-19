package com.red.sovereign.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.features.dinstar.YemenOperator
import com.red.sovereign.ui.theme.SovereignColors
import com.red.sovereign.ui.theme.SovereignGradients
import kotlin.math.sin

/**
 * مكتبة مكوّنات يونس السيادية — الأساس الموحّد للتصميم الداكن الزجاجي الفاخر.
 *
 * كل مكوّن هنا عديم الحالة (stateless) ويأخذ ألوانه من `SovereignColors`
 * حتى يبقى تغيير الهوية البصرية في ملف واحد.
 */

/**
 * بطاقة زجاجية فاخرة بتأثير Glassmorphism مع حواف متدرّجة وظلال عميقة.
 *
 * تتقلّص قليلًا عند الضغط لإعطاء إحساس لمسي، وذلك فقط عند تمرير [onClick].
 */
@Composable
fun SovereignGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    borderBrush: Brush = SovereignGradients.glassCard,
    backgroundColor: Color = SovereignColors.SurfaceCard.copy(alpha = 0.75f),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && onClick != null) 0.97f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "GlassCardScale"
    )

    Surface(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(cornerRadius))
            .border(1.dp, borderBrush, RoundedCornerShape(cornerRadius))
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            ),
        color = backgroundColor,
        shadowElevation = 8.dp,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

/**
 * زر نيون سيادي تفاعلي بارتداد انسيابي وتدرّج لوني براق.
 */
@Composable
fun SovereignNeonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    gradient: Brush = SovereignGradients.emerald,
    contentColor: Color = Color.White,
    enabled: Boolean = true,
    height: Dp = 50.dp
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "NeonButtonScale"
    )

    Box(
        modifier = modifier
            .height(height)
            .scale(scale)
            .clip(RoundedCornerShape(height / 2))
            .background(
                if (enabled) {
                    gradient
                } else {
                    Brush.linearGradient(listOf(Color(0xFF2A374A), Color(0xFF1E293B)))
                }
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = if (enabled) 0.25f else 0.05f),
                shape = RoundedCornerShape(height / 2)
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = contentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

/**
 * شارة حالة حيّة بنبض متوهّج متحرّك (Live Pulsing Badge).
 */
@Composable
fun SovereignStatusBadge(
    label: String,
    modifier: Modifier = Modifier,
    glowColor: Color = SovereignColors.EmeraldNeon,
    textColor: Color = Color.White,
    icon: ImageVector? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "BadgeGlow")
    val alphaGlow by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AlphaGlow"
    )

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, glowColor.copy(alpha = alphaGlow * 0.7f), RoundedCornerShape(8.dp)),
        color = glowColor.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(glowColor.copy(alpha = alphaGlow))
            )
            Spacer(Modifier.width(6.dp))
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = label,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * محاكي موجات صوتية حيّة (Soundwave Visualizer) لعرض التحدّث في المكالمات
 * والرسائل الصوتية وزر الضغط للتحدّث (PTT).
 */
@Composable
fun SovereignWaveVisualizer(
    modifier: Modifier = Modifier,
    isSpeaking: Boolean = true,
    barCount: Int = 18,
    barColor: Color = SovereignColors.EmeraldNeon,
    maxBarHeight: Dp = 36.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "WaveAnimation")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WavePhase"
    )

    Canvas(modifier = modifier.height(maxBarHeight)) {
        val totalWidth = size.width
        val barWidth = (totalWidth / (barCount * 1.8f)).coerceAtLeast(4f)
        val gap = (totalWidth - (barWidth * barCount)) / (barCount - 1).coerceAtLeast(1)

        for (i in 0 until barCount) {
            val normalizedHeight = if (isSpeaking) {
                val wave = sin(phase + (i.toFloat() * 0.5f))
                0.25f + (0.75f * ((wave + 1f) / 2f))
            } else {
                0.15f
            }

            val currentHeight = size.height * normalizedHeight
            val left = i * (barWidth + gap)
            val top = (size.height - currentHeight) / 2f

            drawRoundRect(
                color = barColor.copy(alpha = if (isSpeaking) 0.9f else 0.35f),
                topLeft = Offset(left, top),
                size = Size(barWidth, currentHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

/**
 * إطار صورة شخصية (Avatar) محاط بحلقة ذهبية، مع شارة التشفير التام E2EE
 * أو مؤشّر الاتصال المباشر.
 */
@Composable
fun SovereignAvatarRing(
    initial: String,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    isOnline: Boolean = true,
    isEncrypted: Boolean = true,
    ringColor: Color = SovereignColors.Gold
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // الحلقة الخارجية المتوهّجة مع الحرف الأول من الاسم
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .border(2.dp, ringColor, CircleShape)
                .background(SovereignColors.SurfaceCard),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial.take(1),
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = (size.value * 0.38f).sp
            )
        }

        // شارة التشفير، وإلا مؤشّر الحالة النشطة
        if (isEncrypted) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(SovereignColors.EmeraldNeon)
                    .border(1.5.dp, SovereignColors.Obsidian, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "مشفَّر",
                    tint = Color.Black,
                    modifier = Modifier.size(9.dp)
                )
            }
        } else if (isOnline) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(SovereignColors.CyanNeon)
                    .border(1.5.dp, SovereignColors.Obsidian, CircleShape)
            )
        }
    }
}

/**
 * شارة المشغّل اليمني الذكية (سبأفون، يمن موبايل، يو، واي).
 *
 * البادئات مأخوذة من [YemenOperator] وهي المصدر الوحيد للحقيقة؛ لا تُكتب
 * الأرقام يدويًا هنا حتى لا يتكرّر خطأ الجداول المتوازية.
 */
@Composable
fun SovereignOperatorBadge(
    operator: YemenOperator,
    modifier: Modifier = Modifier
) {
    val gradient = when (operator) {
        YemenOperator.SABAFON -> SovereignGradients.danger
        YemenOperator.YEMEN_MOBILE -> SovereignGradients.emerald
        YemenOperator.YOU -> SovereignGradients.gold
        YemenOperator.Y_TELECOM -> SovereignGradients.cyan
        YemenOperator.UNKNOWN -> Brush.linearGradient(
            listOf(Color(0xFF475569), Color(0xFF334155))
        )
    }

    val label = if (operator == YemenOperator.UNKNOWN) {
        operator.arabicName
    } else {
        "${operator.arabicName} ${operator.prefixes.sorted().joinToString("/")}"
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(gradient),
        color = Color.Transparent
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
        )
    }
}
