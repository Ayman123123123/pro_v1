package com.red.sovereign.features.media

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun AdvancedShareSheet(
    files: List<File>,
    fileNames: List<String>,
    onDismiss: () -> Unit,
    mimeTypes: List<String> = emptyList()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var method by remember { mutableStateOf(ShareMethod.SYSTEM_PICKER) }
    var location by remember { mutableStateOf(SaveLocation.DOWNLOADS) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var resultOk by remember { mutableStateOf<Boolean?>(null) }
    var resultMsg by remember { mutableStateOf("") }

    fun run() {
        if (busy || files.isEmpty()) return
        busy = true; progress = 0f; resultOk = null
        scope.launch {
            when (method) {
                ShareMethod.SYSTEM_PICKER -> {
                    withContext(Dispatchers.Main) { shareViaSystem(context, files, fileNames, mimeTypes) }
                    resultOk = true; resultMsg = "تم فتح قائمة المشاركة"
                }
                ShareMethod.EXPORT_TO_STORAGE -> {
                    var okCount = 0
                    for ((i, f) in files.withIndex()) {
                        val uri = createSaveUri(context, fileNames.getOrElse(i) { f.name }, location)
                        if (uri != null && copyTo(f, uri, context)) okCount++
                        progress = (i + 1).toFloat() / files.size
                    }
                    resultOk = okCount > 0
                    resultMsg = "حُفظ $okCount من ${files.size} في ${location.displayName}"
                }
                ShareMethod.COPY_LINKS -> {
                    val text = files.joinToString("\n") { it.name }
                    withContext(Dispatchers.Main) {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText("RED", text))
                    }
                    resultOk = true; resultMsg = "تم نسخ أسماء ${files.size} ملف"
                }
                ShareMethod.CREATE_ZIP -> {
                    progress = 0.2f
                    val zipName = "RED_Files_${System.currentTimeMillis()}.zip"
                    val uri = createSaveUri(context, zipName, SaveLocation.DOWNLOADS)
                    if (uri == null) { resultOk = false; resultMsg = "تعذّر إنشاء الأرشيف" }
                    else {
                        val ok = zipAll(files, fileNames, uri, context)
                        progress = 1f
                        resultOk = ok
                        resultMsg = if (ok) "أُنشئ الأرشيف في التنزيلات (${files.size} ملف)" else "فشل إنشاء الأرشيف"
                    }
                }
            }
            busy = false
        }
    }

    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("مشاركة وتصدير ${files.size} ملف", fontSize = 18.sp, fontWeight = FontWeight.Bold)

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ShareMethod.values().forEach { m ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (method == m) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { method = m }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(m.icon, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(m.label, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        Text(m.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (method == m) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (method == ShareMethod.EXPORT_TO_STORAGE) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SaveLocation.values().forEach { l ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (location == l) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable { location = l }
                    ) {
                        Text(l.displayName, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
            }
        }

        if (busy) {
            Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.outlineVariant)) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0.05f, 1f)).height(5.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.primary))
            }
        }

        resultOk?.let { ok ->
            Card(colors = CardDefaults.cardColors(containerColor = if (ok) MaterialTheme.colorScheme.primaryContainer else Color(0xFFD32F2F).copy(alpha = 0.12f))) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (ok) Icons.Default.CheckCircle else Icons.Default.ErrorOutline, null, tint = if (ok) MaterialTheme.colorScheme.primary else Color(0xFFD32F2F))
                    Text(resultMsg, Modifier.padding(start = 8.dp), fontSize = 13.sp)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { run() }, enabled = !busy, modifier = Modifier.weight(1f)) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text("تنفيذ")
            }
            TextButton(onClick = onDismiss, enabled = !busy) { Text("إلغاء") }
        }
    }
}

private fun shareViaSystem(context: Context, files: List<File>, names: List<String>, mimes: List<String>) {
    runCatching {
        val uris = java.util.ArrayList<Uri>(files.map { FileProvider.getUriForFile(context, "com.red.sovereign.fileprovider", it) })
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة عبر…").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        Toast.makeText(context, "تعذّرت المشاركة: ${it.message}", Toast.LENGTH_SHORT).show()
    }
}

private suspend fun copyTo(source: File, destination: Uri, context: Context): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(destination)?.use { out ->
                source.inputStream().use { it.copyTo(out, 64 * 1024) }
            } ?: return@withContext false
            true
        }.getOrDefault(false)
    }

private suspend fun zipAll(files: List<File>, names: List<String>, destination: Uri, context: Context): Boolean =
    withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(destination)?.use { raw ->
                java.util.zip.ZipOutputStream(raw).use { zip ->
                    files.forEachIndexed { i, f ->
                        zip.putNextEntry(java.util.zip.ZipEntry(names.getOrElse(i) { f.name }))
                        f.inputStream().use { it.copyTo(zip, 64 * 1024) }
                        zip.closeEntry()
                    }
                }
            } ?: return@withContext false
            true
        }.getOrDefault(false)
    }
