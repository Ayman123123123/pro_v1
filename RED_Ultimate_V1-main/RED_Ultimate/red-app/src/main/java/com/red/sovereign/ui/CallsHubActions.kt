package com.red.sovereign.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.ui.theme.AqyalCyanGlow
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.YounesEmerald

/**
 * أزرار مركز المكالمات — كل نوع بشكله الخاص، منفصل عن ترويسة الدردشة.
 * فردي = هاتف. مؤتمر = شبكة أشخاص. بث = شارة LIVE حمراء. مساحة = منصة سماعة.
 */
@Composable
fun CallsHubLaunchers(
    onPrivateCall: () -> Unit,
    onConference: () -> Unit,
    onLive: () -> Unit,
    onSpace: () -> Unit,
    onExplore: () -> Unit,
    onPstn: () -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("مكالمة فردية", color = Color.White.copy(0.7f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        PrivateCallHubCard(onPrivateCall)

        Text("جلسات جماعية وبث — ليست مكالمة هاتف", color = Color.White.copy(0.7f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ConferenceHubCard(Modifier.weight(1f), onConference)
            LiveHubCard(Modifier.weight(1f), onLive)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SpaceHubCard(Modifier.weight(1f), onSpace)
            ExploreHubCard(Modifier.weight(1f), onExplore)
        }

        PstnHubStrip(onPstn)
    }
}

@Composable
private fun PrivateCallHubCard(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF063528))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(YounesEmerald), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Call, null, tint = Color(0xFF002117), modifier = Modifier.size(28.dp))
        }
        Column(Modifier.weight(1f)) {
            Text("صوت أو فيديو لشخص واحد", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("يرن جهازه — قبول أو رفض", color = Color.White.copy(0.65f), fontSize = 12.sp)
        }
        Icon(Icons.Default.Videocam, null, tint = YounesEmerald)
    }
}

@Composable
private fun ConferenceHubCard(modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .height(132.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF0B2A38))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(AqyalCyanGlow.copy(0.25f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Groups, null, tint = AqyalCyanGlow, modifier = Modifier.size(22.dp))
        }
        Column {
            Text("مؤتمر فيديو", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("دعوة هادئة · شبكة", color = Color.White.copy(0.6f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun LiveHubCard(modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .height(132.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF2A0A10))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xFFE53935)).padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text("مباشر", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
            }
            Icon(Icons.Default.LiveTv, null, tint = Color(0xFFFF8A80), modifier = Modifier.size(20.dp))
        }
        Column {
            Text("بث مباشر", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("مشاهدة أو تجاهل · بلا رنة", color = Color.White.copy(0.6f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun SpaceHubCard(modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .height(120.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF22183A))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFFA78BFA).copy(0.28f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Headset, null, tint = Color(0xFFC4B5FD), modifier = Modifier.size(22.dp))
        }
        Column {
            Text("مساحة صوتية", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("منصة · مستمعون · رفع يد", color = Color.White.copy(0.6f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun ExploreHubCard(modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .height(120.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF2A2308))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(Icons.Default.Search, null, tint = AqyalGold, modifier = Modifier.size(26.dp))
        Column {
            Text("اكتشاف", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text("بثوث وغرف عامة", color = Color.White.copy(0.6f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun PstnHubStrip(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF2A1208))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Default.PhoneInTalk, null, tint = Color(0xFFE31E24))
        Column(Modifier.weight(1f)) {
            Text("هاتف يمني", color = Color.White, fontWeight = FontWeight.SemiBold)
            Text("خط DINSTAR — منفصل عن مكالمات يونس", color = Color.White.copy(0.55f), fontSize = 11.sp)
        }
    }
}

@Composable
fun callTypeGlyph(type: String, route: String): Pair<ImageVector, Color> = when {
    route == "DINSTAR" -> Icons.Default.PhoneInTalk to AqyalGold
    type == "LIVE" -> Icons.Default.LiveTv to Color(0xFFE53935)
    type == "SPACE" -> Icons.Default.Headset to Color(0xFFA78BFA)
    type == "GROUP" -> Icons.Default.Groups to AqyalCyanGlow
    type == "VIDEO" -> Icons.Default.Videocam to YounesEmerald
    else -> Icons.Default.Call to YounesEmerald
}
