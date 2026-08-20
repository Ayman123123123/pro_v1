package com.red.sovereign.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** حوار إعداد مساحة صوتية؛ تنفيذ مؤتمر الصوت نفسه يبقى في منسق المكالمات. */
@Composable
internal fun AudioSpaceDialog(
    roomId: String,
    isHost: Boolean,
    onRoomIdChange: (String) -> Unit,
    onHostChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مساحة صوتية يونس") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("مساحة صوتية مشفرة عبر خادم SFU — صوت فقط، بلا كاميرا. اترك الحقل فارغًا لإنشاء غرفة جديدة بمعرّف تلقائي.", color = Color.Gray, fontSize = 14.sp)
                OutlinedTextField(
                    value = roomId,
                    onValueChange = onRoomIdChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("معرف المساحة (اختياري — مثال: majlis-01)") },
                    singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Checkbox(checked = isHost, onCheckedChange = onHostChange)
                    Text("الانضمام كمضيف (متحدث)", fontSize = 14.sp)
                }
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text(if (roomId.isBlank()) "إنشاء مساحة جديدة" else "دخول المساحة") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
