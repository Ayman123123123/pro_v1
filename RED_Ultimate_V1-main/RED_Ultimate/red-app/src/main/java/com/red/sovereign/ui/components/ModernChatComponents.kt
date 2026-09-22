package com.red.sovereign.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.ui.theme.*

/**
 * مكونات دردشة حديثة - أفضل من واتساب وتيليجرام
 * تصميم Liquid Glass 2026 مع تأثيرات زجاجية وضبابية
 */

// فقاعة رسالة حديثة بتصميم أفضل من واتساب
@Composable
fun ModernMessageBubble(
    message: String,
    isMe: Boolean,
    time: String,
    status: String,
    senderName: String = "",
    senderRedId: String = "",
    isEdited: Boolean = false,
    reactions: List<Pair<String, Int>> = emptyList(),
    onLongClick: () -> Unit = {},
    onReactionClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val bubbleColor = if (isMe) YounesBubbleOut else YounesBubbleIn
    val textColor = Color.White
    
    Column(
        modifier = modifier
            .widthIn(max = 320.dp)
            .shadow(2.dp, RoundedCornerShape(18.dp))
            .clip(
                RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (isMe) 18.dp else 4.dp,
                    bottomEnd = if (isMe) 4.dp else 18.dp
                )
            )
            .background(
                if (isMe) Brush.linearGradient(listOf(YounesBubbleOut, YounesBubbleOutGlow))
                else Brush.linearGradient(listOf(YounesBubbleIn, YounesSurface2))
            )
            .combinedClickable(onClick = {}, onLongClick = onLongClick)
            .padding(12.dp)
    ) {
        // اسم المرسل في المجموعات
        if (!isMe && senderName.isNotBlank()) {
            val colors = listOf(
                Color(0xFF6FD8B0), Color(0xFF7FB5E0), Color(0xFFF0C674),
                Color(0xFFC9A7E8), Color(0xFF8FC7E8), Color(0xFFB5D8A0)
            )
            val colorIndex = kotlin.math.abs(senderRedId.hashCode()) % colors.size
            Text(
                text = senderName,
                color = colors[colorIndex],
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (senderRedId.isNotBlank()) {
                Text(
                    text = senderRedId,
                    color = YounesMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(4.dp))
        }
        
        // محتوى الرسالة
        Text(
            text = message,
            color = textColor,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            fontFamily = PlexArabicFamily
        )
        
        // تفاعلات
        if (reactions.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(reactions) { (emoji, count) ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = YounesSurface2.copy(alpha = 0.8f),
                        modifier = Modifier.clickable { onReactionClick(emoji) }
                    ) {
                        Row(
                            Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(emoji, fontSize = 12.sp)
                            if (count > 1) {
                                Text(" $count", fontSize = 10.sp, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
        
        // الوقت والحالة
        Row(
            Modifier.align(Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isEdited) {
                Text("✏️", fontSize = 10.sp)
            }
            Text(
                text = time,
                fontSize = 11.sp,
                color = if (isMe) Color.White.copy(alpha = 0.7f) else YounesMuted
            )
            if (isMe) {
                val tickColor = when (status) {
                    "READ" -> YounesReadTick
                    "DELIVERED" -> Color.White.copy(alpha = 0.7f)
                    else -> Color.White.copy(alpha = 0.5f)
                }
                Text(
                    text = when (status) {
                        "READ" -> "✓✓"
                        "DELIVERED" -> "✓✓"
                        else -> "✓"
                    },
                    color = tickColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// شريط إدخال حديث بتصميم Liquid Glass
@Composable
fun ModernChatInputBar(
    messageText: String,
    onMessageChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit = {},
    onEmoji: () -> Unit = {},
    onVoice: () -> Unit = {},
    isRecording: Boolean = false,
    placeholder: String = "رسالة مشفرة...",
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = YounesSurface1.copy(alpha = 0.95f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // زر إيموجي
            IconButton(
                onClick = onEmoji,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(YounesSurface2)
            ) {
                Icon(Icons.Default.EmojiEmotions, "إيموجي", tint = YounesMuted)
            }
            
            // حقل النص
            OutlinedTextField(
                value = messageText,
                onValueChange = onMessageChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(placeholder, color = YounesMuted, fontSize = 14.sp) },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = YounesSurface2,
                    unfocusedContainerColor = YounesSurface2,
                    focusedBorderColor = YounesPrimary.copy(alpha = 0.5f),
                    unfocusedBorderColor = YounesBorder.copy(alpha = 0.3f)
                ),
                maxLines = 5
            )
            
            // زر مرفقات
            IconButton(
                onClick = onAttach,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(YounesSurface2)
            ) {
                Icon(Icons.Default.AttachFile, "مرفقات", tint = YounesMuted)
            }
            
            // زر إرسال/صوت
            val sendModifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (messageText.isNotBlank()) YounesPrimary
                    else if (isRecording) YounesRose
                    else YounesSurface2
                )
                .clickable {
                    if (messageText.isNotBlank()) onSend() else onVoice()
                }
            
            Box(sendModifier, contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (messageText.isNotBlank()) Icons.Default.Send else Icons.Default.Mic,
                    contentDescription = if (messageText.isNotBlank()) "إرسال" else "تسجيل",
                    tint = if (messageText.isNotBlank() || isRecording) YounesOnPrimary else YounesMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// بطاقة محادثة حديثة
@Composable
fun ModernConversationCard(
    name: String,
    lastMessage: String,
    time: String,
    unreadCount: Int = 0,
    isOnline: Boolean = false,
    isPinned: Boolean = false,
    isMuted: Boolean = false,
    avatarLetter: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = YounesSurface1),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // أفاتار مع حالة الاتصال
            Box {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(YounesPrimary, YounesCobalt)))
                        .border(2.dp, YounesSurface1, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        avatarLetter.take(1).uppercase(),
                        color = YounesOnPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
                if (isOnline) {
                    Box(
                        Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00C98C))
                            .border(2.dp, YounesSurface1, CircleShape)
                            .align(Alignment.BottomEnd)
                    )
                }
            }
            
            Spacer(Modifier.width(12.dp))
            
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (isPinned) {
                        Icon(Icons.Default.Star, "مثبت", tint = Color(0xFFF5C842), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    if (isMuted) {
                        Icon(Icons.Default.NotificationsOff, "مكتوم", tint = YounesMuted, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(time, fontSize = 11.sp, color = YounesMuted)
                }
                
                Spacer(Modifier.height(2.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = lastMessage,
                        fontSize = 13.sp,
                        color = YounesMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (unreadCount > 0) {
                        Surface(
                            shape = CircleShape,
                            color = YounesPrimary
                        ) {
                            Text(
                                text = if (unreadCount > 99) "99+" else "$unreadCount",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = YounesOnPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// شريط مكالمات حديث
@Composable
fun ModernCallBar(
    peerName: String,
    callType: String,
    isVideo: Boolean,
    onCall: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = YounesSurface2.copy(alpha = 0.8f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.Call,
                contentDescription = callType,
                tint = YounesPrimary,
                modifier = Modifier.size(20.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(peerName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                Text(callType, fontSize = 11.sp, color = YounesMuted)
            }
            IconButton(
                onClick = { onCall(false) },
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(YounesPrimary)
            ) {
                Icon(Icons.Default.Call, "صوتي", tint = YounesOnPrimary, modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = { onCall(true) },
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(YounesCobalt)
            ) {
                Icon(Icons.Default.Videocam, "فيديو", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// مؤشر كتابة متحرك
@Composable
fun ModernTypingIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val dot1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "dot1"
    )
    val dot2 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "dot2"
    )
    val dot3 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, delayMillis = 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "dot3"
    )
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = YounesSurface2),
        shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size((6 + dot1 * 2).dp)
                    .clip(CircleShape)
                    .background(YounesMuted.copy(alpha = 0.5f + dot1 * 0.5f))
            )
            Box(
                Modifier
                    .size((6 + dot2 * 2).dp)
                    .clip(CircleShape)
                    .background(YounesMuted.copy(alpha = 0.5f + dot2 * 0.5f))
            )
            Box(
                Modifier
                    .size((6 + dot3 * 2).dp)
                    .clip(CircleShape)
                    .background(YounesMuted.copy(alpha = 0.5f + dot3 * 0.5f))
            )
        }
    }
}

// شارة حالة الشبكة
@Composable
fun NetworkStatusBadge(
    quality: String,
    isLan: Boolean = false,
    modifier: Modifier = Modifier
) {
    val color = when (quality) {
        "EXCELLENT" -> Color(0xFF00C98C)
        "GOOD" -> Color(0xFF4D9FE8)
        "FAIR" -> Color(0xFFF0C674)
        else -> Color(0xFFF25C5C)
    }
    
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = if (isLan) "محلي $quality" else quality,
                fontSize = 10.sp,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// زر إجراء عائم حديث
@Composable
fun ModernFab(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = YounesPrimary
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        containerColor = color,
        contentColor = YounesOnPrimary,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, text, Modifier.size(20.dp))
            Text(text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}
