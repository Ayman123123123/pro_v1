package com.red.sovereign.features.pstn

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── Colors ────────────────────────────────────────────────────────────────
private val BgDark = Color(0xFF0D0D0D)
private val SurfaceMid = Color(0xFF242424)
private val OrangeGsm = Color(0xFFF57C00)
private val GreenCall = Color(0xFF43A047)
private val BlueWebRTC = Color(0xFF1976D2)
private val PurpleWebRTC = Color(0xFF7B1FA2)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFF9E9E9E)
private val TextHint = Color(0xFF616161)

// ─── Port selection mode (Task 3) ──────────────────────────────────────────

/** وضع اختيار المنفذ: تلقائي (أفضل إشارة) أو يدوي. */
enum class PortSelectionMode(val label: String, val subtitle: String, val icon: ImageVector) {
    SMART("ذكي", "أفضل إشارة", Icons.Rounded.AutoFixHigh),
    MANUAL("يدوي", "اختر المنفذ", Icons.Rounded.SettingsInputComponent)
}

// ─── Call route (Task 4: WebRTC + GSM smart fallback) ───────────────────────

/** مسار الاتصال: تلقائي (يقرر حسب الرقم) أو يدوي. */
enum class CallRoute(val label: String, val icon: ImageVector) {
    AUTO("تلقائي", Icons.Rounded.AutoFixHigh),
    WEBRTC("RED", Icons.Rounded.WifiCalling),
    GSM("GSM", Icons.Rounded.SignalCellularAlt)
}

/** حالة الاتصال المعروضة في الـ UI (Task 4). */
enum class DialCallStatus { IDLE, RINGING, CONNECTED, FAILED }

// ─── Port info (Task 3: Manual + smart port selection) ─────────────────────

/**
 * معلومات منفذ Dinstar للعرض في منتقي المنافذ.
 * تُملأ من `GET /api/pstn/ports/status` أو WebSocket.
 */
data class PstnPortInfo(
    val index: Int,
    val operator: String = "",
    val signalDbm: Int = -120,
    val signalGrade: String = "",
    val signalUsable: Boolean = false,
    val signalRaw: Int = 99,
    val gatewayHost: String = "",
    val isBusy: Boolean = false
) {
    /** التسمية العربية للإشارة — محسوبة من dBm (لا تُمرر في الباني). */
    val signalLabel: String
        get() = when {
            signalDbm >= -70 -> "ممتاز"
            signalDbm >= -85 -> "جيد"
            signalDbm >= -100 -> "ضعيف"
            else -> "معدوم"
        }
}

// ─── DialPadScreen ─────────────────────────────────────────────────────────

/**
 * لوحة أرقام احترافية كاملة.
 * تدعم:
 * - مكالمة WebRTC (أخضر/بنفسجي) + مكالمة PSTN/GSM (برتقالي) مع Smart Fallback
 * - اختيار منفذ ذكي/يدوي (تلقائي أفضل إشارة + قائمة المنافذ مع الحالة)
 * - اكتشاف المشغل اليمني تلقائياً (أيقونة + اسم عربي، تحديث فوري من 3 أرقام)
 * - حالة الاتصال RINGING/CONNECTED/FAILED مع سبب الفشل
 * - DTMF haptic feedback + سجل المكالمات الأخيرة مع أيقونة المزود
 */
@Composable
fun DialPadScreen(
    onNavigateToWebRtcCall: (String) -> Unit,
    onNavigateToPstnCall: (String, Int?, PortSelectionMode) -> Unit,
    recentNumbers: List<String> = emptyList(),
    availablePorts: List<PstnPortInfo> = emptyList(),
    selectedPort: Int? = null,
    selectedGatewayHost: String? = null,
    portSelectionMode: PortSelectionMode = PortSelectionMode.SMART,
    callRoute: CallRoute = CallRoute.AUTO,
    dialStatus: DialCallStatus = DialCallStatus.IDLE,
    dialError: String? = null,
    onPortSelectionChange: (PortSelectionMode) -> Unit = {},
    onPortChange: (Int?) -> Unit = {},
    onGatewayChange: (String?) -> Unit = {},
    onRouteChange: (CallRoute) -> Unit = {}
) {
    var number by remember { mutableStateOf("") }
    var showPortMenu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    // Task 5: تحديث فوري أثناء الكتابة — المطابقة الثلاثية تعمل من 3 أرقام
    val opInfo = YemeniOperatorDetector.getOperatorInfo(number)
    val showOperator = number.length >= 3 && opInfo.key != "Unknown"
    val isMobileNumber = YemeniOperatorDetector.isMobileNumber(number)
    val normalizedLen = YemeniOperatorDetector.normalize(number).length
    val validMobile = normalizedLen == 9 && YemeniOperatorDetector.isValidYemeniMobile(number)

    // Task 4: القرار الذكي للمسار — يمني محمول → GSM عبر Dinstar، غيره → WebRTC
    val smartRoute = if (isMobileNumber) CallRoute.GSM else CallRoute.WEBRTC
    val effectiveRoute = if (callRoute == CallRoute.AUTO) smartRoute else callRoute
    val selectedPortInfo = availablePorts.firstOrNull {
        it.index == selectedPort && (selectedGatewayHost == null || it.gatewayHost == selectedGatewayHost)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Header ─────────────────────────────────────────────────
        Spacer(Modifier.height(32.dp))

        // Task 5: شارة المزود — دائرة ملونة بالحرف الأول + الاسم العربي + التقنية
        AnimatedVisibility(
            visible = showOperator,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically()
        ) {
            Surface(
                color = opInfo.brandColor.copy(alpha = 0.15f),
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
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(opInfo.brandColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = opInfo.iconLetter.ifBlank { "?" },
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = opInfo.name,
                        color = opInfo.brandColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (opInfo.technology.isNotBlank()) {
                        Text(
                            text = opInfo.technology,
                            color = opInfo.brandColor.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
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

        // Task 4: حالة الاتصال + سبب الفشل
        AnimatedVisibility(visible = dialStatus != DialCallStatus.IDLE) {
            val (msg, col) = when (dialStatus) {
                DialCallStatus.RINGING -> "يرن..." to BlueWebRTC
                DialCallStatus.CONNECTED -> "متصل" to GreenCall
                DialCallStatus.FAILED -> (dialError ?: "فشل الاتصال") to Color(0xFFE53935)
                DialCallStatus.IDLE -> "" to TextSecondary
            }
            if (dialStatus != DialCallStatus.IDLE) {
                Surface(
                    color = col.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        msg, color = col, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Task 4: مبدّل المسار اليدوي GSM ↔ WebRTC
        if (number.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                CallRoute.values().forEach { route ->
                    val isSelected = callRoute == route
                    TextButton(
                        onClick = { onRouteChange(route) },
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = if (isSelected) PurpleWebRTC.copy(alpha = 0.22f) else Color.Transparent,
                            contentColor = if (isSelected) Color.White else TextSecondary
                        )
                    ) {
                        Icon(route.icon, null, tint = if (isSelected) PurpleWebRTC else TextSecondary, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            when (route) {
                                CallRoute.AUTO -> "تلقائي (${if (smartRoute == CallRoute.GSM) "GSM" else "RED"})"
                                CallRoute.WEBRTC -> "RED"
                                CallRoute.GSM -> "GSM"
                            },
                            style = TextStyle(fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
                        )
                    }
                }
            }
        }

        // Task 3: شريط اختيار المنفذ (يدوي/ذكي + حالة كل منفذ)
        if (isMobileNumber && (availablePorts.isNotEmpty() || portSelectionMode == PortSelectionMode.MANUAL)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Icon(
                    Icons.Rounded.SettingsInputComponent, null,
                    tint = if (portSelectionMode == PortSelectionMode.MANUAL) PurpleWebRTC else TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    if (portSelectionMode == PortSelectionMode.SMART) "تلقائي (أفضل إشارة)"
                    else selectedPortInfo?.let { "منفذ ${it.index + 1} (${it.signalLabel})" } ?: "يدوي — اختر المنفذ",
                    color = TextSecondary, fontSize = 12.sp
                )
                TextButton(onClick = {
                    if (portSelectionMode == PortSelectionMode.SMART) {
                        onPortSelectionChange(PortSelectionMode.MANUAL)
                        showPortMenu = true
                    } else showPortMenu = true
                }) {
                    Text("تغيير", fontSize = 12.sp, color = PurpleWebRTC)
                }
                if (portSelectionMode == PortSelectionMode.MANUAL) {
                    TextButton(onClick = {
                        onPortSelectionChange(PortSelectionMode.SMART)
                        onPortChange(null)
                    }) {
                        Text("تلقائي", fontSize = 12.sp, color = TextSecondary)
                    }
                }
            }
            DropdownMenu(
                expanded = showPortMenu,
                onDismissRequest = { showPortMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("تلقائي (أفضل إشارة)", style = TextStyle(color = TextPrimary, fontSize = 14.sp)) },
                    onClick = {
                        onPortSelectionChange(PortSelectionMode.SMART)
                        onPortChange(null)
                        onGatewayChange(null)
                        showPortMenu = false
                    }
                )
                availablePorts.forEach { port ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.SignalCellularAlt, null,
                                    tint = when {
                                        port.isBusy -> Color(0xFFE53935)
                                        port.signalUsable -> GreenCall
                                        else -> OrangeGsm
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        "المنفذ ${port.index + 1} (${port.signalLabel})" +
                                            (if (port.isBusy) " — مشغول" else "") +
                                            (if (port.gatewayHost.isNotBlank()) " • ${port.gatewayHost}" else ""),
                                        style = TextStyle(color = TextPrimary, fontSize = 14.sp)
                                    )
                                    Text(
                                        "${port.operator} • ${port.signalDbm}dBm ${port.signalGrade}",
                                        style = TextStyle(fontSize = 10.sp, color = TextSecondary)
                                    )
                                }
                            }
                        },
                        enabled = !port.isBusy,
                        onClick = {
                            onPortSelectionChange(PortSelectionMode.MANUAL)
                            onPortChange(port.index)
                            if (port.gatewayHost.isNotBlank()) onGatewayChange(port.gatewayHost)
                            showPortMenu = false
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("إغلاق", style = TextStyle(color = Color(0xFFE53935), fontSize = 14.sp, fontWeight = FontWeight.Bold)) },
                    onClick = { showPortMenu = false }
                )
            }
        }

        Spacer(Modifier.height(24.dp))

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
            // زر الاتصال الرئيسي — يقرر المسار تلقائياً (Task 4 Smart Fallback)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FloatingActionButton(
                    onClick = {
                        if (number.isEmpty()) return@FloatingActionButton
                        if (effectiveRoute == CallRoute.GSM) {
                            val port = if (portSelectionMode == PortSelectionMode.MANUAL) selectedPort else null
                            onNavigateToPstnCall(number, port, portSelectionMode)
                        } else {
                            onNavigateToWebRtcCall(number)
                        }
                    },
                    containerColor = if (number.isEmpty()) TextHint
                    else if (effectiveRoute == CallRoute.GSM) OrangeGsm else GreenCall,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        if (effectiveRoute == CallRoute.GSM) Icons.Rounded.SignalCellularAlt else Icons.Rounded.Call,
                        if (effectiveRoute == CallRoute.GSM) "اتصال GSM" else "اتصال RED",
                        tint = Color.White, modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (effectiveRoute == CallRoute.GSM) {
                        if (portSelectionMode == PortSelectionMode.MANUAL && selectedPort != null) "GSM • منفذ ${selectedPort + 1}"
                        else "GSM"
                    } else "RED",
                    color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
            }

            // زر المسار البديل (fallback اليدوي)
            if (number.isNotEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FloatingActionButton(
                        onClick = {
                            if (effectiveRoute == CallRoute.GSM) onNavigateToWebRtcCall(number)
                            else {
                                val port = if (portSelectionMode == PortSelectionMode.MANUAL) selectedPort else null
                                onNavigateToPstnCall(number, port, portSelectionMode)
                            }
                        },
                        containerColor = Color(0xFF242424),
                        modifier = Modifier.size(60.dp)
                    ) {
                        Icon(
                            Icons.Rounded.SwapHoriz,
                            "تبديل المسار",
                            tint = if (effectiveRoute == CallRoute.GSM) GreenCall else OrangeGsm,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (effectiveRoute == CallRoute.GSM) "RED بديل" else "GSM بديل",
                        color = TextSecondary, fontSize = 11.sp
                    )
                }
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

        // ── Recent Calls (Task 5: أيقونة المزود لكل رقم) ──────────
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

@OptIn(ExperimentalFoundationApi::class)
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
            .combinedClickable(onClick = onClick, onLongClick = { onLongClick() }),
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
                .background(operatorInfo.brandColor.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                operatorInfo.iconLetter.ifBlank { "?" },
                color = operatorInfo.brandColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
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
