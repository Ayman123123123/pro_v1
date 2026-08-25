package com.red.sovereign.features.media

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.crypto.DecryptedMessage
import com.red.sovereign.media.AttachmentState
import com.red.sovereign.media.AttachmentViewModel
import com.red.sovereign.media.FileTypeUtil
import kotlinx.serialization.json.Json
import java.io.File

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ChatAttachmentBubble(
    item: DecryptedMessage,
    attachments: AttachmentViewModel,
    json: Json,
    isSent: Boolean,
    onOpen: (File, String, String) -> Unit,
    onRequestDownload: (String, DecryptedMessage) -> Unit = { _, _ -> },
    onDelete: (() -> Unit)? = null,
    onForward: (() -> Unit)? = null,
    onInfo: (() -> Unit)? = null,
    onSaveTo: ((File) -> Unit)? = null,
    onOpenWith: ((File) -> Unit)? = null
) {
    val manifest = remember(item.id) { parseManifest(item, json) } ?: return
    val state = attachments.getDownloadState(item.id)
    val working = state is AttachmentState.Working
    val errorText = (state as? AttachmentState.Error)?.message
    val file: File? = (state as? AttachmentState.Downloaded)?.path?.let { p ->
        remember(p) { File(p).takeIf(File::isFile) }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(item.id, com.red.sovereign.settings.SettingsRuntime.current.autoDownloadWifi, com.red.sovereign.settings.SettingsRuntime.current.autoDownloadMobile) {
        if (com.red.sovereign.core.RedQualityManager.shouldAutoDownload(context, manifest.size) &&
            state is AttachmentState.Idle && !item.outgoing
        ) {
            attachments.download(item.id, runCatching { item.plaintext.toString(Charsets.UTF_8) }.getOrDefault("{}"))
        }
    }

    var showActions by remember { mutableStateOf(false) }

    val containerColor = when {
        isSent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        working -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
    }

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            Modifier
                .combinedClickable(onClick = {
                    val f = file
                    if (f != null) onOpen(f, manifest.name, manifest.mimeType)
                    else if (!working) onRequestDownload(manifestJsonFor(item), item)
                }, onLongClick = { showActions = true })
                .padding(12.dp)
        ) {
            if (manifest.mimeType.startsWith("image/") && file != null) {
                Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(12.dp))) {
                    ThumbnailImage(file = file, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(8.dp))
            } else if (manifest.mimeType.startsWith("video/") && file != null) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))) {
                    ThumbnailVideo(file = file, modifier = Modifier.fillMaxSize())
                }
                Spacer(Modifier.height(8.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                val color = FileTypeUtil.getFileColor(manifest.mimeType)
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        working -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = color)
                        errorText != null -> Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFD32F2F), modifier = Modifier.size(22.dp))
                        else -> Icon(FileTypeUtil.getFileIcon(manifest.mimeType, manifest.name), null, tint = color, modifier = Modifier.size(22.dp))
                    }
                }
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(manifest.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    val subtitle = when {
                        working -> "جارٍ التنزيل وفك التشفير…"
                        errorText != null -> "فشل — اضغط لإعادة المحاولة"
                        file != null -> FileTypeUtil.formatFileSize(manifest.size)
                        else -> "اضغط للتنزيل · ${FileTypeUtil.formatFileSize(manifest.size)}"
                    }
                    Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when {
                    file != null && !working -> Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    working -> {}
                    else -> Icon(Icons.Default.CloudDownload, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }

            if (errorText != null) {
                Button(
                    onClick = { if (!working) onRequestDownload(manifestJsonFor(item), item) },
                    modifier = Modifier.padding(top = 8.dp).fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                    Text(" إعادة محاولة التنزيل", fontSize = 12.sp)
                }
            }
        }
    }

    if (showActions) {
        AlertDialog(
            onDismissRequest = { showActions = false },
            title = { Text(manifest.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ActionRow(Icons.Default.Visibility, "فتح", MaterialTheme.colorScheme.primary, enabled = file != null) {
                        showActions = false
                        file?.let { onOpen(it, manifest.name, manifest.mimeType) }
                    }
                    ActionRow(Icons.Default.Download, "حفظ في الجهاز", MaterialTheme.colorScheme.secondary, enabled = file != null) {
                        showActions = false
                        file?.let { onSaveTo?.invoke(it) }
                    }
                    ActionRow(Icons.Default.OpenInNew, "فتح بتطبيق آخر", MaterialTheme.colorScheme.tertiary, enabled = file != null) {
                        showActions = false
                        file?.let { onOpenWith?.invoke(it) }
                    }
                    if (onForward != null) ActionRow(Icons.Default.Forward, "تحويل", MaterialTheme.colorScheme.onSurface, enabled = true) {
                        showActions = false; onForward()
                    }
                    if (onInfo != null) ActionRow(Icons.Default.Info, "معلومات الملف", MaterialTheme.colorScheme.onSurface, enabled = true) {
                        showActions = false; onInfo()
                    }
                    if (onDelete != null) ActionRow(Icons.Default.Delete, "حذف", Color(0xFFD32F2F), enabled = true) {
                        showActions = false; onDelete()
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton({ showActions = false }) { Text("إغلاق") } }
        )
    }
}

private fun manifestJsonFor(item: DecryptedMessage): String =
    runCatching { item.plaintext.toString(Charsets.UTF_8) }.getOrDefault("{}")

@Composable
private fun ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(enabled = enabled, onClick = onClick, onLongClick = {})
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (enabled) color else color.copy(alpha = 0.35f), modifier = Modifier.size(20.dp))
        Text(label, modifier = Modifier.padding(start = 12.dp), color = if (enabled) color else color.copy(alpha = 0.35f), fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}

@Composable
fun UploadProgressBubble(fileName: String, mimeType: String, progress: Float, onCancel: () -> Unit) {
    val color = FileTypeUtil.getFileColor(mimeType)
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(9.dp)).background(color.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    Icon(FileTypeUtil.getFileIcon(mimeType, fileName), null, tint = color, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(fileName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Text("تشفير ورفع آمن… ${(progress * 100).toInt()}%", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                IconButton(onClick = onCancel) { Icon(Icons.Default.Close, "إلغاء") }
            }
            Box(
                Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.outlineVariant)
            ) {
                Box(
                    Modifier.fillMaxWidth(progress.coerceIn(0.02f, 1f)).height(5.dp)
                        .clip(RoundedCornerShape(3.dp)).background(color)
                )
            }
        }
    }
}
