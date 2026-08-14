package com.red.sovereign.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.core.MentionCandidate
import com.red.sovereign.ui.theme.YounesEmerald

@Composable
fun GroupMentionBar(
    candidates: List<MentionCandidate>,
    onPick: (MentionCandidate) -> Unit,
) {
    if (candidates.isEmpty()) return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Text(
                "أعضاء المجموعة",
                color = YounesEmerald,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
            candidates.forEach { person ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(person) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(person.displayName, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Text("  @${person.username}", color = YounesEmerald, fontSize = 12.sp)
                }
            }
        }
    }
}
