package com.red.sovereign.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.red.sovereign.core.RichMessage
import com.red.sovereign.crypto.DecryptedMessage

/**
 * ════════════════════════════════════════════════════════════════════════
 *  DashboardSheets — حوارات اللوحة المستخرجة من `RedDashboard.kt`
 * ════════════════════════════════════════════════════════════════════════
 *
 *  تُمرَّر كل المدخلات كبارامترات (لا تلمس حالة اللوحة مباشرةً) —
 *  اللوحة تنادي فقط. التاريخ بصيغة عربية صريحة `Locale("ar")`.
 */

/** حوار «معلومات الرسالة»: المرسل/النوع/الوقت/الحالة/التعديل/التوجيه/الرد/المؤقت/المعرف. */
@Composable
internal fun MessageInfoDialog(
    info: DecryptedMessage,
    isEdited: Boolean,
    onDismiss: () -> Unit
) {
    val richInfo = if (info.type == "RICH_TEXT") RichMessage.decode(info.plaintext) else null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("معلومات الرسالة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DashboardMessageInfoRow("المرسل", if (info.outgoing) "أنت" else info.senderRedId)
                DashboardMessageInfoRow(
                    "النوع",
                    when (info.type) {
                        "RICH_TEXT" -> "نص غني"
                        "VOICE" -> "رسالة صوتية"
                        "STICKER" -> "ملصق"
                        "IMAGE" -> "صورة"
                        "VIDEO" -> "فيديو"
                        "AUDIO" -> "صوت"
                        "FILE" -> "ملف"
                        else -> info.type
                    }
                )
                DashboardMessageInfoRow(
                    "الوقت",
                    java.text.DateFormat.getDateTimeInstance(
                        java.text.DateFormat.MEDIUM,
                        java.text.DateFormat.SHORT,
                        dashboardArabicLocale
                    ).format(java.util.Date(info.timestamp))
                )
                DashboardMessageInfoRow(
                    "الحالة",
                    when (info.status) {
                        "READ" -> "مقروءة ✓✓"
                        "DELIVERED" -> "وصلت ✓✓"
                        else -> "أُرسلت ✓"
                    }
                )
                if (isEdited) DashboardMessageInfoRow("تعديل", "نعم")
                if (richInfo?.forwardOf != null) DashboardMessageInfoRow("إعادة توجيه", "نعم")
                if (richInfo?.replyTo != null) {
                    DashboardMessageInfoRow("رد على", richInfo.replyTo.take(12))
                }
                if (richInfo?.expiresAt != null) DashboardMessageInfoRow("رسالة مؤقتة", "نعم")
                DashboardMessageInfoRow("المعرّف", info.id.take(16))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } }
    )
}
