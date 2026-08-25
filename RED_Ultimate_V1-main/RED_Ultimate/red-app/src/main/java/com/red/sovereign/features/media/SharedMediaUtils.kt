package com.red.sovereign.features.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.red.sovereign.media.FileTypeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class SaveLocation(val displayName: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    DOWNLOADS("التنزيلات", Icons.Default.Download),
    PICTURES("الصور", Icons.Default.PhotoLibrary),
    DOCUMENTS("المستندات", Icons.Default.Folder),
    CUSTOM("موقع مخصص", Icons.Default.DriveFileMove)
}

enum class ShareMethod(val label: String, val description: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    SYSTEM_PICKER("مشاركة عبر النظام", "افتح قائمة مشاركة Android", Icons.Default.Share),
    EXPORT_TO_STORAGE("تصدير للجهاز", "احفظ نسخاً في تخزينك المحلي", Icons.Default.Download),
    COPY_LINKS("نسخ روابط التحميل", "روابط تحميل للمستلمين", Icons.Default.ContentCopy),
    CREATE_ZIP("إنشاء أرشيف ZIP", "ملف واحد مضغوط بكل الملفات", Icons.Default.FolderZip)
}

enum class BatchAction(val label: String, val description: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    DELETE("حذف", "إزالة من المحادثة", Icons.Default.Delete),
    FORWARD("تحويل", "إرسال لمحادثة أخرى", Icons.Default.Forward),
    EXPORT("تصدير", "حفظ في تخزين الجهاز", Icons.Default.Download),
    SHARE("مشاركة", "عبر تطبيقات أخرى", Icons.Default.Share),
    INFO("معلومات", "عرض التفاصيل", Icons.Default.Info)
}

suspend fun createSaveUri(context: Context, fileName: String, location: SaveLocation): Uri? =
    withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = FileTypeUtil.getMimeFromExtension(fileName)
        val values = android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
        }
        runCatching {
            when (location) {
                SaveLocation.DOWNLOADS -> {
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                }
                SaveLocation.PICTURES -> {
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                    resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                }
                SaveLocation.DOCUMENTS -> {
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS)
                    resolver.insert(MediaStore.Files.getContentUri("external"), values)
                }
                SaveLocation.CUSTOM -> null
            }
        }.getOrNull()
    }

fun saveFileToDestination(source: File, destination: Uri, context: Context, callback: (Boolean, String) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
        val result = runCatching {
            context.contentResolver.openOutputStream(destination)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output, 64 * 1024) }
            } ?: error("UNABLE_TO_OPEN_DESTINATION")
        }
        withContext(Dispatchers.Main) {
            result.fold(
                onSuccess = { callback(true, "تم الحفظ بنجاح") },
                onFailure = { callback(false, "خطأ: ${it.message}") }
            )
        }
    }
}

fun openFileWithSystemPicker(file: File, context: Context, mimeType: String? = null) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, "com.red.sovereign.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType ?: FileTypeUtil.getMimeFromExtension(file.name))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "افتح بـ...").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        Toast.makeText(context, "لا يوجد تطبيق لفتح هذا النوع من الملفات", Toast.LENGTH_LONG).show()
    }
}

@Composable
fun SaveDestinationDialog(
    fileName: String,
    onDismiss: () -> Unit,
    onSave: (SaveLocation) -> Unit
) {
    var selected by remember { mutableStateOf(SaveLocation.DOWNLOADS) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("حفظ الملف", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                Text("اختر موقع الحفظ:", style = MaterialTheme.typography.bodyMedium)
                SaveLocation.values().forEach { location ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected == location) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth().clickable { selected = location }
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(location.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                            Text(
                                location.displayName,
                                modifier = Modifier.weight(1f).padding(start = 10.dp),
                                fontWeight = if (selected == location) FontWeight.Bold else FontWeight.Normal
                            )
                            if (selected == location) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(selected) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

fun formatMediaTime(ms: Long): String {
    if (ms <= 0L || ms == Long.MAX_VALUE) return "--:--"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}
