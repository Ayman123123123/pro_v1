package com.red.core.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * AQYAL Sovereign Ultimate Design System
 * Royal Gold, Obsidian Black, Deep Royal Blue, and Neon Cyan Accents.
 */
val AqyalGold = Color(0xFFF59E0B)
val AqyalGoldLight = Color(0xFFFBBF24)
val AqyalDarkObsidian = Color(0xFF030712)
val AqyalRoyalBlue = Color(0xFF0F172A)
val AqyalSurfaceNavy = Color(0xFF1E293B)
val AqyalCyanGlow = Color(0xFF38BDF8)

private val AqyalColorScheme = darkColorScheme(
    primary = AqyalCyanGlow,
    secondary = AqyalGold,
    background = AqyalDarkObsidian,
    surface = AqyalSurfaceNavy,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun REDTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AqyalColorScheme,
        typography = Typography(),
        content = content
    )
}

/**
 * الخلفية السيادية الملكية الأسطورية مع تأثير تدفق الإضاءة الحية (Cosmic & Royal Gradient)
 */
@Composable
fun SovereignBackground(content: @Composable () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "BackgroundAnimation")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AlphaAnim"
    )

    val epicGradient = Brush.verticalGradient(
        colors = listOf(
            AqyalDarkObsidian,
            AqyalRoyalBlue,
            Color(0xFF090D16)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(epicGradient)
    ) {
        // Decorative royal gold ambient glow in corners
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(AqyalGold.copy(alpha = alphaAnim * 0.15f), Color.Transparent),
                        radius = 1200f
                    )
                )
        )
        content()
    }
}

/**
 * زر سيادي أسطوري فخم مع حواف متدرجة ذهبية وزرقاء (Aqyal Epic Button)
 */
@Composable
fun AqyalEpicButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String,
    isGold: Boolean = true
) {
    val buttonBrush = if (isGold) {
        Brush.horizontalGradient(listOf(AqyalGold, AqyalGoldLight))
    } else {
        Brush.horizontalGradient(listOf(AqyalRoyalBlue, AqyalCyanGlow))
    }

    Box(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(buttonBrush)
    ) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = text,
                color = if (isGold) Color.Black else Color.White,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}
