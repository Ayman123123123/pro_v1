package com.red.features.auth

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun RedSplashScreen(onFinished: () -> Unit) {
    val scale = remember { Animatable(0.5f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            scale.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(800, easing = FastOutSlowInEasing)
            )
        }
        launch {
            delay(600)
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(800, easing = FastOutSlowInEasing)
            )
        }
        delay(3000)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030712)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Logo diamond
            Surface(
                modifier = Modifier.size(120.dp).scale(scale.value),
                color = Color(0xFF00C896),
                shape = androidx.compose.foundation.shape.CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("◆", color = Color(0xFF030712), fontSize = 60.sp, fontWeight = FontWeight.Black)
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            // Brand name
            Text(
                text = "YOUNES",
                color = Color(0xFF00C896),
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 6.sp,
                modifier = Modifier.graphicsLayer(alpha = alpha.value)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "اتصالات سيادية",
                color = Color(0xFF94A3B8),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.graphicsLayer(alpha = alpha.value)
            )
            Spacer(modifier = Modifier.height(32.dp))
            // Loading indicator
            LinearProgressIndicator(
                modifier = Modifier
                    .width(120.dp)
                    .graphicsLayer(alpha = alpha.value),
                color = Color(0xFF00C896),
                trackColor = Color(0xFF1E293B)
            )
        }
    }
}
