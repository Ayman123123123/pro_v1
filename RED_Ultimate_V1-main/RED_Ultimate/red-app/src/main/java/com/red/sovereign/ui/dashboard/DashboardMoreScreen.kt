package com.red.sovereign.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.red.sovereign.auth.AuthState
import com.red.sovereign.ui.theme.AqyalCyanGlow
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.YounesEmerald
import com.red.sovereign.ui.theme.YounesGold

/** قسم الأدوات والهوية؛ يعلن الأفعال إلى منسق اللوحة ولا يحمل حالة تنقل خاصة به. */
@Composable
internal fun DashboardMoreScreen(
    account: AuthState.Authenticated,
    onDinstar: () -> Unit,
    onSettings: () -> Unit,
    onContacts: () -> Unit,
    onDevices: () -> Unit,
    onPrivacy: () -> Unit,
    onBackup: () -> Unit,
    onCommunities: () -> Unit = {},
    onProfile: () -> Unit = {},
    onEvents: () -> Unit = {},
    onPolls: () -> Unit = {}
) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("مساحة يونس", style = MaterialTheme.typography.headlineMedium)
        Text("الهوية والخدمات السيادية في مكان واحد", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(
            Modifier.fillMaxWidth().clickable { onProfile() },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                MoreProfileAvatar(account.username.take(1))
                Column(Modifier.padding(horizontal = 12.dp)) {
                    Text(account.username, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "البروفايل · الصورة والبايو والهوية",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        MoreOption(Icons.Default.SimCard, "الهاتف اليمني", "اتصال صوتي مصرح عبر DINSTAR وشرائح الشبكات اليمنية", AqyalGold, click = onDinstar)
        MoreOption(Icons.Default.Security, "الخصوصية والأمان", "من يرى بياناتك، التشفير، وقفل البصمة", YounesEmerald, click = onPrivacy)
        MoreOption(Icons.Default.CloudSync, "النسخ الاحتياطي", "تأمين محادثاتك وسجلاتك محلياً", YounesGold, click = onBackup)
        MoreOption(Icons.Default.Devices, "الأجهزة المتصلة", "إدارة جلسات يونس على كافة أجهزتك", AqyalCyanGlow, click = onDevices)
        MoreOption(Icons.Default.Settings, "الإعدادات العامة", "الهوية والأجهزة والخادم والجلسة", YounesEmerald, click = onSettings)
        MoreOption(Icons.Default.Contacts, "جهات الاتصال", "الأصدقاء وطلبات التواصل والحظر", AqyalCyanGlow, click = onContacts)
        MoreOption(Icons.Default.Public, "المجتمعات والقنوات", "مجتمعات عامة وقنوات — انضم وتابع (عام، ليس مشفراً)", Color(0xFFA78BFA), click = onCommunities)
        MoreOption(Icons.Default.Event, "الفعاليات", "فعاليات مجتمعية مع RSVP وتسجيل حضور", Color(0xFFE8B84A), click = onEvents)
        MoreOption(Icons.Default.Poll, "الاستطلاعات", "تصويت مجتمعي مع نتائج فورية ونِسَم مئوية", Color(0xFF65D7E7), click = onPolls)
    }
}

@Composable
private fun MoreProfileAvatar(text: String) = Box(
    Modifier.size(42.dp).clip(CircleShape).background(AqyalGold),
    contentAlignment = Alignment.Center
) {
    Text(text, color = Color.Black, fontWeight = FontWeight.Black)
}

@Composable
private fun MoreOption(
    icon: ImageVector,
    title: String,
    detail: String,
    color: Color,
    enabled: Boolean = true,
    click: () -> Unit
) = Card(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = click)) {
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = .16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = color)
        }
        Column(Modifier.padding(horizontal = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}
