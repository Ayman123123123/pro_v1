package com.red.sovereign.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.LocalPhone
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
    HeaderIcon(Icons.Filled.LocalPhone, "مكالمة صوتية", onVoiceCall)
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
 * أزرار ترويسة المجموعة — نمط واتساب النقي:
 * زرّان منفصلان فقط: 📞 مكالمة صوتية جماعية و 🎥 مكالمة فيديو جماعية.
 * كل زر يرن جميع أعضاء المجموعة مباشرة (حتى 32 مشاركاً)، مع تشفير E2EE.
 * المساحات الصوتية والمؤتمرات ومكالمات Zoom هي ميزات مستقلة تماماً
 * ولا تظهر داخل المجموعات — لكل منها شاشة/مسار منفصل.
 *
 * تصميم واتساب للمرجع:
 * - ترويسة المجموعة: [رجوع] [أفاتار+اسم+عدد الأعضاء] [📞] [🎥] [⋮]
 * - 📞: مكالمة صوتية جماعية — ترن الكل، صوت فقط، شبكة للصور الدائرية، المتحدث مضاء
 * - 🎥: مكالمة فيديو جماعية — ترن الكل، شبكة فيديو + تسليط المتحدث + مشاركة الشاشة
 */
@Composable
fun GroupChatCallActions(
    onVideoCall: () -> Unit,
    onVoiceCall: () -> Unit,
    onInfo: () -> Unit,
    onSearch: () -> Unit,
    onMedia: () -> Unit,
    onAvatar: () -> Unit,
    onPoll: () -> Unit,
    onLeave: () -> Unit,
    muted: Boolean = false,
    onToggleMute: () -> Unit = {}
) {
    var menu by remember { mutableStateOf(false) }

    // واتساب: زرّان ثابتان في الترويسة — فيديو ثم صوت (نفس ترتيب المحادثة الخاصة)
    HeaderIcon(Icons.Default.Videocam, "مكالمة فيديو جماعية", onVideoCall)
    HeaderIcon(Icons.Filled.LocalPhone, "مكالمة صوتية جماعية", onVoiceCall)

    HeaderIcon(Icons.Default.NotificationsOff, if (muted) "إلغاء كتم المجموعة" else "كتم إشعارات المجموعة", onToggleMute, accent = muted)
    Box {
        HeaderIcon(Icons.Default.MoreVert, "خيارات المجموعة", { menu = true })
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("معلومات المجموعة") }, leadingIcon = { Icon(Icons.Default.Info, null) }, onClick = { menu = false; onInfo() })
            DropdownMenuItem(text = { Text("بحث في المجموعة") }, leadingIcon = { Icon(Icons.Default.Search, null) }, onClick = { menu = false; onSearch() })
            DropdownMenuItem(text = { Text("الوسائط المشتركة") }, leadingIcon = { Icon(Icons.Default.Photo, null) }, onClick = { menu = false; onMedia() })
            DropdownMenuItem(text = { Text("تغيير صورة المجموعة") }, leadingIcon = { Icon(Icons.Default.Photo, null) }, onClick = { menu = false; onAvatar() })
            DropdownMenuItem(text = { Text("إنشاء استطلاع") }, leadingIcon = { Icon(Icons.Default.Forum, null) }, onClick = { menu = false; onPoll() })
            DropdownMenuItem(text = { Text(if (muted) "إلغاء كتم الإشعارات" else "كتم الإشعارات") }, leadingIcon = { Icon(Icons.Default.NotificationsOff, null) }, onClick = { menu = false; onToggleMute() })
            DropdownMenuItem(text = { Text("مغادرة المجموعة") }, leadingIcon = { Icon(Icons.Default.Logout, null) }, onClick = { menu = false; onLeave() })
        }
    }
}

/**
 * تحويل قديم للتوافق — المساحات/المؤتمرات لم تعد داخل المجموعات.
 * يبقي التوقيع القديم كي لا ينكسر أي استدعاء خارجي، لكنه يتجاهل
 * spaceLive/meetingLive/onSpace/onMeeting ويمرر فقط مكالمات واتساب.
 */
@Composable
fun GroupChatCallActionsLegacy(
    spaceLive: Boolean,
    meetingLive: Boolean,
    onVideoCall: () -> Unit,
    onVoiceCall: () -> Unit,
    onSpace: () -> Unit,
    onMeeting: () -> Unit,
    onInfo: () -> Unit,
    onSearch: () -> Unit,
    onMedia: () -> Unit,
    onAvatar: () -> Unit,
    onPoll: () -> Unit,
    onLeave: () -> Unit,
    muted: Boolean = false,
    onToggleMute: () -> Unit = {}
) = GroupChatCallActions(
    onVideoCall = onVideoCall,
    onVoiceCall = onVoiceCall,
    onInfo = onInfo,
    onSearch = onSearch,
    onMedia = onMedia,
    onAvatar = onAvatar,
    onPoll = onPoll,
    onLeave = onLeave,
    muted = muted,
    onToggleMute = onToggleMute
)

/** شريط واتساب/تلجرام: الجلسة الجارية مثبتة فوق الرسائل ويمكن العودة إليها. — للمساحات/المؤتمرات المستقلة فقط (ليست مكالمات مجموعات) */
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

/**
 * 📞 شريط واتساب لمكالمة المجموعة الجارية — يظهر داخل دردشة المجموعة فقط.
 * - صوت: "مكالمة صوتية جماعية جارية — N مشاركون" + انضمام/عودة
 * - فيديو: "مكالمة فيديو جماعية جارية — شبكة + تسليط المتحدث"
 * - يرن جميع الأعضاء (حتى 32) — يمكن الانضمام لاحقاً
 */
@Composable
fun WhatsAppGroupCallBanner(
    isVideo: Boolean,
    participantCount: Int,
    isInCall: Boolean,
    groupName: String,
    onJoinOrReturn: () -> Unit
) {
    val title = if (isVideo) "مكالمة فيديو جماعية جارية" else "مكالمة صوتية جماعية جارية"
    val subtitle = if (participantCount > 0) "$participantCount مشاركون في $groupName — انقر للانضمام" else "ترن أعضاء $groupName — انقر للانضمام"
    val action = if (isInCall) "العودة" else "انضمام"
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isVideo) Color(0xFF0B3D2A) else Color(0xFF0F2740))
            .border(1.dp, YounesEmerald.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .clickable(onClick = onJoinOrReturn)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(if (isVideo) YounesEmerald else Color(0xFF1E88E5)),
            contentAlignment = Alignment.Center
        ) {
            Icon(if (isVideo) Icons.Default.Videocam else Icons.Filled.LocalPhone, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(subtitle, color = Color.White.copy(0.75f), fontSize = 11.sp, maxLines = 1)
        }
        Box(
            Modifier.clip(RoundedCornerShape(20.dp)).background(YounesEmerald).padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Text(action, color = Color(0xFF002118), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
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
