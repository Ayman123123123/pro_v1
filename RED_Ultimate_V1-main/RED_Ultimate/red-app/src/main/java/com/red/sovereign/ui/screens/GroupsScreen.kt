package com.red.sovereign.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.groups.Group
import com.red.sovereign.groups.GroupViewModel
import com.red.sovereign.groups.GroupState
import com.red.sovereign.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/**
 * 🇾🇪 YOUNES Sovereign — شاشة المجموعات
 * مجموعات مشفرة بأنواع الخصوصية الثلاثة:
 * - تصفية حسب النوع + بحث فوري (debounce لمنع إعادة التركيب)
 * - معاينة قبل الانضمام الأعمى
 * - إنشاء مجموعة جديدة
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    groups: GroupViewModel,
    onCreateGroup: () -> Unit = {},
    onOpenGroupChat: (String) -> Unit = {},
    onOpenGroupInfo: (String) -> Unit = {},
    onStartGroupCall: (Group, Boolean) -> Unit = { _, _ -> }
) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("الكل") } // فلتر سريع حسب الخصوصية
    var showJoinDialog by remember { mutableStateOf(false) }
    var joinToken by remember { mutableStateOf("") }

    // debounce للبحث (300ms): يمنع إعادة الاستعلام مع كل حرف.
    var debouncedQuery by remember { mutableStateOf("") }
    LaunchedEffect(query) {
        delay(300)
        debouncedQuery = query
    }

    val sortedGroups = remember(groups.groups.toList(), filter, debouncedQuery) {
        val filtered = groups.groups.filter { group ->
            val matchesFilter = when (filter) {
                "خاص" -> group.privacy == "PRIVATE"
                "سري" -> group.privacy == "SECRET"
                "عام" -> group.privacy == "PUBLIC"
                else -> true
            }
            val q = debouncedQuery.trim()
            val matchesQuery = q.isBlank() || group.name.contains(q, ignoreCase = true) || group.description.orEmpty().contains(q, ignoreCase = true)
            matchesFilter && matchesQuery
        }
        filtered.sortedByDescending { it.createdAt }
    }

    LaunchedEffect(Unit) { groups.load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("المجموعات", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.End) {
                SmallFloatingActionButton(
                    onClick = { showJoinDialog = true },
                    containerColor = SovereignColors.SurfaceNavy,
                    contentColor = AqyalGold
                ) { Icon(Icons.Rounded.Login, "الانضمام برمز") }
                FloatingActionButton(
                    onClick = onCreateGroup,
                    containerColor = YounesEmerald,
                    contentColor = Color(0xFF002117)
                ) { Icon(Icons.Rounded.GroupAdd, "إنشاء مجموعة") }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { GroupStatsRow(groups.groups) }

            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("ابحث في المجموعات…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AqyalGold,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                    )
                )
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("الكل", "خاص", "سري", "عام")) { title ->
                        FilterChip(
                            selected = filter == title,
                            onClick = { filter = title },
                            label = { Text(title, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AqyalGold.copy(alpha = 0.2f),
                                selectedLabelColor = AqyalGold
                            )
                        )
                    }
                }
            }

            item { Text("مجموعاتي", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.7f)) }

            when {
                groups.state == GroupState.Loading && groups.groups.isEmpty() -> item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AqyalGold)
                    }
                }
                groups.state is GroupState.Error && groups.groups.isEmpty() -> item {
                    GroupsEmptyState(Icons.Rounded.Groups, "تعذر تحميل المجموعات", (groups.state as GroupState.Error).message)
                }
                sortedGroups.isEmpty() -> item {
                    GroupsEmptyState(Icons.Rounded.Groups, "لا توجد مجموعات", "أنشئ مجموعة جديدة أو انضم عبر رمز دعوة.")
                }
                else -> items(sortedGroups, key = { it.id }) { group ->
                    GroupCard(
                        group = group,
                        groups = groups,
                        onOpenChat = { onOpenGroupChat(group.id) },
                        onOpenInfo = { onOpenGroupInfo(group.id) },
                        onCall = { video -> onStartGroupCall(group, video) }
                    )
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (showJoinDialog) {
        // LEGENDARY: معاينة قبل الانضمام الأعمى (اسم/وصف/عدد/موافقة) + دعم QR
        var previewJson by remember { mutableStateOf<String?>(null) }
        var previewError by remember { mutableStateOf<String?>(null) }
        var previewLoading by remember { mutableStateOf(false) }
        var showQrScan by remember { mutableStateOf(false) }
        val previewScope = rememberCoroutineScope()
        if (showQrScan) {
            com.red.sovereign.features.contacts.QrScannerSheet(
                onDismiss = { showQrScan = false },
                onScanned = { /* RED-ID هنا غير مستخدم */ showQrScan = false },
                onGroupToken = { tok ->
                    showQrScan = false
                    joinToken = com.red.sovereign.groups.inviteShareLink(tok)
                    previewJson = null; previewError = null
                }
            )
        }
        AlertDialog(
            onDismissRequest = { showJoinDialog = false; joinToken = ""; previewJson = null; previewError = null },
            title = { Text("الانضمام لمجموعة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = joinToken,
                        onValueChange = {
                            joinToken = it; previewJson = null; previewError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("red.ly/g/… أو الرمز أو QR") },
                        label = { Text("رابط أو رمز الدعوة") },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { showQrScan = true }) {
                                Icon(Icons.Rounded.QrCodeScanner, "مسح QR", tint = AqyalGold)
                            }
                        }
                    )
                    Text(
                        "الصق الرابط (https://red.ly/g/… أو red://join?token=…) أو امسح QR الذي يبدأ بـ RED-GROUP: ثم اعرض المعاينة.",
                        fontSize = 12.sp, color = YounesMuted
                    )
                    if (previewLoading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("جارٍ جلب المعاينة…", fontSize = 13.sp, color = YounesMuted)
                        }
                    }
                    previewError?.let { Text(it, color = SovereignColors.Danger, fontSize = 13.sp) }
                    previewJson?.let { raw ->
                        val name = Regex("\"name\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.getOrNull(1) ?: "مجموعة"
                        val desc = Regex("\"description\"\\s*:\\s*\"([^\"]*)\"").find(raw)?.groupValues?.getOrNull(1).orEmpty()
                        val count = Regex("\"memberCount\"\\s*:\\s*(\\d+)").find(raw)?.groupValues?.getOrNull(1) ?: "؟"
                        val approval = raw.contains("\"requireApproval\"\\s*:\\s*true".toRegex())
                        Surface(shape = RoundedCornerShape(14.dp), color = SovereignColors.SurfaceNavy) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                if (desc.isNotBlank()) Text(desc, fontSize = 13.sp, color = Color.Gray, maxLines = 2)
                                Text("$count عضو " + if (approval) "بموافقة الإدارة" else "انضمام فوري",
                                    fontSize = 12.sp, color = AqyalGold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // زر المعاينة أولاً (لا انضمام أعمى)
                    TextButton(
                        onClick = {
                            val clean = com.red.sovereign.core.DeepLinkHandler.parseGroupInviteToken(joinToken)
                            if (clean.isBlank()) { previewError = "رمز غير صالح"; return@TextButton }
                            previewLoading = true; previewError = null
                            previewScope.launch {
                                when (val r = groups.previewInvite(clean)) {
                                    is com.red.sovereign.auth.ApiResult.Success -> { previewJson = r.value; previewError = null }
                                    is com.red.sovereign.auth.ApiResult.Error -> { previewError = r.message; previewJson = null }
                                }
                                previewLoading = false
                            }
                        },
                        enabled = joinToken.trim().isNotBlank() && !previewLoading
                    ) { Text("معاينة") }
                    Button(
                        onClick = {
                            val token = com.red.sovereign.core.DeepLinkHandler.parseGroupInviteToken(joinToken)
                            if (token.isNotBlank()) {
                                groups.joinWithToken(token) { showJoinDialog = false; joinToken = ""; previewJson = null }
                            }
                        },
                        enabled = joinToken.trim().isNotBlank() && groups.state != GroupState.Saving
                    ) { Text(if (previewJson != null) "تأكيد الانضمام" else "انضمام") }
                }
            },
            dismissButton = { TextButton({ showJoinDialog = false; joinToken = ""; previewJson = null }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun GroupStatsRow(groups: List<Group>) {
    val privateCount = groups.count { it.privacy == "PRIVATE" }
    val secretCount = groups.count { it.privacy == "SECRET" }
    val publicCount = groups.count { it.privacy == "PUBLIC" }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard(Modifier.weight(1f), "الإجمالي", groups.size.toString(), Icons.Rounded.Groups, SovereignColors.Cyan)
        StatCard(Modifier.weight(1f), "خاص", privateCount.toString(), Icons.Rounded.Lock, SovereignColors.Success)
        StatCard(Modifier.weight(1f), "سري", secretCount.toString(), Icons.Rounded.VisibilityOff, Color(0xFF8B5CF6))
        StatCard(Modifier.weight(1f), "عام", publicCount.toString(), Icons.Rounded.Public, AqyalGold)
    }
}

@Composable
private fun StatCard(modifier: Modifier, label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceNavy.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(6.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 11.sp, color = YounesMuted)
        }
    }
}

@Composable
private fun GroupCard(
    group: Group,
    groups: GroupViewModel,
    onOpenChat: () -> Unit,
    onOpenInfo: () -> Unit,
    onCall: (Boolean) -> Unit
) {
    // تحميل صورة المجموعة (id+url) مع fallback للحرف الأول عند غياب الصورة.
    LaunchedEffect(group.id, group.avatarUrl) { groups.loadAvatar(group) }
    val avatarImg = groups.avatars[group.id]

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceNavy.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(onClick = onOpenChat)) {
                Box(Modifier.size(54.dp).clip(CircleShape).background(AqyalGold.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    if (avatarImg != null) {
                        androidx.compose.foundation.Image(avatarImg, group.name, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                    } else {
                        Text(group.name.take(1), color = AqyalGold, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    // LEGENDARY: شارة محلية للمجموعات المحلية + اسم بسطر واحد.
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(group.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (group.id.startsWith("local-")) {
                            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(YounesEmerald.copy(alpha = 0.2f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                Text("محلية", fontSize = 10.sp, color = YounesEmerald, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text(
                        group.description.orEmpty().ifBlank { "مجموعة مشفرة عبر Sender Keys" },
                        fontSize = 12.sp,
                        color = YounesMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Person, null, tint = YounesMuted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${group.members.size} عضو", fontSize = 12.sp, color = YounesMuted)
                    Spacer(Modifier.width(10.dp))
                    val (privacyLabel, privacyColor) = when (group.privacy) {
                        "PRIVATE" -> "خاص" to SovereignColors.Success
                        "SECRET" -> "سري" to Color(0xFF8B5CF6)
                        else -> "عام" to AqyalGold
                    }
                    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(privacyColor.copy(alpha = 0.15f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text(privacyLabel, fontSize = 10.sp, color = privacyColor, fontWeight = FontWeight.SemiBold)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { onCall(false) }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).size(48.dp).background(SovereignColors.VoipBlue.copy(alpha = 0.12f), CircleShape)) {
                        Icon(Icons.Rounded.Call, "اتصال صوتي", tint = SovereignColors.VoipBlue, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { onCall(true) }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).size(48.dp).background(Color(0xFF8B5CF6).copy(alpha = 0.12f), CircleShape)) {
                        Icon(Icons.Rounded.Videocam, "اتصال فيديو", tint = Color(0xFF8B5CF6), modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onOpenInfo, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).size(48.dp).background(Color.White.copy(alpha = 0.05f), CircleShape)) {
                        Icon(Icons.Rounded.Info, "معلومات المجموعة", tint = YounesMuted, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupsEmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String) {
    Column(
        Modifier.fillMaxWidth().padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = AqyalGold, modifier = Modifier.size(62.dp))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(detail, textAlign = TextAlign.Center, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
    }
}
