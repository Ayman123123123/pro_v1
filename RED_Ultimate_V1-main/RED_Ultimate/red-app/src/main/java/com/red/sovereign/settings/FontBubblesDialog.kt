package com.red.sovereign.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.features.chat.LuxuryChatBubble
import com.red.sovereign.ui.theme.PlexArabicFamily
import com.red.sovereign.ui.theme.YounesEmerald

/**
 * سياسة الخط والفقاعة — الجسر بين [YounesSettings] والعرض.
 * PLEX_ARABIC هو الافتراضي (IBM Plex Sans Arabic المضمّن)؛ SYSTEM يتبع خط النظام.
 * BUBBLE_STYLE: LUXURY (زوايا فاخرة) / CLASSIC (12dp موحدة) / MINIMAL (6dp).
 */
object ChatFontPolicy {
    fun familyFor(key: String): FontFamily = when (key) {
        "SYSTEM" -> FontFamily.Default
        else -> PlexArabicFamily
    }

    fun fontLabelAr(key: String): String = when (key) {
        "SYSTEM" -> "خط النظام"
        else -> "Plex Arabic"
    }

    fun bubbleLabelAr(style: String): String = when (style) {
        "CLASSIC" -> "كلاسيكية"
        "MINIMAL" -> "مبسطة"
        else -> "فاخرة"
    }
}

/**
 * حوار اختيار الخط وشكل الفقاعة مع معاينة حية عبر [LuxuryChatBubble].
 * يحفظ فوراً في [SettingsViewModel] فينعكس على MessageContent والشاشات.
 */
@Composable
fun FontBubblesDialog(
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    val state = viewModel.state
    val previewFont = ChatFontPolicy.familyFor(state.fontFamily)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("الخط والفقاعات", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("الخط", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("PLEX_ARABIC", "SYSTEM").forEach { id ->
                        val sel = state.fontFamily == id
                        AssistChip(
                            onClick = { viewModel.setFontFamily(id) },
                            label = { Text(ChatFontPolicy.fontLabelAr(id), fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = { if (sel) Text("●") },
                            colors = if (sel) AssistChipDefaults.assistChipColors(containerColor = YounesEmerald, labelColor = Color(0xFF06090F)) else AssistChipDefaults.assistChipColors()
                        )
                    }
                }
                Text("شكل الفقاعة", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("LUXURY", "CLASSIC", "MINIMAL").forEach { id ->
                        val sel = state.bubbleStyle == id
                        AssistChip(
                            onClick = { viewModel.setBubbleStyle(id) },
                            label = { Text(ChatFontPolicy.bubbleLabelAr(id), fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = { if (sel) Text("●") },
                            colors = if (sel) AssistChipDefaults.assistChipColors(containerColor = YounesEmerald, labelColor = Color(0xFF06090F)) else AssistChipDefaults.assistChipColors()
                        )
                    }
                }
                Text("معاينة حية", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        LuxuryChatBubble(
                            message = "مرحباً بك في يونس — هذه معاينة حية للخط والفقاعة",
                            isMe = false,
                            time = "12:30",
                            status = "READ",
                            senderName = "يونس",
                            senderRedId = "@younes",
                            fontFamily = previewFont,
                            bubbleStyle = state.bubbleStyle
                        )
                        LuxuryChatBubble(
                            message = "التشفير طرف-لطرف مفعّل دائماً",
                            isMe = true,
                            time = "12:31",
                            status = "READ",
                            fontFamily = previewFont,
                            bubbleStyle = state.bubbleStyle
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "يُطبق على MessageContent وكل فقاعات الدردشة فور الحفظ.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("تم") } },
        dismissButton = {
            TextButton(
                onClick = {
                    viewModel.setFontFamily("PLEX_ARABIC")
                    viewModel.setBubbleStyle("LUXURY")
                }
            ) { Text("استعادة الافتراضي") }
        }
    )
}

/** صف دخول صغير يُستخدم داخل AppearanceSettings لفتح الحوار. */
@Composable
fun FontBubblesEntry(onOpen: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp)) {
            Column(Modifier.weight(1f)) {
                Text("الخط والفقاعات", fontWeight = FontWeight.SemiBold)
                Text(
                    "Plex Arabic + معاينة فقاعة حية",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text("تخصيص", color = YounesEmerald, fontWeight = FontWeight.Bold)
        }
    }
}
