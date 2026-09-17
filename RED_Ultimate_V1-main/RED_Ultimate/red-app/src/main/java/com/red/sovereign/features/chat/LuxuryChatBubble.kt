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
import androidx.compose.material3.MaterialTheme

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
    // الرد المقتبس — قابل للنقر يقفز للأصل عبر onReplyClick(messageId).
    replyToMessageId: String? = null,
    replyToPreview: String? = null,
    replyToSender: String? = null,
    onReplyClick: ((String) -> Unit)? = null
) {
    val resolvedForwardOf: String? = richMessage?.forwardOf ?: forwardOf
    val resolvedForwardCount: Int = (richMessage?.forwardCount ?: forwardCount).coerceAtLeast(0)
    val isForwarded: Boolean = resolvedForwardOf != null || resolvedForwardCount > 0
    val resolvedReplyTo: String? = richMessage?.replyTo ?: replyToMessageId
    val resolvedFont = fontFamily ?: FontFamily.Default
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.4f
    val bubbleColor = if (isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val textColor = if (isMe) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val timeColor = if (isMe) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant
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
            // G3: ترويسة المرسل للوارد فقط — الاسم بارز + Red ID الكامل بخط صغير.
            if (!isMe && (senderName.isNotBlank() || senderRedId.isNotBlank())) {
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
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                } else if (senderRedId.isNotBlank()) {
                    Text(
                        text = senderRedId,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
            }
            // P0-B: شارة التحويل — "محوّلة" عند التوفر،
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
            // اقتباس الرد — قابل للنقر يقفز للرسالة الأصلية.
            resolvedReplyTo?.let { replyId ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isMe) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.3f))
                        .then(if (onReplyClick != null) Modifier.clickable { onReplyClick.invoke(replyId) } else Modifier)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .heightIn(min = 28.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (isMe) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary)
                    )
                    Column(Modifier.weight(1f)) {
                        if (!replyToSender.isNullOrBlank()) {
                            Text(
                                text = replyToSender,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = resolvedFont,
                                maxLines = 1
                            )
                        }
                        Text(
                            text = replyToPreview?.takeIf { it.isNotBlank() } ?: "اضغط للانتقال إلى الرسالة الأصلية",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontFamily = resolvedFont,
                            maxLines = 2
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                text = remember(message, isMe) { annotatedWithMentions(message, isMe) },
                color = textColor,
                fontSize = 16.sp,
                lineHeight = 26.sp,
                fontFamily = resolvedFont,
                style = androidx.compose.material3.LocalTextStyle.current.copy(
                    lineBreak = LineBreak.Paragraph
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            // LEGENDARY: سطر الوقت+الحالة بأسلوب واتساب — لا يتداخل مع النص (align End + حد أدنى للعرض)
            Row(
                modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // عزل Bidi للأوقات داخل الجمل العربية
                Text(
                    text = android.text.BidiFormatter.getInstance().unicodeWrap(time),
                    fontSize = 11.sp,
                    color = timeColor,
                    fontFamily = resolvedFont,
                    maxLines = 1
                )
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
                        "READ" -> MaterialTheme.colorScheme.tertiary
                        "FAILED", "ERROR", "DEAD_LETTER" -> MaterialTheme.colorScheme.error
                        "SENDING", "PENDING", "QUEUED" -> timeColor
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

/** LEGENDARY: منشن مميز فعلاً + هاشتاغ — داكنان على الصادرة الفاتحة، مضيئان على الواردة الداكنة (كلها معتمة). دالة خالصة (تُستدعى داخل remember من الأعلى). */
private fun annotatedWithMentions(message: String, isMe: Boolean): androidx.compose.ui.text.AnnotatedString {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.4f
    val mentionColor = if (isMe) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.tertiary
    val hashtagColor = if (isMe) MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.secondary
        val mentionRegex = Regex("@(all|الجميع|online|متصل|[A-Z0-9]{5,16})", RegexOption.IGNORE_CASE)
        val hashtagRegex = Regex("#[\\w\u0600-\u06FF]{2,30}")
        val builder = androidx.compose.ui.text.AnnotatedString.Builder(message)
        mentionRegex.findAll(message).forEach { m ->
            runCatching {
                builder.addStyle(
                    androidx.compose.ui.text.SpanStyle(
                        color = mentionColor,
                        fontWeight = FontWeight.Bold,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                    ),
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
