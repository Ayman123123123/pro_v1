package com.red.sovereign.features.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.filled.LocalPhone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.contacts.DirectoryViewModel
import com.red.sovereign.contacts.PublicRedProfile
import com.red.sovereign.ui.theme.AqyalCyanGlow
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.YounesEmerald

/**
 * 📇 جهات اتصال مثل واتساب — لكن بهوية يونس السيادية
 * لا أرقام هواتف، بل RED ID + username + online + lastSeen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    directory: DirectoryViewModel,
    onBack: () -> Unit,
    onChat: (PublicRedProfile) -> Unit,
    onCall: (PublicRedProfile, Boolean) -> Unit,
    onCreateGroup: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val tokens = remember { TokenStore(context.applicationContext) }
    val myRedId = tokens.redId.orEmpty()
    val myUsername = tokens.username.orEmpty()
    var query by remember { mutableStateOf("") }
    var showQrScanner by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var searchFocused by remember { mutableStateOf(false) }
    val filtered = directory.contacts.filter {
        query.isBlank() || it.displayName.contains(query, true) || it.username.contains(query, true) || it.redId.contains(query, true)
    }.sortedWith(compareByDescending<PublicRedProfile> { directory.isOnline(it.redId) }.thenBy { it.displayName })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("جهات الاتصال", fontWeight = FontWeight.Bold); Text("${directory.contacts.size} جهة • ${directory.onlineIds.size} متصل", color = Color.Gray, fontSize = 12.sp) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "رجوع") } },
                actions = {
                    IconButton(onClick = { showQrScanner = true }) { Icon(Icons.Rounded.QrCodeScanner, "مسح RED ID") }
                    IconButton(onClick = { searchFocused = true }) { Icon(Icons.Default.Search, "بحث") }
                }
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(vertical = 8.dp)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ContactActionRow(Icons.Rounded.GroupAdd, AqyalGold, "مجموعة جديدة", "أنشئ مجموعة مشفرة") { onCreateGroup() }
                    ContactActionRow(Icons.Rounded.PersonAdd, YounesEmerald, "جهة اتصال جديدة", "أضف عبر RED ID أو username") { showQrScanner = true }
                    ContactActionRow(
                        Icons.Default.Share, AqyalCyanGlow, "دعوة عبر RED ID",
                        if (myRedId.isNotBlank()) "شارك $myRedId" else "هويتك غير متاحة — سجّل الدخول أولًا"
                    ) { if (myRedId.isNotBlank()) showShareSheet = true }
                    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("بحث في جهات الاتصال...") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, shape = RoundedCornerShape(14.dp))
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
            }
            if (filtered.isEmpty() && query.isNotBlank()) {
                item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("لا توجد نتائج لـ \"$query\"", color = Color.Gray) } }
            } else {
                items(filtered, key = { it.redId }) { person ->
                    WhatsAppContactRow(person, isOnline = directory.isOnline(person.redId), onChat = { onChat(person) }, onCall = { video -> onCall(person, video) })
                    HorizontalDivider(Modifier.padding(start = 72.dp), color = Color(0xFF1E293B))
                }
            }
            if (directory.requests.isNotEmpty()) {
                item {
                    Text("طلبات معلقة • ${directory.requests.size}", color = AqyalGold, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                    directory.requests.forEach { req ->
                        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(44.dp).clip(CircleShape).background(AqyalGold), contentAlignment = Alignment.Center) { Text(req.requester.displayName.take(1), color = Color.Black, fontWeight = FontWeight.Bold) }
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(req.requester.displayName, color = Color.White, fontWeight = FontWeight.Bold); Text("@${req.requester.username}", color = Color.Gray, fontSize = 12.sp) }
                                TextButton({ directory.resolve(req, false) }) { Text("رفض") }
                                Button({ directory.resolve(req, true) }) { Text("قبول") }
                            }
                        }
                    }
                }
            }
        }
    }

    // QR Scanner Sheet
    if (showQrScanner) {
        QrScannerSheet(
            onDismiss = { showQrScanner = false },
            onScanned = { redId ->
                showQrScanner = false
                // Try to find user in contacts; if found, open chat
                val found = directory.contacts.firstOrNull { it.redId.equals(redId, ignoreCase = true) }
                if (found != null) onChat(found)
                // else could trigger an add-contact flow
            }
        )
    }

    // Focused Search Dialog
    if (searchFocused) {
        FocusedSearchDialog(
            initialQuery = query,
            onDismiss = { searchFocused = false },
            onResultClick = { person ->
                searchFocused = false
                onChat(person)
            }
        )
    }

    // مشاركة RED ID — بمعرّف المستخدم الحقيقي من الجلسة المحلية.
    // لا يُعرض معرّف وهمي عند غياب الجلسة: مشاركة معرّف نائب تعني أن
    // المستقبِل لا يستطيع إضافتك، وهو فشل صامت أسوأ من رسالة صريحة.
    if (showShareSheet && myRedId.isNotBlank()) {
        ShareRedIdSheet(
            onDismiss = { showShareSheet = false },
            redId = myRedId,
            displayName = myUsername.ifBlank { "مستخدم يونس" }
        )
    }
}

@Composable
private fun ContactActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = color, modifier = Modifier.size(22.dp)) }
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(subtitle, color = Color.Gray, fontSize = 12.sp)
        }
    }
}

@Composable
private fun WhatsAppContactRow(person: PublicRedProfile, isOnline: Boolean, onChat: () -> Unit, onCall: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onChat).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(Color(0xFF0F172A), CircleShape)) { Text(person.displayName.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            if (isOnline) Box(Modifier.size(14.dp).clip(CircleShape).background(Color(0xFF00C98C)).padding(2.dp).background(Color(0xFF0F172A), CircleShape).padding(1.dp).background(Color(0xFF00C98C), CircleShape)) {}
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(person.displayName, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1)
            Text(if (isOnline) "متصل الآن" else "آخر ظهور منذ قليل • @${person.username}", color = if (isOnline) Color(0xFF00C98C) else Color.Gray, fontSize = 13.sp, maxLines = 1)
            Text(person.redId, color = Color(0xFF64748B), fontSize = 11.sp, maxLines = 1)
        }
        IconButton(onClick = { onCall(false) }) { Icon(Icons.Filled.LocalPhone, "صوت", tint = YounesEmerald) }
        IconButton(onClick = { onCall(true) }) { Icon(Icons.Default.Videocam, "فيديو", tint = AqyalGold) }
    }
}
