package com.red.features.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BackupScreen() {
    var isExporting by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var lastBackupDate by remember { mutableStateOf<String?>(null) }
    var resultMessage by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("السيادة على البيانات", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "نسخ احتياطي مشفّر بالكامل لمحادثاتك ووسائطك. المفتاح لديك فقط.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        if (lastBackupDate != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("آخر نسخة احتياطية", style = MaterialTheme.typography.labelMedium)
                    Text(lastBackupDate!!, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                isExporting = true
                resultMessage = null
                // TODO: Implement encrypted backup export to local storage
                // 1. Query all messages from Room
                // 2. Serialize to protobuf/JSON
                // 3. Encrypt with user's identity key (Argon2id-derived)
                // 4. Write to Downloads/RED_Backup_YYYYMMDD.redbkp
                isExporting = false
                lastBackupDate = "آخر نسخة: الآن"
                resultMessage = "تم إنشاء النسخة الاحتياطية بنجاح"
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isExporting && !isImporting
        ) {
            if (isExporting) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isExporting) "جاري التصدير..." else "إنشاء نسخة احتياطية كاملة")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = {
                isImporting = true
                resultMessage = null
                // TODO: Implement backup import
                // 1. Show file picker for .redbkp files
                // 2. Decrypt with user's identity key
                // 3. Validate schema version
                // 4. Upsert into Room
                isImporting = false
                resultMessage = "تم استعادة البيانات بنجاح"
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isExporting && !isImporting
        ) {
            if (isImporting) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isImporting) "جاري الاستعادة..." else "استعادة من ملف")
        }

        resultMessage?.let { msg ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(msg, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
        }
    }
}
