package com.red.features.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ProfileScreen() {
    var name by remember { mutableStateOf("") }
    var about by remember { mutableStateOf("") }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var saveResult by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> avatarUri = uri }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("الملف الشخصي", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(24.dp))

        // Avatar with camera picker
        Box {
            Surface(
                modifier = Modifier.size(120.dp),
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (avatarUri != null) {
                        Text("📷", fontSize = 40.sp)
                    } else {
                        Text("👤", fontSize = 40.sp)
                    }
                }
            }
            IconButton(
                onClick = { imagePicker.launch("image/*") },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape)
                    .size(36.dp)
            ) {
                Icon(Icons.Default.CameraAlt, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("اسم العرض") },
            placeholder = { Text("مثال: يونس أحمد") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = about,
            onValueChange = { about = it },
            label = { Text("نبذة") },
            placeholder = { Text("مثال: مهندس اتصالات سيادية") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                isSaving = true
                saveResult = null
                // TODO: Call ProfileApi.updateProfile()
                isSaving = false
                saveResult = "تم الحفظ"
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = name.isNotBlank() && !isSaving
        ) {
            if (isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(if (isSaving) "جاري الحفظ..." else "حفظ الملف الشخصي")
        }

        saveResult?.let { result ->
            Spacer(modifier = Modifier.height(12.dp))
            Text(result, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
        }
    }
}
