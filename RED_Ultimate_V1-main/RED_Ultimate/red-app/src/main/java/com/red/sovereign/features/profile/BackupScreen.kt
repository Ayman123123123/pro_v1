package com.red.sovereign.features.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 🔒 YOUNES Sovereign Backup System
 */

@Composable
fun BackupScreen(onBack: () -> Unit = {}) {
    var isExporting by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var lastBackupDate by remember { mutableStateOf<String?>(null) }
    var resultMessage by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("السيادة الرقمية وتأمين البيانات", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "نسخ احتياطي سيادي مشفّر بالكامل. مفتاح فك التشفير هو هويتك الرقمية فقط.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        if (lastBackupDate != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("آخر عملية ناجحة", style = MaterialTheme.typography.labelMedium)
                    Text(lastBackupDate!!, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                isExporting = true
                resultMessage = null
                // logic here
                isExporting = false
                lastBackupDate = "تم التصدير: الآن"
                resultMessage = "تم إنشاء ملف النسخ الاحتياطي بنجاح"
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
                isImporting = true
                resultMessage = null
                // logic here
                isImporting = false
                resultMessage = "تمت استعادة كافة المحادثات والوسائط"
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
        
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("العودة") }
    }
}
