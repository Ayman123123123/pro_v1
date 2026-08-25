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
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.SovereignColors
import com.red.sovereign.ui.theme.YounesEmerald
import kotlinx.coroutines.delay

/**
 * مراحل اتصال PSTN المعروضة للمستخدم أثناء تأسيس المكالمة.
 *
 * تجعل المسار مرئيًّا بدل «جارٍ الاتصال» المبهمة: تسجيل SIP، ثم جسر
 * الوسائط عبر TURN، ثم INVITE عبر Asterisk، ثم الرنين على شبكة GSM.
 */
enum class ConnectionStage(
    val label: String,
    val description: String,
    val icon: ImageVector,
    val color: Color
) {
    BRIDGING(
        "جارٍ الاتصال بخادم SIP",
        "تأسيس اتصال WebSocket SIP مع Asterisk",
        Icons.Filled.CloudQueue,
        AqyalGold
    ),
    REGISTERING(
        "جارٍ تسجيل حساب SIP",
        "التحقق من بيانات اعتماد SIP",
        Icons.Filled.VerifiedUser,
        AqyalGold
    ),
    INVITING(
        "جارٍ إجراء المكالمة",
        "إرسال SIP INVITE عبر Asterisk",
        Icons.Filled.CallMade,
        AqyalGold
    ),
    TURN_CONNECTING(
        "جارٍ الاتصال بخادم TURN",
        "تأسيس ترحيل الوسائط عبر TURN/STUN",
        Icons.Filled.Router,
        YounesEmerald
    ),
    RINGING(
        "يرنّ",
        "الهاتف يرنّ على الطرف الآخر",
        Icons.Filled.PhoneInTalk,
        AqyalGold
    ),
    CONNECTED(
        "متصل",
        "تأسّس مسار الوسائط — المكالمة نشطة",
        Icons.Filled.Call,
        YounesEmerald
    )
}

/**
 * شاشة مكالمة PSTN الفاخرة بأسلوب Material 3 Expressive.
 *
 * تعرض مراحل الاتصال بوضوح: WebSocket SIP ← TURN ← Asterisk ← GSM،
 * مع مقاييس الجودة الحيّة، والعدّاد اليومي، ولوحة مفاتيح DTMF.
 */
@Composable
fun Material3ExpressivePstnCallScreen(
    status: PstnCallStatus,
    number: String = "",
    metrics: CallMetrics = CallMetrics(),
    onMuteToggle: (Boolean) -> Unit = {},
    onSpeakerToggle: (Boolean) -> Unit = {},
    onKeypadToggle: () -> Unit = {},
    onHoldToggle: (Boolean) -> Unit = {},
    onRecordToggle: (Boolean) -> Unit = {},
    onVideoToggle: (Boolean) -> Unit = {},
    onDtmfDigit: (String) -> Unit = {},
    onHangup: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    var isMuted by remember { mutableStateOf(false) }
    var isSpeaker by remember { mutableStateOf(false) }
    var isHeld by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var isVideoEnabled by remember { mutableStateOf(false) }
    var showKeypad by remember { mutableStateOf(false) }
    var dialedDigits by remember { mutableStateOf("") }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var callStartTime by remember { mutableLongStateOf(0L) }
    var pulseAnimation by remember { mutableFloatStateOf(1f) }

    // المرحلة الحالية مشتقّة من الحالة الواردة — لا حالة موازية تتفرّع
    val currentStage = when (status) {
        PstnCallStatus.REGISTERING -> ConnectionStage.REGISTERING
        PstnCallStatus.BRIDGING -> ConnectionStage.BRIDGING
        PstnCallStatus.INVITING -> ConnectionStage.INVITING
        PstnCallStatus.RINGING -> ConnectionStage.RINGING
        PstnCallStatus.ACTIVE -> ConnectionStage.CONNECTED
        else -> ConnectionStage.BRIDGING
    }

    // نبض بصري أثناء مراحل التأسيس فقط
    LaunchedEffect(currentStage) {
        if (currentStage != ConnectionStage.CONNECTED) {
            while (true) {
                delay(1000)
                pulseAnimation = if (pulseAnimation == 1f) 0.7f else 1f
            }
        } else {
            pulseAnimation = 1f
        }
    }

    LaunchedEffect(status) {
        when (status) {
            PstnCallStatus.ACTIVE -> {
                if (callStartTime == 0L) callStartTime = System.currentTimeMillis()
            }

            PstnCallStatus.IDLE, PstnCallStatus.ENDED -> {
                callStartTime = 0L
                elapsedMs = 0L
            }

            else -> Unit
        }
    }

    LaunchedEffect(status, callStartTime) {
        if (status == PstnCallStatus.ACTIVE && callStartTime > 0) {
            while (true) {
                elapsedMs = System.currentTimeMillis() - callStartTime
                delay(1000)
            }
        }
    }

    LaunchedEffect(status) {
        if (status == PstnCallStatus.IDLE) onBack()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = SovereignColors.ObsidianDeep
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            ConnectionStageHeader(stage = currentStage, pulseAnimation = pulseAnimation)

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .graphicsLayer { scaleX = pulseAnimation; scaleY = pulseAnimation }
                        .clip(CircleShape)
                        .background(currentStage.color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (currentStage != ConnectionStage.CONNECTED) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(60.dp),
                            color = currentStage.color,
                            strokeWidth = 4.dp
                        )
                    } else {
                        Icon(
                            imageVector = currentStage.icon,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = currentStage.color
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // الرقم المطلوب ومشغّله — أهمّ معلومة في شاشة مكالمة
                // هاتفية، وكانت غائبة تماماً قبل الوصل.
                if (number.isNotBlank()) {
                    Text(
                        text = number,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = 1.sp
                    )
                    YemeniOperatorDetector.getOperatorInfo(number)?.let { op ->
                        Text(
                            text = "${op.name} · ${op.technology}",
                            style = MaterialTheme.typography.labelMedium,
                            color = op.brandColor
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Text(
                    text = currentStage.label,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = currentStage.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                if (status == PstnCallStatus.ACTIVE) {
                    Text(
                        text = formatPstnDuration(elapsedMs),
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = 2.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (status == PstnCallStatus.ACTIVE) {
                CallQualityCard(metrics = metrics)
            }

            if (metrics.dailyLimit > 0) {
                DailyLimitCard(metrics = metrics)
            }

            Spacer(modifier = Modifier.height(32.dp))

            if (status != PstnCallStatus.IDLE && status != PstnCallStatus.ENDED) {
                CallControlPanel(
                    isMuted = isMuted,
                    isSpeaker = isSpeaker,
                    isHeld = isHeld,
                    isRecording = isRecording,
                    isVideoEnabled = isVideoEnabled,
                    showKeypad = showKeypad,
                    dialedDigits = dialedDigits,
                    status = status,
                    onMuteToggle = { isMuted = it; onMuteToggle(it) },
                    onSpeakerToggle = { isSpeaker = it; onSpeakerToggle(it) },
                    onHoldToggle = { isHeld = it; onHoldToggle(it) },
                    onRecordToggle = { isRecording = it; onRecordToggle(it) },
                    onVideoToggle = { isVideoEnabled = it; onVideoToggle(it) },
                    onKeypadToggle = { showKeypad = !showKeypad; onKeypadToggle() },
                    onKeypadClose = { showKeypad = false },
                    onDigitPress = { dialedDigits += it; onDtmfDigit(it) },
                    onDigitDelete = {
                        if (dialedDigits.isNotEmpty()) dialedDigits = dialedDigits.dropLast(1)
                    },
                    onHangup = onHangup
                )
            } else {
                Spacer(modifier = Modifier.height(120.dp))
            }
        }
    }
}

@Composable
private fun ConnectionStageHeader(
    stage: ConnectionStage,
    pulseAnimation: Float
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = stage.color.copy(alpha = 0.12f)
            ),
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .graphicsLayer { this.alpha = pulseAnimation },
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = stage.icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = stage.color
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stage.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = stage.color
                )
            }
        }
    }
}

@Composable
private fun CallQualityCard(metrics: CallMetrics) {
    val (quality, qualityColor) = when {
        metrics.jitterMs < 30 && metrics.packetLossPercent < 1f && metrics.roundTripMs < 150 ->
            "ممتازة" to YounesEmerald

        metrics.jitterMs < 50 && metrics.packetLossPercent < 3f && metrics.roundTripMs < 300 ->
            "جيدة" to AqyalGold

        metrics.jitterMs < 80 && metrics.packetLossPercent < 5f ->
            "مقبولة" to Color(0xFFFF9800)

        else ->
            "ضعيفة" to Color(0xFFF44336)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceCard),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.SignalCellular4Bar,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = qualityColor
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "جودة المكالمة: $quality",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = qualityColor
                )
                Text(
                    text = "تذبذب ${"%.0f".format(metrics.jitterMs)}ms  •  " +
                        "فقد ${"%.1f".format(metrics.packetLossPercent)}%  •  " +
                        "ذهاب وإياب ${"%.0f".format(metrics.roundTripMs)}ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DailyLimitCard(metrics: CallMetrics) {
    val progress = (metrics.usedToday.toFloat() / metrics.dailyLimit).coerceIn(0f, 1f)

    Card(
        colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceCard),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "الحد اليومي للمكالمات الهاتفية",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${metrics.usedToday} / ${metrics.dailyLimit}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = AqyalGold,
                trackColor = SovereignColors.ObsidianDeep.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
private fun CallControlPanel(
    isMuted: Boolean,
    isSpeaker: Boolean,
    isHeld: Boolean,
    isRecording: Boolean,
    isVideoEnabled: Boolean,
    showKeypad: Boolean,
    dialedDigits: String,
    status: PstnCallStatus,
    onMuteToggle: (Boolean) -> Unit,
    onSpeakerToggle: (Boolean) -> Unit,
    onHoldToggle: (Boolean) -> Unit,
    onRecordToggle: (Boolean) -> Unit,
    onVideoToggle: (Boolean) -> Unit,
    onKeypadToggle: () -> Unit,
    onKeypadClose: () -> Unit,
    onDigitPress: (String) -> Unit,
    onDigitDelete: () -> Unit,
    onHangup: () -> Unit
) {
    if (showKeypad) {
        KeypadOverlay(
            dialedDigits = dialedDigits,
            onDigitPress = onDigitPress,
            onDigitDelete = onDigitDelete,
            onClose = onKeypadClose
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // الصف الأول: كتم، مكبر، تعليق، تسجيل
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExpressiveCallButton(
                icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                label = if (isMuted) "إلغاء الكتم" else "كتم",
                isActive = isMuted,
                activeColor = Color(0xFFF44336),
                enabled = status == PstnCallStatus.ACTIVE,
                onClick = { onMuteToggle(!isMuted) }
            )

            ExpressiveCallButton(
                icon = if (isSpeaker) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                label = if (isSpeaker) "سماعة الأذن" else "مكبر الصوت",
                isActive = isSpeaker,
                activeColor = AqyalGold,
                enabled = status == PstnCallStatus.ACTIVE,
                onClick = { onSpeakerToggle(!isSpeaker) }
            )

            ExpressiveCallButton(
                icon = Icons.Filled.Pause,
                label = if (isHeld) "استئناف" else "تعليق",
                isActive = isHeld,
                activeColor = Color(0xFFFF9800),
                enabled = status == PstnCallStatus.ACTIVE,
                onClick = { onHoldToggle(!isHeld) }
            )

            ExpressiveCallButton(
                icon = if (isRecording) Icons.Filled.PlayArrow else Icons.Filled.Mic,
                label = if (isRecording) "إيقاف التسجيل" else "تسجيل",
                isActive = isRecording,
                activeColor = Color(0xFFF44336),
                enabled = status == PstnCallStatus.ACTIVE,
                onClick = { onRecordToggle(!isRecording) }
            )
        }

        // الصف الثاني: لوحة المفاتيح، الفيديو، الإنهاء
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExpressiveCallButton(
                icon = Icons.Filled.Keyboard,
                label = if (showKeypad) "إخفاء اللوحة" else "لوحة المفاتيح",
                isActive = showKeypad,
                activeColor = AqyalGold,
                enabled = true,
                onClick = onKeypadToggle
            )

            ExpressiveCallButton(
                icon = if (isVideoEnabled) Icons.Filled.VideocamOff else Icons.Filled.VideoCall,
                label = if (isVideoEnabled) "إيقاف الفيديو" else "فيديو",
                isActive = isVideoEnabled,
                activeColor = YounesEmerald,
                enabled = status == PstnCallStatus.ACTIVE,
                onClick = { onVideoToggle(!isVideoEnabled) }
            )

            ExpressiveCallButton(
                icon = Icons.Filled.CallEnd,
                label = "إنهاء المكالمة",
                isActive = true,
                activeColor = Color(0xFFF44336),
                isDestructive = true,
                enabled = true,
                onClick = onHangup
            )
        }
    }
}

@Composable
private fun ExpressiveCallButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color,
    isDestructive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val bgColor = when {
        isDestructive -> Color(0xFFF44336).copy(alpha = if (enabled) 1f else 0.4f)
        isActive -> activeColor.copy(alpha = if (enabled) 1f else 0.4f)
        else -> SovereignColors.SurfaceCard.copy(alpha = if (enabled) 1f else 0.4f)
    }

    val tintColor = if (isDestructive || isActive) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(bgColor),
            colors = IconButtonDefaults.iconButtonColors(contentColor = tintColor)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(28.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
                .copy(alpha = if (enabled) 1f else 0.3f),
            fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
private fun KeypadOverlay(
    dialedDigits: String,
    onDigitPress: (String) -> Unit,
    onDigitDelete: () -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onClose() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = dialedDigits.ifEmpty { "أدخل نغمات DTMF" },
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("*", "0", "#"),
                    listOf(DELETE_KEY)
                ).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { key ->
                            val isDelete = key == DELETE_KEY
                            Text(
                                text = key,
                                modifier = Modifier
                                    .size(if (isDelete) 72.dp else 64.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isDelete) {
                                            Color(0xFFF44336).copy(alpha = 0.8f)
                                        } else {
                                            Color.White.copy(alpha = 0.15f)
                                        }
                                    )
                                    .clickable {
                                        if (isDelete) onDigitDelete() else onDigitPress(key)
                                    }
                                    .padding(16.dp),
                                fontSize = if (isDelete) 20.sp else 24.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .padding(top = 24.dp)
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "إغلاق لوحة المفاتيح",
                    modifier = Modifier.size(24.dp),
                    tint = Color.White
                )
            }
        }
    }
}

/** مفتاح المسح في لوحة DTMF. */
private const val DELETE_KEY = "\u232B"
