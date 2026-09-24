package com.red.sovereign.features.live

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ════════════════════════════════════════════════════════════
//  DATA
// ════════════════════════════════════════════════════════════

data class LiveComment(
    val id: String,
    val username: String,
    val message: String,
    val color: Color
)

data class LiveGift(
    val emoji: String,
    val name: String,
    val coins: Int,
    val senderName: String
)

/**
 * الهدايا معطلة بقرار المنتج ("بدون هدايا") — تفاعلات مجانية (reactions)
 * فقط. الدوال والشيفرة تبقى للتوافق ولا تُحذف، لكن لا يُستدعى أي إرسال هدايا.
 */
private const val GIFTS_ENABLED = false

// ════════════════════════════════════════════════════════════
//  LIVE STREAM SCREEN — شاشة المشاهدة المباشرة
//  @deprecated نموذج تجريبي خارج مخطط البث: فيديو متدرج وهمي + مشاهدون
//  عشوائيون + تعليقات ثابتة + إدخال غير قابل للكتابة. لا شيء يُجلب منه
//  (التفاعلات الطائرة موجودة فعلاً في YounesLiveStreamOverlay).
//  المسار القانوني: YounesLiveStreamOverlay.
// ════════════════════════════════════════════════════════════

@Deprecated(
    "Mock demo outside build graph — use YounesLiveStreamOverlay (real WebRTC + chat)",
    ReplaceWith("com.red.sovereign.calls.YounesLiveStreamOverlay")
)
@Composable
fun LiveStreamScreen(
    streamId: String,
    onBack: () -> Unit
) {
    var isLiked      by remember { mutableStateOf(false) }
    var likeCount    by remember { mutableStateOf(14_823L) }
    var commentText  by remember { mutableStateOf("") }
    var viewers      by remember { mutableStateOf(14_823L) }
    var showGifts    by remember { mutableStateOf(false) }
    var activeGift   by remember { mutableStateOf<LiveGift?>(null) }
    val scope        = rememberCoroutineScope()
    val listState    = rememberLazyListState()

    val comments = remember {
        mutableStateListOf(
            LiveComment("1", "مطور RED",   "البث رائع جداً! 🔥",       PurplePrimary),
            LiveComment("2", "علي",        "شرح ممتاز! 👍",             BlueElectric),
            LiveComment("3", "سارة",       "كيف أبني مشروع مثل هذا؟",   Color(0xFFE91E63)),
            LiveComment("4", "خالد",       "هل ستشرح الـ Navigation؟",  GoldenAccent),
            LiveComment("5", "فاطمة",      "لايك وشير ❤️",              GreenAccent),
        )
    }

    // Simulate new viewers & comments
    LaunchedEffect(Unit) {
        while (true) {
            delay((2000..5000).random().toLong())
            viewers += (5..50).random()
        }
    }

    val gifts = listOf(
        LiveGift("💎", "ماسة",    5000, ""),
        LiveGift("🚀", "صاروخ",   2000, ""),
        LiveGift("❤️", "قلب",      100, ""),
        LiveGift("🎉", "احتفال",   500, ""),
        LiveGift("👑", "تاج",     10000, ""),
        LiveGift("🔥", "نار",      300, ""),
    )

    Box(modifier = Modifier.fillMaxSize().background(Black)) {
        // Video placeholder — gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF0D001A), PurplePrimary.copy(0.3f), Color(0xFF000D00))))
        )

        // Ambient glow
        Box(modifier = Modifier.size(250.dp).blur(120.dp).clip(CircleShape).background(PurplePrimary.copy(0.2f)).align(Alignment.TopStart).offset((-50).dp, 100.dp))

        // Top bar
        LiveTopBar(streamId = streamId, viewers = viewers, onBack = onBack)

        // Hearts animation
        LikeHearts(isLiked = isLiked)

        // Gift notification — معطل مع الهدايا (GIFTS_ENABLED=false).
        AnimatedVisibility(
            visible  = GIFTS_ENABLED && activeGift != null,
            enter    = slideInVertically { -200 } + fadeIn(),
            exit     = slideOutVertically { -200 } + fadeOut(),
            modifier = Modifier.align(Alignment.TopStart).padding(top = 80.dp, start = 16.dp)
        ) {
            activeGift?.let { gift ->
                GiftNotificationBanner(gift = gift)
            }
        }

        // Comments + Controls at bottom
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Black.copy(0.85f))))
        ) {
            // Comments scroll
            LazyColumn(
                state         = listState,
                modifier      = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                reverseLayout = false
            ) {
                items(comments.takeLast(20), key = { it.id }) { c ->
                    LiveCommentRow(comment = c)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Gift sheet — معطل بقرار المنتج؛ يُعرض فقط إن أُعيد تفعيل العلم.
            AnimatedVisibility(visible = GIFTS_ENABLED && showGifts) {
                GiftPickerSheet(
                    gifts   = gifts,
                    onSendGift = { gift ->
                        val newGift = gift.copy(senderName = "أنت")
                        activeGift = newGift
                        comments.add(LiveComment(
                            System.currentTimeMillis().toString(),
                            "أنت", "أرسل ${gift.emoji} ${gift.name}!", GoldenAccent
                        ))
                        scope.launch {
                            delay(3000)
                            activeGift = null
                        }
                        showGifts = false
                    }
                )
            }

            // Input row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Comment input
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .border(1.dp, Color.White.copy(0.15f), RoundedCornerShape(20.dp))
                        .clickable { }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text("تعليق...", color = Color.White.copy(0.5f), fontSize = 14.sp)
                }

                // Gift button — معطل بقرار المنتج: زر تفاعل مجاني (❤️) بدل الهدايا.
                if (GIFTS_ENABLED) {
                    IconButton(
                        onClick = { showGifts = !showGifts },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(GradientGold))
                    ) {
                        Text("🎁", fontSize = 18.sp)
                    }
                } else {
                    IconButton(
                        onClick = {
                            if (!isLiked) { isLiked = true; likeCount += 1 }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(GradientGold))
                    ) {
                        Text("❤️", fontSize = 18.sp)
                    }
                }

                // Like button
                LikeButton(
                    isLiked    = isLiked,
                    likeCount  = likeCount,
                    onLike     = {
                        isLiked   = !isLiked
                        likeCount += if (isLiked) 1 else -1
                    }
                )

                // Share
                IconButton(
                    onClick  = { },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(0.12f))
                ) {
                    Icon(Icons.Rounded.Share, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }

            NavigationBarItem(Modifier.height(0.dp), false, {}, {}) // Safe area
        }
    }
}

// ════════════════════════════════════════════════════════════
//  TOP BAR
// ════════════════════════════════════════════════════════════

@Composable
private fun LiveTopBar(streamId: String, viewers: Long, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Streamer info
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .border(2.dp, RedBrand, CircleShape)
                    .background(PurplePrimary),
                contentAlignment = Alignment.Center
            ) {
                Text("م", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text("مطور RED", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(RedBrand).padding(horizontal = 5.dp, vertical = 1.dp)) {
                        Text("LIVE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                    Icon(Icons.Rounded.RemoveRedEye, null, tint = Color.White.copy(0.7f), modifier = Modifier.size(12.dp))
                    Text(formatLiveCount(viewers), color = Color.White.copy(0.7f), fontSize = 11.sp)
                }
            }
        }

        // Follow button
        TextButton(
            onClick = { },
            colors  = ButtonDefaults.textButtonColors(contentColor = Color.White),
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(RedBrand.copy(0.85f))
        ) {
            Text("+ تابع", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        Spacer(Modifier.width(8.dp))

        // Close
        IconButton(onClick = onBack) {
            Icon(Icons.Rounded.Close, "إغلاق", tint = Color.White)
        }
    }
}

// ════════════════════════════════════════════════════════════
//  COMMENT ROW
// ════════════════════════════════════════════════════════════

@Composable
private fun LiveCommentRow(comment: LiveComment) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(comment.color.copy(0.8f)),
            contentAlignment = Alignment.Center
        ) {
            Text(comment.username.take(1).uppercase(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(0.45f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            androidx.compose.foundation.text.BasicText(
                text = androidx.compose.ui.text.buildAnnotatedString {
                    pushStyle(androidx.compose.ui.text.SpanStyle(color = comment.color, fontWeight = FontWeight.Bold, fontSize = 12.sp))
                    append("${comment.username}  ")
                    pop()
                    pushStyle(androidx.compose.ui.text.SpanStyle(color = Color.White, fontSize = 13.sp))
                    append(comment.message)
                    pop()
                }
            )
        }
    }
}

// ════════════════════════════════════════════════════════════
//  LIKE BUTTON
// ════════════════════════════════════════════════════════════

@Composable
private fun LikeButton(isLiked: Boolean, likeCount: Long, onLike: () -> Unit) {
    val scale by animateFloatAsState(targetValue = if (isLiked) 1.2f else 1f, animationSpec = spring(Spring.DampingRatioMediumBouncy))
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(0.12f))
            .clickable(onClick = onLike)
            .scale(scale),
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            null, tint = if (isLiked) RedGlow else Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}

// ════════════════════════════════════════════════════════════
//  FLOATING HEARTS
// ════════════════════════════════════════════════════════════

@Composable
private fun LikeHearts(isLiked: Boolean) {
    if (!isLiked) return
    Box(modifier = Modifier.fillMaxSize()) {
        // Simple heart overlay effect
        for (i in 0..2) {
            val infiniteTransition = rememberInfiniteTransition()
            val offsetY by infiniteTransition.animateFloat(
                0f, -400f,
                infiniteRepeatable(tween(2000 + i * 300, delayMillis = i * 500), RepeatMode.Restart)
            )
            val alpha by infiniteTransition.animateFloat(
                1f, 0f,
                infiniteRepeatable(tween(2000 + i * 300, delayMillis = i * 500), RepeatMode.Restart)
            )
            Text(
                "❤️",
                fontSize = (24 + i * 4).sp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = (50 + i * 20).dp, bottom = 100.dp)
                    .offset(y = offsetY.dp)
                    .alpha(alpha)
            )
        }
    }
}

// ════════════════════════════════════════════════════════════
//  GIFT PICKER SHEET
// ════════════════════════════════════════════════════════════

@Composable
private fun GiftPickerSheet(gifts: List<LiveGift>, onSendGift: (LiveGift) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(SurfaceDark)
            .padding(16.dp)
    ) {
        Column {
            Text("إرسال هدية 🎁", color = TextBright, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(gifts) { gift ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(SurfaceMid)
                            .clickable { onSendGift(gift) }
                            .padding(12.dp)
                    ) {
                        Text(gift.emoji, fontSize = 32.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(gift.name, color = TextPrimary, fontSize = 12.sp, textAlign = TextAlign.Center)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🪙", fontSize = 10.sp)
                            Text(" ${gift.coins}", color = GoldenAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
//  GIFT NOTIFICATION BANNER
// ════════════════════════════════════════════════════════════

@Composable
private fun GiftNotificationBanner(gift: LiveGift) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(GradientGold))
            .border(1.dp, GoldenAccent.copy(0.5f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(gift.emoji, fontSize = 28.sp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("${gift.senderName} أرسل هدية!", color = TextOnCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(gift.name, color = TextOnCyan.copy(0.8f), fontSize = 11.sp)
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
//  UTILS
// ════════════════════════════════════════════════════════════

private fun formatLiveCount(n: Long): String = when {
    n >= 1_000_000 -> "%.1fم".format(n / 1_000_000.0)
    n >= 1_000     -> "%.1fك".format(n / 1_000.0)
    else           -> n.toString()
}
