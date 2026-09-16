package com.red.sovereign.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.social.FeedState
import com.red.sovereign.social.FeedViewModel
import com.red.sovereign.social.PostMedia
import com.red.sovereign.social.scheduledLabel
import com.red.sovereign.stories.StoryViewModel
import com.red.sovereign.stories.StoryVisibility
import com.red.sovereign.ui.DashboardStoresHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ─────────────────────────────────────────────
// أدوات مشتركة
// ─────────────────────────────────────────────

/** يستخرج #هاشتاج و@منشن (عربي/لاتيني) للمعاينة والإرسال. */
internal fun extractTags(text: String): Pair<List<String>, List<String>> {
    val tags = mutableListOf<String>()
    val mentions = mutableListOf<String>()
    text.split(Regex("\\s+")).forEach { w ->
        when {
            w.startsWith("#") && w.length in 2..60 -> tags.add(w.take(60))
            w.startsWith("@") && w.length in 2..40 -> mentions.add(w.take(40))
        }
    }
    return tags.distinct().take(10) to mentions.distinct().take(10)
}

private val STORY_BG_COLORS = listOf("#1565C0", "#D32F2F", "#0A7A5E", "#6A1B9A", "#EF6C00", "#101418")

// ─────────────────────────────────────────────
// 1) مؤلف المنشورات مع معاينة حية + مسودة + جدولة
// ─────────────────────────────────────────────

/**
 * مؤلف منشورات يونس — معاينة حية + مسودة مشفرة + جدولة.
 *
 * يتفوق على المنافسين:
 * - معاينة حية (بطاقة مصغرة) أثناء الكتابة — X يعرض مسودة عمياء.
 * - مسودة مشفرة تُحفظ تلقائيًا كل 1.5s وتُستعاد عند الفتح (DraftsStore).
 * - جدولة محلية (بعد ساعة/3 ساعات/غدًا 9ص) عبر ScheduledPostsStore + Worker.
 * - مفتاح النص remember(feed.scope): تبديل التبويب لا يخلط المسودات.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostComposerSheet(
    feed: FeedViewModel,
    username: String,
    onDismiss: () -> Unit,
    onPublished: () -> Unit = onDismiss
) {
    val scopeKey = feed.scope
    var text by remember(scopeKey) { mutableStateOf("") }
    var visibility by remember(scopeKey) { mutableStateOf("PUBLIC") }
    var isPoll by remember(scopeKey) { mutableStateOf(false) }
    var pollOptions by remember(scopeKey) { mutableStateOf(listOf("", "")) }
    var pollHours by remember(scopeKey) { mutableStateOf(24) }
    var scheduledAt by remember(scopeKey) { mutableLongStateOf(0L) }
    var draftRestored by remember(scopeKey) { mutableStateOf(false) }
    var draftSavedTick by remember { mutableStateOf(0L) }
    val mediaUris = remember(scopeKey) { mutableStateListOf<Uri>() }
    var uploadingMedia by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val appContext = context.applicationContext
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // استعادة المسودة مرة واحدة (لا تعيد الكتابة فوق ما يكتبه المستخدم).
    LaunchedEffect(scopeKey) {
        if (!draftRestored) {
            runCatching { feed.loadDraft() }.getOrNull()?.takeIf { it.isNotBlank() }?.let {
                if (text.isBlank()) text = it
            }
            draftRestored = true
        }
    }
    // حفظ تلقائي مشفر بعد 1.5s من التوقف عن الكتابة (نمط المنافسين + تشفير).
    LaunchedEffect(text, scopeKey) {
        if (!draftRestored) return@LaunchedEffect
        if (text.isBlank()) return@LaunchedEffect
        delay(1500)
        feed.saveDraft(text)
        draftSavedTick = System.currentTimeMillis()
    }

    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && mediaUris.size < 4) {
            runCatching { appContext.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            mediaUris.add(uri)
        }
    }
    val mediaApi = remember(appContext) {
        com.red.sovereign.media.MediaApi(appContext, AuthorizedApiClient(DashboardStoresHolder.tokenStore(appContext)))
    }

    val (tags, mentions) = remember(text) { extractTags(text) }
    val publishing = feed.state == FeedState.Publishing || uploadingMedia
    val canPublish = !publishing && (text.trim().length in 1..2000 || mediaUris.isNotEmpty()) &&
        (!isPoll || (text.isNotBlank() && pollOptions.count { it.trim().length >= 2 } >= 2))

    suspend fun uploadMedia(): List<PostMedia> = withContext(Dispatchers.IO) {
        mediaUris.take(4).mapNotNull { uri ->
            val mime = appContext.contentResolver.getType(uri) ?: "image/jpeg"
            when (val up = mediaApi.upload(uri)) {
                is ApiResult.Success -> PostMedia(
                    objectKey = up.value.objectKey,
                    mimeType = mime,
                    durationMs = null
                )
                is ApiResult.Error -> null
            }
        }
    }

    fun doPublish() {
        if (!canPublish) return
        if (scheduledAt > System.currentTimeMillis()) {
            // الجدولة نص/استطلاع V1 (الوسائط تُنشر فورًا — تُستبعد من المجدول).
            val cleanPoll = if (isPoll) pollOptions.map { it.trim() }.filter { it.length >= 2 } else emptyList()
            feed.schedulePost(text.trim(), visibility, cleanPoll, pollHours.takeIf { isPoll }, scheduledAt) { onPublished() }
            return
        }
        coroutineScope.launch {
            if (mediaUris.isNotEmpty()) {
                uploadingMedia = true
                val uploaded = uploadMedia()
                uploadingMedia = false
                if (uploaded.isEmpty()) {
                    android.widget.Toast.makeText(context, "تعذر رفع الوسائط", android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }
                feed.createWithMedia(text.trim(), visibility, uploaded, tags, mentions) { onPublished() }
            } else if (isPoll) {
                feed.createPoll(text.trim(), pollOptions, pollHours, visibility) { onPublished() }
            } else {
                // وسوم تُستخرج تلقائيًا — لا حاجة لإدخال منفصل (أقل احتكاكًا من X).
                feed.createWithMedia(text.trim(), visibility, emptyList(), tags, mentions) { onPublished() }
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SovereignAvatarThemed(username.take(1), size = 40.dp)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text("منشور جديد", fontWeight = FontWeight.Bold)
                    Text(
                        if (draftSavedTick > 0) "مسودة محفوظة تلقائيًا ✓" else "مسودة مشفرة تلقائيًا",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "إغلاق") }
            }

            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(2000) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
                placeholder = { Text(if (isPoll) "سؤال الاستطلاع…" else "ماذا يحدث في يونس؟") },
                maxLines = 7
            )
            Text("${text.length}/2000", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // الجمهور + نمط المنشور
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(visibility == "PUBLIC", { visibility = "PUBLIC" }, { Text("عام") })
                FilterChip(visibility == "FRIENDS", { visibility = "FRIENDS" }, { Text("الأصدقاء") })
                FilterChip(visibility == "PRIVATE", { visibility = "PRIVATE" }, { Text("خاص (أنا فقط)") })
                Spacer(Modifier.weight(1f))
                FilterChip(isPoll, { isPoll = !isPoll }, { Text("استطلاع") })
            }
            if (isPoll) {
                pollOptions.forEachIndexed { i, v ->
                    OutlinedTextField(
                        value = v, onValueChange = { next -> pollOptions = pollOptions.toMutableList().also { it[i] = next.take(80) } },
                        modifier = Modifier.fillMaxWidth(), label = { Text("الخيار ${i + 1}") }, singleLine = true
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ if (pollOptions.size < 6) pollOptions = pollOptions + "" }, Modifier.weight(1f), enabled = pollOptions.size < 6) { Text("+ خيار") }
                    OutlinedButton({ if (pollOptions.size > 2) pollOptions = pollOptions.dropLast(1) }, Modifier.weight(1f), enabled = pollOptions.size > 2) { Text("- خيار") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1 to "ساعة", 24 to "يوم", 72 to "3 أيام", 168 to "أسبوع").forEach { (h, label) ->
                        FilterChip(pollHours == h, { pollHours = h }, { Text(label) })
                    }
                }
            }

            // وسائط (حتى 4 — نفس حد شبكة العرض PostMediaGallery)
            if (!isPoll) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton({ mediaPicker.launch(arrayOf("image/*", "video/*")) }, enabled = mediaUris.size < 4 && !uploadingMedia) {
                        Icon(Icons.Default.AddPhotoAlternate, null, Modifier.size(18.dp)); Text(" وسائط (${mediaUris.size}/4)")
                    }
                    if (uploadingMedia) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                }
                if (mediaUris.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(mediaUris, key = { it.toString() }) { uri ->
                            Box {
                                AsyncImage(
                                    model = uri, contentDescription = "مرفق",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp))
                                )
                                IconButton(
                                    onClick = { mediaUris.remove(uri) },
                                    modifier = Modifier.align(Alignment.TopEnd).size(24.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                ) { Icon(Icons.Default.Close, "إزالة", tint = Color.White, modifier = Modifier.size(14.dp)) }
                            }
                        }
                    }
                }
            }

            // ── معاينة حية (تتفوق على مسودة X العمياء) ──
            Text("معاينة حية", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
            PostLivePreview(
                username = username,
                text = text.ifBlank { "…" },
                visibility = visibility,
                isPoll = isPoll,
                pollOptions = pollOptions.filter { it.isNotBlank() },
                mediaCount = mediaUris.size,
                tags = tags,
                mentions = mentions
            )

            // ── الجدولة (X Premium مقابل مجانية هنا) ──
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, "جدولة", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                FilterChip(scheduledAt == 0L, { scheduledAt = 0L }, { Text("فوري") })
                FilterChip(scheduledAt == hourMark(1), { scheduledAt = hourMark(1) }, { Text("بعد ساعة") })
                FilterChip(scheduledAt == hourMark(3), { scheduledAt = hourMark(3) }, { Text("بعد 3 ساعات") })
                FilterChip(scheduledAt == tomorrowNine(), { scheduledAt = tomorrowNine() }, { Text("غدًا 9ص") })
            }
            if (scheduledAt > 0) Text("يُنشر ${scheduledLabel(scheduledAt)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { feed.saveDraft(text); onDismiss() },
                    modifier = Modifier.weight(1f), enabled = text.isNotBlank()
                ) { Text("حفظ مسودة") }
                Button(onClick = ::doPublish, modifier = Modifier.weight(2f), enabled = canPublish) {
                    if (publishing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(if (scheduledAt > 0) "جدولة النشر" else "نشر")
                }
            }
            if (feed.state is FeedState.Error) Text((feed.state as FeedState.Error).message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** بطاقة معاينة مصغرة — نفس لغة PostCard (أفاتار موحد + شارات + وسوم). */
@Composable
private fun PostLivePreview(
    username: String,
    text: String,
    visibility: String,
    isPoll: Boolean,
    pollOptions: List<String>,
    mediaCount: Int,
    tags: List<String>,
    mentions: List<String>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SovereignAvatarThemed(username.take(1), size = 36.dp)
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(username, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                    Text(if (visibility == "FRIENDS") "الأصدقاء" else if (visibility == "PRIVATE") "خاص" else "عام", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (isPoll) "استطلاع" else "منشور", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
            }
            Text(text, fontSize = 15.sp, maxLines = 5)
            if (tags.isNotEmpty() || mentions.isNotEmpty()) {
                Text((tags + mentions).joinToString(" "), fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold, maxLines = 2)
            }
            if (isPoll && pollOptions.isNotEmpty()) {
                pollOptions.take(4).forEach { opt ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)), shape = RoundedCornerShape(12.dp)) {
                        Text(opt, Modifier.padding(horizontal = 10.dp, vertical = 7.dp), fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
            if (mediaCount > 0) Text("📎 $mediaCount مرفق", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun hourMark(hours: Int): Long = System.currentTimeMillis() + hours * 3_600_000L
private fun tomorrowNine(): Long {
    val cal = java.util.Calendar.getInstance().apply {
        add(java.util.Calendar.DAY_OF_YEAR, 1)
        set(java.util.Calendar.HOUR_OF_DAY, 9); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0)
    }
    return cal.timeInMillis
}

// ─────────────────────────────────────────────
// 2) مؤلف الحالات مع معاينة حية (صورة/فيديو/صوت/نص)
// ─────────────────────────────────────────────

/**
 * مؤلف الحالات — يفعّل createTextStory/createVoiceStory (كانتا كودًا ميتًا
 * بلا منادين) ويضيف معاينة حية قبل الرفع.
 *
 * - تبويبات: صورة / فيديو / صوت / نص (الخادم يقبل image/video/VOICE/TEXT).
 * - مؤقت 24h ظاهر ("تُحذف تلقائيًا غدًا …") — نفس عقد StoryCleanupWorker.
 * - جمهور: جهات اتصالي / الجميع / محددون (يُمرر allowedUserIds).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryComposerSheet(
    stories: StoryViewModel,
    onDismiss: () -> Unit,
    /** مرشحو الجمهور المحدد: (redId, displayName) — يُمرَّر من جهات الاتصال. */
    candidates: List<Pair<String, String>> = emptyList()
) {
    var tab by remember { mutableStateOf(0) } // 0 صورة 1 فيديو 2 صوت 3 نص
    var caption by remember { mutableStateOf("") }
    var textStory by remember { mutableStateOf("") }
    var bgColor by remember { mutableStateOf(STORY_BG_COLORS[0]) }
    var visibility by remember { mutableStateOf(StoryVisibility.CONTACTS) }
    val selectedIds = remember { mutableStateListOf<String>() }
    var audienceSearch by remember { mutableStateOf("") }
    var mediaUri by remember { mutableStateOf<Uri?>(null) }
    var audioUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val uploading = stories.state is com.red.sovereign.stories.StoryState.Uploading

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            mediaUri = uri
        }
    }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            audioUri = uri
        }
    }

    val expiryLabel = remember {
        val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale("ar"))
        "تُحذف تلقائيًا بعد 24 ساعة (غدًا ${fmt.format(java.util.Date(System.currentTimeMillis() + 24 * 3_600_000L))})"
    }
    val canPublish = !uploading && when (tab) {
        0, 1 -> mediaUri != null
        2 -> audioUri != null
        else -> textStory.trim().length in 1..500
    } && (visibility != StoryVisibility.SELECTED || selectedIds.isNotEmpty())

    fun doPublish() {
        if (!canPublish) return
        val allowed = if (visibility == StoryVisibility.SELECTED) selectedIds.toList() else emptyList()
        when (tab) {
            0, 1 -> mediaUri?.let { stories.upload(it, caption.ifBlank { null }, visibleTo = visibility, allowedUserIds = allowed) }
            2 -> audioUri?.let {
                // المدة عبر MetadataRetriever مع release صريح (use يتطلب API 29+).
                val dur = runCatching {
                    val retriever = android.media.MediaMetadataRetriever()
                    try {
                        retriever.setDataSource(context, it)
                        retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    } finally {
                        runCatching { retriever.release() }
                    }
                }.getOrElse { 0L }
                stories.createVoiceStory(it, dur, emptyList(), visibleTo = visibility, allowedUserIds = allowed)
            }
            else -> stories.createTextStory(textStory.trim(), bgColor, visibleTo = visibility, allowedUserIds = allowed)
        }
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("حالة جديدة", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(expiryLabel, fontSize = 12.sp, color = MaterialTheme.colorScheme.tertiary)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("صورة" to Icons.Default.AddPhotoAlternate, "فيديو" to Icons.Default.Videocam, "صوت" to Icons.Default.Mic, "نص" to Icons.Default.TextFields).forEachIndexed { i, (label, icon) ->
                    FilterChip(tab == i, { tab = i; }, { Icon(icon, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(label) })
                }
            }

            // ── المعاينة الحية ──
            StoryLivePreview(
                tab = tab,
                mediaUri = mediaUri,
                audioUri = audioUri,
                textStory = textStory,
                bgColor = bgColor,
                caption = caption,
                onPickMedia = { imagePicker.launch(if (tab == 1) arrayOf("video/*") else arrayOf("image/*")) },
                onPickAudio = { audioPicker.launch(arrayOf("audio/*")) }
            )

            if (tab == 3) {
                OutlinedTextField(textStory, { textStory = it.take(500) }, Modifier.fillMaxWidth(), placeholder = { Text("اكتب حالتك النصية…") }, maxLines = 4)
                Text("${textStory.length}/500", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    STORY_BG_COLORS.forEach { c ->
                        val parsed = runCatching { Color(android.graphics.Color.parseColor(c)) }.getOrElse { Color(0xFF1565C0) }
                        TextButton({ bgColor = c }) {
                            Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(parsed)) {
                                if (bgColor == c) Text("✓", color = Color.White, modifier = Modifier.align(Alignment.Center))
                            }
                        }
                    }
                }
            } else {
                OutlinedTextField(caption, { caption = it.take(280) }, Modifier.fillMaxWidth(), placeholder = { Text("تعليق اختياري…") }, maxLines = 3)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(visibility == StoryVisibility.CONTACTS, { visibility = StoryVisibility.CONTACTS }, { Text("جهات اتصالي") })
                FilterChip(visibility == StoryVisibility.EVERYONE, { visibility = StoryVisibility.EVERYONE }, { Text("الجميع") })
                FilterChip(visibility == StoryVisibility.SELECTED, { visibility = StoryVisibility.SELECTED }, { Text("محددون") })
            }
            if (visibility == StoryVisibility.SELECTED) {
                val q = audienceSearch.trim()
                val filtered = remember(audienceSearch, candidates) {
                    if (q.isEmpty()) candidates
                    else candidates.filter { it.first.contains(q, ignoreCase = true) || it.second.contains(q, ignoreCase = true) }
                }
                OutlinedTextField(audienceSearch, { audienceSearch = it }, Modifier.fillMaxWidth(), placeholder = { Text("بحث بالاسم أو المعرف…") }, maxLines = 1)
                Text("المحددون (${selectedIds.size})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (filtered.isEmpty()) {
                    Text("لا توجد جهات مطابقة", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 180.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(filtered, key = { it.first }) { (redId, name) ->
                            val checked = redId in selectedIds
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked, { on ->
                                    if (on) { if (redId !in selectedIds) selectedIds.add(redId) }
                                    else selectedIds.remove(redId)
                                })
                                Column(Modifier.weight(1f)) {
                                    Text(name, fontSize = 14.sp)
                                    Text(redId, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            Button(onClick = ::doPublish, modifier = Modifier.fillMaxWidth(), enabled = canPublish) {
                if (uploading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("نشر الحالة")
            }
            if (stories.state is com.red.sovereign.stories.StoryState.Error) {
                Text((stories.state as com.red.sovereign.stories.StoryState.Error).message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** معاينة الحالة قبل الرفع — صورة/فيديو/صوت/نص بنفس لغة العارض. */
@Composable
private fun StoryLivePreview(
    tab: Int,
    mediaUri: Uri?,
    audioUri: Uri?,
    textStory: String,
    bgColor: String,
    caption: String,
    onPickMedia: () -> Unit,
    onPickAudio: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().height(220.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
            when (tab) {
                0 -> if (mediaUri != null) AsyncImage(mediaUri, "معاينة الصورة", Modifier.fillMaxWidth().height(220.dp), contentScale = ContentScale.Crop)
                    else OutlinedButton(onClick = onPickMedia) { Text("اختيار صورة") }
                1 -> if (mediaUri != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Videocam, null, tint = Color.White, modifier = Modifier.size(40.dp))
                        Text("فيديو جاهز ✓", color = Color.White, fontSize = 13.sp)
                        TextButton(onClick = onPickMedia) { Text("تغيير") }
                    }
                } else OutlinedButton(onClick = onPickMedia) { Text("اختيار فيديو") }
                2 -> if (audioUri != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Mic, null, tint = Color.White, modifier = Modifier.size(40.dp))
                        Text("مقطع صوتي جاهز ✓", color = Color.White, fontSize = 13.sp)
                        TextButton(onClick = onPickAudio) { Text("تغيير") }
                    }
                } else OutlinedButton(onClick = onPickAudio) { Text("اختيار مقطع صوتي") }
                else -> {
                    val bg = runCatching { Color(android.graphics.Color.parseColor(bgColor)) }.getOrElse { Color(0xFF1565C0) }
                    Box(Modifier.fillMaxWidth().height(220.dp).background(bg), contentAlignment = Alignment.Center) {
                        Text(textStory.ifBlank { "معاينة النص…" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(16.dp))
                    }
                }
            }
            if (caption.isNotBlank() && tab != 3) {
                Text(caption, color = Color.White, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomCenter).background(Color.Black.copy(alpha = 0.5f)).padding(8.dp).fillMaxWidth())
            }
        }
    }
}
