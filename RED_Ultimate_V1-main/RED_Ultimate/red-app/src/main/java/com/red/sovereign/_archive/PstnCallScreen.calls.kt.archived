package com.red.sovereign.calls

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * مراحل مكالمة PSTN كما تصل من الخادم عبر إشارة `PSTN_PROGRESS`.
 *
 * كل مرحلة هنا **تُشتقّ من حدث Asterisk فعلي** يلتقطه
 * `DinstarEventListener` ويوجّهه `PstnCallProgressTracker` إلى صاحب
 * المكالمة وحده:
 *
 * | المرحلة | حدث AMI المُنتِج |
 * |---|---|
 * | [INVITING] | `OriginateResponseEvent` (يربط القناة بالمكالمة) |
 * | [RINGING] | `NewStateEvent` بحالة `Ringing` |
 * | [BRIDGING] | `BridgeEvent` |
 * | [ACTIVE] | `NewStateEvent` بحالة `Up` |
 * | [ENDED] | `HangupEvent` |
 *
 * [REGISTERING] و[ERROR] لا يبثّهما الخادم حالياً: الأولى تخصّ تسجيل
 * البوابة على Asterisk وهو إجراء بدء تشغيل لا يخصّ مكالمة بعينها،
 * والثانية تُعالَج كخطأ REST متزامن من `dialPstn`. تُركتا في التعداد
 * لأن الشاشات تعرضهما بشكل صحيح إن وُجدتا مستقبلاً، ولا يعتمد أي كود
 * على ترتيب العناصر.
 */
enum class PstnCallStatus {
    IDLE,
    REGISTERING,
    BRIDGING,
    INVITING,
    RINGING,
    ACTIVE,
    ENDED,
    ERROR
}

/**
 * مقاييس جودة المكالمة الحيّة والعدّاد اليومي.
 *
 * `jitterMs` و`roundTripMs` بالمللي ثانية، و`packetLossPercent` نسبة
 * مئوية. تُستخدم لتصنيف الجودة المعروض للمستخدم.
 */
data class CallMetrics(
    val jitterMs: Float = 0f,
    val packetLossPercent: Float = 0f,
    val roundTripMs: Float = 0f,
    val errors: List<String> = emptyList(),
    val dailyLimit: Int = 1000,
    val usedToday: Int = 0
)

/** تنسيق مدّة المكالمة بصيغة دقائق:ثوانٍ. */
internal fun formatPstnDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

private fun statusLabel(status: PstnCallStatus): String = when (status) {
    PstnCallStatus.IDLE -> ""
    PstnCallStatus.REGISTERING -> "جارٍ تسجيل الحساب..."
    PstnCallStatus.BRIDGING -> "جارٍ الاتصال..."
    PstnCallStatus.INVITING -> "جارٍ طلب الرقم..."
    PstnCallStatus.RINGING -> "يرنّ..."
    PstnCallStatus.ACTIVE -> "متصل"
    PstnCallStatus.ENDED -> "انتهت المكالمة"
    PstnCallStatus.ERROR -> "خطأ في الاتصال"
}

private fun statusColor(status: PstnCallStatus): Color = when (status) {
    PstnCallStatus.ACTIVE -> Color(0xFF4CAF50)
    PstnCallStatus.RINGING -> Color(0xFFFFC107)
    PstnCallStatus.ERROR -> Color(0xFFF44336)
    PstnCallStatus.ENDED -> Color(0xFF9E9E9E)
    else -> Color(0xFF2196F3)
}

private fun qualityLabel(metrics: CallMetrics): String {
    val score = when {
        metrics.jitterMs < 30 && metrics.packetLossPercent < 1f && metrics.roundTripMs < 150 -> "ممتازة"
        metrics.jitterMs < 50 && metrics.packetLossPercent < 3f && metrics.roundTripMs < 300 -> "جيدة"
        metrics.jitterMs < 80 && metrics.packetLossPercent < 5f -> "مقبولة"
        else -> "ضعيفة"
    }
    return "$score  •  تذبذب ${"%.0f".format(metrics.jitterMs)}ms  •  فقد ${"%.1f".format(metrics.packetLossPercent)}%"
}

private fun PstnCallStatus.symbol(): String = when (this) {
    PstnCallStatus.RINGING -> "\u23F3"
    PstnCallStatus.ACTIVE -> "\u260E"
    PstnCallStatus.ERROR -> "\u26A0"
    PstnCallStatus.ENDED -> "\u2716"
    PstnCallStatus.IDLE -> ""
    PstnCallStatus.BRIDGING, PstnCallStatus.INVITING, PstnCallStatus.REGISTERING -> "\u25CB"
}

/**
 * شاشة مكالمة PSTN الأساسية — عرض مبسّط يعتمد ألوان الثيم النشط.
 *
 * للنسخة الفاخرة ذات مراحل الاتصال ولوحة المفاتيح انظر
 * [Material3ExpressivePstnCallScreen] في `CallScreen.kt`.
 */
@Composable
fun PstnCallScreen(
    status: PstnCallStatus,
    metrics: CallMetrics = CallMetrics(),
    onMuteToggle: (Boolean) -> Unit = {},
    onSpeakerToggle: (Boolean) -> Unit = {},
    onHangup: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    var isMuted by remember { mutableStateOf(false) }
    var isSpeaker by remember { mutableStateOf(false) }
    var elapsedMs by remember { mutableLongStateOf(0L) }
    var callStartTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(status) {
        when (status) {
            PstnCallStatus.ACTIVE -> {
                if (callStartTime == 0L) {
                    callStartTime = System.currentTimeMillis()
                }
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
                delay(1000L)
            }
        }
    }

    LaunchedEffect(status) {
        if (status == PstnCallStatus.IDLE) {
            onBack()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(statusColor(status).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    val connecting = status == PstnCallStatus.BRIDGING ||
                        status == PstnCallStatus.INVITING ||
                        status == PstnCallStatus.REGISTERING
                    if (connecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = statusColor(status),
                            strokeWidth = 3.dp
                        )
                    } else {
                        Text(text = status.symbol(), fontSize = 36.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = statusLabel(status),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                if (status == PstnCallStatus.ACTIVE) {
                    Text(
                        text = formatPstnDuration(elapsedMs),
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = 2.sp
                    )
                }

                if (status == PstnCallStatus.ERROR) {
                    Text(
                        text = metrics.errors.firstOrNull() ?: "خطأ غير معروف",
                        style = MaterialTheme.typography.bodyMedium,
                        color = statusColor(status),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (status == PstnCallStatus.ACTIVE) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = qualityLabel(metrics),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    if (metrics.dailyLimit > 0) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "اليوم: ${metrics.usedToday} / ${metrics.dailyLimit}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            LinearProgressIndicator(
                                progress = {
                                    (metrics.usedToday.toFloat() / metrics.dailyLimit)
                                        .coerceIn(0f, 1f)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            if (status != PstnCallStatus.IDLE && status != PstnCallStatus.ENDED) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CallButton(
                        icon = if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                        label = if (isMuted) "إلغاء الكتم" else "كتم",
                        tint = if (isMuted) Color(0xFFF44336) else MaterialTheme.colorScheme.onSurface,
                        enabled = status == PstnCallStatus.ACTIVE
                    ) {
                        isMuted = !isMuted
                        onMuteToggle(isMuted)
                    }

                    CallButton(
                        icon = Icons.Filled.VolumeUp,
                        label = if (isSpeaker) "سماعة الأذن" else "مكبر الصوت",
                        tint = if (isSpeaker) Color(0xFF2196F3) else MaterialTheme.colorScheme.onSurface,
                        enabled = status == PstnCallStatus.ACTIVE
                    ) {
                        isSpeaker = !isSpeaker
                        onSpeakerToggle(isSpeaker)
                    }

                    CallButton(
                        icon = Icons.Filled.CallEnd,
                        label = "إنهاء",
                        tint = Color.White,
                        enabled = status != PstnCallStatus.ENDED,
                        backgroundColor = Color(0xFFF44336)
                    ) {
                        onHangup()
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun CallButton(
    icon: ImageVector,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(
                    if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.4f)
                ),
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = tint.copy(alpha = if (enabled) 1f else 0.4f)
            )
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
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.7f else 0.3f)
        )
    }
}
