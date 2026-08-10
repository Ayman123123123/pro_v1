package com.red.features.chat

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.red.core.theme.SovereignColors
import com.red.sovereign.features.media.VoiceNotePlayer

/**
 * 🎨 YOUNES Sovereign Media Bubble — فقاعة الوسائط المتقدمة
 * يدعم: صورة، فيديو، ملف، رسالة صوتية، موقع، جهة اتصال، استطلاع
 */
@Composable
fun MediaBubble(
    type: String,
    url: String,
    fileName: String? = null,
    fileSize: String? = null,
    durationMs: Long = 0,
    isMe: Boolean = false,
    onPlay: () -> Unit = {},
    onClick: () -> Unit = {}
) {
    Column(modifier = Modifier.widthIn(max = 260.dp).padding(4.dp)) {
        when (type) {
            "IMAGE" -> {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .height(200.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onClick)
                )
            }

            "VIDEO" -> {
                Box(
                    modifier = Modifier
                        .height(200.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onClick),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize())
                    // أيقونة التشغيل مع خلفية
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.Black.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    // مدة الفيديو
                    if (durationMs > 0) {
                        Surface(
                            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                            shape = RoundedCornerShape(4.dp),
                            color = Color.Black.copy(alpha = 0.7f)
                        ) {
                            Text(formatDuration(durationMs), color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
            }

            "VOICE", "AUDIO" -> {
                VoiceNotePlayer(
                    durationMs = durationMs,
                    isMe = isMe,
                    onPlayPause = onPlay,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            "FILE" -> {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isMe) SovereignColors.Cyan.copy(alpha = 0.1f) else SovereignColors.SurfaceNavy
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // أيقونة الملف حسب النوع
                        val fileIcon = when {
                            fileName?.endsWith(".pdf") == true -> Icons.Rounded.PictureAsPdf
                            fileName?.endsWith(".apk") == true -> Icons.Rounded.Android
                            fileName?.endsWith(".zip") == true -> Icons.Rounded.FolderZip
                            else -> Icons.Rounded.Description
                        }
                        val fileColor = when {
                            fileName?.endsWith(".pdf") == true -> Color(0xFFE53935)
                            fileName?.endsWith(".apk") == true -> SovereignColors.Success
                            fileName?.endsWith(".zip") == true -> SovereignColors.Gold
                            else -> SovereignColors.Cyan
                        }

                        Box(
                            modifier = Modifier.size(44.dp).background(fileColor.copy(alpha = 0.15f), androidx.compose.foundation.shape.CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(fileIcon, null, tint = fileColor, modifier = Modifier.size(22.dp))
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(fileName ?: "ملف", maxLines = 1, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Color.White)
                            fileSize?.let {
                                Text(it, fontSize = 11.sp, color = Color.Gray)
                            }
                        }

                        IconButton(onClick = { /* Download */ }) {
                            Icon(Icons.Rounded.Download, null, tint = fileColor, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            "LOCATION" -> {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SovereignColors.SurfaceNavy,
                    modifier = Modifier.clickable(onClick = onClick)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Rounded.LocationOn, null, tint = SovereignColors.Danger, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(4.dp))
                        Text("موقع مشارك", fontSize = 13.sp, color = Color.Gray)
                    }
                }
            }

            "CONTACT" -> {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SovereignColors.SurfaceNavy
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Person, null, tint = SovereignColors.Cyan, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(fileName ?: "جهة اتصال", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                    }
                }
            }

            "POLL" -> {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = SovereignColors.SurfaceNavy
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Poll, null, tint = SovereignColors.Gold, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(fileName ?: "استطلاع", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
