package com.red.sovereign.calls

import android.content.Context
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
import com.red.sovereign.ui.theme.YounesEmerald

/**
 * شاشة واردة PSTN Expressive حقيقية - ليست Box ميت
 * تعمل على الشبكة المحلية وكل الشبكات
 */
@Composable
fun Material3ExpressiveIncomingPstnCallScreen(
    callerNumber: String,
    callerName: String? = null,
    callId: String,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onDeclineWithMessage: (String) -> Unit = {},
    onAcceptVideo: () -> Unit = {},
    context: Context
) {
    val operatorInfo = remember(callerNumber) { YemeniOperatorDetector.getOperatorInfo(callerNumber) }
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    Box(
        Modifier.fillMaxSize().background(Color(0xFF0A1628)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxWidth().padding(28.dp)
        ) {
            Box(
                Modifier.size(110.dp).scale(pulse).clip(CircleShape).background(YounesEmerald.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.size(80.dp).clip(CircleShape).background(YounesEmerald), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Phone, null, tint = Color.White, modifier = Modifier.size(42.dp))
                }
            }

            Text(callerName ?: "مكالمة يمنية واردة", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(callerNumber, color = Color.White.copy(alpha = 0.8f), fontSize = 22.sp, fontWeight = FontWeight.Medium)

            operatorInfo?.let { op ->
                Card(colors = CardDefaults.cardColors(containerColor = op.brandColor.copy(alpha = 0.2f)), shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(op.brandColor))
                        Spacer(Modifier.width(6.dp))
                        Text(op.name, color = op.brandColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            Text("ID: ${callId.take(8)}", color = Color.White.copy(alpha = 0.3f), fontSize = 10.sp)

            Spacer(Modifier.height(24.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledIconButton(
                        onClick = onReject,
                        modifier = Modifier.size(68.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFE53935))
                    ) { Icon(Icons.Default.CallEnd, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
                    Spacer(Modifier.height(6.dp))
                    Text("رفض", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledIconButton(
                        onClick = onAcceptVideo,
                        modifier = Modifier.size(68.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF42A5F5))
                    ) { Icon(Icons.Default.Videocam, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
                    Spacer(Modifier.height(6.dp))
                    Text("فيديو", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FilledIconButton(
                        onClick = onAccept,
                        modifier = Modifier.size(68.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = YounesEmerald)
                    ) { Icon(Icons.Default.Call, null, tint = Color.White, modifier = Modifier.size(28.dp)) }
                    Spacer(Modifier.height(6.dp))
                    Text("قبول", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("مشغول", "سأتصل لاحقاً", "في اجتماع").forEach { msg ->
                    OutlinedButton(onClick = { onDeclineWithMessage(msg) }, shape = RoundedCornerShape(20.dp)) {
                        Text(msg, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
