package com.red.sovereign.features.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.red.sovereign.ui.theme.*

// ════════════════════════════════════════════════════════════
//  DATA
// ════════════════════════════════════════════════════════════

data class Message(
    val id: String,
    val content: String,
    val time: String,
    val isMe: Boolean,
    val status: String,  // SENT | DELIVERED | READ
    val replyTo: Message? = null,
    val reactions: Map<String, Int> = emptyMap(),
    val isVoice: Boolean = false,
    val voiceDuration: String = "",
    val isForwarded: Boolean = false
)

val EMOJI_REACTIONS = listOf("❤️", "😂", "👍", "😮", "😢", "🔥")

// ════════════════════════════════════════════════════════════
//  LUXURY CHAT BUBBLE
// ════════════════════════════════════════════════════════════

@Composable
fun LuxuryChatBubble(
    message: Message,
    isMe: Boolean,
    onLongPress: (Message) -> Unit = {},
    onReact: (Message, String) -> Unit = { _, _ -> },
    onReplyTo: (Message) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showReactions by remember { mutableStateOf(false) }

    // Bubble shape — sharp corner indicating direction
    val shape = RoundedCornerShape(
        topStart    = 18.dp,
        topEnd      = 18.dp,
        bottomStart = if (isMe) 18.dp else 4.dp,
        bottomEnd   = if (isMe) 4.dp else 18.dp
    )

    // Color
    val bubbleBrush = if (isMe)
        Brush.linearGradient(listOf(BubbleMeGradStart, BubbleMeGradEnd))
    else
        Brush.linearGradient(listOf(BubbleOther, SurfaceMid))

    val textColor  = TextPrimary
    val timeColor  = TextSecondary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
            // Forwarded indicator
            if (message.isForwarded) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = if (!isMe) 8.dp else 0.dp, end = if (isMe) 8.dp else 0.dp, bottom = 2.dp)
                ) {
                    Icon(Icons.Rounded.Reply, null, tint = TextTertiary, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("أُعيد توجيهه", color = TextTertiary, fontSize = 11.sp)
                }
            }

            // Bubble
            Box(
                modifier = Modifier
                    .widthIn(min = 80.dp, max = 280.dp)
                    .clip(shape)
                    .background(bubbleBrush)
                    .pointerInput(message.id) {
                        detectTapGestures(
                            onLongPress = {
                                showReactions = true
                                onLongPress(message)
                            }
                        )
                    }
            ) {
                Column(modifier = Modifier.padding(start = 12.dp, top = 8.dp, end = 12.dp, bottom = 6.dp)) {
                    // Reply preview
                    message.replyTo?.let { reply ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .padding(8.dp)
                        ) {
                            Column {
                                Text(
                                    text  = if (reply.isMe) "أنت" else "المستقبِل",
                                    color = AqyalCyanGlow,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(reply.content, color = TextSecondary, fontSize = 13.sp, maxLines = 2)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }

                    // Voice message
                    if (message.isVoice) {
                        VoiceBubbleContent(message = message, isMe = isMe)
                    } else {
                        Text(
                            text       = message.content,
                            color      = textColor,
                            fontSize   = 15.sp,
                            lineHeight = 21.sp
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    // Time + Status
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(message.time, fontSize = 11.sp, color = timeColor)
                        if (isMe) {
                            when (message.status) {
                                "READ"      -> Icon(Icons.Rounded.DoneAll, null, tint = ReadReceipt,    modifier = Modifier.size(15.dp))
                                "DELIVERED" -> Icon(Icons.Rounded.DoneAll, null, tint = TextTertiary,   modifier = Modifier.size(15.dp))
                                else        -> Icon(Icons.Rounded.Done,    null, tint = TextTertiary,   modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                }
            }

            // Reactions display
            if (message.reactions.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .offset(y = (-6).dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceElevated)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        message.reactions.entries.take(3).forEach { (emoji, count) ->
                            Text("$emoji${if (count > 1) " $count" else ""}", fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // Reaction Picker Popup
        if (showReactions) {
            ReactionPicker(
                onReact  = { emoji -> onReact(message, emoji); showReactions = false },
                onDismiss = { showReactions = false },
                onReply  = { onReplyTo(message); showReactions = false }
            )
        }
    }
}

// ════════════════════════════════════════════════════════════
//  VOICE BUBBLE
// ════════════════════════════════════════════════════════════

@Composable
private fun VoiceBubbleContent(message: Message, isMe: Boolean) {
    var playing by remember { mutableStateOf(false) }
    val waveProgress by animateFloatAsState(
        targetValue   = if (playing) 1f else 0f,
        animationSpec = tween(message.voiceDuration.toLongOrNull()?.toInt() ?: 3000)
    )

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.width(220.dp)) {
        IconButton(
            onClick  = { playing = !playing },
            modifier = Modifier.size(40.dp)
                .clip(CircleShape)
                .background(if (isMe) RedPrimary.copy(alpha = 0.6f) else SurfaceLight)
        ) {
            Icon(
                imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                null, tint = TextPrimary, modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        // Waveform bars
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            val barHeights = listOf(12, 18, 24, 16, 28, 20, 14, 22, 18, 26, 12, 16, 20, 14, 24, 18)
            barHeights.forEachIndexed { i, h ->
                val fraction = i.toFloat() / barHeights.size
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(h.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (fraction <= waveProgress) ReadReceipt
                            else TextSecondary.copy(alpha = 0.4f)
                        )
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(message.voiceDuration, color = TextSecondary, fontSize = 11.sp)
    }
}

// ════════════════════════════════════════════════════════════
//  REACTION PICKER
// ════════════════════════════════════════════════════════════

@Composable
private fun ReactionPicker(
    onReact: (String) -> Unit,
    onDismiss: () -> Unit,
    onReply: () -> Unit
) {
    Popup(
        onDismissRequest = onDismiss,
        properties       = PopupProperties(focusable = true)
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceElevated)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Emoji row
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EMOJI_REACTIONS.forEach { emoji ->
                    Text(
                        text     = emoji,
                        fontSize = 28.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onReact(emoji) }
                            .padding(4.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            // Action row
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TextButton(onClick = onReply) {
                    Icon(Icons.Rounded.Reply, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("رد", fontSize = 13.sp)
                }
                TextButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("نسخ", fontSize = 13.sp)
                }
                TextButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Delete, null, tint = RedPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("حذف", fontSize = 13.sp, color = RedPrimary)
                }
            }
        }
    }
}
