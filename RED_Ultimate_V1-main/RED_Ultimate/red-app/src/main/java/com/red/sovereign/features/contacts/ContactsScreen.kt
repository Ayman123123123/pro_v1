package com.red.sovereign.features.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.contacts.DirectoryState
import com.red.sovereign.contacts.DirectoryViewModel
import com.red.sovereign.contacts.PublicRedProfile
import com.red.sovereign.core.YounesId
import com.red.sovereign.ui.theme.AqyalCyanGlow
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.YounesEmerald

/**
 * الوجهة الوحيدة لإضافة جهة اتصال: تطبيع + تحقق عبر YounesId ثم إرسال الطلب.
 * كل مداخل الإضافة (حوار، QR، نتائج الخادم) تمر من هنا — لا POST مباشر متفرق.
 * الحراس هنا (نفسك/محظور/معروف) يمنعون الطلبات المستحيلة قبل الشبكة.
 * @return true إن قُبِل المدخل وأُرسل، false إن كانت الصيغة مرفوضة أو محروسة.
 */
fun requestContactUnified(
    directory: DirectoryViewModel,
    raw: String,
    selfRedId: String = "",
    blockedIds: Set<String> = emptySet(),
    knownRedIds: Set<String> = emptySet(),
    selfUsername: String = ""
): Boolean {
    val trimmed = raw.trim().removePrefix("@")
    if (trimmed.isBlank()) return false
    // مقارنة غير حساسة لحالة الأحرف — الخادم RED-ID علوي لكن الإدخال قد يأتي صغيرًا.
    val blockedLower = blockedIds.map { it.trim().lowercase() }.toSet()
    val knownLower = knownRedIds.map { it.trim().lowercase() }.toSet()
    // RED ID رقمي أولًا (تطبيع YounesId يقبل RED-/YNS- واللصق القديم).
    val normalized = YounesId.normalizeInput(trimmed)
    if (YounesId.isValid(normalized)) {
        if (selfRedId.isNotBlank() && normalized.equals(selfRedId.trim(), ignoreCase = true)) return false
        if (blockedLower.contains(normalized.lowercase())) return false
        if (knownLower.contains(normalized.lowercase())) return false
        directory.requestByRedId(normalized)
        return true
    }
    // احتياط username: أحرف/أرقام/._ بطول ≥3 (الخادم يحلّه) — لا نرفضه كـ RED ID.
    if (trimmed.length >= 3 && Regex("^[A-Za-z0-9_.]+$").matches(trimmed)) {
        if (selfUsername.isNotBlank() && trimmed.equals(selfUsername.trim().removePrefix("@"), ignoreCase = true)) return false
        if (blockedLower.contains(trimmed.lowercase())) return false
        if (knownLower.contains(trimmed.lowercase())) return false
        directory.requestByRedId(trimmed)
        return true
    }
    return false
}

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
    val lifecycleOwner = LocalLifecycleOwner.current
    val tokens = remember { TokenStore(context.applicationContext) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) directory.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val myRedId = tokens.redId.orEmpty()
    val myUsername = tokens.username.orEmpty()
    var query by remember { mutableStateOf("") }
    // المدخل المستقر للفلترة المضمنة — debounce 350ms حتى لا تُعاد فلترة
    // القائمة وفرزها مع كل حرف أثناء الكتابة السريعة.
    var debouncedQuery by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        snapshotFlow { query }
            .debounce(350)
            .distinctUntilChanged()
            .collectLatest { stable -> debouncedQuery = stable }
    }
    var showQrScanner by remember { mutableStateOf(false) }
    var showAddContact by remember { mutableStateOf(false) }
    var contactRedId by remember { mutableStateOf("") }
    var showShareSheet by remember { mutableStateOf(false) }
    var searchFocused by remember { mutableStateOf(false) }
    var addContactError by remember { mutableStateOf<String?>(null) }
    // الاكتشاف من الخادم: استعلام مستقر ≥3 أحرف يُرسل لـ /api/directory/search
    // (DirectoryViewModel.search) ويُعرض في directory.results — المحلي أولًا دائمًا.
    LaunchedEffect(debouncedQuery) {
        val term = debouncedQuery.trim()
        if (term.length >= 3) directory.search(term)
    }
    val blockedIds = directory.blocked.toSet()
    val knownRedIds = directory.contacts.map { it.redId }.toSet() + blockedIds
    val knownLower = (knownRedIds.map { it.trim().lowercase() } + directory.contacts.map { it.username.trim().lowercase() }).toSet()
    // الاكتشاف من الخادم يُعرض فقط لاستعلام مستقر ≥3 أحرف — وإلا بقيت نتائج
    // بحث سابق عالقة توحي زورًا بوجود اكتشاف لسؤال أقصر. المقارنة lowerCase
    // حتى لا يظهر معرّف معروف بصيغة مختلفة كـ "جديد".
    val serverResults = if (debouncedQuery.trim().length >= 3) directory.results.filter {
        it.redId.trim().lowercase() !in knownLower && it.username.trim().lowercase() !in knownLower
    } else emptyList()
    val filtered = directory.contacts.filter {
        it.redId !in blockedIds &&
            (debouncedQuery.isBlank() || it.displayName.contains(debouncedQuery, true) || it.username.contains(debouncedQuery, true) || it.redId.contains(debouncedQuery, true))
    }.sortedWith(compareByDescending<PublicRedProfile> { directory.isOnline(it.redId) }.thenBy { it.displayName })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column { Text("جهات الاتصال", fontWeight = FontWeight.Bold); Text("${directory.contacts.size} جهة • ${directory.onlineIds.size} متصل", color = Color.Gray, fontSize = 12.sp) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } },
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
                    ContactActionRow(Icons.Rounded.PersonAdd, YounesEmerald, "جهة اتصال جديدة", "أضف عبر RED ID أو username") { showAddContact = true }
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
                    WhatsAppContactRow(person, isOnline = directory.isOnline(person.redId), lastSeen = directory.lastSeenLabel(person.redId), onChat = { onChat(person) }, onCall = { video -> onCall(person, video) }, onBlock = { directory.block(person) })
                    HorizontalDivider(Modifier.padding(start = 72.dp), color = Color(0xFF1E293B))
                }
            }
            // الاكتشاف من الخادم — نتائج /api/directory/search بعد استبعاد المعروفين.
            if (serverResults.isNotEmpty()) {
                item {
                    Text("اكتشاف من الخادم • ${serverResults.size}", color = AqyalCyanGlow, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                    serverResults.forEach { person ->
                        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF162534))) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(44.dp).clip(CircleShape).background(AqyalCyanGlow), contentAlignment = Alignment.Center) { Text(person.displayName.take(1), color = Color.Black, fontWeight = FontWeight.Bold) }
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(person.displayName, color = Color.White, fontWeight = FontWeight.Bold); Text("@${person.username} • ${person.redId}", color = Color.Gray, fontSize = 12.sp) }
                                Button(onClick = { requestContactUnified(directory, person.redId, myRedId, blockedIds, knownRedIds, myUsername) }) { Text("إضافة") }
                            }
                        }
                    }
                }
            }
            // حالة الدليل — رسالة/خطأ من آخر عملية (إرسال طلب، بحث خادم).
            val dirState = directory.state
            if (dirState is DirectoryState.Message || dirState is DirectoryState.Error) {
                item {
                    val isError = dirState is DirectoryState.Error
                    val text = if (isError) (dirState as DirectoryState.Error).message else (dirState as DirectoryState.Message).text
                    Text(text, color = if (isError) Color(0xFFF87171) else YounesEmerald, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
            }
            if (directory.requests.isNotEmpty()) {
                item {
                    // طلبات من محظورين لا تُعرض للقبول — الحظر يعني لا تواصل.
                    val visibleRequests = directory.requests.filter { req -> blockedIds.none { it.equals(req.requester.redId, ignoreCase = true) } }
                    Text("طلبات واردة • ${visibleRequests.size}", color = AqyalGold, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                    visibleRequests.forEach { req ->
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
            if (directory.outgoingRequests.isNotEmpty()) {
                item {
                    // طلبات مرسلة لمعرّف أصبح محظورًا لاحقًا تُخفى حتى فك الحظر أو الإلغاء.
                    val visibleOutgoing = directory.outgoingRequests.filter { req -> blockedIds.none { it.equals(req.recipient.redId, ignoreCase = true) } }
                    Text("طلبات مرسلة • ${visibleOutgoing.size}", color = AqyalCyanGlow, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                    visibleOutgoing.forEach { req ->
                        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF162534))) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(44.dp).clip(CircleShape).background(AqyalCyanGlow), contentAlignment = Alignment.Center) { Text(req.recipient.displayName.take(1), color = Color.Black, fontWeight = FontWeight.Bold) }
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(req.recipient.displayName, color = Color.White, fontWeight = FontWeight.Bold)
                                    Text("بانتظار قبول @${req.recipient.username}", color = Color.Gray, fontSize = 12.sp)
                                }
                                TextButton({ directory.cancel(req) }) { Text("إلغاء") }
                            }
                        }
                    }
                }
            }
            // المحظورون — من الخادم (GET /api/contacts/blocked) مع فك حظر مباشر.
            if (directory.blocked.isNotEmpty()) {
                item {
                    Text("محظورون • ${directory.blocked.size}", color = Color(0xFFF87171), fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
                    directory.blocked.forEach { redId ->
                        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFF87171)), contentAlignment = Alignment.Center) { Text(redId.take(1), color = Color.Black, fontWeight = FontWeight.Bold) }
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) { Text(redId, color = Color.White, fontWeight = FontWeight.Bold); Text("محظور — لا رسائل ولا مكالمات", color = Color.Gray, fontSize = 12.sp) }
                                TextButton({ directory.unblock(PublicRedProfile(redId, "", redId)) }) { Text("فك الحظر") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddContact) {
        AlertDialog(
            onDismissRequest = { showAddContact = false; addContactError = null },
            title = { Text("إضافة جهة اتصال") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = contactRedId,
                        onValueChange = { contactRedId = it; addContactError = null },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("RED ID أو اسم المستخدم") },
                        singleLine = true,
                        isError = addContactError != null,
                        supportingText = addContactError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (requestContactUnified(directory, contactRedId, myRedId, blockedIds, knownRedIds, myUsername)) {
                        contactRedId = ""
                        addContactError = null
                        showAddContact = false
                    } else {
                        addContactError = YounesId.ERROR_MESSAGE
                    }
                }, enabled = contactRedId.isNotBlank()) { Text("إرسال الطلب") }
            },
            dismissButton = { TextButton(onClick = { showAddContact = false; addContactError = null }) { Text("إلغاء") } }
        )
    }

    // QR Scanner Sheet — كل المسارات تمر عبر requestContactUnified (تطبيع + تحقق واحد).
    if (showQrScanner) {
        QrScannerSheet(
            onDismiss = { showQrScanner = false },
            onScanned = { redId ->
                showQrScanner = false
                val normalized = YounesId.normalizeInput(redId)
                val found = directory.contacts.firstOrNull { it.redId.equals(normalized, ignoreCase = true) }
                if (found != null) onChat(found)
                else requestContactUnified(directory, normalized, myRedId, blockedIds, knownRedIds, myUsername)
            }
        )
    }

    // Focused Search Dialog — يُمرَّر الدليل الحقيقي مع debounce داخلي 350ms.
    if (searchFocused) {
        FocusedSearchDialog(
            initialQuery = query,
            contacts = directory.contacts,
            serverResults = serverResults,
            isOnline = directory::isOnline,
            onDismiss = { searchFocused = false },
            onResultClick = { person ->
                searchFocused = false
                onChat(person)
            },
            onAddServerResult = { person -> requestContactUnified(directory, person.redId, myRedId, blockedIds, knownRedIds, myUsername) }
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
private fun WhatsAppContactRow(person: PublicRedProfile, isOnline: Boolean, lastSeen: String?, onChat: () -> Unit, onCall: (Boolean) -> Unit, onBlock: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onChat).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(Color(0xFF0F172A)), contentAlignment = Alignment.Center) { Text(person.displayName.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            if (isOnline) Box(Modifier.size(14.dp).clip(CircleShape).background(Color(0xFF00C98C)).padding(2.dp).background(Color(0xFF0F172A), CircleShape).padding(1.dp).background(Color(0xFF00C98C), CircleShape)) {}
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(person.displayName, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1)
            // آخر ظهور حقيقي من الخادم بدل النص الثابت المضلل (كان "منذ قليل" دائماً).
            val subtitle = if (isOnline) "متصل الآن" else lastSeen ?: "@${person.username}"
            Text(subtitle, color = if (isOnline) Color(0xFF00C98C) else Color.Gray, fontSize = 13.sp, maxLines = 1)
            Text(person.redId, color = Color(0xFF64748B), fontSize = 11.sp, maxLines = 1)
        }
        IconButton(onClick = { onCall(false) }) { Icon(Icons.Default.Call, "صوت", tint = YounesEmerald) }
        IconButton(onClick = { onCall(true) }) { Icon(Icons.Default.Videocam, "فيديو", tint = AqyalGold) }
        // حظر مباشر من صف الجهة — كان directory.block بلا أي زر عميل، فيُحظر فقط
        // عبر طلبات واردة غير مرئية. يزيل الخادم الجهة ويضيفها للمحظورين.
        IconButton(onClick = onBlock) { Icon(Icons.Default.Block, "حظر", tint = Color(0xFFF87171)) }
    }
}
