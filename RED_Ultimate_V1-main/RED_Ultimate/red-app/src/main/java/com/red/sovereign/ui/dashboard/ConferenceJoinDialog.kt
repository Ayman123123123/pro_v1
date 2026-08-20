package com.red.sovereign.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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

/** حوار إدخال معرف مؤتمر؛ يبدأ الاتصال فعليًا من منسق شاشة المكالمات. */
@Composable
internal fun ConferenceJoinDialog(
    roomId: String,
    onRoomIdChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onJoin: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("الانضمام إلى مؤتمر جماعي") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("أدخل اسم الغرفة أو معرف المؤتمر للاتصال الآمن عبر SFU:", color = Color.Gray, fontSize = 14.sp)
                OutlinedTextField(
                    value = roomId,
                    onValueChange = onRoomIdChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("معرف الغرفة (مثال: red-room-123)") },
                    singleLine = true
                )
            }
        },
        confirmButton = { Button(onClick = onJoin, enabled = roomId.trim().isNotBlank()) { Text("انضمام") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
