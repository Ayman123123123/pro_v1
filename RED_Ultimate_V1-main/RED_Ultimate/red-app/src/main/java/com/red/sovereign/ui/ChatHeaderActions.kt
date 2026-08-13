package com.red.sovereign.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.ui.theme.YounesEmerald

/**
 * أزرار ترويسة المحادثة الخاصة — نمط واتساب/تلجرام:
 * فيديو ثم صوت فقط. البحث والوسائط والأمان في القائمة.
 */
@Composable
fun PrivateChatCallActions(
    onVideoCall: () -> Unit,
    onVoiceCall: () -> Unit,
    onSearch: () -> Unit,
    onMedia: () -> Unit,
    onSafety: () -> Unit,
    onProfile: (() -> Unit)?
) {
    var menu by remember { mutableStateOf(false) }
    HeaderIcon(Icons.Default.Videocam, "مكالمة فيديو", onVideoCall)
    HeaderIcon(Icons.Default.Call, "مكالمة صوتية", onVoiceCall)
    Box {
        HeaderIcon(Icons.Default.MoreVert, "المزيد", { menu = true })
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("البحث في المحادثة") }, leadingIcon = { Icon(Icons.Default.Search, null) }, onClick = { menu = false; onSearch() })
            DropdownMenuItem(text = { Text("الوسائط المشتركة") }, leadingIcon = { Icon(Icons.Default.Photo, null) }, onClick = { menu = false; onMedia() })
            DropdownMenuItem(text = { Text("رمز الأمان") }, leadingIcon = { Icon(Icons.Default.Security, null) }, onClick = { menu = false; onSafety() })
            if (onProfile != null) {
                DropdownMenuItem(text = { Text("معلومات الجهة") }, leadingIcon = { Icon(Icons.Default.Info, null) }, onClick = { menu = false; onProfile() })
            }
        }
    }
}

/**
 * أزرار ترويسة المجموعة — نمط واتساب Voice Chat + تلجرام Voice Chat:
 * مساحة صوتية (لا ترن الجميع) ثم مؤتمر فيديو. ليست مكالمة هاتف فردية.
 */
@Composable
fun GroupChatCallActions(
    spaceLive: Boolean,
    meetingLive: Boolean,
    onSpace: () -> Unit,
    onMeeting: () -> Unit,
    onInfo: () -> Unit,
    onSearch: () -> Unit,
    onMedia: () -> Unit,
    onAvatar: () -> Unit,
    onPoll: () -> Unit,
    onLeave: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    HeaderIcon(
        icon = Icons.Default.Headset,
        label = if (spaceLive) "العودة للمساحة" else "مساحة صوتية",
        onClick = onSpace,
        accent = spaceLive
    )
    HeaderIcon(
        icon = Icons.Default.Videocam,
        label = if (meetingLive) "العودة للمؤتمر" else "مؤتمر فيديو",
        onClick = onMeeting,
        accent = meetingLive
    )
    Box {
        HeaderIcon(Icons.Default.MoreVert, "خيارات المجموعة", { menu = true })
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("معلومات المجموعة") }, leadingIcon = { Icon(Icons.Default.Info, null) }, onClick = { menu = false; onInfo() })
            DropdownMenuItem(text = { Text("بحث في المجموعة") }, leadingIcon = { Icon(Icons.Default.Search, null) }, onClick = { menu = false; onSearch() })
            DropdownMenuItem(text = { Text("الوسائط المشتركة") }, leadingIcon = { Icon(Icons.Default.Photo, null) }, onClick = { menu = false; onMedia() })
            DropdownMenuItem(text = { Text("تغيير صورة المجموعة") }, leadingIcon = { Icon(Icons.Default.Photo, null) }, onClick = { menu = false; onAvatar() })
            DropdownMenuItem(text = { Text("إنشاء استطلاع") }, leadingIcon = { Icon(Icons.Default.Forum, null) }, onClick = { menu = false; onPoll() })
            DropdownMenuItem(text = { Text("مغادرة المجموعة") }, leadingIcon = { Icon(Icons.Default.Logout, null) }, onClick = { menu = false; onLeave() })
        }
    }
}

/** شريط واتساب/تلجرام: الجلسة الجارية مثبتة فوق الرسائل ويمكن العودة إليها. */
@Composable
fun GroupLiveSessionBanner(
    isVideo: Boolean,
    inSession: Boolean,
    onJoinOrReturn: () -> Unit
) {
    val title = if (isVideo) "مؤتمر فيديو جارٍ" else "مساحة صوتية جارية"
    val action = if (inSession) "العودة" else "انضمام"
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isVideo) Color(0xFF0E3B2E) else Color(0xFF2A2150))
            .clickable(onClick = onJoinOrReturn)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(if (isVideo) YounesEmerald else Color(0xFFA78BFA)),
            contentAlignment = Alignment.Center
        ) {
            Icon(if (isVideo) Icons.Default.Videocam else Icons.Default.Headset, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text("لا ترن الأعضاء — ادخل أو اخرج متى شئت", color = Color.White.copy(0.7f), fontSize = 11.sp)
        }
        TextButton(onJoinOrReturn) { Text(action, color = YounesEmerald, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun HeaderIcon(icon: ImageVector, label: String, onClick: () -> Unit, accent: Boolean = false) {
    IconButton(onClick) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (accent) YounesEmerald else MaterialTheme.colorScheme.onSurface
        )
    }
}
