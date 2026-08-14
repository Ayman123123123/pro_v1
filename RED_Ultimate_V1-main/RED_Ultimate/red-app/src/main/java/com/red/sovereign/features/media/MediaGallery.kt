package com.red.sovereign.features.media

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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

@Composable
fun ConversationMediaGrid(
    messages: List<DecryptedMessage>,
    modifier: Modifier = Modifier,
    attachments: AttachmentViewModel? = null,
) {
    var filter by remember { mutableStateOf(0) }
    val allMedia = remember(messages) {
        messages.filter { it.type in setOf("IMAGE", "VIDEO", "FILE", "AUDIO") }
            .sortedByDescending { it.timestamp }
    }
    val filtered = remember(allMedia, filter) {
        when (filter) {
            1 -> allMedia.filter { it.type == "IMAGE" }
            2 -> allMedia.filter { it.type == "VIDEO" }
            3 -> allMedia.filter { it.type == "FILE" }
            4 -> allMedia.filter { it.type == "AUDIO" }
            else -> allMedia
        }
    }
    val context = LocalContext.current
    val repository = remember {
        EncryptedAttachmentRepository(context, AuthorizedApiClient(TokenStore(context)))
    }
    var viewer by remember { mutableStateOf<DecryptedMessage?>(null) }

    Column(modifier) {
        if (allMedia.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                Text("لا توجد صور أو ملفات مفكوكة هنا بعد", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("الكل" to 0, "صور" to 1, "فيديو" to 2, "ملفات" to 3, "صوت" to 4).forEach { (label, idx) ->
                    FilterChip(selected = filter == idx, onClick = { filter = idx }, label = { Text(label, fontSize = 11.sp) })
                }
            }
            Text("${filtered.size} من ${allMedia.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(bottom = 6.dp))
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Text("لا توجد عناصر في هذا الفلتر", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            } else {
                val visual = filtered.filter { it.type == "IMAGE" || it.type == "VIDEO" }
                val others = filtered.filter { it.type == "FILE" || it.type == "AUDIO" }
                if (visual.isNotEmpty()) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.height(280.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(visual, key = { it.id }) { item ->
                            MediaThumbnail(item, repository) { viewer = item }
                        }
                    }
                }
                others.forEach { item ->
                    MediaListRow(item, onOpen = { viewer = item })
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
