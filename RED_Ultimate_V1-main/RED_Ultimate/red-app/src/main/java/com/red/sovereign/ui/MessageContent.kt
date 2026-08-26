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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Send
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
import com.red.sovereign.settings.SettingsRuntime
import com.red.sovereign.stories.StoryVideoPlayer
import com.red.sovereign.ui.theme.AqyalCyanGlow
import com.red.sovereign.ui.theme.AqyalGold
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

internal fun messageDisplayText(message: DecryptedMessage): String =
    if (message.type == "RICH_TEXT") RichMessage.decode(message.plaintext)?.text.orEmpty() else message.plaintext.toString(Charsets.UTF_8)

@Composable

internal fun RichTextMessage(message: DecryptedMessage, conversation: List<DecryptedMessage>, mentionLabel: (String) -> String = { it }) {
    val rich = RichMessage.decode(message.plaintext)
    if (rich == null) { Text("رسالة غير صالحة", color = MaterialTheme.colorScheme.error); return }
    rich.replyTo?.let { replyId -> conversation.firstOrNull { it.id == replyId }?.let { quoted -> Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .45f))) { Text(messageDisplayText(quoted), Modifier.padding(7.dp), maxLines = 2, style = MaterialTheme.typography.bodySmall) } } }
    if (rich.forwardOf != null) Text("معاد توجيهها", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val annotated = remember(rich.text, rich.mentions, rich.hashtags) {
        val t = rich.text
        val mentions = (rich.mentions + RED_ID_PARTIAL.findAll(t).map { it.value } + GroupMentions.NAME_TOKEN.findAll(t).map { it.value }).distinct()
        val hashtags = rich.hashtags + HASHTAG_PARTIAL.findAll(t).map { it.value }.toList()
        androidx.compose.ui.text.buildAnnotatedString {
            append(t)
            mentions.forEach { m -> val idx = t.indexOf(m); if (idx >= 0) addStyle(androidx.compose.ui.text.SpanStyle(color = YounesEmerald, fontWeight = FontWeight.Bold), idx, idx + m.length) }
            hashtags.forEach { h -> val idx = t.indexOf(h); if (idx >= 0) addStyle(androidx.compose.ui.text.SpanStyle(color = AqyalCyanGlow), idx, idx + h.length) }
        }
    }
    Text(annotated, color = if (message.outgoing) Color(0xFF001B14) else MaterialTheme.colorScheme.onSurface)
    rich.poll?.let { poll ->
        InlinePollCard(poll, isOutgoing = message.outgoing)
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
    if (rich.mentions.isNotEmpty()) Text("ذكر: ${rich.mentions.joinToString { mentionLabel(it) }}", style = MaterialTheme.typography.labelSmall, color = YounesEmerald)
}

@Composable

private fun InlinePollCard(poll: com.red.sovereign.core.InlinePoll, isOutgoing: Boolean) {
    var selected by remember(poll.pollId) { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var votes by remember(poll.pollId) { androidx.compose.runtime.mutableStateOf(poll.votes) }
    val total = votes.sum().coerceAtLeast(1)
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
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
                val ratio = (optionVotes.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                val isSelected = selected == option
                Card(
                    Modifier.fillMaxWidth().clickable(enabled = !poll.isClosed) {
                        if (selected == option) {
                            selected = null
                            votes = votes.toMutableList().also { it[index] = (it.getOrElse(index) { 0 } - 1).coerceAtLeast(0) }
                        } else {
                            selected = option
                            votes = votes.toMutableList().also { it[index] = it.getOrElse(index) { 0 } + 1 }
                        }
                    },
                    colors = CardDefaults.cardColors(containerColor = if (isSelected) YounesEmerald.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(option, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            if (poll.isClosed || selected != null) Text("${(ratio * 100).toInt()}%", color = YounesEmerald, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        if (poll.isClosed || selected != null) {
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)), color = YounesEmerald, trackColor = MaterialTheme.colorScheme.surface)
                        }
                    }
                }
            }
            Text("إجمالي الأصوات: $total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
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
                if (voiceState.paused) "متوقف مؤقتًا ${formatDuration(voiceMessages.elapsedSeconds)}"
                else "● تسجيل ${formatDuration(voiceMessages.elapsedSeconds)}",
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

        // 🔒 إذا قُفل التسجيل، اعرض أزرار الإرسال والإلغاء
        if (isLocked) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = voiceMessages::cancel,
                    modifier = Modifier.weight(1f)
                ) { Text("حذف") }
                // الإرسال يتم عبر زر الإرسال الرئيسي في شريط الكتابة
                OutlinedButton(
                    onClick = { /* triggered via main send button */ },
                    modifier = Modifier.weight(1f),
                    enabled = false
                ) { Text("🔒 مُقفل — استخدم زر الإرسال") }
            }
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
                "معاينة الرسالة الصوتية • ${formatDuration(duration)}",
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
                else { Icon(Icons.Default.Send, null); Text(" إرسال") }
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
        if (!item.outgoing && shouldAutoDownload(context, manifest.size)) attachments.download(manifestJson)
    }
    val isDownloaded = when (val current = attachments.state) {
        is AttachmentState.Downloaded -> current.name == manifest.name
        is AttachmentState.Exported -> current.name == manifest.name
        else -> false
    }
    val isDownloading = attachments.state is AttachmentState.Working
    val downloadedUri = when (val current = attachments.state) {
        is AttachmentState.Downloaded -> if (current.name == manifest.name) {
            android.net.Uri.fromFile(java.io.File(current.path))
        } else null
        is AttachmentState.Exported -> if (current.name == manifest.name) {
            android.net.Uri.fromFile(java.io.File(current.path))
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
            onPlayPause = { attachments.download(manifestJson) },
            onSeek = { /* no-op before download */ },
            onSpeedChange = { /* no-op before download */ },
            onDownload = { attachments.download(manifestJson) },
            onWaveformTap = { attachments.download(manifestJson) }
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
        if (!item.outgoing && shouldAutoDownload(context, manifest.size)) attachments.download(manifestJson)
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
        if (!item.outgoing && shouldAutoDownload(context, manifest.size)) attachments.download(item.plaintext.toString(Charsets.UTF_8))
    }
    val downloaded = when (val current = attachments.state) {
        is AttachmentState.Downloaded -> current.path to current.name
        is AttachmentState.Exported -> current.path to current.name
        else -> null
    }
    val isWorking = attachments.state is AttachmentState.Working
    if (downloaded?.second == manifest.name) {
        val file = java.io.File(downloaded.first)
        val bitmap = remember(file.lastModified()) {
            val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
            android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts)?.asImageBitmap()
        }
        if (bitmap != null) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomEnd) {
                androidx.compose.foundation.Image(
                    bitmap, contentDescription = "صورة",
                    modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable {
                        val uri = android.net.Uri.fromFile(file)
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
                    Text(" ✓ مشفرة • ${formatBytes(manifest.size)}", color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
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
                    Text("${formatBytes(manifest.size)} • مشفرة", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    IconButton({ attachments.download(item.plaintext.toString(Charsets.UTF_8)) }, enabled = !isWorking) {
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
        if (!item.outgoing && shouldAutoDownload(context, manifest.size)) attachments.download(item.plaintext.toString(Charsets.UTF_8))
    }
    val downloaded = when (val current = attachments.state) {
        is AttachmentState.Downloaded -> current.path to current.name
        is AttachmentState.Exported -> current.path to current.name
        else -> null
    }
    if (downloaded?.second == manifest.name) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.Black), shape = RoundedCornerShape(16.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentAlignment = Alignment.Center) {
                StoryVideoPlayer(android.net.Uri.fromFile(java.io.File(downloaded.first)), Modifier.fillMaxSize())
                Surface(
                    Modifier.align(Alignment.Center).size(52.dp),
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.55f),
                    onClick = {
                        val uri = android.net.Uri.fromFile(java.io.File(downloaded.first))
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, "video/*")
                            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        runCatching { context.startActivity(intent) }
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(34.dp)) }
                }
            }
        }
    } else {
        val isWorking = attachments.state is AttachmentState.Working
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(YounesEmerald.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Videocam, null, tint = YounesEmerald, modifier = Modifier.size(34.dp))
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(manifest.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text("فيديو مشفر · ${formatBytes(manifest.size)}", style = MaterialTheme.typography.labelSmall)
                }
                if (isWorking) CircularProgressIndicator(Modifier.size(24.dp), color = YounesEmerald, strokeWidth = 3.dp)
                else IconButton({ attachments.download(item.plaintext.toString(Charsets.UTF_8)) }, enabled = !isWorking) {
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
        if (!item.outgoing && shouldAutoDownload(context, manifest.size)) attachments.download(item.plaintext.toString(Charsets.UTF_8))
    }
    val downloaded = when (val current = attachments.state) {
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
                VoiceNotePlayer(android.net.Uri.fromFile(java.io.File(downloaded.first)), Modifier.fillMaxWidth())
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
                    Text("صوت مشفر · ${formatBytes(manifest.size)}", style = MaterialTheme.typography.labelSmall)
                }
                IconButton({ attachments.download(item.plaintext.toString(Charsets.UTF_8)) }, enabled = attachments.state !is AttachmentState.Working) {
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
        if (!item.outgoing && shouldAutoDownload(context, manifest.size)) attachments.download(item.plaintext.toString(Charsets.UTF_8))
    }
    val isWorking = attachments.state is AttachmentState.Working
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
                Text("${manifest.mimeType} · ${formatBytes(manifest.size)}", style = MaterialTheme.typography.labelSmall)
            }
            if (isWorking) CircularProgressIndicator(Modifier.size(24.dp), color = YounesEmerald, strokeWidth = 3.dp)
            else IconButton({ attachments.download(item.plaintext.toString(Charsets.UTF_8)) }, enabled = !isWorking) {
                Icon(Icons.Default.Download, "تنزيل وفك تشفير المرفق", tint = YounesEmerald)
            }
        }
    }
}

private fun shouldAutoDownload(context: android.content.Context, sizeBytes: Long): Boolean =
    RedQualityManager.shouldAutoDownload(context, sizeBytes)

private fun formatDuration(seconds: Int) = "%d:%02d".format(seconds / 60, seconds % 60)

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

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

private val RED_ID_PARTIAL = Regex(YounesId.MENTION_PATTERN)
// الهاشتاجات العربية/اللاتينية

internal val HASHTAG_PARTIAL = Regex("#[\w\u0600-\u06FF]{2,30}")
// اسم المستخدم للـ @ autocomplete

internal val USERNAME_PARTIAL = Regex("@([A-Za-z0-9_.]{1,20})$")
// الهاشتاج لـ # autocomplete

internal val HASHTAG_AUTOCOMPLETE = Regex("#([\w\u0600-\u06FF]{1,20})$")

private val EMOJI_CATEGORIES = listOf(
    "سريعة" to listOf("😀", "😂", "😍", "👍", "❤️", "🔥", "👏", "🙏", "🎉", "😢", "😮", "✅"),
    "الوجوه" to listOf("😀", "😃", "😄", "😁", "😆", "😅", "😂", "🙂", "🙃", "😉", "😊", "🥰", "😍", "🤩", "😘", "😋", "😎", "🤔", "😴", "😭", "😡", "🥳"),
    "الإشارات" to listOf("👍", "👎", "👌", "✌️", "🤞", "🤟", "🤘", "👏", "🙌", "🫶", "🤝", "🙏", "💪", "👀", "❤️", "💚", "💛", "💙"),
    "الأشياء" to listOf("📱", "💻", "⌚", "📷", "🎥", "🎙️", "🔒", "🔑", "💡", "📌", "📎", "📁", "📄", "📚", "🎁", "🏆", "✅", "⚠️"),
    "الطبيعة" to listOf("🌙", "☀️", "⭐", "🔥", "🌈", "🌹", "🌿", "🌳", "🌊", "⛰️", "🐪", "🦅", "🐝", "🦋"),
    "الطعام" to listOf("☕", "🍵", "🥤", "🍞", "🥐", "🍚", "🍗", "🥗", "🍎", "🍉", "🍇", "🍯", "🎂"),
    "السفر" to listOf("🚗", "🚕", "🚌", "✈️", "🚁", "🚢", "🗺️", "🏠", "🏢", "🏥", "🏫", "🕌", "⛺"),
    "الرموز" to listOf("✅", "❌", "⚠️", "❗", "❓", "💯", "➕", "➖", "♻️", "🔴", "🟢", "🟡", "🔵", "🇾🇪")
)

private val ATTACHMENT_JSON = Json { ignoreUnknownKeys = true }
