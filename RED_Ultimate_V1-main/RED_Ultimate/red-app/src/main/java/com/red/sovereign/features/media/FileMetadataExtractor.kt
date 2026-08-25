package com.red.sovereign.features.media

import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.media.FileTypeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExtractedMeta(
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
    val pageCount: Int? = null,
    val lastModified: Long = 0L
)

suspend fun extractMeta(file: File, mimeType: String): ExtractedMeta =
    withContext(Dispatchers.IO) {
        when {
            mimeType.startsWith("image/") -> {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, opts)
                ExtractedMeta(width = opts.outWidth.takeIf { it > 0 }, height = opts.outHeight.takeIf { it > 0 }, lastModified = file.lastModified())
            }
            mimeType.startsWith("video/") || mimeType.startsWith("audio/") -> {
                runCatching {
                    val r = MediaMetadataRetriever()
                    r.setDataSource(file.absolutePath)
                    val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                    val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                    val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    r.release()
                    ExtractedMeta(width = w, height = h, durationMs = d, lastModified = file.lastModified())
                }.getOrDefault(ExtractedMeta(lastModified = file.lastModified()))
            }
            mimeType == "application/pdf" -> {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    runCatching {
                        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                            PdfRenderer(fd).use { renderer -> ExtractedMeta(pageCount = renderer.pageCount, lastModified = file.lastModified()) }
                        }
                    }.getOrDefault(ExtractedMeta(lastModified = file.lastModified()))
                } else ExtractedMeta(lastModified = file.lastModified())
            }
            else -> ExtractedMeta(lastModified = file.lastModified())
        }
    }

@Composable
fun FileInfoDialog(
    file: File,
    fileName: String,
    mimeType: String,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var meta by remember { mutableStateOf<ExtractedMeta?>(null) }
    LaunchedEffect(file.absolutePath) { meta = extractMeta(file, mimeType) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(fileName, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoLine(FileTypeUtil.getFileIcon(mimeType, fileName), "النوع", mimeType)
                InfoLine(Icons.Default.Description, "الحجم", FileTypeUtil.formatFileSize(file.length()))
                meta?.let { m ->
                    if (m.width != null && m.height != null)
                        InfoLine(Icons.Default.CropFree, "الأبعاد", "${m.width} × ${m.height}")
                    m.durationMs?.takeIf { it > 0 }?.let {
                        InfoLine(Icons.Default.Timer, "المدة", formatMediaTime(it))
                    }
                    m.pageCount?.let {
                        InfoLine(Icons.Default.Description, "عدد الصفحات", "$it")
                    }
                    if (m.lastModified > 0)
                        InfoLine(Icons.Default.CalendarToday, "آخر تعديل", SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date(m.lastModified)))
                }
                InfoLine(Icons.Default.Folder, "الذاكرة المؤقتة", file.parent ?: "-", monospace = true)
            }
        },
        confirmButton = {},
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                onDelete?.let {
                    Button(onClick = it, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))) {
                        Text("حذف", color = Color.White)
                    }
                }
                TextButton(onDismiss) { Text("إغلاق") }
            }
        }
    )
}

@Composable
private fun InfoLine(icon: ImageVector, label: String, value: String, monospace: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp).padding(top = 1.dp))
        Column(Modifier.padding(start = 10.dp)) {
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
