package com.red.sovereign.features.calls.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.features.calls.ConferenceState

// ─── Colors ────────────────────────────────────────────────────────────────
private val BgDark = Color(0xFF0A0A0A)
private val SurfaceDark = Color(0xFF161616)
private val SurfaceMid = Color(0xFF1E1E1E)
private val PurpleAccent = Color(0xFF7C4DFF)
private val BlueAccent = Color(0xFF1E88E5)
private val GreenCall = Color(0xFF43A047)
private val RedEnd = Color(0xFFE53935)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFF9E9E9E)

// ─── ConferenceCallScreen ──────────────────────────────────────────────────

/**
 * شاشة المؤتمر الجماعي.
 * تعرض شبكة (Grid) من المشاركين مع شريط تحكم كامل.
 */
@Composable
fun ConferenceCallScreen(
    conferenceState: ConferenceState,
    participants: List<ConferenceParticipant> = emptyList(),
    onEndCall: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleVideo: () -> Unit,
    onRaiseHand: () -> Unit,
    onInviteMembers: () -> Unit,
    isMuted: Boolean = false,
    isVideoEnabled: Boolean = true
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        // ── Top Bar ────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Title
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    conferenceState.title.ifBlank { "مؤتمر جماعي" },
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(GreenCall))
                    Text(
                        "${conferenceState.participantCount} مشارك",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            // Role badge
            if (conferenceState.isHost) {
                Surface(
                    color = PurpleAccent.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "مضيف",
                        color = PurpleAccent,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        fontSize = 12.sp
                    )
                }
            }

            // Invite button
            IconButton(onClick = onInviteMembers) {
                Icon(Icons.Rounded.PersonAdd, "دعوة", tint = TextSecondary)
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

        // ── Participant Grid ────────────────────────────────────────
        if (participants.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Groups, null, tint = TextSecondary, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("في انتظار المشاركين...", color = TextSecondary, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Grid of 2 per row
                items(participants.chunked(2)) { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        row.forEach { participant ->
                            ParticipantTile(
                                participant = participant,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

        // ── Controls ───────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ConferenceControlBtn(
                icon = if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                label = if (isMuted) "كتم" else "ميكروفون",
                active = isMuted,
                activeColor = RedEnd,
                onClick = onToggleMute
            )
            ConferenceControlBtn(
                icon = if (isVideoEnabled) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                label = "كاميرا",
                active = !isVideoEnabled,
                activeColor = RedEnd,
                onClick = onToggleVideo
            )
            ConferenceControlBtn(
                icon = Icons.Rounded.BackHand,
                label = "رفع يد",
                active = false,
                activeColor = Color(0xFFFF9800),
                onClick = onRaiseHand
            )
            // End Call
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FloatingActionButton(
                    onClick = onEndCall,
                    containerColor = RedEnd,
                    modifier = Modifier.size(60.dp)
                ) {
                    Icon(Icons.Rounded.CallEnd, "إنهاء", tint = Color.White, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text("إنهاء", color = TextSecondary, fontSize = 11.sp)
            }
        }
    }
}

// ─── AudioSpaceScreen ──────────────────────────────────────────────────────

/**
 * مساحة صوتية (مثل Clubhouse / X Spaces).
 * تفصل بين المتحدثين والمستمعين.
 */
@Composable
fun AudioSpaceScreen(
    conferenceState: ConferenceState,
    speakers: List<ConferenceParticipant> = emptyList(),
    listeners: List<ConferenceParticipant> = emptyList(),
    isMuted: Boolean = false,
    hasRaisedHand: Boolean = false,
    onLeave: () -> Unit,
    onToggleMute: () -> Unit,
    onRaiseHand: () -> Unit,
    onShare: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0A0A0A), Color(0xFF0F0520), Color(0xFF0A0A0A))
                )
            )
    ) {
        // ── Header ─────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Live badge
                Surface(
                    color = RedEnd.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(RedEnd))
                        Text("مباشر", color = RedEnd, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                // Share button
                IconButton(onClick = onShare) {
                    Icon(Icons.Rounded.Share, "مشاركة", tint = TextSecondary)
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                conferenceState.title.ifBlank { "مساحة صوتية" },
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${listeners.size + speakers.size} مستمع",
                color = TextSecondary,
                fontSize = 13.sp
            )
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Speakers section
            item {
                Spacer(Modifier.height(16.dp))
                Text("المتحدثون", color = TextSecondary, fontSize = 12.sp, letterSpacing = 1.sp)
                Spacer(Modifier.height(12.dp))
            }
            item {
                SpaceParticipantGrid(participants = speakers, isSpeakers = true)
            }

            // Listeners section
            if (listeners.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("المستمعون · ${listeners.size}", color = TextSecondary, fontSize = 12.sp, letterSpacing = 1.sp)
                    Spacer(Modifier.height(12.dp))
                }
                item {
                    SpaceParticipantGrid(participants = listeners, isSpeakers = false)
                }
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

        // ── Bottom Controls ────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mute (only if speaker)
            if (conferenceState.role in listOf("HOST", "CO_HOST", "SPEAKER")) {
                ConferenceControlBtn(
                    icon = if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                    label = if (isMuted) "كتم" else "تحدث",
                    active = isMuted,
                    activeColor = RedEnd,
                    onClick = onToggleMute
                )
            } else {
                // Raise hand (listeners)
                ConferenceControlBtn(
                    icon = if (hasRaisedHand) Icons.Rounded.BackHand else Icons.Rounded.PanTool,
                    label = if (hasRaisedHand) "تم رفع يدك" else "رفع يد",
                    active = hasRaisedHand,
                    activeColor = Color(0xFFFF9800),
                    onClick = onRaiseHand
                )
            }

            // Leave
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedButton(
                    onClick = onLeave,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = RedEnd),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RedEnd.copy(alpha = 0.5f)),
                    modifier = Modifier.height(44.dp)
                ) {
                    Icon(Icons.Rounded.ExitToApp, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("مغادرة")
                }
            }
        }
    }
}

// ─── Helper Composables ────────────────────────────────────────────────────

@Composable
fun ParticipantTile(
    participant: ConferenceParticipant,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.aspectRatio(0.75f),
        color = SurfaceDark,
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(listOf(Color(0xFF3E3E3E), Color(0xFF1A1A1A)))
                    )
                    .border(
                        width = if (participant.isSpeaking) 2.dp else 0.dp,
                        color = if (participant.isSpeaking) GreenCall else Color.Transparent,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(participant.name.take(1).uppercase(), color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            // Muted icon
            if (participant.isMuted) {
                Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    Surface(color = RedEnd, shape = CircleShape) {
                        Icon(Icons.Rounded.MicOff, null, tint = Color.White, modifier = Modifier.size(16.dp).padding(2.dp))
                    }
                }
            }
            // Name at bottom
            Text(
                participant.name.take(12),
                color = TextPrimary,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                maxLines = 1
            )
        }
    }
}

@Composable
fun SpaceParticipantGrid(participants: List<ConferenceParticipant>, isSpeakers: Boolean) {
    val columns = 4
    val rows = participants.chunked(columns)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                row.forEach { participant ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(64.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(SurfaceMid)
                                .border(
                                    width = if (participant.isSpeaking && isSpeakers) 2.dp else 0.dp,
                                    color = if (participant.isSpeaking) GreenCall else Color.Transparent,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(participant.name.take(1).uppercase(), color = TextPrimary, fontSize = 20.sp)
                            if (participant.isMuted && isSpeakers) {
                                Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                                    Surface(color = RedEnd, shape = CircleShape) {
                                        Icon(Icons.Rounded.MicOff, null, tint = Color.White, modifier = Modifier.size(14.dp).padding(2.dp))
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            participant.name.take(8),
                            color = TextSecondary,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                        if (isSpeakers && participant.role == "HOST") {
                            Text("مضيف", color = PurpleAccent, fontSize = 9.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConferenceControlBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(if (active) activeColor.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.07f))
        ) {
            Icon(
                icon, label,
                tint = if (active) activeColor else TextSecondary,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = TextSecondary, fontSize = 10.sp)
    }
}

// ─── Data ──────────────────────────────────────────────────────────────────

data class ConferenceParticipant(
    val userId: String,
    val name: String,
    val role: String = "LISTENER",
    val isMuted: Boolean = false,
    val isSpeaking: Boolean = false,
    val hasVideo: Boolean = false
)
