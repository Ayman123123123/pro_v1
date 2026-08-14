package com.red.sovereign.features.media

import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.red.sovereign.crypto.DecryptedMessage
import com.red.sovereign.media.AttachmentManifest
import com.red.sovereign.media.AttachmentState
import com.red.sovereign.media.AttachmentViewModel
import com.red.sovereign.ui.theme.AqyalCyanGlow
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.YounesEmerald
import kotlinx.serialization.json.Json
import java.io.File

/**
 * معرض الوسائط الاحترافي — شبكة صور مصغّرة + فلترة بالنوع + عدّاد.
 * يعمل للمحادثات الفردية والمجموعات. الوسائط مشفّرة E2EE.
 * النقر على أي وسيط: تنزيل وفك تشفير ثم فتح بالعارض المناسب.
 */
@Composable
fun MediaGalleryDialog(
    title: String,
    messages: List<DecryptedMessage>,
    attachments: AttachmentViewModel,
    onDismiss: () -> Unit
) {
    val json = remember { Json { ignoreUnknownKeys = true } }
    val context = LocalContext.current

    // فلتر النوع: 0=الكل، 1=صور، 2=فيديو، 3=ملفات، 4=صوت
    var filter by remember { mutableStateOf(0) }

    // الوسيط الجاري فتحه (تنزيل + عرض تلقائي عند اكتمال فك التشفير)
    var openingManifest by remember { mutableStateOf<AttachmentManifest?>(null) }
    var openingMessageId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(openingManifest, openingMessageId?.let { attachments.getDownloadState(it) }) {
        val current = openingMessageId?.let { attachments.getDownloadState(it) } ?: AttachmentState.Idle
        if (openingManifest != null && current is AttachmentState.Downloaded && current.name == openingManifest?.name) {
            val file = File(current.path)
            if (file.isFile) {
                val uri = FileProvider.getUriForFile(context, "com.red.sovereign.fileprovider", file)
                runCatching {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, openingManifest?.mimeType ?: "application/octet-stream")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(intent)
                }.onFailure { cause ->
                    // لا يوجد تطبيق معالِج لهذا النوع — إبلاغ واضح بدل الفشل الصامت
                    android.widget.Toast.makeText(
                        context,
                        "لا يوجد تطبيق لفتح هذا النوع من الملفات",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
            openingManifest = null
        }
    }

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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FilterList, null, tint = AqyalGold, modifier = Modifier.size(20.dp))
                Text("  $title (${allMedia.size})", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            if (allMedia.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Text("لا توجد وسائط مشتركة بعد", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("الكل" to 0, "صور" to 1, "فيديو" to 2, "ملفات" to 3, "صوت" to 4).forEach { (label, idx) ->
                            FilterChip(
                                selected = filter == idx,
                                onClick = { filter = idx },
                                label = { Text(label, fontSize = 11.sp) }
                            )
                        }
                    }
                    if (filtered.isEmpty()) {
                        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            Text("لا توجد ${listOf("الكل","صور","فيديو","ملفات","صوت")[filter]}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        }
                    } else {
                        if (filter == 0 || filter == 1 || filter == 2) {
                            val gridItems = filtered.filter { it.type in setOf("IMAGE", "VIDEO") }
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                modifier = Modifier.height(360.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(gridItems, key = { it.id }) { item -> MediaThumbnail(item, attachments, json, openingManifest != null) { manifest, id -> openingManifest = manifest; openingMessageId = id } }
                            }
                            if (filter == 0) {
                                val others = filtered.filter { it.type in setOf("FILE", "AUDIO") }
                                if (others.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    others.forEach { item -> MediaListRow(item, attachments, json, openingManifest != null) { manifest, id -> openingManifest = manifest; openingMessageId = id } }
                                }
                            }
                        } else {
                            Column(Modifier.height(360.dp)) {
                                filtered.forEach { item -> MediaListRow(item, attachments, json, openingManifest != null) { manifest, id -> openingManifest = manifest; openingMessageId = id } }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("إغلاق") } }
    )
}

/** خلية شبكة: أيقونة نوع واضحة + النقر يفتح الوسيط (تنزيل مشفّر ثم عرض). */
@Composable
private fun MediaThumbnail(
    item: DecryptedMessage,
    attachments: AttachmentViewModel,
    json: Json,
    isOpening: Boolean,
    onOpen: (AttachmentManifest, String) -> Unit
) {
    val isVideo = item.type == "VIDEO"
    val manifestJson = item.plaintext.toString(Charsets.UTF_8)
    val manifest = remember(manifestJson) { runCatching { json.decodeFromString<AttachmentManifest>(manifestJson) }.getOrNull() }

    Surface(
        Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).clickable(enabled = !isOpening && manifest != null) {
            manifest?.let { onOpen(it, item.id); attachments.download(item.id, manifestJson) }
        },
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (item.type) {
                "IMAGE" -> Icon(Icons.Default.Photo, null, tint = YounesEmerald, modifier = Modifier.size(32.dp))
                "VIDEO" -> Icon(Icons.Default.Videocam, null, tint = AqyalCyanGlow, modifier = Modifier.size(32.dp))
                else -> Icon(Icons.Default.InsertDriveFile, null, modifier = Modifier.size(28.dp))
            }
            if (isVideo) {
                Surface(
                    Modifier.align(Alignment.Center).size(28.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = Color.Black.copy(alpha = 0.5f)
                ) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                }
            }
        }
    }
}

/** صف قائمة للملفات والصوت — أيقونة + اسم + حجم + تاريخ + فتح عند النقر. */
@Composable
private fun MediaListRow(
    item: DecryptedMessage,
    attachments: AttachmentViewModel,
    json: Json,
    isOpening: Boolean,
    onOpen: (AttachmentManifest, String) -> Unit
) {
    val manifestJson = item.plaintext.toString(Charsets.UTF_8)
    val manifest = remember(item.id) {
        runCatching { json.decodeFromString<AttachmentManifest>(manifestJson) }.getOrNull()
    }
    Surface(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable(enabled = !isOpening && manifest != null) {
            manifest?.let { onOpen(it, item.id); attachments.download(item.id, manifestJson) }
        },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(
                    when (item.type) {
                        "AUDIO" -> AqyalCyanGlow.copy(alpha = 0.16f)
                        else -> AqyalGold.copy(alpha = 0.16f)
                    }
                ),
                contentAlignment = Alignment.Center
            ) {
                when (item.type) {
                    "AUDIO" -> Icon(Icons.Default.MusicNote, null, tint = AqyalCyanGlow, modifier = Modifier.size(22.dp))
                    else -> Icon(Icons.Default.InsertDriveFile, null, tint = AqyalGold, modifier = Modifier.size(22.dp))
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(
                    manifest?.name ?: "وسيط مشفّر",
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold, fontSize = 13.sp
                )
                Text(
                    "${manifest?.let { formatBytes(it.size) } ?: ""} · ${item.type}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
            if (isOpening && attachments.getDownloadState(item.id) is AttachmentState.Working) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = AqyalGold)
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}



