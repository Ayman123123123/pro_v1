package com.red.sovereign.features.media

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.core.database.LocalHistoryEntity
import com.red.sovereign.crypto.DecryptedMessage
import com.red.sovereign.media.AttachmentManifest
import com.red.sovereign.media.AttachmentViewModel
import com.red.sovereign.media.EncryptedAttachmentRepository
import com.red.sovereign.ui.theme.AqyalCyanGlow
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.YounesEmerald
import kotlinx.serialization.json.Json
import java.io.File

fun LocalHistoryEntity.toDecryptedMessage() = DecryptedMessage(
    id = id,
    conversationId = conversationId,
    senderRedId = senderId,
    plaintext = encryptedPlaintext,
    timestamp = createdAt,
    sequence = 0,
    type = messageType,
    outgoing = outgoing,
    status = status,
)

/**
 * معرض الوسائط — شبكة صور مفكوكة على الجهاز، لا أيقونات وهمية.
 * كل خلية تفك تشفيرها بنفسها حتى لا تتشارك حالة AttachmentViewModel.
 */
@Composable
fun MediaGalleryDialog(
    title: String,
    messages: List<DecryptedMessage>,
    attachments: AttachmentViewModel,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FilterList, null, tint = AqyalGold, modifier = Modifier.size(20.dp))
                Text("  $title", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            ConversationMediaGrid(
                messages = messages,
                modifier = Modifier.fillMaxWidth().height(420.dp),
                attachments = attachments,
            )
        },
        confirmButton = { TextButton(onDismiss) { Text("إغلاق") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConversationMediaGrid(
    messages: List<DecryptedMessage>,
    modifier: Modifier = Modifier,
    attachments: AttachmentViewModel? = null,
) {
    var filter by remember { mutableStateOf(0) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    val allMedia = remember(messages) {
        messages.filter { it.type in setOf("IMAGE", "VIDEO", "FILE", "AUDIO") }
            .sortedByDescending { it.timestamp }
    }
    val linkMessages = remember(messages) {
        messages.filter { it.plaintext.toString(Charsets.UTF_8).contains("http", true) || it.plaintext.toString(Charsets.UTF_8).contains("www.", true) }
            .sortedByDescending { it.timestamp }
    }
    val voiceMessages = remember(messages) {
        messages.filter { it.type == "AUDIO" || it.plaintext.toString(Charsets.UTF_8).contains("[voice]", true) }
            .sortedByDescending { it.timestamp }
    }
    val filtered = remember(allMedia, filter, linkMessages, voiceMessages) {
        when (filter) {
            1 -> allMedia.filter { it.type == "IMAGE" }
            2 -> allMedia.filter { it.type == "VIDEO" }
            3 -> allMedia.filter { it.type == "FILE" }
            4 -> allMedia.filter { it.type == "AUDIO" }
            5 -> linkMessages
            6 -> voiceMessages
            else -> allMedia
        }
    }
    // تجميع زمني: اليوم / أمس / هذا الأسبوع / أقدم
    val grouped = remember(filtered) {
        val now = System.currentTimeMillis()
        val dayMs = 24 * 60 * 60 * 1000L
        filtered.groupBy { msg ->
            val diff = now - msg.timestamp
            when {
                diff < dayMs && isSameDay(now, msg.timestamp) -> "اليوم"
                diff < 2 * dayMs && isSameDay(now - dayMs, msg.timestamp) -> "أمس"
                diff < 7 * dayMs -> "هذا الأسبوع"
                else -> formatMonth(msg.timestamp)
            }
        }
    }
    val context = LocalContext.current
    val repository = remember {
        EncryptedAttachmentRepository(context, AuthorizedApiClient(TokenStore(context)))
    }
    var viewer by remember { mutableStateOf<DecryptedMessage?>(null) }

    Column(modifier) {
        if (allMedia.isEmpty() && linkMessages.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                Text("لا توجد صور أو ملفات مفكوكة هنا بعد", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            // شريط الفلاتر الموسع + تحديد متعدد
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                item { FilterChip(selected = filter == 0, onClick = { filter = 0 }, label = { Text("الكل", fontSize = 11.sp) }) }
                item { FilterChip(selected = filter == 1, onClick = { filter = 1 }, label = { Text("صور", fontSize = 11.sp) }) }
                item { FilterChip(selected = filter == 2, onClick = { filter = 2 }, label = { Text("فيديو", fontSize = 11.sp) }) }
                item { FilterChip(selected = filter == 3, onClick = { filter = 3 }, label = { Text("ملفات", fontSize = 11.sp) }) }
                item { FilterChip(selected = filter == 4, onClick = { filter = 4 }, label = { Text("صوت", fontSize = 11.sp) }) }
                item { FilterChip(selected = filter == 5, onClick = { filter = 5 }, label = { Text("روابط", fontSize = 11.sp) }) }
                item { FilterChip(selected = filter == 6, onClick = { filter = 6 }, label = { Text("صوتيات", fontSize = 11.sp) }) }
                item {
                    FilterChip(selected = selectionMode, onClick = { selectionMode = !selectionMode; if (!selectionMode) selectedIds = emptySet() }, label = { Text(if (selectionMode) "إلغاء التحديد" else "تحديد", fontSize = 11.sp) })
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${filtered.size} من ${if (filter == 5) linkMessages.size else if (filter == 6) voiceMessages.size else allMedia.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                if (selectionMode && selectedIds.isNotEmpty()) {
                    Text("${selectedIds.size} محدد", color = YounesEmerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (selectionMode && selectedIds.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    androidx.compose.material3.AssistChip(onClick = { selectedIds = filtered.map { it.id }.toSet() }, label = { Text("تحديد الكل", fontSize = 11.sp) })
                    androidx.compose.material3.AssistChip(onClick = { selectedIds = emptySet() }, label = { Text("إلغاء", fontSize = 11.sp) })
                }
            }
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Text("لا توجد عناصر في هذا الفلتر", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            } else {
                // عرض مجمّع زمنياً مع عناوين لاصقة
                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.height(320.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    grouped.forEach { (dateLabel, items) ->
                        item(key = "header_$dateLabel") {
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Text(dateLabel, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = AqyalGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        val visual = items.filter { it.type == "IMAGE" || it.type == "VIDEO" }
                        val others = items.filter { it.type == "FILE" || it.type == "AUDIO" || it.type !in setOf("IMAGE","VIDEO") }
                        if (visual.isNotEmpty()) {
                            // شبكة مصغرة داخل LazyColumn عبر FlowRow
                            item(key = "grid_$dateLabel") {
                                FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    visual.forEach { msg ->
                                        Box(Modifier.size(110.dp)) {
                                            MediaThumbnailSelectable(msg, repository, selected = selectedIds.contains(msg.id), selectionMode = selectionMode, onSelect = {
                                                selectedIds = if (selectedIds.contains(msg.id)) selectedIds - msg.id else selectedIds + msg.id
                                            }, onOpen = { viewer = msg })
                                        }
                                    }
                                }
                            }
                        }
                        items.forEach { msg ->
                            if (msg.type == "FILE" || msg.type == "AUDIO" || (msg.type !in setOf("IMAGE","VIDEO") && filter == 5)) {
                                item(key = msg.id) {
                                    MediaListRow(msg, onOpen = { viewer = msg })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    viewer?.let { item ->
        MediaItemViewer(item = item, repository = repository, onDismiss = { viewer = null })
    }
}

@Composable
private fun MediaThumbnail(
    item: DecryptedMessage,
    repository: EncryptedAttachmentRepository,
    onOpen: () -> Unit,
) {
    var path by remember(item.id) { mutableStateOf<String?>(null) }
    var failed by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(item.id) {
        val json = item.plaintext.toString(Charsets.UTF_8)
        when (val result = repository.downloadAndDecrypt(json)) {
            is ApiResult.Success -> path = result.value.absolutePath
            is ApiResult.Error -> failed = true
        }
    }
    val preview = remember(path) { path?.let { decodePreview(it, item.type == "VIDEO") } }
    Surface(
        Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).clickable(onClick = onOpen),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                preview != null -> Image(preview, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                failed -> Icon(
                    if (item.type == "VIDEO") Icons.Default.Videocam else Icons.Default.Photo,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
                path == null -> CircularProgressIndicator(Modifier.size(22.dp), color = YounesEmerald, strokeWidth = 2.dp)
                else -> Icon(
                    if (item.type == "VIDEO") Icons.Default.Videocam else Icons.Default.Photo,
                    null,
                    tint = YounesEmerald,
                    modifier = Modifier.size(32.dp),
                )
            }
            if (item.type == "VIDEO") {
                Surface(Modifier.align(Alignment.Center).size(28.dp), shape = androidx.compose.foundation.shape.CircleShape, color = Color.Black.copy(alpha = 0.5f)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                }
            }
        }
    }
}

@Composable
private fun MediaListRow(item: DecryptedMessage, onOpen: () -> Unit) {
    val manifest = remember(item.id) {
        runCatching { Json { ignoreUnknownKeys = true }.decodeFromString<AttachmentManifest>(item.plaintext.toString(Charsets.UTF_8)) }.getOrNull()
    }
    Surface(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable(onClick = onOpen),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(
                    if (item.type == "AUDIO") AqyalCyanGlow.copy(alpha = 0.16f) else AqyalGold.copy(alpha = 0.16f)
                ),
                contentAlignment = Alignment.Center,
            ) {
                if (item.type == "AUDIO") Icon(Icons.Default.MusicNote, null, tint = AqyalCyanGlow, modifier = Modifier.size(22.dp))
                else Icon(Icons.Default.InsertDriveFile, null, tint = AqyalGold, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(manifest?.name ?: "وسيط مشفّر", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text("${manifest?.let { formatBytes(it.size) } ?: ""} · ${item.type}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun MediaItemViewer(
    item: DecryptedMessage,
    repository: EncryptedAttachmentRepository,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var path by remember(item.id) { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(item.id) {
        when (val result = repository.downloadAndDecrypt(item.plaintext.toString(Charsets.UTF_8))) {
            is ApiResult.Success -> path = result.value.absolutePath
            is ApiResult.Error -> error = result.message
        }
    }
    val preview = remember(path) { path?.let { decodePreview(it, item.type == "VIDEO") } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (item.type == "VIDEO") "فيديو مشفّر" else if (item.type == "IMAGE") "صورة مشفّرة" else "ملف مشفّر") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                when {
                    error != null -> Text("تعذر فك التشفير على الجهاز.", color = MaterialTheme.colorScheme.error)
                    path == null -> CircularProgressIndicator(color = YounesEmerald)
                    preview != null -> Image(preview, null, Modifier.fillMaxWidth().height(280.dp), contentScale = ContentScale.Fit)
                    else -> Text("جاهز للفتح في تطبيق خارجي بعد فك التشفير محلياً.")
                }
            }
        },
        confirmButton = {
            val filePath = path
            TextButton(
                enabled = filePath != null,
                onClick = {
                    val file = filePath?.let(::File) ?: return@TextButton
                    val uri = FileProvider.getUriForFile(context, "com.red.sovereign.fileprovider", file)
                    val mime = when (item.type) {
                        "IMAGE" -> "image/*"
                        "VIDEO" -> "video/*"
                        "AUDIO" -> "audio/*"
                        else -> "*/*"
                    }
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mime)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching { context.startActivity(intent) }
                    onDismiss()
                },
            ) { Text("فتح") }
        },
        dismissButton = { TextButton(onDismiss) { Text("إغلاق") } },
    )
}

private fun decodePreview(path: String, video: Boolean) = runCatching {
    if (video) {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(path)
        val frame = retriever.getFrameAtTime(0)
        retriever.release()
        frame?.asImageBitmap()
    } else {
        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
        BitmapFactory.decodeFile(path, opts)?.asImageBitmap()
    }
}.getOrNull()

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private fun isSameDay(a: Long, b: Long): Boolean {
    val fmt = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
    return fmt.format(java.util.Date(a)) == fmt.format(java.util.Date(b))
}

private fun formatMonth(ts: Long): String {
    return java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale("ar")).format(java.util.Date(ts))
}

@Composable
private fun MediaThumbnailSelectable(
    item: DecryptedMessage,
    repository: EncryptedAttachmentRepository,
    selected: Boolean,
    selectionMode: Boolean,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
) {
    var path by remember(item.id) { mutableStateOf<String?>(null) }
    var failed by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(item.id) {
        val json = item.plaintext.toString(Charsets.UTF_8)
        when (val result = repository.downloadAndDecrypt(json)) {
            is ApiResult.Success -> path = result.value.absolutePath
            is ApiResult.Error -> failed = true
        }
    }
    val preview = remember(path) { path?.let { decodePreview(it, item.type == "VIDEO") } }
    Surface(
        Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).clickable { if (selectionMode) onSelect() else onOpen() },
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                preview != null -> Image(preview, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                failed -> Icon(if (item.type == "VIDEO") Icons.Default.Videocam else Icons.Default.Photo, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                path == null -> CircularProgressIndicator(Modifier.size(22.dp), color = YounesEmerald, strokeWidth = 2.dp)
                else -> Icon(if (item.type == "VIDEO") Icons.Default.Videocam else Icons.Default.Photo, null, tint = YounesEmerald, modifier = Modifier.size(32.dp))
            }
            if (item.type == "VIDEO") {
                Surface(Modifier.align(Alignment.Center).size(28.dp), shape = androidx.compose.foundation.shape.CircleShape, color = Color.Black.copy(alpha = 0.5f)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                }
            }
            if (selectionMode) {
                Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (selected) YounesEmerald else Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                    if (selected) Icon(Icons.Default.Photo, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}
