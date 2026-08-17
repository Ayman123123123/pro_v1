package com.red.sovereign.features.profile

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 🔒 YOUNES Sovereign Backup System — حقيقي ومشفّر
 * لم يعد وهميًا: ينشئ ملف مشفّر فعليًا عبر BackupManager (AES256-GCM + Android Keystore)
 * ويحسب SHA-256 ويخزن metadata.
 */
@Composable
fun BackupScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backupManager = remember { BackupManager(context) }

    var isExporting by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var lastBackup by remember { mutableStateOf(backupManager.getLastBackupInfo()) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var backups by remember { mutableStateOf(backupManager.listBackups()) }

    fun refresh() {
        lastBackup = backupManager.getLastBackupInfo()
        backups = backupManager.listBackups()
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("السيادة الرقمية وتأمين البيانات", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "نسخ احتياطي سيادي مشفّر بالكامل عبر Android Keystore (AES256-GCM). المفتاح في جهازك فقط.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        if (lastBackup != null) {
            Spacer(modifier = Modifier.height(16.dp))
            val backup = lastBackup!!
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("آخر نسخة احتياطية", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(backup.createdAt))}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("الحجم: ${backup.sizeBytes / 1024} KB", style = MaterialTheme.typography.bodySmall)
                    Text("SHA-256: ${backup.checksum.take(16)}...", style = MaterialTheme.typography.bodySmall, fontSize = 10.sp)
                    Text("الملف: ${backup.fileName}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (backups.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text("${backups.size} نسخة محفوظة في files/sovereign_backups", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                isExporting = true
                resultMessage = null
                errorMessage = null
                scope.launch {
                    val result = backupManager.createBackup()
                    withContext(Dispatchers.Main) {
                        isExporting = false
                        result.onSuccess { info ->
                            refresh()
                            resultMessage = "تم إنشاء النسخة بنجاح: ${info.fileName} (${info.sizeBytes / 1024} KB)"
                            Toast.makeText(context, "Backup: ${info.absolutePath}", Toast.LENGTH_LONG).show()
                        }.onFailure { e ->
                            errorMessage = "فشل النسخ: ${e.message}"
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isExporting && !isImporting
        ) {
            if (isExporting) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isExporting) "جاري التصدير..." else "بدء النسخ الاحتياطي الكامل")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = {
                val latest = backupManager.getLastBackupInfo() ?: backupManager.listBackups().firstOrNull()
                if (latest == null) {
                    errorMessage = "لا توجد نسخة احتياطية للاستعادة"
                    return@OutlinedButton
                }
                isImporting = true
                resultMessage = null
                errorMessage = null
                scope.launch {
                    val file = java.io.File(latest.absolutePath)
                    val res = backupManager.restoreBackup(file)
                    withContext(Dispatchers.Main) {
                        isImporting = false
                        res.onSuccess {
                            resultMessage = "تمت الاستعادة بنجاح — أعد تشغيل التطبيق لتطبيق البيانات"
                            Toast.makeText(context, "Restore completed — restart app", Toast.LENGTH_LONG).show()
                        }.onFailure { e ->
                            errorMessage = "فشل الاستعادة: ${e.message}"
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isExporting && !isImporting
        ) {
            if (isImporting) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isImporting) "جاري الاستعادة..." else "استعادة البيانات السيادية")
        }

        resultMessage?.let { msg ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(msg, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
        }
        errorMessage?.let { msg ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(msg, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("العودة") }
    }
}
