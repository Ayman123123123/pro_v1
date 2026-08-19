package com.red.sovereign.features.pstn

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

/**
 * شاشة المكالمة الهاتفية الواردة عبر بوابة DINSTAR.
 *
 * تعرض المشغّل اليمني المشتقّ من بادئة الرقم، مع نبض بصري على زر الرد.
 */
@Composable
fun IncomingPstnCallScreen(
    number: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val opInfo = YemeniOperatorDetector.getOperatorInfo(number)

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF0D1829), Color(0xFF030710)))
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Surface(
                color = AqyalGold.copy(alpha = 0.2f),
                shape = CircleShape,
                border = BorderStroke(1.dp, AqyalGold.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhoneInTalk,
                        contentDescription = null,
                        tint = AqyalGold,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "مكالمة هاتفية واردة",
                        color = AqyalGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(Modifier.weight(0.5f))

        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.take(2),
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
                text = "${opInfo.name} • اليمن",
                color = opInfo.brandColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FloatingActionButton(
                onClick = onDecline,
                containerColor = YounesRose,
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CallEnd,
                    contentDescription = "رفض",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            FloatingActionButton(
                onClick = onAccept,
                containerColor = YounesEmerald,
                modifier = Modifier
                    .size(72.dp)
                    .scale(pulseScale)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Call,
                    contentDescription = "رد",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * تنسيق الرقم اليمني للعرض بمجموعات مقروءة.
 *
 * الصيغة الدولية تُعرض كما هي لأن تقسيمها يختلف حسب رمز الدولة.
 */
internal fun formatPhoneNumber(number: String): String {
    if (number.startsWith("+")) return number
    return when (number.length) {
        in 4..6 -> "${number.take(3)} ${number.drop(3)}"
        in 7..9 -> "${number.take(3)} ${number.substring(3, minOf(6, number.length))} ${number.drop(6)}"
        else -> number
    }
}
