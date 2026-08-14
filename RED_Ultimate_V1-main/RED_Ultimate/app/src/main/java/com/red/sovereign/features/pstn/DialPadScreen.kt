package com.red.sovereign.features.pstn

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── Colors ────────────────────────────────────────────────────────────────
private val BgDark = Color(0xFF0D0D0D)
private val SurfaceDark = Color(0xFF1A1A1A)
private val SurfaceMid = Color(0xFF242424)
private val RedAccent = Color(0xFFE53935)
private val OrangeGsm = Color(0xFFF57C00)
private val GreenCall = Color(0xFF43A047)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFF9E9E9E)
private val TextHint = Color(0xFF616161)

// ─── DialPadScreen ─────────────────────────────────────────────────────────

/**
 * لوحة أرقام احترافية كاملة.
 * تدعم:
 * - مكالمة WebRTC (أخضر)
 * - مكالمة PSTN/GSM (برتقالي)
 * - اكتشاف المشغل اليمني تلقائياً
 * - DTMF haptic feedback
 * - سجل المكالمات الأخيرة
 */
@Composable
fun DialPadScreen(
    onNavigateToWebRtcCall: (String) -> Unit,
    onNavigateToPstnCall: (String) -> Unit,
    recentNumbers: List<String> = emptyList()
) {
    var number by remember { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current
    val opInfo = YemeniOperatorDetector.getOperatorInfo(number)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Header ─────────────────────────────────────────────────
        Spacer(Modifier.height(32.dp))

        // Operator Badge
        AnimatedVisibility(
            visible = number.length >= 2,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically()
        ) {
            Surface(
                color = Color(opInfo.brandColor.value).copy(alpha = 0.15f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(opInfo.brandColor.value))
                    )
                    Text(
                        text = opInfo.name,
                        color = Color(opInfo.brandColor.value),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // ── Number Display ─────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = formatPhoneNumber(number),
                color = if (number.isEmpty()) TextHint else TextPrimary,
                fontSize = when {
                    number.length > 12 -> 28.sp
                    number.length > 8 -> 36.sp
                    else -> 44.sp
                },
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            // Backspace
            if (number.isNotEmpty()) {
                IconButton(
                    onClick = {
                        number = number.dropLast(1)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(Icons.Rounded.Backspace, "حذف", tint = TextSecondary)
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        // ── Keypad 3×4 ────────────────────────────────────────────
        val keys = listOf(
            "1" to "", "2" to "ABC", "3" to "DEF",
            "4" to "GHI", "5" to "JKL", "6" to "MNO",
            "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
            "*" to "", "0" to "+", "#" to ""
        )

        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            keys.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { (digit, letters) ->
                        DialKey(
                            digit = digit,
                            letters = letters,
                            onClick = {
                                if (number.length < 15) number += digit
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onLongClick = {
                                if (digit == "0") { number += "+"; haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        // ── Call Buttons ──────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // PSTN/GSM call — Orange
            if (number.isNotEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FloatingActionButton(
                        onClick = { if (number.isNotEmpty()) onNavigateToPstnCall(number) },
                        containerColor = OrangeGsm,
                        modifier = Modifier.size(60.dp)
                    ) {
                        Icon(Icons.Rounded.SignalCellularAlt, "GSM", tint = Color.White, modifier = Modifier.size(26.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("خطي", color = TextSecondary, fontSize = 11.sp)
                }
            }

            // WebRTC call — Green (main)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FloatingActionButton(
                    onClick = { if (number.isNotEmpty()) onNavigateToWebRtcCall(number) },
                    containerColor = if (number.isEmpty()) TextHint else GreenCall,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(Icons.Rounded.Call, "اتصال RED", tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text("RED", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }

            // Clear number
            if (number.isNotEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { number = "" },
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(SurfaceMid)
                    ) {
                        Icon(Icons.Rounded.Close, "مسح", tint = TextSecondary, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("مسح", color = TextSecondary, fontSize = 11.sp)
                }
            }
        }

        // ── Recent Calls ──────────────────────────────────────────
        if (recentNumbers.isNotEmpty() && number.isEmpty()) {
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.06f), modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                items(recentNumbers.take(5)) { num ->
                    RecentCallRow(
                        number = num,
                        operatorInfo = YemeniOperatorDetector.getOperatorInfo(num),
                        onClick = { number = num }
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ─── Dial Key ─────────────────────────────────────────────────────────────

@Composable
private fun DialKey(
    digit: String,
    letters: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(SurfaceMid)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = digit,
                color = TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Light
            )
            if (letters.isNotEmpty()) {
                Text(
                    text = letters,
                    color = TextSecondary,
                    fontSize = 9.sp,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun RecentCallRow(
    number: String,
    operatorInfo: OperatorInfo,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(operatorInfo.brandColor.value).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.History, null, tint = Color(operatorInfo.brandColor.value), modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(formatPhoneNumber(number), color = TextPrimary, fontSize = 16.sp)
            Text(operatorInfo.name, color = TextSecondary, fontSize = 12.sp)
        }
        Spacer(Modifier.weight(1f))
        Icon(Icons.Rounded.Call, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
    }
}

// ─── Utils ─────────────────────────────────────────────────────────────────

private fun formatPhoneNumber(number: String): String {
    if (number.isEmpty()) return "أدخل رقم الهاتف"
    if (number.startsWith("+")) return number
    return when (number.length) {
        in 1..3 -> number
        in 4..6 -> "${number.take(3)} ${number.drop(3)}"
        in 7..9 -> "${number.take(3)} ${number.substring(3, minOf(6, number.length))} ${number.drop(6)}"
        else -> "${number.take(3)} ${number.substring(3, 6)} ${number.drop(6)}"
    }
}
