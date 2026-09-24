package com.red.sovereign.features.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.core.RichMessage
import com.red.sovereign.ui.theme.AqyalCyanGlow
import com.red.sovereign.ui.theme.PlexArabicFamily
import com.red.sovereign.ui.theme.YounesEmerald

/** عتامة الوقت/الثانوي — قابلة للقياس (≥0.85) ومستخدمة في كل النصوص الثانوية. */
private const val BubbleDimAlpha: Float = 0.85f

/**
 * فقاعة دردشة "Luxury" — تصميم عصري وحصري للمنصة السيادية.
 * يتميز بزوايا ناعمة وتدرج لوني يعكس حالة الرسالة.
 */
@Composable
fun LuxuryChatBubble(
    message: String,
    isMe: Boolean,
    time: String,
    status: String,
    onLongClick: () -> Unit = {},
    onClick: () -> Unit = {},
    onSwipeReply: ((String) -> Unit)? = null,
    messageId: String = "",
    modifier: Modifier = Modifier,
    // G3: معرف المرسل — اسم + Red ID ظاهر للرسائل الواردة (مجموعات/خاص).
    // افتراضي فارغ للحفاظ على كل الاستدعاءات الحالية دون كسر البناء.
    senderName: String = "",
    senderRedId: String = "",
    fontFamily: FontFamily? = null,
    bubbleStyle: String = "LUXURY",
    isSelected: Boolean = false,
    // P0-B: شارة التحويل — RichMessage.forwardOf/forwardCount موجودان في الموديل.
    // مرّر richMessage عند توفر الحمولة المفكوكة، وإلا تُستخدم forwardOf/forwardCount
    // اليدوية شرطيًا (لا شارة عند غياب المعلومة). "كثيرة التحويل" عندما العدد > 5.
    forwardOf: String? = null,
    forwardCount: Int = 0,
    richMessage: RichMessage? = null,
    // replyTo: اقتباس قابل للنقر يقفز للأصل عبر onReplyClick.
    replyToId: String? = null,
    replyToText: String? = null,
    replyToSender: String? = null,
    onReplyClick: (() -> Unit)? = null,
    // تفاعلات: emoji -> count (chips قابلة للنقر عبر onReactionClick).
    reactions: Map<String, Int> = emptyMap(),
    onReactionClick: ((String) -> Unit)? = null,
    isEdited: Boolean = false,
    hideSenderHeader: Boolean = false
) {
    val resolvedForwardOf: String? = richMessage?.forwardOf ?: forwardOf
    val resolvedForwardCount: Int = (richMessage?.forwardCount ?: forwardCount).coerceAtLeast(0)
    val isForwarded: Boolean = resolvedForwardOf != null || resolvedForwardCount > 0
    val resolvedReplyId: String? = richMessage?.replyTo ?: replyToId
    val resolvedReplyText: String? = replyToText ?: resolvedReplyId?.let { "رسالة: ${it.take(32)}" }
    val hasReply: Boolean = resolvedReplyText != null || resolvedReplyId != null
    val resolvedEdited: Boolean = isEdited || (richMessage?.editOf != null)
    val resolvedFont = fontFamily ?: PlexArabicFamily
    val bubbleColor = if (isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isMe) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    // وقت ≥11sp بعتامة 0.85 قابلة للقياس (كان 0.70).
    val timeColor = textColor.copy(alpha = BubbleDimAlpha)
    // منشن/هاشتاغ مميز وقابل للقياس (بلا alpha): الصادرة نص داكن عالي التباين، الواردة زمرد/كوبالت صلبان.
    val mentionColor = if (isMe) Color(0xFF002118) else YounesEmerald
    val hashtagColor = if (isMe) Color(0xFF0B3D91) else AqyalCyanGlow
    val shape = when (bubbleStyle) {
        "CLASSIC" -> RoundedCornerShape(12.dp)
        "MINIMAL" -> RoundedCornerShape(6.dp)
        else -> RoundedCornerShape(
            topStart = 18.dp,
            topEnd = 18.dp,
            bottomStart = if (isMe) 18.dp else 4.dp,
            bottomEnd = if (isMe) 4.dp else 18.dp
        )
    }

    var dragX by remember { mutableStateOf(0f) }
    val swipeThreshold = with(LocalDensity.current) { 80.dp.toPx() }
    val isReplyTriggered = remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    // LEGENDARY: محاذاة واتساب — فقاعتي 85% كحد أقصى + تجميع بصري (رسائل متتالية متقاربة)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        // أيقونة الرد عند السحب (تظهر تدريجياً)
        if (dragX > 20) {
            androidx.compose.material3.Icon(
                Icons.AutoMirrored.Rounded.Reply,
                "رد",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = (dragX / swipeThreshold).coerceIn(0f, 1f)),
                modifier = Modifier.align(if (isMe) Alignment.CenterStart else Alignment.CenterStart).padding(start = 8.dp)
            )
        }
        Column(
            modifier = Modifier
                .widthIn(min = 80.dp, max = 300.dp)
                .clip(shape)
                .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else bubbleColor)
                .then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
                // LEGENDARY FIX: دمج Tap+Long في combinedClickable + Drag بمفتاح مختلف (كان pointerInput(Unit) مكرراً فيلغي long-press ويبتلع السكرول)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .pointerInput(messageId) {
                    detectDragGestures(
                        onDragStart = {},
                        onDrag = { change, dragAmount ->
                            // سحب أفقي فقط للرد — العمودي يُترك للـ LazyColumn (كان يبتلع السكرول)
                            if (kotlin.math.abs(dragAmount.x) > kotlin.math.abs(dragAmount.y) * 1.5f) {
                                change.consume()
                                val newDragX = dragX + dragAmount.x
                                if (newDragX > 0 && !isReplyTriggered.value) {
                                    dragX = newDragX.coerceAtMost(swipeThreshold * 2)
                                    if (dragX >= swipeThreshold) {
                                        isReplyTriggered.value = true
                                        if (messageId.isNotBlank()) onSwipeReply?.invoke(messageId)
                                        android.widget.Toast.makeText(ctx, "تم تعيين الرسالة للرد", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } else if (newDragX < 0) {
                                    dragX = newDragX.coerceAtLeast(-swipeThreshold)
                                }
                            }
                        },
                        onDragEnd = {
                            dragX = 0f
                            isReplyTriggered.value = false
                        },
                        onDragCancel = {
                            dragX = 0f
                            isReplyTriggered.value = false
                        }
                    )
                }
                .padding(start = 12.dp, top = 8.dp, end = 10.dp, bottom = 6.dp)
                .graphicsLayer { translationX = dragX }
        ) {
            // G3: ترويسة المرسل للوارد فقط — الاسم بارز + Red ID الكامل بخط صغير (تُخفى عند الدمج البصري).
            if (!isMe && !hideSenderHeader && (senderName.isNotBlank() || senderRedId.isNotBlank())) {
                Text(
                    text = senderName.ifBlank { senderRedId },
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = resolvedFont,
                    maxLines = 1
                )
                if (senderRedId.isNotBlank() && senderName.isNotBlank()) {
                    Text(
                        text = senderRedId,
                        color = timeColor,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                } else if (senderRedId.isNotBlank()) {
                    Text(
                        text = senderRedId,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = BubbleDimAlpha),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
            }
            // P0-B: شارة التحويل — "محوّلة" عند التوفر + عداد،
            // و"كثيرة التحويل" عندما العدد > 5.
            if (isForwarded) {
                Text(
                    text = when {
                        resolvedForwardCount > 5 -> "كثيرة التحويل • $resolvedForwardCount"
                        resolvedForwardCount > 0 -> "محوّلة • $resolvedForwardCount"
                        else -> "محوّلة"
                    },
                    color = timeColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = resolvedFont,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
            }
            // replyTo: اقتباس قابل للنقر يقفز للأصل عبر onReplyClick.
            if (hasReply) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f))
                        .then(if (onReplyClick != null) Modifier.clickable(onClick = onReplyClick) else Modifier)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .heightIn(min = 28.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        if (!replyToSender.isNullOrBlank() || richMessage?.replyTo != null) {
                            Text(
                                text = replyToSender?.takeIf { it.isNotBlank() } ?: "رد",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = resolvedFont,
                                maxLines = 1
                            )
                        }
                        Text(
                            text = resolvedReplyText.orEmpty(),
                            color = timeColor,
                            fontSize = 12.sp,
                            fontFamily = resolvedFont,
                            maxLines = 2
                        )
                    }
                }
            }
            Text(
                text = remember(message, isMe) { annotatedWithMentions(message, textColor, mentionColor, hashtagColor) },
                color = textColor,
                fontSize = 16.sp,
                lineHeight = 26.sp,
                fontFamily = resolvedFont,
                style = androidx.compose.material3.LocalTextStyle.current.copy(
                    lineBreak = LineBreak.Paragraph
                )
            )
            // تفاعلات: chips مع العد — الضغط toggle عبر onReactionClick.
            if (reactions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    reactions.entries.sortedByDescending { it.value }.take(6).forEach { (emoji, count) ->
                        androidx.compose.material3.Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                            modifier = Modifier.then(
                                if (onReactionClick != null) Modifier.clickable { onReactionClick(emoji) } else Modifier
                            )
                        ) {
                            Row(
                                Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(emoji, fontSize = 13.sp)
                                if (count > 1) {
                                    Text(
                                        count.toString(),
                                        fontSize = 11.sp,
                                        color = timeColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // LEGENDARY: سطر الوقت+الحالة بأسلوب واتساب — لا يتداخل مع النص (align End + حد أدنى للعرض)
            Row(
                modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // عزل Bidi للأوقات داخل الجمل العربية — ≥11sp بعتامة 0.85 قابلة للقياس.
                Text(
                    text = android.text.BidiFormatter.getInstance().unicodeWrap(time),
                    fontSize = 11.sp,
                    color = timeColor,
                    fontFamily = resolvedFont,
                    maxLines = 1
                )
                if (resolvedEdited) {
                    Text(
                        text = "• مُعدَّلة",
                        fontSize = 11.sp,
                        color = timeColor,
                        fontFamily = resolvedFont,
                        maxLines = 1
                    )
                }
                if (isMe) {
                    // واتساب: ✓ رمادي (مرسَل) / ✓✓ رمادي (مستلَم) / ✓✓ أزرق (مقروء) + ◷ قيد الإرسال + ⚠ فشل
                    val tick = when (status.uppercase()) {
                        "READ" -> "✓✓"
                        "DELIVERED" -> "✓✓"
                        "SENT" -> "✓✓"
                        "SENDING", "PENDING", "QUEUED" -> "◷"
                        "FAILED", "ERROR", "DEAD_LETTER" -> "⚠"
                        else -> "✓"
                    }
                    val tickColor = when (status.uppercase()) {
                        "READ" -> com.red.sovereign.ui.theme.YounesReadTick
                        "FAILED", "ERROR", "DEAD_LETTER" -> Color(0xFFF25C5C)
                        "SENDING", "PENDING", "QUEUED" -> timeColor.copy(alpha = BubbleDimAlpha)
                        else -> timeColor
                    }
                    Text(
                        text = tick,
                        color = tickColor,
                        fontSize = 12.sp,
                        fontWeight = if (status.uppercase() == "READ") FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1
                    )
            }
        }
    }
}
}

/** LEGENDARY: تمييز @all/@user بلون أساسي + #hashtag بلون مميز ثانٍ (واتساب يبرز المنشن — كان نصاً عادياً يضيع). دالة خالصة (تُستدعى داخل remember من الأعلى). */
private fun annotatedWithMentions(
    message: String,
    base: Color,
    mentionColor: Color = Color(0xFF14C79A),
    hashtagColor: Color = Color(0xFF4D9FE8)
): androidx.compose.ui.text.AnnotatedString {
        val mentionRegex = Regex("@(all|الجميع|online|متصل|[A-Z0-9]{5,16})", RegexOption.IGNORE_CASE)
        val hashtagRegex = Regex("#[\\w\u0600-\u06FF\\-]{2,30}")
        val builder = androidx.compose.ui.text.AnnotatedString.Builder(message)
        mentionRegex.findAll(message).forEach { m ->
            runCatching {
                builder.addStyle(
                    androidx.compose.ui.text.SpanStyle(color = mentionColor, fontWeight = FontWeight.Bold),
                    m.range.first, m.range.last + 1
                )
            }
        }
        hashtagRegex.findAll(message).forEach { m ->
            runCatching {
                builder.addStyle(
                    androidx.compose.ui.text.SpanStyle(color = hashtagColor, fontWeight = FontWeight.Bold),
                    m.range.first, m.range.last + 1
                )
            }
        }
        return builder.toAnnotatedString()
}
