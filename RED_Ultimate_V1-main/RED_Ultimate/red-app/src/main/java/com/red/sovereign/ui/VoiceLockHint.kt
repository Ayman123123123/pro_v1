package com.red.sovereign.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * تلميح القفل الصوتي الموحّد — المصدر الوحيد لحالة «التسجيل مقفل».
 *
 * كان الزر المقفل مكررًا حرفيًا في `RedDashboard.kt` و`MessageContent.kt`
 * كـ `OutlinedButton(enabled=false)` لا يفعل شيئًا عند الضغط — زر ميت
 * يوحي بإمكانية لا توجد. الآن: نص إرشادي فقط + زر «حذف» فعّال،
 * والإرسال يتم عبر زر الإرسال الرئيسي في شريط الكتابة.
 */
@Composable
fun VoiceLockHint(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onDelete,
            modifier = Modifier.weight(1f)
        ) { Text("حذف") }
        // نص فقط — الإرسال عبر زر الإرسال الرئيسي في شريط الكتابة.
        Text(
            "🔒 مُقفل — استخدم زر الإرسال",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f)
        )
    }
}
