package com.red.sovereign.features.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.ui.theme.*

// ════════════════════════════════════════════════════════════
//  RED MESSAGE INPUT — حقل إدخال الرسائل المتطور
// ════════════════════════════════════════════════════════════

@Composable
fun RedMessageInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    onRecordVoice: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAttachMenu  by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var isRecording     by remember { mutableStateOf(false) }
    val hasText = text.isNotBlank()

    val sendScale by animateFloatAsState(targetValue = if (hasText) 1f else 0f, animationSpec = spring(Spring.DampingRatioMediumBouncy))
    val micScale  by animateFloatAsState(targetValue = if (!hasText) 1f else 0f, animationSpec = spring(Spring.DampingRatioMediumBouncy))

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceDark)
    ) {
        // Attach menu
        AnimatedVisibility(visible = showAttachMenu) {
            AttachmentMenu(onDismiss = { showAttachMenu = false })
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Attach / Emoji toggle
            IconButton(
                onClick  = { showAttachMenu = !showAttachMenu; showEmojiPicker = false },
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = if (showAttachMenu) Icons.Rounded.Close else Icons.Rounded.AttachFile,
                    contentDescription = "إرفاق",
                    tint   = if (showAttachMenu) RedPrimary else TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Text field
            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 46.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(SurfaceMid)
                    .border(1.dp, if (text.isNotEmpty()) RedPrimary.copy(alpha = 0.3f) else Color.Transparent, RoundedCornerShape(24.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Emoji icon
                    Icon(
                        Icons.Rounded.EmojiEmotions, "إيموجي",
                        tint     = if (showEmojiPicker) GoldenAccent else TextTertiary,
                        modifier = Modifier.size(20.dp).clickable { showEmojiPicker = !showEmojiPicker }
                    )
                    Spacer(Modifier.width(8.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        if (text.isEmpty()) {
                            Text("اكتب رسالة...", color = TextTertiary, fontSize = 15.sp)
                        }
                        BasicTextField(
                            value         = text,
                            onValueChange = onTextChange,
                            textStyle     = TextStyle(color = TextPrimary, fontSize = 15.sp, lineHeight = 21.sp),
                            cursorBrush   = SolidColor(RedPrimary),
                            maxLines      = 6,
                            modifier      = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Send / Record button
            Box(modifier = Modifier.size(46.dp)) {
                // Record button
                AnimatedVisibility(
                    visible = !hasText,
                    enter   = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
                    exit    = scaleOut() + fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(if (isRecording) RedPrimary else SurfaceMid)
                            .clickable {
                                isRecording = !isRecording
                                onRecordVoice()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isRecording) {
                            RecordingPulse()
                        }
                        Icon(
                            imageVector = if (isRecording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                            null,
                            tint   = if (isRecording) Color.White else TextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Send button
                AnimatedVisibility(
                    visible = hasText,
                    enter   = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
                    exit    = scaleOut() + fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(GradientRedPrimary))
                            .clickable(onClick = onSend),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Send,
                            null,
                            tint   = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
//  RECORDING PULSE ANIMATION
// ════════════════════════════════════════════════════════════

@Composable
private fun RecordingPulse() {
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse)
    )
    Box(
        modifier = Modifier
            .size(46.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(RedPrimary.copy(alpha = 0.3f))
    )
}

// ════════════════════════════════════════════════════════════
//  ATTACHMENT MENU
// ════════════════════════════════════════════════════════════

@Composable
private fun AttachmentMenu(onDismiss: () -> Unit) {
    val items = listOf(
        Triple(Icons.Rounded.Image,           "صورة",      AqyalCyan),
        Triple(Icons.Rounded.Videocam,        "فيديو",     PurpleAccent),
        Triple(Icons.Rounded.InsertDriveFile, "ملف",       BlueAccent),
        Triple(Icons.Rounded.AudioFile,       "صوت",       GoldenAccent),
        Triple(Icons.Rounded.LocationOn,      "موقع",      GreenAccent),
        Triple(Icons.Rounded.Person,          "جهة اتصال", OrangeAccent),
        Triple(Icons.Rounded.Poll,            "استطلاع",   RedPrimary),
        Triple(Icons.Rounded.CameraAlt,       "كاميرا",    Color(0xFFE91E63)),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceDark)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        items.chunked(4).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                rowItems.forEach { (icon, label, tint) ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { onDismiss() }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(tint.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(icon, label, tint = tint, modifier = Modifier.size(26.dp))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(label, color = TextSecondary, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
