package com.red.sovereign.features.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.media.VoiceMessageState
import com.red.sovereign.media.VoiceMessageViewModel
import com.red.sovereign.media.voice.VoiceRecorderPanel
import com.red.sovereign.media.voice.VoiceRecordButton
import com.red.sovereign.ui.theme.*

/**
 * 💬 YOUNES Sovereign — Smart Chat Input Bar
 * شريط إدخال الرسائل الذكي التفاعلي المستوحى من أفضل تجارب تلجرام وواتساب
 */
@Composable
fun SovereignChatInputBar(
    messageText: String,
    onMessageChange: (String) -> Unit,
    onSend: () -> Unit,
    replyPreviewText: String? = null,
    editingPreviewText: String? = null,
    onCancelReplyOrEdit: () -> Unit = {},
    disappearingMs: Long? = null,
    onToggleDisappearing: () -> Unit = {},
    onToggleEmoji: () -> Unit = {},
    onToggleAttachments: () -> Unit = {},
    voiceState: VoiceMessageState,
    voiceMessages: VoiceMessageViewModel,
    hasRecordPermission: Boolean,
    onVoicePress: () -> Unit = {},
    onVoiceRelease: () -> Unit = {},
    onVoiceClick: () -> Unit = {},
    onStopAndPreview: () -> Unit = onVoiceClick,
    placeholderText: String = "رسالة مشفرة…",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        // 📌 1. شريط المعاينة عند الرد أو التعديل (Reply / Edit Banner)
        val hasPreview = replyPreviewText != null || editingPreviewText != null
        AnimatedVisibility(
            visible = hasPreview,
            enter = slideInVertically(animationSpec = tween(200)) { it } + fadeIn(),
            exit = slideOutVertically(animationSpec = tween(200)) { it } + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(32.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (editingPreviewText != null) "تعديل الرسالة" else "رد على رسالة",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = editingPreviewText ?: replyPreviewText.orEmpty(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onCancelReplyOrEdit,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "إلغاء",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // 🎙️ 2. لوحة التسجيل الصوتي المتطورة (تظهر فقط أثناء التسجيل أو المعاينة)
        VoiceRecorderPanel(
            state = voiceState,
            elapsedSeconds = voiceMessages.elapsedSeconds,
            waveform = voiceMessages.waveform,
            isLocked = voiceMessages.isLocked,
            cancelProgress = voiceMessages.cancelProgress,
            hasPermission = hasRecordPermission,
            onPress = onVoicePress,
            onRelease = onVoiceRelease,
            onLockRequest = { voiceMessages.lockRecording() },
            onCancel = { voiceMessages.cancel() },
            onUpdateCancelProgress = { voiceMessages.updateCancelProgress(it) },
            onStopAndPreview = onStopAndPreview,
            onSend = { if (voiceState is VoiceMessageState.Preview) onVoiceClick() else onSend() },
            onDiscard = { voiceMessages.discardPreview() },
            onClick = onVoiceClick
        )

        // 💬 3. شريط الكتابة العصري وزر الإجراء الذكي (Smart Dock & Action Button)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // كبسولة الإدخال الرئيسية (Main Input Capsule)
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(26.dp)),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // زر الرموز التعبيرية (Emoji)
                    IconButton(
                        onClick = onToggleEmoji,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEmotions,
                            contentDescription = "الرموز التعبيرية",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // زر المرفقات (+)
                    IconButton(
                        onClick = onToggleAttachments,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "إرفاق",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // حقل كتابة النص
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (messageText.isEmpty()) {
                            Text(
                                text = placeholderText,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontSize = 15.sp,
                                fontFamily = TajawalFamily
                            )
                        }
                        BasicTextField(
                            value = messageText,
                            onValueChange = onMessageChange,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp,
                                fontFamily = TajawalFamily
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            maxLines = 5,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                        )
                    }

                    // مؤشر الرسالة المؤقتة (Disappearing Message Chip/Icon)
                    if (disappearingMs != null) {
                        val label = when (disappearingMs) {
                            3600000L -> "1س"
                            86400000L -> "24س"
                            604800000L -> "7ي"
                            7776000000L -> "90ي"
                            else -> "⏳"
                        }
                        AssistChip(
                            onClick = onToggleDisappearing,
                            label = { Text("⏳ $label", fontSize = 11.sp, color = AqyalGold) },
                            modifier = Modifier.height(28.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = AqyalGold.copy(alpha = 0.18f)
                            )
                        )
                    } else {
                        IconButton(
                            onClick = onToggleDisappearing,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "رسالة مؤقتة",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // 🚀 / 🎙️ زر الإجراء التفاعلي الذكي (Smart Action Button)
            AnimatedContent(
                targetState = messageText.isNotBlank(),
                transitionSpec = {
                    (scaleIn(animationSpec = tween(200)) + fadeIn(animationSpec = tween(200)))
                        .togetherWith(scaleOut(animationSpec = tween(200)) + fadeOut(animationSpec = tween(200)))
                },
                label = "ActionButtonTransition"
            ) { hasText ->
                if (hasText) {
                    // 🚀 زر الإرسال المتوهج
                    FilledIconButton(
                        onClick = onSend,
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "إرسال",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    // 🎙️ زر التسجيل الصوتي
                    VoiceRecordButton(
                        state = voiceState,
                        isLocked = voiceMessages.isLocked,
                        hasPermission = hasRecordPermission && voiceState !is VoiceMessageState.Sending,
                        onPress = onVoicePress,
                        onRelease = onVoiceRelease,
                        onLockRequest = { voiceMessages.lockRecording() },
                        onCancel = { voiceMessages.cancel() },
                        onUpdateCancelProgress = { voiceMessages.updateCancelProgress(it) },
                        onClick = onVoiceClick
                    )
                }
            }
        }
    }
}
