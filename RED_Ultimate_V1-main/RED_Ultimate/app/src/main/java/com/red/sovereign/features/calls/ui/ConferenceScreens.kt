package com.red.sovereign.features.calls.ui

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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.ui.theme.*

// ════════════════════════════════════════════════════════════
//  DATA MODELS
// ════════════════════════════════════════════════════════════

data class ConferenceParticipant(
    val userId: String,
    val name: String,
    val role: String = "LISTENER",   // HOST | CO_HOST | SPEAKER | LISTENER
    val isMuted: Boolean   = false,
    val isSpeaking: Boolean = false,
    val hasVideo: Boolean  = false,
    val avatarColor: Color = SurfaceElevated
)

data class SpacePreview(
    val id: String,
    val title: String,
    val hostName: String,
    val listeners: Int,
    val speakers: Int,
    val isLive: Boolean,
    val topic: String,
    val accentColor: Color
)

enum class ConferenceRole { HOST, CO_HOST, SPEAKER, LISTENER }

// ════════════════════════════════════════════════════════════
//  CONFERENCE SCREEN — شاشة الفضاءات الصوتية
// ════════════════════════════════════════════════════════════

@Composable
fun ConferenceScreen(onBack: () -> Unit) {
    var selectedSpace by remember { mutableStateOf<SpacePreview?>(null) }

    val spaces = remember {
        listOf(
            SpacePreview("1", "مستقبل الذكاء الاصطناعي في 2026 🤖", "د. خالد العمري", 1842, 5, true, "تقنية", PurplePrimary),
            SpacePreview("2", "أفضل تطبيقات التواصل — مقارنة شاملة", "سارة المهندس",  943, 3, true, "تقنية", BlueElectric),
            SpacePreview("3", "ريادة الأعمال في العالم العربي", "محمد الريادي",  2341, 7, true, "أعمال", GoldenAccent),
            SpacePreview("4", "موسيقى وإبداع — جلسة لايف", "نورة الموسيقار", 521, 2, true, "فن",    OrangeAccent),
            SpacePreview("5", "RED Ultimate — التطوير المفتوح", "فريق RED",      388, 4, false,"تقنية", RedBrand),
        )
    }

    if (selectedSpace != null) {
        ActiveSpaceScreen(space = selectedSpace!!, onLeave = { selectedSpace = null })
    } else {
        SpacesDiscoverScreen(spaces = spaces, onJoinSpace = { selectedSpace = it })
    }
}

// ════════════════════════════════════════════════════════════
//  SPACES DISCOVER — اكتشاف الفضاءات
// ════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpacesDiscoverScreen(
    spaces: List<SpacePreview>,
    onJoinSpace: (SpacePreview) -> Unit
) {
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
                                .background(Brush.linearGradient(GradientSpace)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Mic, null, tint = PurpleAccent, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("فضاءات", color = TextBright, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = { /* Start new space */ }) {
                        Icon(Icons.Rounded.Add, "فضاء جديد", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgPrimary)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text           = { Text("ابدأ فضاءً", fontWeight = FontWeight.Bold) },
                icon           = { Icon(Icons.Rounded.Mic, null) },
                onClick        = { /* Start space */ },
                containerColor = Brush.linearGradient(GradientPurpleRed).let { PurplePrimary },
                contentColor   = TextOnRed,
                shape          = RoundedCornerShape(16.dp)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier       = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Live now header
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(RedBrand)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text("LIVE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("يحدث الآن", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }

            items(spaces.filter { it.isLive }) { space ->
                SpaceCard(space = space, onClick = { onJoinSpace(space) })
            }

            if (spaces.any { !it.isLive }) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text("قادمة", color = TextSecondary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                items(spaces.filter { !it.isLive }) { space ->
                    SpaceCard(space = space, onClick = { onJoinSpace(space) })
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun SpaceCard(space: SpacePreview, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(SurfaceDark, SurfaceMid)
                )
            )
            .border(
                1.dp,
                Brush.linearGradient(listOf(space.accentColor.copy(0.3f), Color.Transparent)),
                RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(18.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Topic chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(space.accentColor.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(space.topic, color = space.accentColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        space.title,
                        color      = TextBright,
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 22.sp
                    )
                }
                Spacer(Modifier.width(12.dp))
                if (space.isLive) {
                    LivePulseBadge()
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Host info
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(space.accentColor.copy(0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(space.hostName.take(1), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(space.hostName, color = TextSecondary, fontSize = 13.sp)
                }

                // Stats
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Mic, null, tint = TextTertiary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("${space.speakers}", color = TextTertiary, fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Headphones, null, tint = TextTertiary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(formatCount(space.listeners), color = TextTertiary, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Button(
                onClick        = onClick,
                modifier       = Modifier.fillMaxWidth().height(44.dp),
                shape          = RoundedCornerShape(12.dp),
                colors         = ButtonDefaults.buttonColors(
                    containerColor = space.accentColor.copy(alpha = 0.15f),
                    contentColor   = space.accentColor
                )
            ) {
                Icon(Icons.Rounded.Mic, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (space.isLive) "انضم الآن" else "تذكيرني", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun LivePulseBadge() {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        1f, 0.3f,
        infiniteRepeatable(tween(900), RepeatMode.Reverse)
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(RedBrand.copy(alpha = alpha))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color.White))
            Text("LIVE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
    }
}

// ════════════════════════════════════════════════════════════
//  ACTIVE SPACE SCREEN — الفضاء النشط
// ════════════════════════════════════════════════════════════

@Composable
private fun ActiveSpaceScreen(
    space: SpacePreview,
    onLeave: () -> Unit
) {
    var isMuted      by remember { mutableStateOf(true) }
    var hasRaisedHand by remember { mutableStateOf(false) }
    var myRole       by remember { mutableStateOf(ConferenceRole.LISTENER) }

    val speakers = remember {
        listOf(
            ConferenceParticipant("h1", "د. خالد العمري", "HOST",  false, true, false, PurplePrimary),
            ConferenceParticipant("s1", "سارة المهندس",  "CO_HOST", false, false, false, BlueElectric),
            ConferenceParticipant("s2", "محمد التقني",   "SPEAKER", true,  false, false, GoldenAccent),
        )
    }
    val listeners = remember {
        (1..18).map { i ->
            ConferenceParticipant(
                "l$i", "مستمع $i", "LISTENER",
                avatarColor = listOf(AqyalCyan, PurplePrimary, OrangeAccent, BlueElectric, GreenAccent).random()
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(GradientSpace))
    ) {
        // Ambient blurs
        Box(modifier = Modifier.size(300.dp).offset((-80).dp, 60.dp).blur(100.dp)
            .clip(CircleShape).background(space.accentColor.copy(0.12f)))
        Box(modifier = Modifier.size(200.dp).offset(200.dp, 400.dp).blur(80.dp)
            .clip(CircleShape).background(PurpleDeep.copy(0.3f)))

        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            SpaceActiveHeader(space = space, onLeave = onLeave)

            LazyColumn(
                modifier       = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
            ) {
                // Speakers section
                item {
                    Text(
                        "المتحدثون · ${speakers.size}",
                        color = TextSecondary, fontSize = 12.sp,
                        fontWeight = FontWeight.Medium, letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(16.dp))
                }
                item {
                    LazyVerticalGrid(
                        columns            = GridCells.Fixed(3),
                        modifier           = Modifier.height(((speakers.size + 2) / 3 * 120).dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement   = Arrangement.spacedBy(12.dp),
                        userScrollEnabled  = false
                    ) {
                        items(speakers) { p ->
                            SpeakerTile(participant = p)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }

                // Listeners section
                item {
                    HorizontalDivider(color = DividerColor)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "المستمعون · ${formatCount(listeners.size.toLong() + 1800)}",
                        color = TextSecondary, fontSize = 12.sp,
                        fontWeight = FontWeight.Medium, letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(12.dp))
                }
                item {
                    ListenerGrid(listeners = listeners)
                }
            }

            // Bottom controls
            SpaceBottomControls(
                role          = myRole,
                isMuted       = isMuted,
                hasRaisedHand = hasRaisedHand,
                onToggleMute  = { isMuted = !isMuted },
                onRaiseHand   = { hasRaisedHand = !hasRaisedHand },
                onLeave       = onLeave
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpaceActiveHeader(space: SpacePreview, onLeave: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.3f))
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                LivePulseBadge()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { /* share */ }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.Share, "مشاركة", tint = TextSecondary)
                    }
                    IconButton(onClick = { /* more */ }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.MoreVert, "المزيد", tint = TextSecondary)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(space.title, color = TextBright, fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Headphones, null, tint = TextTertiary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(formatCount(space.listeners.toLong() + 1800), color = TextTertiary, fontSize = 13.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AccessTime, null, tint = TextTertiary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("١:٢٤:٣٠", color = TextTertiary, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun SpeakerTile(participant: ConferenceParticipant) {
    val infiniteTransition = rememberInfiniteTransition()
    val ringAlpha by infiniteTransition.animateFloat(
        0.4f, 1f,
        infiniteRepeatable(tween(800), RepeatMode.Reverse)
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            // Speaking ring
            if (participant.isSpeaking) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .border(3.dp, GreenAccent.copy(alpha = ringAlpha), CircleShape)
                )
            }
            // Avatar
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(participant.avatarColor, participant.avatarColor.copy(0.5f))))
                    .border(
                        if (participant.role == "HOST") 2.dp else 0.dp,
                        Brush.linearGradient(GradientGold),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(participant.name.take(1).uppercase(), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
            }
            // Muted overlay
            if (participant.isMuted) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(RedBrand)
                        .border(1.5.dp, BgPrimary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.MicOff, null, tint = Color.White, modifier = Modifier.size(12.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(participant.name.take(10), color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, textAlign = TextAlign.Center)
        if (participant.role == "HOST") {
            Text("المضيف", color = GoldenAccent, fontSize = 10.sp)
        } else if (participant.role == "CO_HOST") {
            Text("مضيف مشارك", color = PurpleAccent, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ListenerGrid(listeners: List<ConferenceParticipant>) {
    val rows = listeners.chunked(6)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { p ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(44.dp)) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(p.avatarColor.copy(0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(p.name.take(1).uppercase(), color = Color.White, fontSize = 14.sp)
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(p.name.take(5), color = TextTertiary, fontSize = 9.sp, maxLines = 1, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun SpaceBottomControls(
    role: ConferenceRole,
    isMuted: Boolean,
    hasRaisedHand: Boolean,
    onToggleMute: () -> Unit,
    onRaiseHand: () -> Unit,
    onLeave: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Black.copy(0.8f), Black)))
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mute or Raise hand
            if (role in listOf(ConferenceRole.HOST, ConferenceRole.CO_HOST, ConferenceRole.SPEAKER)) {
                SpaceControlBtn(
                    icon        = if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                    label       = if (isMuted) "إلغاء الكتم" else "كتم",
                    active      = isMuted,
                    activeColor = RedBrand,
                    onClick     = onToggleMute
                )
            } else {
                SpaceControlBtn(
                    icon        = if (hasRaisedHand) Icons.Rounded.BackHand else Icons.Rounded.PanTool,
                    label       = if (hasRaisedHand) "إنزال اليد" else "ارفع يدك",
                    active      = hasRaisedHand,
                    activeColor = GoldenAccent,
                    onClick     = onRaiseHand
                )
            }

            // Share
            SpaceControlBtn(Icons.Rounded.Share, "مشاركة", false, AqyalCyanGlow) { }

            // Leave
            OutlinedButton(
                onClick  = onLeave,
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = RedBrand),
                border   = BorderStroke(1.5.dp, RedBrand.copy(alpha = 0.6f)),
                shape    = RoundedCornerShape(14.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Icon(Icons.Rounded.ExitToApp, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("مغادرة", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SpaceControlBtn(
    icon: ImageVector, label: String, active: Boolean,
    activeColor: Color, onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(if (active) activeColor.copy(0.2f) else SurfaceMid)
                .border(if (active) 1.dp else 0.dp, if (active) activeColor.copy(0.5f) else Color.Transparent, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, label, tint = if (active) activeColor else TextSecondary, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = TextTertiary, fontSize = 10.sp)
    }
}

// ════════════════════════════════════════════════════════════
//  UTILS
// ════════════════════════════════════════════════════════════

private fun formatCount(n: Long): String = when {
    n >= 1_000_000 -> "%.1fم".format(n / 1_000_000.0)
    n >= 1_000     -> "%.1fك".format(n / 1_000.0)
    else           -> n.toString()
}
