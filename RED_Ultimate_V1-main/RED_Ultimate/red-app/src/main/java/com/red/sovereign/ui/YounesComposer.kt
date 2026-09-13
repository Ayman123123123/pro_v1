package com.red.sovereign.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.ui.theme.PlexArabicFamily
import com.red.sovereign.ui.theme.YounesEmerald
import com.red.sovereign.ui.theme.YounesOnBrand

/**
 * شريط كتابة يونس — نمط واتساب 2026: حقل كبسولة + إرسال أو ميكروفون.
 * لا يخترع API. الإرسال يبقى E2EE عبر [com.red.sovereign.core.ChatComposer].
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun YounesComposer(
    text: String,
    onTextChange: (String) -> Unit,
    sendError: String? = null,
    enabled: Boolean,
    sendEnabled: Boolean,
    enterToSend: Boolean,
    disappearingMs: Long?,
    placeholder: String = "رسالة مشفرة…",
    onClearDisappearing: () -> Unit,
    onSetDisappearing: () -> Unit,
    onToggleEmoji: () -> Unit,
    onStickers: () -> Unit,
    onAttach: () -> Unit,
    onSend: () -> Unit,
    voice: @Composable () -> Unit,
    extra: @Composable () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        extra()
        sendError?.takeIf { it.isNotBlank() }?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ComposerCircleButton(onClick = onAttach, enabled = enabled) {
                Icon(Icons.Default.AttachFile, "إرفاق", tint = MaterialTheme.colorScheme.primary)
            }

            Surface(
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 0.dp,
            ) {
                Row(
                    Modifier.padding(start = 2.dp, end = 6.dp).heightIn(min = 48.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .combinedClickable(
                                enabled = enabled,
                                onClick = onToggleEmoji,
                                onLongClick = onStickers,
                                onClickLabel = "رموز أو اضغط طويلاً للملصقات",
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.EmojiEmotions, "رموز أو اضغط طويلاً للملصقات", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = onTextChange,
                        modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                        enabled = enabled,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = PlexArabicFamily,
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        maxLines = if (enterToSend) 1 else 5,
                        keyboardOptions = KeyboardOptions(
                            imeAction = if (enterToSend) ImeAction.Send else ImeAction.Default,
                        ),
                        keyboardActions = KeyboardActions(onSend = { if (enterToSend && sendEnabled) onSend() }),
                        decorationBox = { inner ->
                            Box {
                                if (text.isEmpty()) {
                                    Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                                }
                                inner()
                            }
                        },
                    )
                    val timerLabel = when (disappearingMs) {
                        3_600_000L -> "1س"
                        86_400_000L -> "24س"
                        604_800_000L -> "7ي"
                        null -> null
                        else -> "⏳"
                    }
                    Box(
                        Modifier
                            .padding(bottom = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .combinedClickable(
                                enabled = enabled,
                                onClick = { if (timerLabel != null) onClearDisappearing() else onSetDisappearing() },
                            )
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (timerLabel != null) {
                            Text("⏳$timerLabel", color = MaterialTheme.colorScheme.tertiary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFamily = PlexArabicFamily)
                        } else {
                            Icon(Icons.Default.History, "رسالة مؤقتة", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            if (sendEnabled) {
                // زر إرسال 48dp زمرد + أيقونة داكنة YounesOnBrand (9.17:1 AAA).
                // تموّج افتراضي + تسمية "إرسال" — ضمن نظام الأزرار الموحّد.
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(YounesEmerald)
                        .combinedClickable(onClick = onSend, onClickLabel = "إرسال"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, "إرسال", tint = YounesOnBrand, modifier = Modifier.size(22.dp))
                }
            } else {
                voice()
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ComposerCircleButton(
    onClick: () -> Unit,
    enabled: Boolean,
    icon: @Composable () -> Unit,
) {
    // 48dp ضمن نطاق pill الموحّد 48–56 (كان 44dp دون حد اللمس المريح).
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(enabled = enabled, onClick = onClick, onClickLabel = "زر الملحقات"),
        contentAlignment = Alignment.Center,
    ) { icon() }
}
