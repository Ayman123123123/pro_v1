package com.red.sovereign.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
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
 * 💎 YOUNES Sovereign UI Components — Master Library for Luxury Dark & Glass Design
 */

/**
 * بطاقة زجاجية فاخرة بتأثير Glassmorphism مع حواف متوهجة وظلال عميقة.
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
                } else Modifier
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
 * زر نيون سيادي تفاعلي مع ارتداد انسيابي وتدرج لوني براق.
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
            .background(if (enabled) gradient else Brush.linearGradient(listOf(Color(0xFF2A374A), Color(0xFF1E293B))))
            .border(1.dp, Color.White.copy(alpha = if (enabled) 0.25f else 0.05f), RoundedCornerShape(height / 2))
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
 * شارة حالة حية بنابض متوهج ومتحرك (Live Pulsing Badge).
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
                Icon(icon, null, tint = textColor, modifier = Modifier.size(12.dp))
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
 * محاكي وموجات صوتية حية (Soundwave Visualizer) لعرض التحدث في المكالمات والـ PTT.
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
            } else 0.15f

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
 * إطار صورة شخصية (Avatar) محاط بحلقات التشفير السيادي E2EE وحالة الاتصال.
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
        // الحلقة الخارجية المتوهجة
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .border(2.dp, ringColor, CircleShape)
                .background(SovereignColors.SurfaceCard)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial.take(1),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = (size.value * 0.38f).sp
                )
            }
        }

        // شارة التشفير أو الحالة النشطة
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
                    contentDescription = "مشفر",
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
 * شارة المشغل اليمني الذكية (Sabafon, Yemen Mobile, YOU, Y-Telecom).
 */
@Composable
fun SovereignOperatorBadge(
    operator: YemenOperator,
    modifier: Modifier = Modifier
) {
    val (bgGradient, label) = when (operator) {
        YemenOperator.SABAFON -> SovereignGradients.danger to "سبأفون 71"
        YemenOperator.YEMEN_MOBILE -> SovereignGradients.emerald to "يمن موبايل 77/78"
        YemenOperator.YOU -> SovereignGradients.gold to "يو 73"
        YemenOperator.Y_TELECOM -> SovereignGradients.cyan to "واي 70"
        YemenOperator.UNKNOWN -> Brush.linearGradient(listOf(Color(0xFF475569), Color(0xFF334155))) to "هاتف محلي"
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgGradient),
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
