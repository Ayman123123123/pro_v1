package com.red.sovereign.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.red.sovereign.groups.Group

/**
 * ════════════════════════════════════════════════════════════════════════
 *  DashboardPoll — حوارات الاستطلاعات مستخرجة من `RedDashboard.kt`
 * ════════════════════════════════════════════════════════════════════════
 *
 *  المصدر: `RedDashboard.kt` — كتلة `if (showGroupPollDialog)` (حوار
 *  «استطلاع في المجموعة»: سؤال + 2..6 خيارات + إرسال).
 *  نُقلت كما هي منطقيًا؛ اللوحة تمرر الحالة والـ callbacks فقط
 *  (لا تلمس هذه الملفات حالة اللوحة مباشرةً) — نفس نمط
 *  `DashboardSheets.kt` / `DashboardGallery.kt`.
 *
 *  ملاحظة: بطاقة التصويت الحية تعيش في `MessageContent.kt`
 *  (`InlinePollCard` عبر `RichTextMessage`) والأصوات في
 *  `ChatPollVoteStore` — هذا الملف للإنشاء فقط. صيغة الإرسال
 *  `(question, options)` تبني اللوحة منها `InlinePoll` + `RichMessage`
 *  وترسلها عبر `RedConnectionService.sendGroupRichText`.
 */

/** حوار «استطلاع في المجموعة»: السؤال + الخيارات + زر الإرسال. */
@Composable
internal fun DashboardGroupPollDialog(
    openGroup: Group?,
    question: String,
    onQuestionChange: (String) -> Unit,
    options: List<String>,
    onOptionsChange: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    onSendPoll: (question: String, options: List<String>) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("استطلاع في المجموعة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    question,
                    { onQuestionChange(it.take(280)) },
                    Modifier.fillMaxWidth(),
                    label = { Text("السؤال") },
                    maxLines = 3
                )
                options.forEachIndexed { index, value ->
                    OutlinedTextField(
                        value = value,
                        onValueChange = { next ->
                            onOptionsChange(options.toMutableList().also { it[index] = next.take(80) })
                        },
                        Modifier.fillMaxWidth(),
                        label = { Text("الخيار ${index + 1}") },
                        singleLine = true
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        { if (options.size < 6) onOptionsChange(options + "") },
                        Modifier.weight(1f),
                        enabled = options.size < 6
                    ) { Text("+ خيار") }
                    OutlinedButton(
                        { if (options.size > 2) onOptionsChange(options.dropLast(1)) },
                        Modifier.weight(1f),
                        enabled = options.size > 2
                    ) { Text("- خيار") }
                }
            }
        },
        confirmButton = {
            val validPoll = question.isNotBlank() && options.count { it.trim().length >= 2 } >= 2
            Button(
                enabled = validPoll && openGroup != null,
                onClick = {
                    onSendPoll(
                        question.trim(),
                        options.map { it.trim() }.filter { it.length >= 2 }
                    )
                }
            ) { Text("إرسال الاستطلاع") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
