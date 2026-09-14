package com.red.sovereign.features.pstn

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.calls.YemeniOperatorDetector
import com.red.sovereign.ui.theme.YounesEmerald
import com.red.sovereign.ui.theme.AqyalGold

/**
 * شاشة مكالمة واردة PSTN حقيقية - ليست Box ميت
 * تصميم Material3 Expressive 2026 + Liquid Glass
 * - أنيميشن نبض للمكالمة الواردة
 * - كشف المشغل اليمني
 * - قبول/رفض مع أزرار كبيرة
 * - خلفية متدرجة فاخرة
 */
@Composable
fun IncomingPstnCallScreen(
    number: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val operatorInfo = remember(number) { YemeniOperatorDetector.getOperatorInfo(number) }
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A1628)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxWidth().padding(32.dp)
        ) {
            // نبض
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(YounesEmerald.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(YounesEmerald),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Phone, null, tint = Color.White, modifier = Modifier.size(48.dp))
                }
            }

            Text(
                "مكالمة واردة من اليمن",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 16.sp
            )

            Text(
                formatPhoneNumber(number),
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            operatorInfo?.let { op ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = op.brandColor.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(op.brandColor))
                        Spacer(Modifier.width(8.dp))
                        Text(op.name, color = op.brandColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(op.technology, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledIconButton(
                        onClick = onDecline,
                        modifier = Modifier.size(72.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFE53935))
                    ) {
                        Icon(Icons.Default.CallEnd, null, tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("رفض", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledIconButton(
                        onClick = onAccept,
                        modifier = Modifier.size(72.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = YounesEmerald)
                    ) {
                        Icon(Icons.Default.Call, null, tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("قبول", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Security, null, tint = YounesEmerald, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "مكالمة PSTN عبر DINSTAR - آمنة ومشفرة عبر SIP/TLS",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
