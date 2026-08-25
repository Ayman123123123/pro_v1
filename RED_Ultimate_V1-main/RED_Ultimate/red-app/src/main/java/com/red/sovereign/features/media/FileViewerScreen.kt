package com.red.sovereign.features.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.crypto.DecryptedMessage
import com.red.sovereign.media.AttachmentManifest
import com.red.sovereign.media.AttachmentState
import com.red.sovereign.media.AttachmentViewModel
import com.red.sovereign.media.FileTypeUtil
import kotlinx.serialization.json.Json
import java.io.File

@Composable
fun FileViewerScreen(
    file: File,
    fileName: String,
    mimeType: String,
    onDismiss: () -> Unit,
    onSave: (() -> Unit)? = null,
    onOpenWith: ((File) -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    when {
        mimeType.startsWith("image/") -> ImageViewerScreen(file, fileName, mimeType, onDismiss, onSave, onOpenWith, onDelete)
        mimeType.startsWith("video/") -> VideoPlayerScreen(file, fileName, onDismiss, onSave, onOpenWith, onDelete)
        mimeType.startsWith("audio/") -> AudioPlayerScreen(file, fileName, onDismiss, onSave, onOpenWith, onDelete)
        else -> DocumentViewerScreen(file, fileName, mimeType, onDismiss, onSave, onOpenWith, onDelete)
    }
}

fun parseManifest(item: DecryptedMessage, json: Json): AttachmentManifest? {
    val raw = runCatching { item.plaintext.toString(Charsets.UTF_8) }.getOrNull() ?: return null
    return runCatching { json.decodeFromString<AttachmentManifest>(raw) }.getOrNull()
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MediaGalleryItem(
    item: DecryptedMessage,
    attachments: AttachmentViewModel,
    json: Json,
    isBusy: Boolean,
    onOpen: (File, AttachmentManifest) -> Unit,
    onRequestDownload: (AttachmentManifest) -> Unit,
    onLongPress: (DecryptedMessage) -> Unit
) {
    val manifest = remember(item.id) { parseManifest(item, json) } ?: return
    val state = attachments.getDownloadState(item.id)
    val downloadedFile = (state as? AttachmentState.Downloaded)?.let { s ->
        remember(s.path) { File(s.path).takeIf(File::isFile) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                onClick = {
                    val f = downloadedFile
                    when {
                        f != null -> onOpen(f, manifest)
                        state !is AttachmentState.Working -> onRequestDownload(manifest)
                    }
                },
                onLongClick = { onLongPress(item) }
            )
    ) {
        val f = downloadedFile
        if (f != null) {
            when {
                item.type == "IMAGE" || manifest.mimeType.startsWith("image/") ->
                    ThumbnailImage(file = f, modifier = Modifier.fillMaxSize())
                item.type == "VIDEO" || manifest.mimeType.startsWith("video/") ->
                    ThumbnailVideo(file = f, modifier = Modifier.fillMaxSize(), durationMs = 0L)
                else ->
                    ThumbnailDocument(fileName = manifest.name, mimeType = manifest.mimeType, modifier = Modifier.fillMaxSize())
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when {
                    state is AttachmentState.Working -> CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                    else -> Icon(
                        when {
                            item.type == "VIDEO" -> Icons.Default.Videocam
                            item.type == "AUDIO" -> Icons.Default.MusicNote
                            item.type == "IMAGE" -> Icons.Default.CloudDownload
                            else -> Icons.Default.InsertDriveFile
                        },
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MediaListItem(
    item: DecryptedMessage,
    attachments: AttachmentViewModel,
    json: Json,
    selected: Boolean,
    onOpen: (File, AttachmentManifest) -> Unit,
    onRequestDownload: (AttachmentManifest) -> Unit,
    onToggleSelect: (DecryptedMessage) -> Unit
) {
    val manifest = remember(item.id) { parseManifest(item, json) } ?: return
    val state = attachments.getDownloadState(item.id)
    val working = state is AttachmentState.Working
    val downloadedFile = (state as? AttachmentState.Downloaded)?.path?.let { p -> remember(p) { File(p).takeIf(File::isFile) } }

    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable {
                    val f = downloadedFile
                    if (f != null) onOpen(f, manifest)
                    else if (!working) onRequestDownload(manifest)
                }
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val color = FileTypeUtil.getFileColor(manifest.mimeType)
            val icon = FileTypeUtil.getFileIcon(manifest.mimeType, manifest.name)
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                if (working) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = color)
                else Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(manifest.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Row {
                    Text(FileTypeUtil.formatFileSize(manifest.size), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SpacerWidth(6)
                    Text(manifest.mimeType.substringBefore('/').uppercase(), fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (downloadedFile != null) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun SpacerWidth(dpValue: Int) {
    androidx.compose.foundation.layout.Spacer(Modifier.size(dpValue.dp))
}
