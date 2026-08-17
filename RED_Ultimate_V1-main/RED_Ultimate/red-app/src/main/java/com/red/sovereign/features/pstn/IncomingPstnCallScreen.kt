package com.red.sovereign.features.pstn

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.calls.YemeniOperatorDetector
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.YounesEmerald
import com.red.sovereign.ui.theme.YounesRose

@Composable
fun IncomingPstnCallScreen(
    number: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val opInfo = YemeniOperatorDetector.getOperatorInfo(number)

    // Pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0D1829), Color(0xFF030710))))
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Badges
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Surface(
                color = AqyalGold.copy(alpha = 0.2f),
                shape = CircleShape,
                border = androidx.compose.foundation.BorderStroke(1.dp, AqyalGold.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Rounded.PhoneInTalk, "GSM", tint = AqyalGold, modifier = Modifier.size(14.dp))
                    Text("مكالمة هاتفية واردة", color = AqyalGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.weight(0.5f))

        // Caller Info
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(Color.White.copy(0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.take(2).uppercase(),
                color = Color.White,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(Modifier.height(32.dp))
        
        Text(
            text = formatPhoneNumber(number),
            color = Color.White,
            fontSize = 36.sp,
            fontWeight = FontWeight.Light
        )
        
        if (opInfo != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${opInfo.name} • اليـمـن",
                color = opInfo.brandColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.weight(1f))

        // Call Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Decline
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FloatingActionButton(
                    onClick = onDecline,
                    containerColor = YounesRose,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(Icons.Rounded.CallEnd, "رفض", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }

            // Accept
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FloatingActionButton(
                    onClick = onAccept,
                    containerColor = YounesEmerald,
                    modifier = Modifier
                        .size(72.dp)
                        .scale(pulseScale)
                ) {
                    Icon(Icons.Rounded.Call, "رد", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }
        
        Spacer(Modifier.height(24.dp))
    }
}

private fun formatPhoneNumber(number: String): String {
    if (number.startsWith("+")) return number
    return when (number.length) {
        in 4..6 -> "${number.take(3)} ${number.drop(3)}"
        in 7..9 -> "${number.take(3)} ${number.substring(3, minOf(6, number.length))} ${number.drop(6)}"
        else -> number
    }
}
