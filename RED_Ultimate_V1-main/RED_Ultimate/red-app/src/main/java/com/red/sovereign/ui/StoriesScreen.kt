package com.red.sovereign.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.red.sovereign.stories.Story
import com.red.sovereign.stories.StoryViewerState
import com.red.sovereign.stories.StoryVideoPlayer

@Composable
fun StoryFullscreen(viewer: StoryViewerState, storiesList: List<Story>, onClose: () -> Unit, onNext: () -> Unit, onPrev: () -> Unit) {
    if (viewer is StoryViewerState.Closed) return
    
    val story = when (viewer) {
        is StoryViewerState.Loading -> viewer.story
        is StoryViewerState.Image -> viewer.story
        is StoryViewerState.Video -> viewer.story
        is StoryViewerState.Unsupported -> viewer.story
        is StoryViewerState.Error -> viewer.story
        StoryViewerState.Closed -> error("unreachable")
    }
    
    var progress by remember { mutableFloatStateOf(0f) }
    var isPaused by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    LaunchedEffect(story.id, viewer, isPaused) {
        if (isPaused) return@LaunchedEffect
        if (viewer is StoryViewerState.Image || viewer is StoryViewerState.Unsupported || viewer is StoryViewerState.Error) {
            progress = 0f
            val duration = 5000L
            val interval = 50L
            val steps = duration / interval
            for (i in 1..steps) {
                if (isPaused) break
                delay(interval)
                progress = i.toFloat() / steps
            }
            if (!isPaused) onNext()
        } else if (viewer is StoryViewerState.Video) {
            progress = 0f
        }
    }
    
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    isPaused = true
                    tryAwaitRelease()
                    isPaused = false
                },
                onTap = { offset ->
                    if (offset.x < size.width * 0.3f) onPrev() else onNext()
                }
            )
        }, contentAlignment = Alignment.Center) {
            when (viewer) {
                is StoryViewerState.Loading -> CircularProgressIndicator(color = Color.White)
                is StoryViewerState.Image -> Image(viewer.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                is StoryViewerState.Video -> StoryVideoPlayer(viewer.uri, Modifier.fillMaxSize())
                is StoryViewerState.Text -> {
                    val bg = try { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(story.backgroundColor ?: "#1565C0")) } catch (_: Exception) { Color(0xFF1565C0) }
                    Box(Modifier.fillMaxSize().background(bg), contentAlignment = Alignment.Center) {
                        Text(story.caption ?: "", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(24.dp))
                    }
                }
                is StoryViewerState.Voice -> {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(androidx.compose.material.icons.Icons.Default.Mic, null, tint = Color.White, modifier = Modifier.size(64.dp))
                        Text("رسالة صوتية • ${story.durationMs?.let { "${it/1000}ث" } ?: ""}", color = Color.White, fontSize = 16.sp)
                        story.waveform.takeIf { it.isNotEmpty() }?.let { wave ->
                            androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 24.dp)) {
                                val step = size.width / wave.size.coerceAtLeast(1)
                                wave.forEachIndexed { i, v ->
                                    val h = size.height * (v.coerceIn(4,100)/100f)
                                    drawLine(Color.White, start = androidx.compose.ui.geometry.Offset(i*step+step/2, (size.height-h)/2), end = androidx.compose.ui.geometry.Offset(i*step+step/2, (size.height+h)/2), strokeWidth = 3f)
                                }
                            }
                        }
                        Button(onClick = { /* TODO: play voice story */ }) { Text("تشغيل") }
                    }
                }
                is StoryViewerState.Unsupported -> Text(viewer.message, color = Color.White)
                is StoryViewerState.Error -> Text("تعذر تحميل الحالة: ${viewer.message}", color = MaterialTheme.colorScheme.error)
                else -> Unit
            }
        }
        
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(top = 40.dp, start = 8.dp, end = 8.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.weight(1f).height(2.dp).clip(RoundedCornerShape(1.dp)),
                    color = Color.White, trackColor = Color.White.copy(alpha = 0.3f)
                )
            }
            
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color.Gray), contentAlignment = Alignment.Center) {
                    Text(story.ownerDisplayName.take(1), color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(story.ownerDisplayName, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text("منذ قليل", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, "إغلاق", tint = Color.White)
                }
            }
            
            Spacer(Modifier.weight(1f))
            
            if (!story.caption.isNullOrBlank()) {
                Text(
                    text = story.caption, color = Color.White, fontSize = 15.sp,
                    modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.4f)).padding(16.dp)
                )
            }
            
            // Reply box
            var replyText by remember { mutableStateOf("") }
            val context = androidx.compose.ui.platform.LocalContext.current
            Row(Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = replyText, onValueChange = { replyText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("رد على الحالة...", color = Color.White.copy(alpha = 0.7f)) },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                        focusedBorderColor = Color.White,
                        unfocusedTextColor = Color.White, focusedTextColor = Color.White
                    ),
                    trailingIcon = {
                        if (replyText.isNotBlank()) {
                            IconButton({
                                val rich = com.red.sovereign.core.RichMessage(
                                    action = "STORY_REPLY", text = replyText.trim(), replyTo = story.id
                                )
                                com.red.sovereign.core.RedConnectionService.sendRichText(context, story.ownerRedId, "", rich)
                                replyText = ""
                                onClose()
                            }) {
                                Icon(Icons.AutoMirrored.Filled.Send, "إرسال الرد", tint = Color.White)
                            }
                        }
                    }
                )
            }
        }
    }
    }
}
