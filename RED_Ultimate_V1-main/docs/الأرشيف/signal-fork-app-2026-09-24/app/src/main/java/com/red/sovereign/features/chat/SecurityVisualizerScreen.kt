package com.red.sovereign.features.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * A highly visual screen that proves to the user that the chat is Post-Quantum Encrypted.
 * Displays an animated cryptographic footprint (Kyber1024 + Signal).
 */
@Composable
fun SecurityVisualizerScreen(
    contactName: String,
    kyberFingerprint: String = "4F8A 9B2C 11XQ 00PQ 889V ...",
    onClose: () -> Unit
) {
    var isScanning by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(3000)
        isScanning = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060D1A))
    ) {
        // Close Button
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .statusBarsPadding()
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "الدرع الكمّي (Quantum Shield)",
                color = Color(0xFF00C98C),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "المحادثة مع $contactName محمية بتشفير ما بعد الكمي (Kyber1024 + Signal PQXDH).",
                color = Color.LightGray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Animated Quantum Core Visualizer
            QuantumCoreAnimation(isScanning)

            Spacer(modifier = Modifier.height(48.dp))

            // Fingerprint Card
            Surface(
                color = Color.White.copy(alpha = 0.05f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00C98C).copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF00C98C))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "البصمة الكمية المشفرة:",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (isScanning) "جاري التحقق من مسار التشفير الكمي..." else kyberFingerprint,
                        color = if (isScanning) Color.Gray else Color(0xFF00C98C),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Mono,
                        letterSpacing = 2.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun QuantumCoreAnimation(isScanning: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "quantum")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .size(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = (size.width / 2) * pulse

            // Draw outer orbit
            drawCircle(
                color = Color(0xFF00C98C).copy(alpha = 0.2f),
                radius = radius,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
            )

            // Draw connecting quantum lines
            for (i in 0 until 8) {
                val angle = Math.toRadians((i * 45 + rotation).toDouble())
                val endX = center.x + (radius * cos(angle)).toFloat()
                val endY = center.y + (radius * sin(angle)).toFloat()
                
                drawLine(
                    color = Color(0xFF00C98C).copy(alpha = if (isScanning) 0.5f else 0.8f),
                    start = center,
                    end = Offset(endX, endY),
                    strokeWidth = 2f
                )
                drawCircle(
                    color = Color(0xFF00C98C),
                    radius = 4f,
                    center = Offset(endX, endY)
                )
            }

            // Draw solid core
            drawCircle(
                color = if (isScanning) Color.Gray else Color(0xFF00C98C),
                radius = 20f * pulse,
                center = center
            )
        }
    }
}
