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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.core.GroupMentions
import com.red.sovereign.core.MentionCandidate
import com.red.sovereign.ui.theme.YounesEmerald

/**
 * شريط اقتراحات المنشن في المجموعات — P1-D: زر @all ذكي.
 *
 * - [canUseAll=false] يعرض صف @all مقفلًا ("للمشرفين فقط") بدل إخفائه بصمت،
 *   حتى يفهم العضو العادي في المجموعات الكبيرة (>32) لماذا لا يعمل @all.
 * - [onAllClick] زر @all سريع أعلى القائمة (اختياري، null = بلا زر مخصص).
 * - [allMuted] يعرض 🔕 بجانب @all عند تفعيل الكتم المنفصل له.
 * - كل البارامترات الجديدة لها قيم افتراضية — المتصلون القدامى يبنون بلا تغيير.
 */
@Composable
fun GroupMentionBar(
    candidates: List<MentionCandidate>,
    onPick: (MentionCandidate) -> Unit,
    canUseAll: Boolean = true,
    allMuted: Boolean = false,
    highlightsOnly: Boolean = false,
    onAllClick: (() -> Unit)? = null,
) {
    if (candidates.isEmpty() && onAllClick == null) return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "أعضاء المجموعة",
                    color = YounesEmerald,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                if (highlightsOnly) {
                    Text(
                        "✨ Highlights",
                        color = MaterialTheme.colorScheme.tertiary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            // زر @all السريع — بارز أعلى القائمة مع تلميح الطوارئ.
            if (onAllClick != null) {
                if (canUseAll) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onAllClick() }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (allMuted) "📢 @all  الجميع  🔕" else "📢 @all  الجميع",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "  تنبيه للجميع — أضف عاجل للطوارئ",
                            color = YounesEmerald,
                            fontSize = 11.sp,
                        )
                    }
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .alpha(0.55f)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "🔒 @all  الجميع",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "  للمشرفين فقط في المجموعات الكبيرة (>${GroupMentions.LARGE_GROUP_ALL_THRESHOLD})",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
            candidates.forEach { person ->
                val isAll = person.redId == "ALL" || person.username.equals("all", ignoreCase = true)
                val isOnline = person.redId == "ONLINE" || person.username.equals("online", ignoreCase = true)
                when {
                    // صف @all داخل الاقتراحات: قفل بصري لغير المشرفين بدل الإخفاء الصامت.
                    isAll && !canUseAll -> {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .alpha(0.55f)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("🔒 ${person.displayName}", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                            Text("  للمشرفين فقط", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                    isAll || isOnline -> {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(person) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                (if (isAll) "📢 " else "🟢 ") + person.displayName + (if (isAll && allMuted) "  🔕" else ""),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                            )
                            Text("  @${person.username}", color = YounesEmerald, fontSize = 12.sp)
                        }
                    }
                    else -> {
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
    }
}
