package com.red.sovereign.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** حوار اتصال فردي؛ يمرر التنفيذ للمنسق كي تبقى خدمة المكالمات خارج الواجهة. */
@Composable
internal fun NewPrivateCallDialog(
    target: String,
    onTargetChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onStartAudio: () -> Unit,
    onStartVideo: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مكالمة جديدة مشفرة E2EE") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("أدخل معرّف يونس (RED ID) الخاص بصديقك للاتصال المشفر الفوري:", color = Color.Gray, fontSize = 13.sp)
                OutlinedTextField(
                    value = target,
                    onValueChange = onTargetChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("معرف يونس (مثال: red-user-123)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onStartAudio, enabled = target.trim().isNotBlank()) { Text("صوتية") }
                Button(onClick = onStartVideo, enabled = target.trim().isNotBlank()) { Text("فيديو") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
