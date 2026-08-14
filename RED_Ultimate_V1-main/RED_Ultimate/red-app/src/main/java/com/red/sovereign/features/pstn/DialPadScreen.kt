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
import com.red.sovereign.calls.YemeniOperatorDetector
import com.red.sovereign.calls.OperatorInfo
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.YounesEmerald

// ─── Colors ────────────────────────────────────────────────────────────────
private val BgDark = Color(0xFF030710)
private val SurfaceDark = Color(0xFF0D1829)
private val SurfaceMid = Color(0xFF162334)
private val OrangeGsm = Color(0xFFF57C00)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFF9E9E9E)
private val TextHint = Color(0xFF616161)

/**
 * لوحة أرقام احترافية كاملة بنمط يونس السيادي.
 * تدعم:
 * - مكالمة WebRTC E2EE (أخضر زمردي)
 * - مكالمة PSTN/GSM عبر DINSTAR (ذهبي / برتقالي)
 * - اكتشاف المشغل اليمني تلقائياً مع الألوان الحقيقية
 * - DTMF haptic feedback
 * - زر إغلاق / رجوع
 */
@Composable
fun DialPadScreen(
    onDismiss: () -> Unit = {},
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
            .background(Brush.verticalGradient(listOf(Color(0xFF0D1829), Color(0xFF030710))))
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Top Bar ────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "رجوع", tint = Color.White)
            }
            Text("لوحة الاتصال السيادية", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.size(48.dp))
        }

        Spacer(Modifier.height(12.dp))

        // Operator Badge
        AnimatedVisibility(
            visible = opInfo != null,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically()
        ) {
            if (opInfo != null) {
                Surface(
                    color = opInfo.brandColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, opInfo.brandColor.copy(alpha = 0.5f)),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(opInfo.brandColor)
                        )
                        Text(
                            text = "${opInfo.name} · ${opInfo.technology}",
                            color = opInfo.brandColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
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
                    number.length > 8 -> 34.sp
                    else -> 42.sp
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

        Spacer(Modifier.height(20.dp))

        // ── Keypad 3×4 ────────────────────────────────────────────
        val keys = listOf(
            "1" to "", "2" to "ABC", "3" to "DEF",
            "4" to "GHI", "5" to "JKL", "6" to "MNO",
            "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
            "*" to "", "0" to "+", "#" to ""
        )

        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                if (number.length < 16) number += digit
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
                .padding(horizontal = 32.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // PSTN/GSM call — Gold / DINSTAR
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FloatingActionButton(
                    onClick = { if (number.isNotEmpty()) onNavigateToPstnCall(number) },
                    containerColor = if (number.isEmpty()) Color.White.copy(0.1f) else AqyalGold,
                    modifier = Modifier.size(62.dp)
                ) {
                    Icon(Icons.Rounded.PhoneInTalk, "GSM Dinstar", tint = Color.Black, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text("هاتف يمني", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }

            // WebRTC call — Emerald Green (RED VoIP)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FloatingActionButton(
                    onClick = { if (number.isNotEmpty()) onNavigateToWebRtcCall(number) },
                    containerColor = if (number.isEmpty()) Color.White.copy(0.1f) else YounesEmerald,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(Icons.Rounded.Call, "اتصال RED", tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text("مكالمة RED", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            // Clear number
            if (number.isNotEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { number = "" },
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(SurfaceMid)
                    ) {
                        Icon(Icons.Rounded.Close, "مسح", tint = TextSecondary, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("مسح", color = TextSecondary, fontSize = 11.sp)
                }
            } else {
                Spacer(Modifier.size(56.dp))
            }
        }

        Spacer(Modifier.height(16.dp))
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
            .size(70.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFF1E293B), Color(0xFF0F172A))))
            .border(1.dp, Color.White.copy(0.1f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = digit,
                color = TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (letters.isNotEmpty()) {
                Text(
                    text = letters,
                    color = TextSecondary,
                    fontSize = 9.sp,
                    letterSpacing = 1.2.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// ─── Utils ─────────────────────────────────────────────────────────────────

private fun formatPhoneNumber(number: String): String {
    if (number.isEmpty()) return "أدخل الرقم المطلوب..."
    if (number.startsWith("+")) return number
    return when (number.length) {
        in 1..3 -> number
        in 4..6 -> "${number.take(3)} ${number.drop(3)}"
        in 7..9 -> "${number.take(3)} ${number.substring(3, minOf(6, number.length))} ${number.drop(6)}"
        else -> "${number.take(3)} ${number.substring(3, 6)} ${number.drop(6)}"
    }
}
