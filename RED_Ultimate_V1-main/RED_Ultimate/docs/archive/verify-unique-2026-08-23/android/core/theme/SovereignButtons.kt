package com.red.core.theme

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 🏛️ YOUNES Sovereign Button System
 * أنواع الأزرار: ذهبي، سيادي، خطر، زجاجي، شفاف، أيقوني، تبديل
 */

// ─── الألوان السيادية ───
object SovereignColors {
    val Gold = Color(0xFFF59E0B)
    val GoldLight = Color(0xFFFBBF24)
    val GoldDark = Color(0xFFD97706)
    val Cyan = Color(0xFF38BDF8)
    val CyanDark = Color(0xFF0284C7)
    val Obsidian = Color(0xFF030712)
    val Navy = Color(0xFF0F172A)
    val SurfaceNavy = Color(0xFF1E293B)
    val Danger = Color(0xFFEF4444)
    val DangerDark = Color(0xFFB91C1C)
    val Success = Color(0xFF10B981)
    val Warning = Color(0xFFF59E0B)
    val DinstarGold = Color(0xFFF4B400)
    val VoipBlue = Color(0xFF1E88E5)
    val SpacePurple = Color(0xFF8E24AA)
    val LiveRed = Color(0xFFE53935)
}

// ─── التدرجات السيادية ───
object SovereignGradients {
    val gold = Brush.horizontalGradient(listOf(SovereignColors.Gold, SovereignColors.GoldLight))
    val cyan = Brush.horizontalGradient(listOf(SovereignColors.CyanDark, SovereignColors.Cyan))
    val danger = Brush.horizontalGradient(listOf(SovereignColors.DangerDark, SovereignColors.Danger))
    val royal = Brush.horizontalGradient(listOf(SovereignColors.Navy, SovereignColors.Cyan))
    val dinstar = Brush.horizontalGradient(listOf(SovereignColors.GoldDark, SovereignColors.DinstarGold))
    val live = Brush.horizontalGradient(listOf(Color(0xFFB71C1C), SovereignColors.LiveRed))
    val space = Brush.horizontalGradient(listOf(Color(0xFF4A148C), SovereignColors.SpacePurple))
}

/**
 * 🔶 الزر الذهبي السيادي — للأفعال الرئيسية
 */
@Composable
fun SovereignGoldButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    SovereignGradientButton(
        text = text,
        onClick = onClick,
        gradient = SovereignGradients.gold,
        textColor = Color.Black,
        icon = icon,
        enabled = enabled,
        isLoading = isLoading,
        modifier = modifier
    )
}

/**
 * 🔵 الزر السيادي الأزرق — لـ VoIP والإتصالات
 */
@Composable
fun SovereignCyanButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    SovereignGradientButton(
        text = text,
        onClick = onClick,
        gradient = SovereignGradients.cyan,
        textColor = Color.White,
        icon = icon,
        enabled = enabled,
        isLoading = isLoading,
        modifier = modifier
    )
}

/**
 * 🔴 زر الخطر — للحذف وإنهاء المكالمة
 */
@Composable
fun SovereignDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    SovereignGradientButton(
        text = text,
        onClick = onClick,
        gradient = SovereignGradients.danger,
        textColor = Color.White,
        icon = icon,
        enabled = enabled,
        modifier = modifier
    )
}

/**
 * 🟡 زر Dinstar الذهبي — للمكالمات الخطية اليمنية
 */
@Composable
fun DinstarGoldButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = Icons.Default.SimCard,
    enabled: Boolean = true
) {
    SovereignGradientButton(
        text = text,
        onClick = onClick,
        gradient = SovereignGradients.dinstar,
        textColor = Color.Black,
        icon = icon,
        enabled = enabled,
        modifier = modifier
    )
}

/**
 * 🔴 زر البث المباشر
 */
@Composable
fun LiveButton(
    text: String = "بث مباشر",
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLive: Boolean = false
) {
    val pulseScale = rememberInfiniteTransition().animateFloat(
        initialValue = 1f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "LivePulse"
    )

    SovereignGradientButton(
        text = if (isLive) "● مباشر" else text,
        onClick = onClick,
        gradient = SovereignGradients.live,
        textColor = Color.White,
        icon = Icons.Default.LiveTv,
        modifier = modifier.then(if (isLive) Modifier.scale(pulseScale.value) else Modifier)
    )
}

/**
 * 🟣 زر Audio Space
 */
@Composable
fun SpaceButton(
    text: String = "غرفة صوتية",
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SovereignGradientButton(
        text = text,
        onClick = onClick,
        gradient = SovereignGradients.space,
        textColor = Color.White,
        icon = Icons.Default.Mic,
        modifier = modifier
    )
}

/**
 * 🪟 الزر الزجاجي الشفاف — Glassmorphism
 */
@Composable
fun SovereignGlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tintColor: Color = SovereignColors.Cyan
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, tintColor.copy(alpha = 0.4f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = tintColor.copy(alpha = 0.08f),
            contentColor = tintColor
        )
    ) {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

/**
 * ⭕ زر أيقوني دائري — FAB مصغر
 */
@Composable
fun SovereignIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = SovereignColors.SurfaceNavy,
    tint: Color = Color.White,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .background(backgroundColor, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/**
 * 🔄 زر التبديل السيادي — Toggle
 */
@Composable
fun SovereignToggleButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = SovereignColors.Cyan,
    icon: ImageVector? = null
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) activeColor else Color.Transparent,
        label = "ToggleBg"
    )
    val contentColor = if (isSelected) Color.White else Color.Gray

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = bgColor,
        modifier = modifier.height(40.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(icon, null, tint = contentColor, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, color = contentColor, fontWeight = FontWeight.Medium, fontSize = 13.sp)
        }
    }
}

/**
 * 🔘 زر خصوصية — لاختيار من يستطيع الرؤية
 */
@Composable
fun PrivacyOptionButton(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) SovereignColors.Cyan else Color.Gray.copy(alpha = 0.3f)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) SovereignColors.Cyan.copy(alpha = 0.1f) else Color.Transparent,
        border = BorderStroke(1.5.dp, borderColor),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = if (isSelected) SovereignColors.Cyan else Color.Gray, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = if (isSelected) SovereignColors.Cyan else Color.White)
            Spacer(Modifier.weight(1f))
            if (isSelected) {
                Icon(Icons.Default.CheckCircle, null, tint = SovereignColors.Cyan, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ─── الأساس الداخلي ───
@Composable
private fun SovereignGradientButton(
    text: String,
    onClick: () -> Unit,
    gradient: Brush,
    textColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    Box(
        modifier = modifier
            .height(52.dp)
            .shadow(8.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(gradient)
            .then(if (enabled && !isLoading) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = textColor
                )
                Spacer(Modifier.width(8.dp))
            } else if (icon != null) {
                Icon(icon, null, tint = textColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = if (enabled) textColor else textColor.copy(alpha = 0.5f),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
