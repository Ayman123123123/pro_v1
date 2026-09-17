package com.red.sovereign.ui

/**
 * عرض الرسائل داخل المحادثة: النص الغني، الوسائط، الصوت، التفاعلات.
 *
 * استُخرج من `RedDashboard.kt` الذي بلغ 3,604 أسطر، وهو حجم يجعل
 * الملف عصيًّا على المراجعة وبطيء الترجمة عند كل تعديل. المجموعة هنا
 * متماسكة ومغلقة: لا تحتاج أي رمز من بقيّة اللوحة، ويستهلكها
 * `ChatHubScreen` وحده.
 *
 * الرؤية `internal` مقصورة على ما تستدعيه اللوحة فعلًا؛ وما يُستعمل
 * داخل هذا الملف فقط بقي `private` حتى لا يتسرّب إلى بقيّة الوحدة.
 *
 * لم يتغيّر أي سطر منطق أثناء النقل — النقل بنيوي بحت.
 */

import android.content.Intent
import androidx.core.content.FileProvider
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.core.GroupMentions
import com.red.sovereign.core.RedQualityManager
import com.red.sovereign.core.RichMessage
import com.red.sovereign.core.isPendingDecryptPlaceholder
import com.red.sovereign.core.pendingDecryptDisplayText
import com.red.sovereign.core.YounesId
import com.red.sovereign.core.database.MessageReactionEntity
import com.red.sovereign.crypto.DecryptedMessage
import com.red.sovereign.media.AttachmentManifest
import com.red.sovereign.media.AttachmentState
import com.red.sovereign.media.AttachmentViewModel
import com.red.sovereign.media.VoiceManifest
import com.red.sovereign.media.VoiceMessageState
import com.red.sovereign.media.VoiceMessageViewModel
import com.red.sovereign.media.VoiceNotePlayer
import com.red.sovereign.media.voice.VoiceBubble
import com.red.sovereign.settings.ChatFontPolicy
import com.red.sovereign.settings.SettingsRuntime
import com.red.sovereign.stories.StoryVideoPlayer
import com.red.sovereign.ui.theme.AqyalCyanGlow
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.YounesBubbleIn
import com.red.sovereign.ui.theme.YounesEmerald
import java.io.File
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

internal fun resolveRichMessages(source: List<DecryptedMessage>): List<DecryptedMessage> {
    val visible = linkedMapOf<String, DecryptedMessage>()
    source.sortedBy(DecryptedMessage::timestamp).forEach { message ->
        val rich = if (message.type == "RICH_TEXT") RichMessage.decode(message.plaintext) else null
        when {
            rich?.action == "DELETE" && rich.deleteOf != null -> visible.remove(rich.deleteOf)
            rich?.action == "EDIT" && rich.editOf != null -> visible[rich.editOf]?.let { original -> visible[rich.editOf] = original.copy(plaintext = RichMessage.encode(RichMessage(text = rich.text, replyTo = RichMessage.decode(original.plaintext)?.replyTo))) }
            // التفاعلات ليست رسائل — تُعرض كـ chips عبر جدول message_reactions، فلا تُدرج هنا
            rich?.action == "REACTION" || rich?.action == "REACTION_REMOVE" -> Unit
            rich?.expiresAt != null && rich.expiresAt <= System.currentTimeMillis() -> Unit
            else -> visible[message.id] = message
        }
    }
    return visible.values.toList()
}

/**
 * دمج الفقاعات المتتالية (2026-09-10): رسالتان متتاليتان من نفس المرسل
 * بفارق أقل من دقيقتين تُدمجان بصريًا — تُخفى ترويسة المرسل عن الثانية
 * ويُقلَّص التباعد. يمنع ازدحام الترويسات في المجموعات النشطة.
 */
internal fun shouldMergeWithPrevious(
    previous: DecryptedMessage?,
    current: DecryptedMessage,
    mergeWindowMs: Long = 120_000L
): Boolean {
    if (previous == null) return false
    if (previous.senderRedId != current.senderRedId) return false
    if (previous.outgoing != current.outgoing) return false
    return (current.timestamp - previous.timestamp) in 0..mergeWindowMs
}

internal fun messageDisplayText(message: DecryptedMessage): String {
    // Phase-1 (2026-09-14): العنصر النائب يعرض نصاً ثابتاً (يخفي الوسم الداخلي).
    if (isPendingDecryptPlaceholder(message.plaintext)) return pendingDecryptDisplayText()
    return if (message.type == "RICH_TEXT") RichMessage.decode(message.plaintext)?.text.orEmpty() else message.plaintext.toString(Charsets.UTF_8)
}

/**
 * فقاعة موحدة (2026-09-10): تدمج `LuxuryChatBubble` (نص) مع عارضات
 * `MessageContent` (وسائط/صوت/استطلاع) في مسار واحد.
 *
 * كانت `RedDashboard` تفرّع يدويًا: `LuxuryChatBubble` للنص الصرف و`Card`
 * مكررة للوسائط — أي تغيير في الشكل كان يتطلب تعديلين. الآن كل الأنواع
 * تمر هنا: الترويسة + المحتوى + الوقت/العلامات + التفاعلات في مكان واحد.
 * `ChatThreadScreen` و`ChatHubScreen` يستدعيانها؛ المسارات القديمة تبقى
 * للتوافق حتى اكتمال الهجرة.
 */
@Composable
internal fun UnifiedMessageBubble(
    message: DecryptedMessage,
    conversation: List<DecryptedMessage>,
    isOutgoing: Boolean = message.outgoing,
    senderName: String = "",
    senderRedId: String = message.senderRedId,
    hideSenderHeader: Boolean = false,
    timeText: String,
    isEdited: Boolean = false,
    reactions: List<MessageReactionEntity> = emptyList(),
    currentRedId: String = "",
    onToggleReaction: ((String) -> Unit)? = null,
    attachments: AttachmentViewModel? = null,
    myRedId: String? = null,
    onPollVote: ((pollId: String, optionIndex: Int?) -> Unit)? = null,
    onLongClick: () -> Unit = {},
    onInfoClick: () -> Unit = {},
    /** يقفز لرسالة الأصل عند نقر اقتباس الرد. */
    onReplyClick: ((String) -> Unit)? = null
) {
    // محاذاة واتساب: الصادر End والوارد Start — مثل LuxuryChatBubble.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start
    ) {
    Card(
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .combinedClickable(onClick = onInfoClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            // معتمة قابلة للقياس (لا copy(alpha)): زمرد صادر + YounesBubbleIn وارد.
            containerColor = if (isOutgoing) YounesEmerald
            else YounesBubbleIn
        ),
        shape = RoundedCornerShape(
            topStart = 20.dp, topEnd = 20.dp,
            bottomStart = if (isOutgoing) 20.dp else 5.dp,
            bottomEnd = if (isOutgoing) 5.dp else 20.dp
        )
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (!isOutgoing && !hideSenderHeader && (senderName.isNotBlank() || senderRedId.isNotBlank())) {
                Text(
                    senderName.ifBlank { senderRedId },
                    color = YounesEmerald,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (senderName.isNotBlank() && senderRedId.isNotBlank()) {
                    Text(
                        senderRedId,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(2.dp))
            }
            // النص الصادر الموحد 0xFF06090F على الفقاعة الفاتحة.
            val fallbackTextColor = if (isOutgoing) Color(0xFF06090F) else MaterialTheme.colorScheme.onSurface
            when (message.type) {
                "FILE", "IMAGE", "VIDEO", "AUDIO" -> if (attachments != null) AttachmentMessage(message, attachments) else Text(messageDisplayText(message), color = fallbackTextColor)
                "VOICE" -> if (attachments != null) VoiceMessage(message, attachments) else Text(messageDisplayText(message), color = fallbackTextColor)
                "STICKER" -> if (attachments != null) StickerMessage(message, attachments) else Text(messageDisplayText(message), color = fallbackTextColor)
                "RICH_TEXT" -> RichTextMessage(message, conversation, myRedId = myRedId, onPollVote = onPollVote, onReplyClick = onReplyClick)
                "GROUP_MESSAGE" -> {
                    val text = message.plaintext.toString(Charsets.UTF_8)
                    val asVoice = runCatching { ATTACHMENT_JSON.decodeFromString<VoiceManifest>(text) }.isSuccess
                    val asFile = runCatching { ATTACHMENT_JSON.decodeFromString<AttachmentManifest>(text) }.isSuccess
                    when {
                        asVoice && attachments != null -> VoiceMessage(message, attachments)
                        asFile && attachments != null -> AttachmentMessage(message, attachments)
                        else -> Text(text, fontSize = 16.sp, color = fallbackTextColor)
                    }
                }
                else -> Text(message.plaintext.toString(Charsets.UTF_8), fontSize = 16.sp, color = fallbackTextColor)
            }
            if (reactions.isNotEmpty() && onToggleReaction != null) {
                Spacer(Modifier.height(4.dp))
                MessageReactions(reactions = reactions, currentRedId = currentRedId, onToggle = onToggleReaction)
            }
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(timeText, fontSize = 11.sp, color = if (isOutgoing) Color(0xFF06090F).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.85f))
                if (isEdited) Text("✏️", fontSize = 11.sp)
                if (isOutgoing) {
                    val ticks = when (message.status) { "READ", "DELIVERED" -> "✓✓" else -> "✓" }
                    Text(ticks, color = if (message.status == "READ") Color(0xFF0B3D91) else Color(0xFF06090F).copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    }
}

@Composable

internal fun RichTextMessage(
    message: DecryptedMessage,
    conversation: List<DecryptedMessage>,
    mentionLabel: (String) -> String = { it },
    /** معرفي الحالي — مع onPollVote يفعّل وضع التصويت المتزامن E2EE. */
    myRedId: String? = null,
    /** يُرسل POLL_VOTE عبر RedConnectionService (فردي/جماعي حسب الموقع). */
    onPollVote: ((pollId: String, optionIndex: Int?) -> Unit)? = null,
    /** يقفز لرسالة الأصل عند نقر اقتباس الرد. */
    onReplyClick: ((String) -> Unit)? = null
) {
    val rich = RichMessage.decode(message.plaintext)
    if (rich == null) { Text("رسالة غير صالحة", color = MaterialTheme.colorScheme.error); return }
    // اقتباس الرد — قابل للنقر يقفز للأصل عبر onReplyClick(messageId).
    rich.replyTo?.let { replyId ->
        val quoted = conversation.firstOrNull { it.id == replyId }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (message.outgoing) Color(0xFF0A2F27) else Color(0xFF24384F),
            modifier = Modifier.fillMaxWidth().then(if (onReplyClick != null) Modifier.clickable { onReplyClick.invoke(replyId) } else Modifier)
        ) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.width(3.dp).heightIn(min = 28.dp).clip(RoundedCornerShape(50)).background(if (message.outgoing) Color(0xFF06090F) else YounesEmerald))
                Column(Modifier.weight(1f)) {
                    Text(
                        (quoted?.senderRedId ?: "").ifBlank { "رد" },
                        color = YounesEmerald,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        quoted?.let { messageDisplayText(it) } ?: "اضغط للانتقال إلى الرسالة الأصلية",
                        color = Color(0xFFDCE7E2),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
    // شارة التحويل مع العداد — موحدة مع LuxuryChatBubble.
    val forwardCount = rich.forwardCount.coerceAtLeast(0)
    if (rich.forwardOf != null || forwardCount > 0) Text(
        when {
            forwardCount > 5 -> "كثيرة التحويل • $forwardCount"
            forwardCount > 0 -> "محوّلة • $forwardCount"
            else -> "محوّلة"
        },
        style = MaterialTheme.typography.labelSmall,
        color = if (message.outgoing) Color(0xFF06090F).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.85f)
    )
    val annotated = remember(rich.text, rich.mentions, rich.hashtags, message.outgoing) {
        val t = rich.text
        val mentions = (rich.mentions + RED_ID_PARTIAL.findAll(t).map { it.value } + GroupMentions.NAME_TOKEN.findAll(t).map { it.value }).distinct()
        val hashtags = rich.hashtags + HASHTAG_PARTIAL.findAll(t).map { it.value }.toList()
        // منشن مميز فعلاً + هاشتاغ: داكنان مميزان على الصادر، مضيئان على الوارد — كلها معتمة.
        val mentionColor = if (message.outgoing) Color(0xFF06307A) else Color(0xFF3DE8BC)
        val hashtagColor = if (message.outgoing) Color(0xFF4A2F00) else Color(0xFF7CC4FF)
        androidx.compose.ui.text.buildAnnotatedString {
            append(t)
            mentions.forEach { m -> val idx = t.indexOf(m); if (idx >= 0) addStyle(androidx.compose.ui.text.SpanStyle(color = mentionColor, fontWeight = FontWeight.Bold, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), idx, idx + m.length) }
            hashtags.forEach { h -> val idx = t.indexOf(h); if (idx >= 0) addStyle(androidx.compose.ui.text.SpanStyle(color = hashtagColor, fontWeight = FontWeight.Bold), idx, idx + h.length) }
        }
    }
    Text(annotated, color = if (message.outgoing) Color(0xFF06090F) else MaterialTheme.colorScheme.onSurface, fontFamily = ChatFontPolicy.familyFor(SettingsRuntime.current.fontFamily))
    // CALL_STARTED: بطاقة انضمام موحدة (واتساب) — تعمل للدعوة الفائتة والانضمام المتأخر معاً.
    if (rich.action == "CALL_STARTED" && !rich.callId.isNullOrBlank() && !message.outgoing && myRedId != null) {
        val joinContext = LocalContext.current
        Spacer(Modifier.height(6.dp))
        Button(onClick = {
            runCatching {
                com.red.sovereign.calls.GroupCallService.accept(joinContext, rich.callId!!, myRedId, rich.callIsVideo)
            }
        }) { Text(if (rich.callIsVideo) "📹 انضم للفيديو" else "📞 انضم للمكالمة") }
    }
    rich.poll?.let { poll ->
        InlinePollCard(poll, isOutgoing = message.outgoing, myRedId = myRedId, onVote = onPollVote)
    }
    rich.expiresAt?.let {
        val remaining = (it - System.currentTimeMillis()).coerceAtLeast(0)
        val label = when {
            remaining <= 0 -> "انتهت"
            remaining < 3600000 -> "${remaining/60000}د"
            remaining < 86400000 -> "${remaining/3600000}س"
            else -> "${remaining/86400000}ي"
        }
        Text("⏳ مؤقتة • $label", style = MaterialTheme.typography.labelSmall, color = AqyalGold)
    }
    if (rich.mentions.isNotEmpty()) Text("ذكر: ${rich.mentions.joinToString { mentionLabel(it) }}", style = MaterialTheme.typography.labelSmall, color = if (message.outgoing) Color(0xFF06090F) else YounesEmerald)
}

@Composable

private fun InlinePollCard(
    poll: com.red.sovereign.core.InlinePoll,
    isOutgoing: Boolean,
    myRedId: String? = null,
    onVote: ((pollId: String, optionIndex: Int?) -> Unit)? = null
) {
    // وضع متزامن E2EE: الأصوات تُجمَع من رسائل POLL_VOTE الواردة عبر
    // ChatPollVoteStore (كانت البطاقة تزيد عدّادًا محليًا فقط ولا ترسل شيئًا).
    // بلا onVote تبقى البطاقة عرضًا محليًا كما كانت.
    val synced = myRedId != null && onVote != null
    val storeCounts = if (synced) ChatPollVoteStore.counts(poll.pollId, poll.options.size) else emptyList()
    val id = myRedId ?: null
    val myVote = if (synced && id != null) ChatPollVoteStore.myVote(poll.pollId, id) else null
    val baseVotes = poll.votes
    val votes = if (synced && storeCounts.any { it > 0 }) {
        // دمج لقطة المُنشئ (تتضمن أصواتًا سابقة للإرسال) مع الوارد الجديد.
        baseVotes.mapIndexed { index, base -> base + storeCounts.getOrElse(index) { 0 } }
            .let { merged ->
                // إن صوّتُّ ولم أظهر في اللقطة، أضف صوتي (تفادي النقصان البصري).
                if (myVote != null && baseVotes.getOrElse(myVote) { 0 } == 0 &&
                    storeCounts.getOrElse(myVote) { 0 } == 0
                ) merged.toMutableList().also { it[myVote] = it[myVote] + 1 } else merged
            }
    } else if (synced && myVote != null) {
        baseVotes.mapIndexed { index, base -> if (index == myVote) base + 1 else base }
            .let { if (it.size < poll.options.size) it + List(poll.options.size - it.size) { 0 } else it }
    } else {
        baseVotes
    }
    // total حقيقي بلا تضخيم +1 عند الصفر (القسمة محمية بـ total > 0).
    val total = votes.sum()
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Forum, null, tint = YounesEmerald, modifier = Modifier.size(18.dp))
                Text(" استطلاع المجموعة", style = MaterialTheme.typography.labelMedium, color = YounesEmerald, fontWeight = FontWeight.Bold)
            }
            Text(poll.question, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            poll.options.forEachIndexed { index, option ->
                val optionVotes = votes.getOrElse(index) { 0 }
                val ratio = if (total > 0) (optionVotes.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
                val isSelected = if (synced) myVote == index else null
                Card(
                    Modifier.fillMaxWidth().clickable(enabled = !poll.isClosed && onVote != null) {
                        if (onVote != null) onVote(poll.pollId, if (myVote == index) null else index)
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected == true) Color(0xFF123B32)
                        else MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(option, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = if (isSelected == true) FontWeight.Bold else FontWeight.Normal)
                            // عرض النسبة موحد دائمًا — بلا شروط متباينة.
                            Text(
                                "${(ratio * 100).toInt()}%",
                                color = YounesEmerald, fontSize = 12.sp, fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)), color = YounesEmerald, trackColor = MaterialTheme.colorScheme.surface)
                    }
                }
            }
            Text(
                if (synced) "إجمالي الأصوات: $total · صوتك: ${myVote?.let { poll.options.getOrNull(it) } ?: "لا شيء"}"
                else "إجمالي الأصوات: $total",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * مخزن تصويتات استطلاعات الدردشة (E2EE): pollId -> (مصوّت -> فهرس الخيار).
 * يُغذّى من رسائل POLL_VOTE الواردة (انظر RedDashboard) ومن التصويت
 * الصادر المتفائل، وتقرأه بطاقات الاستطلاع الحيّة.
 */
internal object ChatPollVoteStore {
    val votersByPoll = androidx.compose.runtime.mutableStateMapOf<String, Map<String, Int>>()
    fun record(pollId: String, voter: String, optionIndex: Int?) {
        val next = (votersByPoll[pollId] ?: emptyMap()).toMutableMap()
        if (optionIndex == null) next.remove(voter) else next[voter] = optionIndex
        votersByPoll[pollId] = next.toMap()
    }
    fun counts(pollId: String, optionCount: Int): List<Int> {
        val counts = IntArray(optionCount)
        votersByPoll[pollId]?.values?.forEach { idx -> if (idx in counts.indices) counts[idx]++ }
        return counts.toList()
    }
    fun myVote(pollId: String, me: String): Int? = votersByPoll[pollId]?.get(me)
}

@Composable

private fun VoiceRecordingControls(
    voiceState: VoiceMessageState.Recording,
    voiceMessages: VoiceMessageViewModel,
    isLocked: Boolean,
    cancelProgress: Float
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var dragOffsetX by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0f) }
    var dragOffsetY by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0f) }

    Column {
        // ⏺️ شريط التسجيل العلوي
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (voiceState.paused) AqyalGold else MaterialTheme.colorScheme.error))
            Spacer(Modifier.width(6.dp))
            Text(
                if (voiceState.paused) "متوقف مؤقتًا ${dashboardFormatDuration(voiceMessages.elapsedSeconds)}"
                else "● تسجيل ${dashboardFormatDuration(voiceMessages.elapsedSeconds)}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            IconButton(voiceMessages::togglePause) {
                Icon(if (voiceState.paused) Icons.Default.PlayArrow else Icons.Default.Pause, if (voiceState.paused) "استئناف" else "إيقاف مؤقت")
            }
            TextButton(voiceMessages::cancel) { Text("إلغاء") }
        }
        VoiceWaveform(voiceMessages.waveform, MaterialTheme.colorScheme.error, Modifier.fillMaxWidth().height(34.dp))

        // 🎚️ منطقة السحب — إذا السحب لليسار/الأسفل = إلغاء تدريجي
        if (cancelProgress > 0f) {
            Text(
                "↩️ اسحب لمعاودة التسجيل • ${(cancelProgress * 100).toInt()}%",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelSmall
            )
        }

        // 🔒 إذا قُفل التسجيل، اعرض تلميح القفل الموحد (زر حذف فعال + نص فقط)
        if (isLocked) {
            VoiceLockHint(onDelete = voiceMessages::cancel)
        } else {
            // 🔓 نصيحة للمستخدم: اسحب للقفل أو ارفع الإصبع للإرسال
            Text(
                "💡 اسحب للأعلى للقفل • ارفع الإصبع للإرسال • اسحب للأسفل للإلغاء",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
        }
    }
}

@Composable

private fun VoicePreviewControls(
    duration: Int,
    waveform: List<Int>,
    onSend: () -> Unit,
    onDiscard: () -> Unit,
    isSending: Boolean
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PlayArrow, null, tint = YounesEmerald)
            Spacer(Modifier.width(6.dp))
            Text(
                "معاينة الرسالة الصوتية • ${dashboardFormatDuration(duration)}",
                color = YounesEmerald,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
        }
        VoiceWaveform(waveform, YounesEmerald, Modifier.fillMaxWidth().height(34.dp))
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onDiscard,
                modifier = Modifier.weight(1f),
                enabled = !isSending
            ) {
                Icon(Icons.Default.Close, null); Text(" حذف")
            }
            Button(
                onClick = onSend,
                modifier = Modifier.weight(1f),
                enabled = !isSending && duration >= 1
            ) {
                if (isSending) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White)
                else { Icon(Icons.AutoMirrored.Filled.Send, null); Text(" إرسال") }
            }
        }
    }
}

@Composable

private fun VoiceWaveform(values: List<Int>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val samples = values.ifEmpty { List(24) { 8 } }
        val step = size.width / samples.size.coerceAtLeast(1)
        samples.forEachIndexed { index, value ->
            val height = (size.height * (value.coerceIn(4, 100) / 100f)).coerceAtLeast(3f)
            val x = step * index + step / 2
            drawLine(color, start = androidx.compose.ui.geometry.Offset(x, (size.height - height) / 2), end = androidx.compose.ui.geometry.Offset(x, (size.height + height) / 2), strokeWidth = (step * .42f).coerceIn(2f, 7f), cap = StrokeCap.Round)
        }
    }
}

/** عرض رسالة ملصق — إيموجي كبير كمعاينة (الصورة الفعلية تُحمّل عند التوفر). */
@Composable

internal fun StickerMessage(item: DecryptedMessage, attachments: AttachmentViewModel) {
    val payload = remember(item.id) {
        runCatching { ATTACHMENT_JSON.decodeFromString<com.red.sovereign.media.StickerMessagePayload>(item.plaintext.toString(Charsets.UTF_8)) }.getOrNull()
    }
    if (payload == null) {
        Text("ملصق غير صالح", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        return
    }
    // عرض الإيموجي كمعاينة كبيرة (الصورة تُحمّل عند التوفر عبر attachments)
    Text(payload.emoji, fontSize = 64.sp)
}

@Composable

internal fun VoiceMessage(item: DecryptedMessage, attachments: AttachmentViewModel) {
    val manifestJson = item.plaintext.toString(Charsets.UTF_8)
    val manifest = remember(manifestJson) { runCatching { ATTACHMENT_JSON.decodeFromString<VoiceManifest>(manifestJson) }.getOrNull() }
    if (manifest == null) {
        Text("رسالة صوتية غير صالحة", color = MaterialTheme.colorScheme.error)
        return
    }
    val context = LocalContext.current
    LaunchedEffect(item.id, SettingsRuntime.current.autoDownloadWifi, SettingsRuntime.current.autoDownloadMobile) {
        if (!item.outgoing && shouldAutoDownload(context, manifest.size)) attachments.downloadVoice(item.id, manifestJson)
    }
    val isDownloaded = when (val current = attachments.getDownloadState(item.id)) {
        is AttachmentState.Downloaded -> current.name == manifest.name
        is AttachmentState.Exported -> current.name == manifest.name
        else -> false
    }
    val isDownloading = attachments.getDownloadState(item.id) is AttachmentState.Working
    val downloadedUri = when (val current = attachments.getDownloadState(item.id)) {
        is AttachmentState.Downloaded -> if (current.name == manifest.name) {
            contentUriForFile(context, java.io.File(current.path))
        } else null
        is AttachmentState.Exported -> if (current.name == manifest.name) {
            contentUriForFile(context, java.io.File(current.path))
        } else null
        else -> null
    }

    if (downloadedUri != null) {
        // 🎙️ مشغّل احترافي مع waveform
        VoiceNotePlayer(
            uri = downloadedUri,
            waveform = manifest.waveform,
            durationSeconds = manifest.durationSeconds,
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        // 💬 فقاعة احترافية قبل التنزيل
        VoiceBubble(
            manifest = manifest,
            isOutgoing = item.outgoing,
            isDownloaded = isDownloaded,
            isDownloading = isDownloading,
            onPlayPause = { attachments.downloadVoice(item.id, manifestJson) },
            onSeek = { /* no-op before download */ },
            onSpeedChange = { /* no-op before download */ },
            onDownload = { attachments.downloadVoice(item.id, manifestJson) },
            onWaveformTap = { attachments.downloadVoice(item.id, manifestJson) }
        )
    }
}

@Composable

internal fun AttachmentMessage(item: DecryptedMessage, attachments: AttachmentViewModel) {
    val manifestJson = item.plaintext.toString(Charsets.UTF_8)
    val manifest = remember(manifestJson) { runCatching { ATTACHMENT_JSON.decodeFromString<AttachmentManifest>(manifestJson) }.getOrNull() }
    if (manifest == null) {
        Text("مرفق مشفر غير صالح", color = MaterialTheme.colorScheme.error)
        return
    }
    val context = LocalContext.current
    LaunchedEffect(item.id, SettingsRuntime.current.autoDownloadWifi, SettingsRuntime.current.autoDownloadMobile) {
        if (!item.outgoing && shouldAutoDownload(context, manifest.size)) attachments.download(item.id, manifestJson)
    }
    when {
        manifest.mimeType.startsWith("image/") -> ImageMessage(item, manifest, attachments)
        manifest.mimeType.startsWith("video/") -> VideoMessage(item, manifest, attachments)
        manifest.mimeType.startsWith("audio/") -> AudioMessage(item, manifest, attachments)
        else -> FileMessage(item, manifest, attachments)
    }
}

@Composable

private fun ImageMessage(item: DecryptedMessage, manifest: AttachmentManifest, attachments: AttachmentViewModel) {
    val context = LocalContext.current
    LaunchedEffect(item.id, SettingsRuntime.current.autoDownloadWifi, SettingsRuntime.current.autoDownloadMobile) {
        if (!item.outgoing && shouldAutoDownload(context, manifest.size)) attachments.download(item.id, item.plaintext.toString(Charsets.UTF_8))
    }
    val downloaded = when (val current = attachments.getDownloadState(item.id)) {
        is AttachmentState.Downloaded -> current.path to current.name
        is AttachmentState.Exported -> current.path to current.name
        else -> null
    }
    val isWorking = attachments.getDownloadState(item.id) is AttachmentState.Working
    if (downloaded?.second == manifest.name) {
        val file = java.io.File(downloaded.first)
        // G3: فك الترميز كان على الخيط الرئيسي (remember + decodeFile) — يسبب
        // تقطعاً وANR مع الصور الكبيرة. الآن لاتزامني على IO مع تقليص
        // محسوب لحد أقصى 1024px لتقليل الذاكرة وإعادة التركيب.
        val fileKey = file.absolutePath to runCatching { file.lastModified() }.getOrDefault(0L)
        val bitmap by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(
            initialValue = null, fileKey
        ) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeFile(file.absolutePath, bounds)
                    var sample = 1
                    val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
                    while (maxDim / (sample * 2) >= 1024 && sample < 8) sample *= 2
                    val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                    android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts)?.asImageBitmap()
                }.getOrNull()
            }
        }
        val currentBitmap = bitmap
        if (currentBitmap != null) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomEnd) {
                androidx.compose.foundation.Image(
                    currentBitmap, contentDescription = "صورة",
                    modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable {
                        // content:// عبر FileProvider (API 24+ يحظر file://).
                        val uri = contentUriForFile(context, file)
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "image/*")
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching { context.startActivity(intent) }
                    },
                    contentScale = ContentScale.Crop
                )
                // شارة الحجم والتحقق المشفر
                Surface(Modifier.padding(6.dp), shape = RoundedCornerShape(8.dp), color = Color.Black.copy(alpha = 0.6f)) {
                    Text(" ✓ مشفرة • ${dashboardFormatBytes(manifest.size)}", color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                }
            }
        }
    } else {
        Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                if (isWorking) {
                    CircularProgressIndicator(color = YounesEmerald, strokeWidth = 3.dp)
                    Spacer(Modifier.height(10.dp))
                    Text("جارٍ فك التشفير…", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                } else {
                    Icon(Icons.Default.Photo, null, tint = YounesEmerald, modifier = Modifier.size(52.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(manifest.name.take(24), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
                    Text("${dashboardFormatBytes(manifest.size)} • مشفرة", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    IconButton({ attachments.download(item.id, item.plaintext.toString(Charsets.UTF_8)) }, enabled = !isWorking) {
                        Surface(Modifier.size(44.dp), shape = CircleShape, color = YounesEmerald) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Download, "تنزيل", tint = Color(0xFF002118)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable

private fun VideoMessage(item: DecryptedMessage, manifest: AttachmentManifest, attachments: AttachmentViewModel) {
    val context = LocalContext.current
    LaunchedEffect(item.id, SettingsRuntime.current.autoDownloadWifi, SettingsRuntime.current.autoDownloadMobile) {
        if (!item.outgoing && shouldAutoDownload(context, manifest.size)) attachments.download(item.id, item.plaintext.toString(Charsets.UTF_8))
    }
    val downloaded = when (val current = attachments.getDownloadState(item.id)) {
        is AttachmentState.Downloaded -> current.path to current.name
        is AttachmentState.Exported -> current.path to current.name
        else -> null
    }
    if (downloaded?.second == manifest.name) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.Black), shape = RoundedCornerShape(16.dp)) {
            // content:// عبر FileProvider (API 24+ يحظر file://).
            val videoUri = contentUriForFile(context, java.io.File(downloaded.first))
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentAlignment = Alignment.Center) {
                StoryVideoPlayer(videoUri, Modifier.fillMaxSize())
                Box(
                    Modifier
                        .align(Alignment.Center).size(52.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .clickable {
                        val uri = contentUriForFile(context, java.io.File(downloaded.first))
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "video/*")
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching { context.startActivity(intent) }
                    }
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(34.dp))
                }
            }
        }
    } else {
        val isWorking = attachments.getDownloadState(item.id) is AttachmentState.Working
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(YounesEmerald.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Videocam, null, tint = YounesEmerald, modifier = Modifier.size(34.dp))
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(manifest.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text("فيديو مشفر · ${dashboardFormatBytes(manifest.size)}", style = MaterialTheme.typography.labelSmall)
                }
                if (isWorking) CircularProgressIndicator(Modifier.size(24.dp), color = YounesEmerald, strokeWidth = 3.dp)
                else IconButton({ attachments.download(item.id, item.plaintext.toString(Charsets.UTF_8)) }, enabled = !isWorking) {
                    Icon(Icons.Default.Download, "تنزيل الفيديو", tint = YounesEmerald)
                }
            }
        }
    }
}

@Composable

private fun AudioMessage(item: DecryptedMessage, manifest: AttachmentManifest, attachments: AttachmentViewModel) {
    val context = LocalContext.current
    LaunchedEffect(item.id, SettingsRuntime.current.autoDownloadWifi, SettingsRuntime.current.autoDownloadMobile) {
        if (!item.outgoing && shouldAutoDownload(context, manifest.size)) attachments.download(item.id, item.plaintext.toString(Charsets.UTF_8))
    }
    val downloaded = when (val current = attachments.getDownloadState(item.id)) {
        is AttachmentState.Downloaded -> current.path to current.name
        is AttachmentState.Exported -> current.path to current.name
        else -> null
    }
    if (downloaded?.second == manifest.name) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(AqyalCyanGlow.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.MusicNote, null, tint = AqyalCyanGlow, modifier = Modifier.size(20.dp))
                    }
                    Text(manifest.name, Modifier.padding(start = 10.dp).weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("✓ مشفرة", color = YounesEmerald, fontSize = 10.sp)
                }
                VoiceNotePlayer(uri = contentUriForFile(context, java.io.File(downloaded.first)), modifier = Modifier.fillMaxWidth())
            }
        }
    } else {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(AqyalCyanGlow.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.MusicNote, null, tint = AqyalCyanGlow, modifier = Modifier.size(30.dp))
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(manifest.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text("صوت مشفر · ${dashboardFormatBytes(manifest.size)}", style = MaterialTheme.typography.labelSmall)
                }
                IconButton({ attachments.download(item.id, item.plaintext.toString(Charsets.UTF_8)) }, enabled = attachments.getDownloadState(item.id) !is AttachmentState.Working) {
                    Icon(Icons.Default.Download, "تنزيل الصوت")
                }
            }
        }
    }
}

@Composable

private fun FileMessage(item: DecryptedMessage, manifest: AttachmentManifest, attachments: AttachmentViewModel) {
    val context = LocalContext.current
    LaunchedEffect(item.id, SettingsRuntime.current.autoDownloadWifi, SettingsRuntime.current.autoDownloadMobile) {
        if (!item.outgoing && shouldAutoDownload(context, manifest.size)) attachments.download(item.id, item.plaintext.toString(Charsets.UTF_8))
    }
    val isWorking = attachments.getDownloadState(item.id) is AttachmentState.Working
    val fileColor = when {
        manifest.mimeType.contains("pdf") -> AqyalCyanGlow
        manifest.mimeType.contains("zip") || manifest.mimeType.contains("compressed") -> AqyalGold
        manifest.mimeType.contains("text") || manifest.mimeType.contains("word") -> Color(0xFF4FC3F7)
        manifest.mimeType.contains("sheet") || manifest.mimeType.contains("excel") -> YounesEmerald
        manifest.mimeType.contains("presentation") || manifest.mimeType.contains("powerpoint") -> Color(0xFFF06292)
        else -> AqyalCyanGlow
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(fileColor.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, tint = fileColor, modifier = Modifier.size(30.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(manifest.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text("${manifest.mimeType} · ${dashboardFormatBytes(manifest.size)}", style = MaterialTheme.typography.labelSmall)
            }
            if (isWorking) CircularProgressIndicator(Modifier.size(24.dp), color = YounesEmerald, strokeWidth = 3.dp)
            else IconButton({ attachments.download(item.id, item.plaintext.toString(Charsets.UTF_8)) }, enabled = !isWorking) {
                Icon(Icons.Default.Download, "تنزيل وفك تشفير المرفق", tint = YounesEmerald)
            }
        }
    }
}

private fun shouldAutoDownload(context: android.content.Context, sizeBytes: Long): Boolean =
    RedQualityManager.shouldAutoDownload(context, sizeBytes)

/**
 * content:// عبر FileProvider للملفات المحلية (API 24+ يحظر file://
 * ويرمي FileUriExposedException — لا استعمال لـ Uri.fromFile إطلاقاً).
 */
private fun contentUriForFile(context: android.content.Context, file: java.io.File): android.net.Uri =
    FileProvider.getUriForFile(context, "com.red.sovereign.fileprovider", file)

// (نُقلت formatDuration/formatBytes إلى DashboardMedia.kt بصيغة dashboard* وLocale("ar"). 2026-09-10)

@Composable
internal fun MessageActionRow(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(YounesEmerald.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = YounesEmerald, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.padding(start = 14.dp)) {
                Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
    }
}

/**
 * عرض تفاعلات الإيموجي تحت رسالة (chips مع العد). الضغط على إيموجي = toggle
 * (إزالة إن كان تفاعلك، لا شيء إن لم يكن). E2EE: الإيموجي محلي فقط.
 */
@Composable

internal fun MessageReactions(
    reactions: List<MessageReactionEntity>,
    currentRedId: String,
    onToggle: (emoji: String) -> Unit
) {
    if (reactions.isEmpty()) return
    // تجميع حسب الإيموجي مع العد، مرتب تنازلياً حسب العد
    val grouped = remember(reactions) {
        reactions.groupBy { it.emoji }
            .mapValues { it.value.size }
            .entries.sortedByDescending { it.value }
            .associate { it.key to it.value }
    }
    val myEmoji = remember(reactions, currentRedId) {
        reactions.firstOrNull { it.senderId == currentRedId }?.emoji
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        items(grouped.entries.toList(), key = { it.key }) { (emoji, count) ->
            val mine = emoji == myEmoji
            Surface(
                shape = RoundedCornerShape(50),
                color = if (mine) YounesEmerald.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (mine) YounesEmerald else androidx.compose.ui.graphics.Color.Transparent),
                modifier = Modifier.clickable { onToggle(emoji) }
            ) {
                Row(
                    Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(emoji, fontSize = 14.sp)
                    Text(count.toString(), fontSize = 11.sp, color = if (mine) YounesEmerald else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (mine) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

/** قائمة الإيموجي السريعة للتفاعل — تظهر أعلى قائمة إجراءات الرسالة. */
@Composable

internal fun ReactionEmojiBar(onPick: (String) -> Unit) {
    val quick = remember { listOf("👍", "❤️", "😂", "🙏", "🔥", "👏", "😮", "😢", "🎉", "💯") }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        items(quick) { emoji ->
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(40.dp).clickable { onPick(emoji) }
            ) {
                Box(contentAlignment = Alignment.Center) { Text(emoji, fontSize = 22.sp) }
            }
        }
    }
}

@Composable

internal fun EmojiPicker(onEmoji: (String) -> Unit) {
    var category by remember { mutableIntStateOf(0) }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(vertical = 6.dp)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(horizontal = 8.dp)) {
                items(EMOJI_CATEGORIES.indices.toList()) { index ->
                    FilterChip(selected = category == index, onClick = { category = index }, label = { Text(EMOJI_CATEGORIES[index].first) })
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(horizontal = 6.dp)) {
                items(EMOJI_CATEGORIES[category].second) { emoji ->
                    TextButton({ onEmoji(emoji) }) { Text(emoji, fontSize = 24.sp) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable

internal fun AttachmentSheet(
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onDocument: () -> Unit,
    onDismiss: () -> Unit
) = ModalBottomSheet(
    onDismissRequest = onDismiss,
    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    containerColor = MaterialTheme.colorScheme.surface
) {
    Column(Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("إرفاق", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f).clickable(onClick = { onCamera(); onDismiss() }).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Camera, null, tint = YounesEmerald, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(4.dp))
                Text("الكاميرا", fontWeight = FontWeight.Medium)
                Text("التقط صورة أو فيديو", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Column(Modifier.weight(1f).clickable(onClick = { onGallery(); onDismiss() }).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Photo, null, tint = AqyalCyanGlow, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(4.dp))
                Text("المعرض", fontWeight = FontWeight.Medium)
                Text("اختر من الصور والفيديوهات", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Column(Modifier.weight(1f).clickable(onClick = { onDocument(); onDismiss() }).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null, tint = AqyalGold, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(4.dp))
                Text("ملف", fontWeight = FontWeight.Medium)
                Text("PDF، مستندات، مضغوطات", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// مصدر الحقيقة الوحيد: core/YounesId.kt. النمط كان مكرّرًا هنا وفي
// QrScannerSheet وSafetyViewModel بصياغات متباينة، فكان معرّف يقبله
// أحدها وترفضه الشاشة التالية.

// الهاشتاجات العربية/اللاتينية

// ATTACHMENT_JSON معرّف في DashboardIdentifiers.kt (نفس الحزمة) — لا تكرره هنا.

internal val HASHTAG_PARTIAL = Regex("#[\\w\u0600-\u06FF]{2,30}")
// اسم المستخدم للـ @ autocomplete

internal val USERNAME_PARTIAL = Regex("@([A-Za-z0-9_.]{1,20})$")
// الهاشتاج لـ # autocomplete

internal val HASHTAG_AUTOCOMPLETE = Regex("#([\\w\u0600-\u06FF]{1,20})$")
