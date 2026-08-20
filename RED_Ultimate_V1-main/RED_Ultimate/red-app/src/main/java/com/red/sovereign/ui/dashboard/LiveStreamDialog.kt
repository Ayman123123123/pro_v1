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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * حوار إعداد البث المباشر. تبقى بداية خدمة البث واستدعاء عقد الخادم في منسق اللوحة
 * حتى لا يملك المكوّن أي حالة جلسة أو كلمة مرور طويلة العمر.
 */
@Composable
internal fun LiveStreamDialog(
    roomId: String,
    title: String,
    isBroadcaster: Boolean,
    isPrivate: Boolean,
    password: String,
    onRoomIdChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onBroadcasterChange: (Boolean) -> Unit,
    onPrivateChange: (Boolean) -> Unit,
    onPasswordChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مركز البث المباشر") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "انضم لمشاهدة بث عام عبر المعرّف أو أنشئ بثك الخاص.",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = roomId,
                    onValueChange = onRoomIdChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("معرف البث أو رابط الدعوة (مثال: stream-123)") },
                    singleLine = true
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(checked = isBroadcaster, onCheckedChange = onBroadcasterChange)
                    Text("بدء البث كمنتج", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                if (isBroadcaster) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { onTitleChange(it.take(120)) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("عنوان البث (مثال: بث يونس العام)") },
                        singleLine = true
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(checked = isPrivate, onCheckedChange = onPrivateChange)
                        Text("بث خاص بكلمة سر", fontSize = 14.sp)
                    }
                    if (isPrivate) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { onPasswordChange(it.take(128)) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("كلمة سر البث الخاص (8 أحرف على الأقل)") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation()
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { onPasswordChange(it.take(128)) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("كلمة مرور البث الخاص (إن طُلبت)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = (roomId.trim().isNotBlank() || isBroadcaster) &&
                    (!isBroadcaster || !isPrivate || password.length in 8..128)
            ) {
                Text(if (isBroadcaster) "إنشاء وبدء البث" else "انضمام للبث")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
