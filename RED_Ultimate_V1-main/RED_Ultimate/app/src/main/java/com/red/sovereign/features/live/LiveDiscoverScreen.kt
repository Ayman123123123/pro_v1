package com.red.sovereign.features.live

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.ui.theme.*

// ════════════════════════════════════════════════════════════
//  DATA MODELS
// ════════════════════════════════════════════════════════════

data class LiveStream(
    val id: String,
    val title: String,
    val streamerName: String,
    val viewers: Long,
    val category: String,
    val isLive: Boolean   = true,
    val thumbnailColor: Color,
    val thumbnailColor2: Color,
    val streamerAvatarColor: Color,
    val tags: List<String> = emptyList()
)

// ════════════════════════════════════════════════════════════
//  LIVE DISCOVER SCREEN — اكتشاف البث المباشر
//  @deprecated نموذج تجريبي خارج مخطط البناء ببيانات ثابتة وهمية.
//  ما جُلب منه للمنتج الحقيقي: بانر البطل + شرائح الفئات + تنسيق ك/م
//  (HeroLiveBanner/formatLiveViewers في RedExploreScreen ببيانات حية).
//  المسار القانوني: RedExploreScreen.
// ════════════════════════════════════════════════════════════

@Deprecated(
    "Static demo outside build graph — use RedExploreScreen (live data + hero + categories)",
    ReplaceWith("com.red.sovereign.features.explore.RedExploreScreen")
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveDiscoverScreen(
    onStreamClick: (String) -> Unit,
    onGoLive: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf("الكل") }
    val categories = listOf("الكل", "تقنية", "ألعاب", "موسيقى", "تعليم", "ترفيه", "رياضة", "طبخ")

    val streams = remember {
        listOf(
            LiveStream("1", "بناء تطبيق RED من الصفر! 🔴",   "مطور RED",  14_823, "تقنية",   thumbnailColor = Color(0xFF1A0A2E), thumbnailColor2 = PurplePrimary, streamerAvatarColor = PurplePrimary, tags = listOf("كوتلن", "أندرويد")),
            LiveStream("2", "Championship Finals 🎮",         "ProGamer",  87_421, "ألعاب",   thumbnailColor = Color(0xFF001A0A), thumbnailColor2 = GreenAccent,   streamerAvatarColor = GreenAccent,   tags = listOf("قيمنق", "بطولة")),
            LiveStream("3", "جلسة عزف موسيقى عربية 🎵",       "نورة الفن", 3_291,  "موسيقى",  thumbnailColor = Color(0xFF1A0A00), thumbnailColor2 = OrangeAccent,  streamerAvatarColor = GoldenAccent,  tags = listOf("عود", "موسيقى")),
            LiveStream("4", "تعلم Flutter في ساعة واحدة!",   "المبرمج",   9_812,  "تعليم",   thumbnailColor = Color(0xFF000D1A), thumbnailColor2 = BlueElectric,  streamerAvatarColor = BlueAccent,    tags = listOf("فلاتر", "تعليم")),
            LiveStream("5", "محادثة مباشرة مع المتابعين 💬",  "ريما",      2_441,  "ترفيه",   thumbnailColor = Color(0xFF1A0015), thumbnailColor2 = Color(0xFFE91E63), streamerAvatarColor = Color(0xFFE91E63), tags = listOf("live", "chat")),
            LiveStream("6", "مباراة مباشرة — كرة القدم ⚽",  "الملعب",    231_000,"رياضة",   thumbnailColor = Color(0xFF001400), thumbnailColor2 = GreenCall,     streamerAvatarColor = GreenCall,     tags = listOf("كرة", "مباشر")),
            LiveStream("7", "طبخ شرقي — أكلات شهية 🍖",      "الطباخة",   5_321,  "طبخ",     thumbnailColor = Color(0xFF1A0A00), thumbnailColor2 = OrangeAccent,  streamerAvatarColor = OrangeAccent,  tags = listOf("طبخ", "شهي")),
            LiveStream("8", "أخبار التقنية اليومية 📱",       "تك نيوز",   18_200, "تقنية",   thumbnailColor = Color(0xFF000A1A), thumbnailColor2 = AqyalCyanGlow, streamerAvatarColor = AqyalCyan,     tags = listOf("أخبار", "تقنية")),
        )
    }

    val displayed = if (selectedCategory == "الكل") streams else streams.filter { it.category == selectedCategory }

    Scaffold(
        containerColor = BgPrimary,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Brush.linearGradient(GradientLive)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.LiveTv, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("مباشر", color = TextBright, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = { /* search */ }) {
                        Icon(Icons.Rounded.Search, "بحث", tint = TextPrimary)
                    }
                    IconButton(onClick = { /* notifications */ }) {
                        Icon(Icons.Rounded.NotificationsOutlined, "إشعارات", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgPrimary)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick        = onGoLive,
                containerColor = RedBrand,
                contentColor   = Color.White,
                shape          = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.FiberManualRecord, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("ابدأ البث", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier       = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            // Hero featured stream
            item {
                FeaturedHeroBanner(stream = streams.first(), onClick = { onStreamClick(streams.first().id) })
            }

            // Category chips
            item {
                LazyRow(
                    modifier              = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    contentPadding        = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick  = { selectedCategory = cat },
                            label    = { Text(cat, fontSize = 13.sp) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = RedBrand,
                                selectedLabelColor     = Color.White,
                                containerColor         = SurfaceMid,
                                labelColor             = TextSecondary
                            ),
                            border = null,
                            shape  = RoundedCornerShape(20.dp)
                        )
                    }
                }
            }

            // Section header
            item {
                Text(
                    "بث مباشر الآن",
                    color    = TextBright,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Grid of streams
            item {
                LiveStreamGrid(
                    streams   = displayed,
                    onClick   = { onStreamClick(it.id) }
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
//  HERO BANNER
// ════════════════════════════════════════════════════════════

@Composable
private fun FeaturedHeroBanner(stream: LiveStream, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clickable(onClick = onClick)
    ) {
        // Gradient background as thumbnail
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(stream.thumbnailColor, stream.thumbnailColor2)))
        )
        // Blur overlay at bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, BgPrimary.copy(0.95f))))
        )
        // Content
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            // LIVE badge
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(RedBrand)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("🔴 LIVE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
                Text(formatViewers(stream.viewers), color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Icon(Icons.Rounded.RemoveRedEye, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(stream.title, color = TextBright, fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(stream.streamerAvatarColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stream.streamerName.take(1), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(6.dp))
                Text(stream.streamerName, color = TextSecondary, fontSize = 13.sp)
            }
        }
    }
}

// ════════════════════════════════════════════════════════════
//  STREAM GRID
// ════════════════════════════════════════════════════════════

@Composable
private fun LiveStreamGrid(streams: List<LiveStream>, onClick: (LiveStream) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        streams.chunked(2).forEach { rowStreams ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowStreams.forEach { stream ->
                    LiveStreamCard(
                        stream  = stream,
                        onClick = { onClick(stream) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowStreams.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LiveStreamCard(stream: LiveStream, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(0.75f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        // Thumbnail gradient
        Box(modifier = Modifier.fillMaxSize().background(Brush.linearGradient(listOf(stream.thumbnailColor, stream.thumbnailColor2))))

        // Bottom gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.55f)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Black.copy(0.9f))))
        )

        // Content
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)) {
            // LIVE + viewers
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(RedBrand)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text("LIVE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
                Text(formatViewers(stream.viewers), color = TextPrimary, fontSize = 11.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(stream.title, color = TextBright, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2, lineHeight = 17.sp)
            Spacer(Modifier.height(4.dp))
            // Streamer
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(stream.streamerAvatarColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stream.streamerName.take(1), color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(4.dp))
                Text(stream.streamerName, color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        // Streamer avatar (top left)
        Box(
            modifier = Modifier
                .padding(10.dp)
                .size(36.dp)
                .clip(CircleShape)
                .border(2.dp, Color.White.copy(0.3f), CircleShape)
                .background(stream.streamerAvatarColor)
                .align(Alignment.TopStart),
            contentAlignment = Alignment.Center
        ) {
            Text(stream.streamerName.take(1).uppercase(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
        }
    }
}

// ════════════════════════════════════════════════════════════
//  UTILS
// ════════════════════════════════════════════════════════════

private fun formatViewers(n: Long): String = when {
    n >= 1_000_000 -> "%.1fم".format(n / 1_000_000.0)
    n >= 1_000     -> "%.1fك".format(n / 1_000.0)
    else           -> n.toString()
}
