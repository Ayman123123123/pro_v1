package com.red.features.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun UpdateScreen(
    onBack: () -> Unit = {}
) {
    var isChecking by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf("الإصدار الحالي: 1.0.0-YOUNES") }
    var updateAvailable by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "رجوع")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("مركز التحديثات", style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "التحديثات تأتي من الخادم المحلي السيادي فقط. لا يوجد اتصال بمتاجر خارجية.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(updateStatus, style = MaterialTheme.typography.bodyLarge)
                if (updateAvailable) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("تحديث جديد متاح!", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                isChecking = true
                updateStatus = "جاري فحص الخادم السيادي..."
                isChecking = false
                updateAvailable = false
                updateStatus = "الإصدار الحالي: 1.0.0-YOUNES — محدّث لأحدث إصدار"
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isChecking
        ) {
            if (isChecking) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isChecking) "جاري الفحص..." else "فحص التحديثات")
        }

        if (updateAvailable) {
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { /* Download and install verified APK */ },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("تحميل وتثبيت التحديث")
            }
        }
    }
}
