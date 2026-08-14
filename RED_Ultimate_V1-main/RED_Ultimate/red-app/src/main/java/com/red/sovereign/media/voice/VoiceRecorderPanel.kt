package com.red.sovereign.media.voice

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border as foundationBorder
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.media.VoiceMessageState
import com.red.sovereign.media.voice.VoiceColors

/**
 * 🎙️ YOUNES Sovereign — Voice Recorder Panel
 * لوحة تسجيل/معاينة احترافية تظهر فوق شريط الكتابة
 */
@Composable
fun VoiceRecorderPanel(
    state: VoiceMessageState,
    elapsedSeconds: Int,
    waveform: List<Int>,
    isLocked: Boolean,
    cancelProgress: Float,
    hasPermission: Boolean,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onLockRequest: () -> Unit,
    onCancel: () -> Unit,
    onUpdateCancelProgress: (Float) -> Unit,
    onStopAndPreview: () -> Unit,
    onSend: () -> Unit,
    onDiscard: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = state !is VoiceMessageState.Idle,
        enter = slideInVertically(animationSpec = tween(300)) { it } + fadeIn(),
        exit = slideOutVertically(animationSpec = tween(300)) { it } + fadeOut(),
        modifier = modifier
    ) {
        when (val s = state) {
            is VoiceMessageState.Recording -> RecordingPanel(
                paused = s.paused,
                elapsedSeconds = elapsedSeconds,
                waveform = waveform,
                isLocked = isLocked,
                cancelProgress = cancelProgress,
                onPress = onPress,
                onRelease = onRelease,
                onLockRequest = onLockRequest,
                onCancel = onCancel,
                onUpdateCancelProgress = onUpdateCancelProgress,
                onClick = onClick,
                onStopAndPreview = onStopAndPreview
            )
            is VoiceMessageState.Preview -> PreviewPanel(
                duration = s.durationSeconds,
                onSend = onSend,
                onDiscard = onDiscard
            )
            VoiceMessageState.Sending -> SendingPanel()
            is VoiceMessageState.Sent -> SentPanel(duration = s.durationSeconds)
            is VoiceMessageState.Error -> ErrorPanel(message = s.message)
            VoiceMessageState.Idle -> Unit
        }
    }
}

@Composable
private fun RecordingPanel(
    paused: Boolean,
    elapsedSeconds: Int,
    waveform: List<Int>,
    isLocked: Boolean,
    cancelProgress: Float,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onLockRequest: () -> Unit,
    onCancel: () -> Unit,
    onUpdateCancelProgress: (Float) -> Unit,
    onClick: () -> Unit,
    onStopAndPreview: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .shadow(8.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isLocked) {
                    Brush.linearGradient(
                        colors = listOf(
                            VoiceColors.LockGoldDark.copy(alpha = 0.95f),
                            VoiceColors.LockGold.copy(alpha = 0.95f)
                        )
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF1A2F4A),
                            Color(0xFF0A1628)
                        )
                    )
                }
            )
            .padding(12.dp)
    ) {
        // Top row: timer + status + actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VoiceTimerDisplay(
                seconds = elapsedSeconds,
                isActive = !paused,
                isPaused = paused,
                color = if (isLocked) Color.White else VoiceColors.RecordingRed
            )

            Spacer(Modifier.weight(1f))

            if (isLocked) {
                VoiceLockIndicator()
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "إلغاء",
                            tint = VoiceColors.CancelRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onLockRequest,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "قفل",
                            tint = VoiceColors.LockGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Waveform
        VoiceWaveformCanvas(
            samples = waveform,
            color = if (isLocked) VoiceColors.WaveformLocked else VoiceColors.WaveformActive,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            isActive = !paused
        )

        // Cancel progress
        if (cancelProgress > 0f) {
            Spacer(Modifier.height(8.dp))
            VoiceCancelProgressBar(progress = cancelProgress)
        }

        // Hint text
        Spacer(Modifier.height(8.dp))
        Text(
            text = when {
                isLocked -> "🎙️ التسجيل مُقفل • اضغط إرسال للمعاينة أو حذف للإلغاء"
                cancelProgress > 0.1f -> "↩️ استمر في السحب لليسار/الأسفل للإلغاء"
                paused -> "⏸ متوقف مؤقتًا • اضغط ▶ للمتابعة"
                else -> "🎙️ اضغط مطوّلاً للتسجيل • حرر للمعاينة • اسحب للأعلى للقفل"
            },
            color = if (isLocked) Color.White else Color.White.copy(alpha = 0.7f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.fillMaxWidth()
        )

        // Stop & Preview button (when locked)
        if (isLocked) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onStopAndPreview,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = VoiceColors.LockGoldDark
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Stop,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("إيقاف والمعاينة", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PreviewPanel(
    duration: Int,
    onSend: () -> Unit,
    onDiscard: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        VoiceColors.SuccessEmerald.copy(alpha = 0.15f),
                        VoiceColors.PlayedEmerald.copy(alpha = 0.10f)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = VoiceColors.SuccessEmerald.copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayCircle,
                contentDescription = null,
                tint = VoiceColors.SuccessEmerald,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "معاينة الرسالة الصوتية",
                    color = VoiceColors.SuccessEmerald,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "المدة: ${formatDuration(duration)}",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        VoicePreviewActions(
            isSending = false,
            onSend = onSend,
            onDiscard = onDiscard
        )
    }
}

@Composable
private fun SendingPanel() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(VoiceColors.LockGold.copy(alpha = 0.15f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            color = VoiceColors.LockGold,
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "جارٍ تشفير الرسالة الصوتية ورفعها…",
            color = VoiceColors.LockGold,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SentPanel(duration: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(VoiceColors.SuccessEmerald.copy(alpha = 0.2f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.Check,
            contentDescription = null,
            tint = VoiceColors.SuccessEmerald,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "✅ تم إرسال رسالة صوتية • ${formatDuration(duration)}",
            color = VoiceColors.SuccessEmerald,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ErrorPanel(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(VoiceColors.CancelRed.copy(alpha = 0.2f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.Error,
            contentDescription = null,
            tint = VoiceColors.CancelRed,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = localizeVoiceError(message),
            color = VoiceColors.CancelRed,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * تحويل رسائل خطأ التسجيل الصوتي إلى نص عربي واضح.
 *
 * كانت الواجهة تعرض رسائل خام بالإنجليزية (مثل MICROPHONE_PERMISSION_REQUIRED
 * أو رسالة استثناء MediaRecorder) فتبدو «خطأ» بلا تفسير للمستخدم.
 */
private fun localizeVoiceError(message: String): String {
    val m = message.uppercase()
    return when {
        m.contains("MICROPHONE_PERMISSION_REQUIRED") || m.contains("PERMISSION") ->
            "تحتاج إلى منح إذن الميكروفون. فعّل إذن الميكروفون من إعدادات التطبيق ثم أعد المحاولة."
        m.contains("VOICE_RECORDER_START_FAILED") || m.contains("prepare") || m.contains("start failed") || m.contains("MEDIARECORDER") ->
            "تعذر بدء التسجيل — قد يكون الميكروفون قيد الاستخدام من تطبيق آخر. أغلق أي مكالمة/تسجيل آخر ثم أعد المحاولة."
        m.contains("VOICE_TOO_SHORT") ->
            "التسجيل قصير جدًا — اضغط مطوّلاً وسجّل ثانية واحدة على الأقل."
        m.contains("VOICE_ENCRYPTION_FAILED") || m.contains("ENCRYPT") ->
            "تعذر تشفير الرسالة الصوتية. أعد المحاولة."
        m.contains("UPLOAD") || m.contains("NETWORK") ->
            "تعذر رفع الرسالة الصوتية — تأكد من اتصالك بالخادم ثم أعد المحاولة."
        else -> "تعذر التسجيل. تأكد من إذن الميكروفون ومن اتصالك بالخادم ثم أعد المحاولة."
    }
}

private fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(minutes, secs)
}

private fun androidx.compose.ui.Modifier.border(
    width: androidx.compose.ui.unit.Dp,
    color: androidx.compose.ui.graphics.Color,
    shape: androidx.compose.ui.graphics.Shape
) = this.then(
    this.foundationBorder(width, color, shape)
)
