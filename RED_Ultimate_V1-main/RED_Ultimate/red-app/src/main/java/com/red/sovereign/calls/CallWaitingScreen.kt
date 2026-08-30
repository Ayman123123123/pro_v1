package com.red.sovereign.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Hold
import androidx.compose.material.icons.filled.Resume
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.SovereignColors
import com.red.sovereign.ui.theme.YounesEmerald
import kotlinx.coroutines.delay

/**
 * شاشة انتظار المكالمة — Call Waiting Screen
 *
 * تظهر عندما يكون المستخدم في مكالمة نشطة ويستقبل مكالمة أخرى.
 * تتيح:
 * - عرض معلومات المكالمة الواردة
 * - قبول المكالمة الواردة (switch)
 * - رفض المكالمة الواردة
 * - وضع المكالمة الحالية على الانتظار (hold)
 * - عرض مؤقت للمكالمة الحالية
 */
@Composable
fun CallWaitingScreen(
    activeCall: ActiveCallInfo? = null,
    incomingCall: IncomingCallInfo? = null,
    onAcceptIncoming: () -> Unit = {},
    onRejectIncoming: () -> Unit = {},
    onHoldActive: () -> Unit = {},
    onResumeActive: () -> Unit = {},
    onEndActive: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    var timerSeconds by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        while (timerSeconds < 3600) { // max 1 hour
            delay(1000)
            timerSeconds++
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مكالمة في الانتظار", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SovereignColors.SurfaceDark
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.CallEnd, "إنهاء", tint = Color.Red)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))

            // Active call indicator
            if (activeCall != null) {
                ActiveCallCard(call = activeCall, isHeld = activeCall.isHeld)
                Spacer(Modifier.height(16.dp))
            }

            // Incoming call card
            if (incomingCall != null) {
                IncomingCallCard(call = incomingCall)
                Spacer(Modifier.height(24.dp))
            }

            // Timer
            Text(
                formatTimer(timerSeconds),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 24.sp,
                fontWeight = FontWeight.Light
            )
            Spacer(Modifier.height(8.dp))
            Text("مدة المكالمة الحالية", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)

            Spacer(Modifier.height(32.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (activeCall != null) {
                    if (activeCall.isHeld) {
                        ActionButton(
                            icon = Icons.Default.Resume,
                            label = "استئناف",
                            color = YounesEmerald,
                            onClick = onResumeActive
                        )
                    } else {
                        ActionButton(
                            icon = Icons.Default.Hold,
                            label = "انتظار",
                            color = AqyalGold,
                            onClick = onHoldActive
                        )
                    }
                    ActionButton(
                        icon = Icons.Default.CallEnd,
                        label = "إنهاء",
                        color = Color.Red,
                        onClick = onEndActive
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Incoming call actions
            if (incomingCall != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ActionButton(
                        icon = Icons.Default.CallReceived,
                        label = "قبول",
                        color = YounesEmerald,
                        onClick = onAcceptIncoming,
                        large = true
                    )
                    ActionButton(
                        icon = Icons.Default.CallEnd,
                        label = "رفض",
                        color = Color.Red,
                        onClick = onRejectIncoming,
                        large = true
                    )
                }
            }
        }
    }
}

data class ActiveCallInfo(
    val peer: String,
    val isVideo: Boolean,
    val isHeld: Boolean,
    val durationSeconds: Long = 0L
)

data class IncomingCallInfo(
    val peer: String,
    val isVideo: Boolean,
    val callId: String
)

@Composable
fun ActiveCallCard(call: ActiveCallInfo, isHeld: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isHeld) SovereignColors.SurfaceDarkVariant else AqyalGold.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (call.isVideo) YounesEmerald else SovereignColors.SurfaceDarkVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (call.isVideo) Icons.Default.Videocam else Icons.Default.Call,
                    "مكالمة",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(call.peer, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    if (isHeld) "في الانتظار" else "مكالمة نشطة",
                    color = if (isHeld) AqyalGold else YounesEmerald,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun IncomingCallCard(call: IncomingCallInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceDarkVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(YounesEmerald.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.CallReceived,
                    "مكالمة واردة",
                    tint = YounesEmerald,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("مكالمة واردة", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            Text(call.peer, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                if (call.isVideo) "مكالمة فيديو" else "مكالمة صوتية",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
    large: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(if (large) 64.dp else 52.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, label, tint = color, modifier = Modifier.size(if (large) 32.dp else 24.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = color, fontSize = 11.sp)
    }
}

fun formatTimer(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
