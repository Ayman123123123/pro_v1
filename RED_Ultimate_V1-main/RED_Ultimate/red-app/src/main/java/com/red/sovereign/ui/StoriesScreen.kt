package com.red.sovereign.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import com.red.sovereign.stories.STORY_REACTIONS
import com.red.sovereign.stories.Story
import com.red.sovereign.stories.StoryViewerState
import com.red.sovereign.stories.StoryVideoPlayer
import com.red.sovereign.stories.storyAgeLabel

@Composable
fun StoryFullscreen(
    viewer: StoryViewerState,
    onClose: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onReact: (Story, String) -> Unit,
    onReply: (Story, String) -> Unit,
    /** true عندما تكون الحالة المعروضة للمستخدم الحالي — يُظهر زر الحذف. */
    isOwn: Boolean = false,
    /** يحلّ معرف المشاهِد إلى اسم عرض (جهات الاتصال أولًا) — لقائمة المشاهِدين. */
    nameResolver: (String) -> String = { it },
    /** حذف الحالة (للمالك فقط) — يُغلق العارض عند النجاح عبر ViewModel. */
    onDelete: ((Story) -> Unit)? = null,
    /** قائمة المشاهِدين الحقيقية من الخادم (للمالك) — تُجلب عند فتح العين. */
    viewers: List<com.red.sovereign.stories.StoryViewerDto> = emptyList(),
    viewersLoading: Boolean = false,
    /** يُستدعى عند فتح قائمة المشاهِدين لجلبها من الخادم. */
    onOpenViewers: (Story) -> Unit = {},
    /** رسالة خطأ آخر جلب للمشاهِدين — null تعني لا خطأ. */
    viewersError: String? = null,
    /** إعادة محاولة جلب المشاهِدين بعد خطأ. */
    onRetryViewers: (Story) -> Unit = {},
    /** كتم حالات هذا المالك (محلي) — زر السماعة في الترويسة لغير المالك. */
    muted: Boolean = false,
    onToggleMute: () -> Unit = {}
) {
    if (viewer is StoryViewerState.Closed) return

    val story = when (viewer) {
        is StoryViewerState.Loading -> viewer.story
        is StoryViewerState.Image -> viewer.story
        is StoryViewerState.Video -> viewer.story
        is StoryViewerState.Text -> viewer.story
        is StoryViewerState.Voice -> viewer.story
        is StoryViewerState.Unsupported -> viewer.story
        is StoryViewerState.Error -> viewer.story
        else -> error("unreachable")
    }

    // مفاتيح story.id: بلا مفتاح كان شريط التقدم ونص الرد يعيشان عبر
    // الانتقال next/prev فيبقى رد القصة السابقة ويتجمد التقدم على قيمة قديمة.
    var progress by remember(story.id) { mutableFloatStateOf(0f) }
    var isPaused by remember(story.id) { mutableStateOf(false) }
    var showViewers by remember(story.id) { mutableStateOf(false) }
    var showDeleteConfirm by remember(story.id) { mutableStateOf(false) }
    var replyText by remember(story.id) { mutableStateOf("") }
    val isVideo = viewer is StoryViewerState.Video
    LaunchedEffect(story.id, isPaused) {
        if (isPaused) return@LaunchedEffect
        // الفيديو يقود تقدمه بنفسه عبر onProgress أدناه — المؤقت هنا
        // للصور/النص فقط. (كان الفيديو يجمّد الشريط عند 0f.)
        if (!isVideo) {
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
        } else {
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
                is StoryViewerState.Video -> StoryVideoPlayer(
                    uri = viewer.uri,
                    modifier = Modifier.fillMaxSize(),
                    autoPlay = true,
                    onProgress = { positionMs, durationMs ->
                        if (!isPaused && durationMs > 0L) {
                            progress = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                        }
                    },
                    onEnded = onNext
                )
                is StoryViewerState.Text -> {
                    val bg = try { androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(story.backgroundColor ?: "#0A7A5E")) } catch (_: Exception) { Color(0xFF0A7A5E) }
                    Box(Modifier.fillMaxSize().background(bg), contentAlignment = Alignment.Center) {
                        Text(story.caption ?: "", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(24.dp))
                    }
                }
                is StoryViewerState.Voice -> {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(androidx.compose.material.icons.Icons.Default.Mic, null, tint = Color.White, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("رسالة صوتية", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(16.dp))
                        com.red.sovereign.stories.VoiceStoryPlayer(
                            mediaUrl = viewer.uri.toString(),
                            durationMs = story.durationMs ?: 0L,
                            waveform = story.waveform,
                            onFinished = onNext
                        )
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
                // عارض الوسائط ستارة سوداء معيارية (مثل يوتيوب) في الوضعين —
                // الأبيض على الأسود 21:1، والأفاتار من colorScheme لا Gray مثبت.
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        story.ownerDisplayName.take(1),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        fontFamily = com.red.sovereign.ui.theme.PlexArabicFamily
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(story.ownerDisplayName, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text(storyAgeLabel(story.createdAt), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
                // قائمة المشاهِدين: العدد من الخادم (viewCount) والأسماء من endpoint المشاهِدين.
                IconButton(onClick = { onOpenViewers(story); showViewers = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Visibility, "المشاهِدون", tint = Color.White)
                        Text("${story.viewCount}", color = Color.White, fontSize = 12.sp)
                    }
                }
                if (isOwn && onDelete != null) {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            Icons.Default.Delete,
                            "حذف الحالة",
                            tint = com.red.sovereign.ui.theme.YounesRose
                        )
                    }
                }
                if (!isOwn) {
                    IconButton(onClick = onToggleMute) {
                        Icon(
                            if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            if (muted) "إلغاء كتم حالاته" else "كتم حالاته",
                            tint = Color.White
                        )
                    }
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
            
            // Reactions + Reply box — نفس قائمة الخادم المسموحة STORY_REACTIONS
            // (كانت 4 فقط هنا مقابل 7 في StoryModels.kt فيُرفض الباقي بـ400).
            // replyText مرفوع لأعلى بمفتاح story.id (سطر ~82) — لا تعريف محلي هنا.
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                STORY_REACTIONS.forEach { emoji ->
                    AssistChip(onClick = { onReact(story, emoji) }, label = { Text(emoji) }, colors = AssistChipDefaults.assistChipColors(containerColor = Color.White.copy(alpha = 0.2f)))
                }
            }
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
                                onReply(story, replyText.trim())
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

    if (showViewers) {
        AlertDialog(
            onDismissRequest = { showViewers = false },
            title = { Text("المشاهِدون (${story.viewCount})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (viewersError != null) {
                        Text(viewersError, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { onRetryViewers(story) }) { Text("إعادة المحاولة") }
                    }
                    when {
                        viewersLoading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Text("جارٍ التحميل...") }
                        viewers.isEmpty() && viewersError == null -> Text("لا توجد مشاهدات مسجلة بعد — تظهر هنا أسماء من شاهد حالتك.")
                        viewers.isNotEmpty() -> LazyColumn(Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(viewers, key = { it.redId }) { v ->
                                val label = v.displayName.ifBlank { v.username.ifBlank { v.redId } }
                                // remember(v.redId): بلا مفتاح كان حل الاسم يُعاد حسابه لكل صف
                                // عند أي تغيير في القائمة (وميض أثناء التحميل).
                                val resolved = remember(v.redId, label) { nameResolver(v.redId).ifBlank { label } }
                                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    com.red.sovereign.ui.components.SovereignAvatarThemed(
                                        text = resolved.take(1).ifBlank { "؟" },
                                        size = 36.dp
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(resolved, fontWeight = FontWeight.Medium)
                                        Text(v.redId, fontSize = 11.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                        else -> Unit
                    }
                }
            },
            confirmButton = { TextButton({ showViewers = false }) { Text("إغلاق") } }
        )
    }

    if (showDeleteConfirm && isOwn && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("حذف الحالة؟") },
            text = { Text("ستُحذف هذه الحالة نهائيًا ولن يتمكن أحد من مشاهدتها.") },
            confirmButton = {
                TextButton({
                    showDeleteConfirm = false
                    onDelete(story)
                    onClose()
                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton({ showDeleteConfirm = false }) { Text("إلغاء") } }
        )
    }
}
