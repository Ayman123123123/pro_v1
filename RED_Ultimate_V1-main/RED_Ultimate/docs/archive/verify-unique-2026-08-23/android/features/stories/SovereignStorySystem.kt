package com.red.sovereign.features.stories

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.red.core.theme.SovereignColors
import com.red.sovereign.features.privacy.PrivacyLevel

/**
 * 📖 YOUNES Sovereign Story & Post System
 * نظام القصص والمنشورات المتقدم — خصوصية + مشاهدون + أنواع متعددة
 */

// ━━━━━━━━━━━━ النماذج ━━━━━━━━━━━━

enum class StoryType { IMAGE, VIDEO, TEXT, VOICE }
enum class StoryBackground(val color: Long) {
    RED(0xFFD32F2F), BLUE(0xFF1565C0), GREEN(0xFF2E7D32),
    PURPLE(0xFF6A1B9A), ORANGE(0xFFE65100), PINK(0xFFAD1457),
    DARK(0xFF212121), GOLD(0xFFF59E0B)
}

data class SovereignStory(
    val id: String,
    val userId: String,
    val userName: String,
    val userAvatar: String? = null,
    val type: StoryType,
    val mediaUrl: String? = null,
    val caption: String? = null,
    val backgroundColor: StoryBackground = StoryBackground.RED,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = createdAt + 24 * 3600 * 1000, // 24 ساعة
    val visibleTo: PrivacyLevel = PrivacyLevel.EVERYONE,
    val viewCount: Int = 0,
    val isViewed: Boolean = false,
    val isMyStory: Boolean = false,
    val reaction: String? = null
)

// ━━━━━━━━━━━━ شريط القصص المتقدم ━━━━━━━━━━━━

@Composable
fun SovereignStoryBar(
    stories: List<SovereignStory>,
    onAddStory: () -> Unit,
    onStoryClick: (SovereignStory) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        // إضافة قصة جديدة
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(SovereignColors.SurfaceNavy)
                        .clickable(onClick = onAddStory),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.AddCircle,
                        null,
                        tint = SovereignColors.Cyan,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text("قصتك", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
            }
        }

        // قصص المستخدمين
        items(stories.distinctBy { it.userId }) { story ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clickable { onStoryClick(story) }
                ) {
                    // حلقة التقدم
                    val ringColor = if (story.isViewed) Color.Gray else SovereignColors.Cyan
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .border(3.dp, ringColor, CircleShape)
                            .padding(3.dp)
                    ) {
                        // الأفاتار
                        AsyncImage(
                            model = story.userAvatar,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }

                    // عدد القصص غير المشاهدة
                    val unseenCount = stories.count { it.userId == story.userId && !it.isViewed }
                    if (unseenCount > 1) {
                        Badge(
                            modifier = Modifier.align(Alignment.TopEnd),
                            containerColor = SovereignColors.Cyan
                        ) {
                            Text(unseenCount.toString(), fontSize = 9.sp)
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    story.userName,
                    fontSize = 11.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

// ━━━━━━━━━━━━ عارض القصص المتقدم ━━━━━━━━━━━━

@Composable
fun SovereignStoryViewer(
    stories: List<SovereignStory>,
    initialIndex: Int = 0,
    onClose: () -> Unit,
    onReply: (SovereignStory, String) -> Unit = { _, _ -> },
    onReaction: (SovereignStory, String) -> Unit = { _, _ -> },
    onDelete: (SovereignStory) -> Unit = {}
) {
    var currentIndex by remember { mutableIntStateOf(initialIndex) }
    var progress by remember { mutableFloatStateOf(0f) }
    var replyText by remember { mutableStateOf("") }
    var showViewers by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val exoPlayer = remember { ExoPlayer.Builder(context).build() }

    if (stories.isEmpty()) return
    val currentStory = stories[currentIndex]

    // تحديث المشغل عند تغيير القصة
    LaunchedEffect(currentIndex) {
        if (currentStory.type == StoryType.VIDEO && currentStory.mediaUrl != null) {
            exoPlayer.setMediaItem(MediaItem.fromUri(currentStory.mediaUrl))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        } else {
            exoPlayer.stop()
        }
    }

    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (offset.x < size.width / 3) {
                        if (currentIndex > 0) currentIndex--
                    } else {
                        if (currentIndex < stories.lastIndex) currentIndex++ else onClose()
                    }
                }
            }
    ) {
        // المحتوى
        when (currentStory.type) {
            StoryType.IMAGE -> AsyncImage(
                model = currentStory.mediaUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            StoryType.VIDEO -> AndroidView(
                factory = { PlayerView(it).apply { player = exoPlayer; useController = false } },
                modifier = Modifier.fillMaxSize()
            )
            StoryType.TEXT -> Box(
                modifier = Modifier.fillMaxSize().background(Color(currentStory.backgroundColor.color)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    currentStory.caption?.let {
                        Text(
                            it,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            lineHeight = 34.sp
                        )
                    }
                }
            }
            StoryType.VOICE -> Box(
                modifier = Modifier.fillMaxSize().background(SovereignColors.Navy),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Mic, null, tint = SovereignColors.Cyan, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("رسالة صوتية", color = Color.White, fontSize = 18.sp)
                }
            }
        }

        // أشرطة التقدم
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, start = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            stories.forEachIndexed { index, _ ->
                LinearProgressIndicator(
                    progress = {
                        when {
                            index < currentIndex -> 1f
                            index == currentIndex -> progress
                            else -> 0f
                        }
                    },
                    modifier = Modifier.weight(1f).height(2.5.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
            }
        }

        // الرأس
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, start = 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // أفاتار + اسم
            AsyncImage(
                model = currentStory.userAvatar,
                contentDescription = null,
                modifier = Modifier.size(36.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(currentStory.userName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                Text("منذ ${getTimeAgo(currentStory.createdAt)}", fontSize = 11.sp, color = Color.Gray)
            }
            Spacer(Modifier.weight(1f))

            // مشاهدون (لقصتي فقط)
            if (currentStory.isMyStory) {
                IconButton(onClick = { showViewers = !showViewers }) {
                    Icon(Icons.Rounded.Visibility, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Text(currentStory.viewCount.toString(), fontSize = 10.sp, color = Color.White)
                }
            }

            // إغلاق
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, null, tint = Color.White)
            }
        }

        // التذييل — الرد أو المشاهدون
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 24.dp, start = 16.dp, end = 16.dp)
        ) {
            if (showViewers && currentStory.isMyStory) {
                // قائمة المشاهدين
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.7f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("المشاهدون", fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(8.dp))
                        Text("${currentStory.viewCount} مشاهدة", color = Color.Gray, fontSize = 13.sp)
                    }
                }
            } else if (!currentStory.isMyStory) {
                // شريط الرد
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        placeholder = { Text("الرد على القصة...", color = Color.Gray) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.White.copy(alpha = 0.1f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.08f)
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    // أزرار التفاعل السريع
                    Row {
                        IconButton(onClick = { onReaction(currentStory, "❤️") }) {
                            Text("❤️", fontSize = 20.sp)
                        }
                        IconButton(onClick = { onReaction(currentStory, "🔥") }) {
                            Text("🔥", fontSize = 20.sp)
                        }
                        if (replyText.isNotBlank()) {
                            IconButton(onClick = { onReply(currentStory, replyText); replyText = "" }) {
                                Icon(Icons.Rounded.Send, null, tint = SovereignColors.Cyan)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ━━━━━━━━━━━━ إنشاء قصة متقدم ━━━━━━━━━━━━

@Composable
fun SovereignCreateStoryScreen(
    onPublish: (SovereignStory) -> Unit = {},
    onBack: () -> Unit = {}
) {
    var mode by remember { mutableStateOf(StoryType.TEXT) }
    var textInput by remember { mutableStateOf("") }
    var selectedBg by remember { mutableStateOf(StoryBackground.RED) }
    var selectedPrivacy by remember { mutableStateOf(PrivacyLevel.EVERYONE) }
    var caption by remember { mutableStateOf("") }

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onPublish(SovereignStory(id = "s_${System.currentTimeMillis()}", userId = "me", userName = "يونس", type = StoryType.IMAGE, mediaUrl = it.toString(), caption = caption, visibleTo = selectedPrivacy, isMyStory = true)) }
    }
    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onPublish(SovereignStory(id = "s_${System.currentTimeMillis()}", userId = "me", userName = "يونس", type = StoryType.VIDEO, mediaUrl = it.toString(), caption = caption, visibleTo = selectedPrivacy, isMyStory = true)) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (mode == StoryType.TEXT) Color(selectedBg.color) else SovereignColors.Obsidian)
    ) {
        // الرأس
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.Close, null, tint = Color.White) }
            Spacer(Modifier.weight(1f))

            // خصوصية
            Surface(
                onClick = { selectedPrivacy = PrivacyLevel.entries[(selectedPrivacy.ordinal + 1) % PrivacyLevel.entries.size] },
                shape = RoundedCornerShape(8.dp),
                color = Color.White.copy(alpha = 0.15f)
            ) {
                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(selectedPrivacy.icon, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(selectedPrivacy.label, fontSize = 11.sp, color = Color.White)
                }
            }
        }

        // المحتوى حسب النوع
        when (mode) {
            StoryType.TEXT -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.weight(1f))
                    TextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("ماذا يدور في ذهنك؟", color = Color.White.copy(alpha = 0.5f)) },
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
                    )
                    Spacer(Modifier.weight(1f))

                    // اختيار الخلفية
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StoryBackground.entries.forEach { bg ->
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(bg.color))
                                    .border(2.dp, if (bg == selectedBg) Color.White else Color.Transparent, CircleShape)
                                    .clickable { selectedBg = bg }
                            )
                        }
                    }
                }
            }
            StoryType.IMAGE, StoryType.VIDEO -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            if (mode == StoryType.IMAGE) Icons.Rounded.Image else Icons.Rounded.Videocam,
                            null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            if (mode == StoryType.IMAGE) "اضغط لاختيار صورة" else "اضغط لاختيار فيديو",
                            color = Color.White.copy(alpha = 0.5f), fontSize = 16.sp
                        )
                    }
                }
            }
            StoryType.VOICE -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Mic, null, tint = SovereignColors.Cyan, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("اضغط للتسجيل", color = Color.White, fontSize = 16.sp)
                    }
                }
            }
        }

        // الشريط السفلي
        Column(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)) {
            // شرح القصة
            if (mode != StoryType.TEXT) {
                OutlinedTextField(
                    value = caption, onValueChange = { caption = it },
                    placeholder = { Text("أضف شرح...") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.White.copy(alpha = 0.08f))
                )
                Spacer(Modifier.height(12.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // أزرار النوع
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    IconButton(onClick = { mode = StoryType.TEXT }) {
                        Icon(Icons.Rounded.TextFields, null, tint = if (mode == StoryType.TEXT) SovereignColors.Cyan else Color.White, modifier = Modifier.size(28.dp))
                    }
                    IconButton(onClick = { imageLauncher.launch("image/*") }) {
                        Icon(Icons.Rounded.Image, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    IconButton(onClick = { videoLauncher.launch("video/*") }) {
                        Icon(Icons.Rounded.Videocam, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    IconButton(onClick = { mode = StoryType.VOICE }) {
                        Icon(Icons.Rounded.Mic, null, tint = if (mode == StoryType.VOICE) SovereignColors.Cyan else Color.White, modifier = Modifier.size(28.dp))
                    }
                }

                // زر النشر
                FloatingActionButton(
                    onClick = {
                        if (textInput.isNotBlank()) {
                            onPublish(SovereignStory(
                                id = "s_${System.currentTimeMillis()}",
                                userId = "me", userName = "يونس",
                                type = StoryType.TEXT, caption = textInput,
                                backgroundColor = selectedBg, visibleTo = selectedPrivacy, isMyStory = true
                            ))
                        }
                    },
                    containerColor = SovereignColors.Cyan,
                    shape = CircleShape
                ) {
                    Icon(Icons.Rounded.Send, null, tint = Color.White)
                }
            }
        }
    }
}

// ─── مساعد الوقت ───
private fun getTimeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "الآن"
        diff < 3600_000 -> "${diff / 60_000} دقيقة"
        diff < 86400_000 -> "${diff / 3600_000} ساعة"
        else -> "${diff / 86400_000} يوم"
    }
}
