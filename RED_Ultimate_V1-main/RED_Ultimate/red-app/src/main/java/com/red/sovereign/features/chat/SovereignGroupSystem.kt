package com.red.sovereign.features.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.contacts.PublicRedProfile
import com.red.sovereign.groups.Group
import com.red.sovereign.groups.GroupMember
import com.red.sovereign.groups.GroupViewModel
import com.red.sovereign.ui.theme.SovereignColors
import com.red.sovereign.core.MessageStore
import com.red.sovereign.crypto.SafetyState
import com.red.sovereign.crypto.SafetyViewModel

/** LEGENDARY: استطلاع مجموعة (مرآة GroupPollResult خادم) */
@kotlinx.serialization.Serializable
data class GroupPollUi(
    val id: String = "",
    val question: String = "",
    val options: List<String> = emptyList(),
    val counts: List<Long> = emptyList(),
    val totalVoters: Long = 0,
    val closed: Boolean = false,
    val closesAt: String? = null,
    val myVotes: List<Int> = emptyList()
)

/** LEGENDARY: قارئ مجموعة (مرآة GroupReadEntry خادم) */
@kotlinx.serialization.Serializable
data class GroupReaderUi(
    val redId: String = "",
    val username: String = "",
    val lastReadSequence: Long = 0L,
    val updatedAt: String = ""
)

@Composable
fun GroupInvitesTab(group: Group, groups: GroupViewModel) {
    val ctx = LocalContext.current
    var showQrFor by remember(group.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(group.id) { groups.listInvites(group) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = { groups.createInvite(group) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = SovereignColors.Cyan),
            shape = RoundedCornerShape(12.dp)
        ) { Text("إنشاء رابط دعوة جديد", color = Color.Black, fontWeight = FontWeight.Bold) }
        if (groups.invites.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("لا توجد روابط نشطة — أنشئ واحداً أعلاه", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            groups.invites.forEach { inv ->
                val link = com.red.sovereign.groups.inviteShareLink(inv.token)
                val qrPayload = com.red.sovereign.groups.inviteQrPayload(inv.token)
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(link, fontSize = 13.sp, color = SovereignColors.Cyan, maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text("يستخدم ${inv.uses}/${inv.maxUses} • " +
                            if (inv.requireApproval) "بموافقة المشرف" else "انضمام فوري",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(
                                    ClipData.newPlainText("دعوة", link))
                                Toast.makeText(ctx, "تم نسخ الرابط", Toast.LENGTH_SHORT).show()
                            }) { Text("نسخ") }
                            TextButton(onClick = { showQrFor = if (showQrFor == inv.token) null else inv.token }) {
                                Text(if (showQrFor == inv.token) "إخفاء QR" else "عرض QR")
                            }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { groups.revokeInvite(group, inv.id) }) {
                                Text("إلغاء", color = SovereignColors.Danger)
                            }
                        }
                        if (showQrFor == inv.token) {
                            val bmp = remember(qrPayload) {
                                runCatching { com.red.sovereign.util.QrCodeGenerator.generate(qrPayload, 420).asImageBitmap() }.getOrNull()
                            }
                            if (bmp != null) {
                                androidx.compose.foundation.Image(bmp, "QR الدعوة",
                                    modifier = Modifier.size(200.dp).clip(RoundedCornerShape(12.dp))
                                        .background(Color.White).padding(8.dp)
                                        .align(Alignment.CenterHorizontally))
                                Text("امسح بـ RED للدخول المباشر", fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.CenterHorizontally))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GroupPollsSection(group: Group, groups: GroupViewModel) {
    var question by remember(group.id) { mutableStateOf("") }
    var optText by remember(group.id) { mutableStateOf("") }
    val opts = remember(group.id) { mutableStateListOf<String>() }
    var hideResults by remember(group.id) { mutableStateOf(false) }
    LaunchedEffect(group.id) { groups.loadPolls(group) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("الاستطلاعات (${groups.polls.size})", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        OutlinedTextField(value = question, onValueChange = { question = it.take(300) },
            label = { Text("سؤال جديد…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = optText, onValueChange = { optText = it.take(100) },
                label = { Text("خيار…") }, singleLine = true, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = {
                if (optText.trim().isNotBlank() && opts.size < 10) { opts.add(optText.trim()); optText = "" }
            }) { Text("إضافة") }
        }
        if (opts.isNotEmpty()) {
            Text(opts.joinToString(" • "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = hideResults, onCheckedChange = { hideResults = it })
                Text("إخفاء النتائج حتى التصويت", fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Button(onClick = {
                    groups.createPoll(group, question, opts.toList(), hideResults, 1440) {
                        question = ""; opts.clear()
                    }
                }, enabled = question.trim().length >= 2 && opts.size >= 2) { Text("نشر") }
            }
        }
        groups.polls.forEach { p ->
            Surface(shape = RoundedCornerShape(12.dp), color = SovereignColors.SurfaceNavy, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.question, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (p.closed) Text("مغلق", color = SovereignColors.Danger, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    p.options.forEachIndexed { i, opt ->
                        val count = p.counts.getOrNull(i) ?: 0L
                        val mine = p.myVotes.contains(i)
                        val pct = if (p.totalVoters > 0 && count >= 0) (count * 100 / p.totalVoters).toInt() else 0
                        Surface(
                            onClick = { if (!p.closed) groups.votePoll(group, p.id, listOf(i)) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (mine) SovereignColors.Cyan.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(opt, modifier = Modifier.weight(1f), fontSize = 14.sp)
                                Text(if (count < 0) "؟" else "$count ($pct%)",
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${p.totalVoters} مصوّت", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        if (!p.closed) TextButton({ groups.closePoll(group, p.id) }) {
                            Text("إغلاق", fontSize = 12.sp, color = SovereignColors.Danger)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 👥 YOUNES Sovereign Group System
 * نظام المجموعات المتقدم السيادي
 */

enum class GroupRole(val label: String, val icon: ImageVector, val color: Color) {
    OWNER("المالك السيادي", Icons.Rounded.VpnKey, SovereignColors.Gold),
    ADMIN("مشرف", Icons.Rounded.Shield, SovereignColors.Cyan),
    MODERATOR("مراقب", Icons.Rounded.VerifiedUser, SovereignColors.Success),
    MEMBER("عضو", Icons.Rounded.Person, Color(0xFF9FB0C2))
}

enum class GroupPrivacy(val label: String, val icon: ImageVector, val desc: String) {
    PUBLIC("عامة", Icons.Rounded.Public, "متاحة للجميع محلياً"),
    PRIVATE("خاصة", Icons.Rounded.Lock, "بالدعوة فقط — تشفير كامل"),
    SECRET("سرية", Icons.Rounded.VisibilityOff, "مخفية — دعوة المالك فقط")
}

@Composable
fun CreateGroupScreen(
    onBack: () -> Unit = {},
    friends: List<PublicRedProfile> = emptyList(),
    avatarUri: android.net.Uri? = null,
    onPickAvatar: () -> Unit = {},
    onCreate: (name: String, description: String?, privacy: String, memberRedIds: List<String>, avatarUri: android.net.Uri?) -> Unit = { _, _, _, _, _ -> },
    // LEGENDARY: حالة الحفظ للزر (كانت الشاشة تتجمد بلا مؤشر) + خطأ خارجي
    isSaving: Boolean = false,
    externalError: String? = null
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var privacy by remember { mutableStateOf(GroupPrivacy.PRIVATE) }
    val selectedMembers = remember { androidx.compose.runtime.mutableStateMapOf<String, PublicRedProfile>() }
    var memberSearch by remember { mutableStateOf("") }
    var avatarUploadError by remember { mutableStateOf<String?>(null) }
    var showAvatarRetry by remember { mutableStateOf(false) }

    val filteredFriends = remember(memberSearch, friends) {
        val q = memberSearch.trim()
        if (q.isEmpty()) friends
        else friends.filter { it.displayName.contains(q, ignoreCase = true) || it.username.contains(q, ignoreCase = true) || it.redId.contains(q, ignoreCase = true) }
    }
    // LEGENDARY: تحقق فوري + عداد (واتساب: 100 حرف — نحن 64 للأمان)
    val nameError = when {
        name.isBlank() -> null
        name.trim().length < 2 -> "قصير — حرفان على الأقل"
        name.trim().length > 64 -> "طويل — 64 حد أقصى"
        else -> null
    }
    val canCreate = name.trim().length in 2..64 && !isSaving

    // LEGENDARY: تمرير كامل للشاشات الصغيرة (كانت Column ثابتة تقطع الزر)
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface) }
            Column {
                Text("إنشاء مجموعة سيادية", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("اختر الأصدقاء الذين تريد إضافتهم", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(16.dp))
        // صورة المجموعة الحقيقية: معاينة محلية فورية، والرفع عبر
        // GroupViewModel.create(avatarUri) -> MediaApi + PATCH avatarUrl.
        // فشل الرفع لا يُفشل الإنشاء (تحذير + مجموعة بلا صورة).
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            val ctx = LocalContext.current
            val preview: androidx.compose.ui.graphics.ImageBitmap? = remember(avatarUri) {
                avatarUri?.let { uri ->
                    runCatching {
                        ctx.contentResolver.openInputStream(uri)?.use { stream ->
                            android.graphics.BitmapFactory.decodeStream(stream)?.asImageBitmap()
                        }
                    }.getOrNull()
                }
            }
            Surface(
                onClick = onPickAvatar,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(64.dp)
            ) {
                if (preview != null) {
                    androidx.compose.foundation.Image(preview, "صورة المجموعة", Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                } else {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.Rounded.AddAPhoto, "إضافة صورة المجموعة", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("صورة المجموعة (اختيارية)", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("انقر لاختيار صورة — تُرفع عند التأسيس", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                if (avatarUploadError != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        Text("فشل رفع الصورة: $avatarUploadError", color = SovereignColors.Danger, fontSize = 11.sp)
                        if (showAvatarRetry) {
                            TextButton(onClick = { showAvatarRetry = false; onPickAvatar() }) { Text("إعادة المحاولة", color = SovereignColors.Cyan, fontSize = 11.sp) }
                        } else {
                            TextButton(onClick = { showAvatarRetry = true }) { Text("إظهار إعادة المحاولة", color = SovereignColors.Cyan, fontSize = 11.sp) }
                        }
                    }
                }
            }
            if (avatarUri != null) {
                TextButton(onClick = onPickAvatar) { Text("تغيير", fontSize = 12.sp) }
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = name, onValueChange = { if (it.length <= 70) name = it },
            label = { Text("اسم المجموعة") },
            supportingText = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(nameError ?: "${name.trim().length}/64", color = if (nameError != null) SovereignColors.Danger else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
            },
            isError = nameError != null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant, focusedBorderColor = SovereignColors.Cyan, errorBorderColor = SovereignColors.Danger)
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = description, onValueChange = { if (it.length <= 256) description = it },
            label = { Text("الوصف (اختياري)") },
            supportingText = { Text("${description.length}/256", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant, focusedBorderColor = SovereignColors.Cyan)
        )
        Spacer(Modifier.height(16.dp))
        Text("خصوصية المجموعة", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        GroupPrivacy.entries.forEach { level ->
            val isSelected = level == privacy
            Surface(
                onClick = { privacy = level },
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) SovereignColors.Cyan.copy(alpha = 0.1f) else SovereignColors.SurfaceNavy,
                border = BorderStroke(1.dp, if (isSelected) SovereignColors.Cyan else Color.Transparent),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(level.icon, null, tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(level.label, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                        Text(level.desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("إضافة أعضاء (${selectedMembers.size} مختار)", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            if (selectedMembers.isNotEmpty()) {
                TextButton({ selectedMembers.clear() }) { Text("إلغاء التحديد", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
            }
        }
        // LEGENDARY: شرائح الأعضاء المختارين — معاينة فورية لشكل المجموعة قبل التأسيس (ظهور حي)
        if (selectedMembers.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                items(selectedMembers.values.toList(), key = { it.redId }) { m ->
                    Surface(shape = RoundedCornerShape(20.dp), color = SovereignColors.Cyan.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, SovereignColors.Cyan.copy(alpha = 0.4f))) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(Modifier.size(24.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(m.displayName.take(1), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(m.displayName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.width(4.dp))
                            TextButton(onClick = { selectedMembers.remove(m.redId) },
                                contentPadding = PaddingValues(2.dp)) {
                                Text("✕", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedTextField(
            value = memberSearch, onValueChange = { memberSearch = it },
            label = { Text("ابحث عن صديق…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant, focusedBorderColor = SovereignColors.Cyan)
        )
        Spacer(Modifier.height(8.dp))
        if (friends.isEmpty()) {
            Text("لا يوجد أصدقاء بعد — أضف أصدقاء أولاً لتتمكن من إضافتهم للمجموعة", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(vertical = 16.dp))
        } else {
            LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 260.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(filteredFriends, key = { it.redId }) { friend ->
                    val checked = selectedMembers.containsKey(friend.redId)
                    Surface(
                        onClick = { if (checked) selectedMembers.remove(friend.redId) else selectedMembers[friend.redId] = friend },
                        shape = RoundedCornerShape(10.dp),
                        color = if (checked) SovereignColors.Cyan.copy(alpha = 0.12f) else SovereignColors.SurfaceNavy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(Modifier.size(36.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                Box(contentAlignment = Alignment.Center) { Text(friend.displayName.take(1), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) }
                            }
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(friend.displayName, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                                Text("@${friend.username}", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                            }
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { if (it) selectedMembers[friend.redId] = friend else selectedMembers.remove(friend.redId) },
                                colors = CheckboxDefaults.colors(checkedColor = SovereignColors.Cyan)
                            )
                        }
                    }
                }
                if (filteredFriends.isEmpty()) {
                    item { Text("لا توجد نتائج مطابقة", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp)) }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        // LEGENDARY: خطأ خارجي من الخادم + زر بحالة تحميل (كان يتجمد بلا رد)
        if (externalError != null) {
            Surface(shape = RoundedCornerShape(12.dp), color = SovereignColors.Danger.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, SovereignColors.Danger.copy(alpha = 0.4f)), modifier = Modifier.fillMaxWidth()) {
                Text(externalError, color = SovereignColors.Danger, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
            }
            Spacer(Modifier.height(8.dp))
        }
        Button(
            onClick = { onCreate(name.trim(), description.trim().takeIf(String::isNotEmpty), privacy.name, selectedMembers.keys.toList(), avatarUri) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SovereignColors.Cyan),
            shape = RoundedCornerShape(14.dp),
            enabled = canCreate
        ) {
            if (isSaving) {
                CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("جارٍ التأسيس…", fontWeight = FontWeight.Bold, color = Color.Black)
            } else {
                Text(
                    when {
                        nameError != null -> nameError
                        selectedMembers.isEmpty() -> "تأسيس المجموعة (بلا أعضاء — يمكنك الإضافة لاحقاً)"
                        else -> "تأسيس المجموعة (${selectedMembers.size} عضو)"
                    },
                    fontWeight = FontWeight.Bold, color = Color.Black, fontSize = 14.sp
                )
            }
        }
    }
}

/**
 * توافق رجعي: المسار القديم بأربع معاملات يبقى يعمل — يفوّض للجديد بصورة null.
 * يُستخدم من الشاشات التي لم تُهاجر بعد لمنتقي الصورة.
 */
@Composable
fun CreateGroupScreenLegacy(
    onBack: () -> Unit = {},
    friends: List<PublicRedProfile> = emptyList(),
    onCreate: (name: String, description: String?, privacy: String, memberRedIds: List<String>) -> Unit = { _, _, _, _ -> }
) {
    CreateGroupScreen(
        onBack = onBack,
        friends = friends,
        avatarUri = null,
        onPickAvatar = {},
        onCreate = { name, description, privacy, memberRedIds, _ -> onCreate(name, description, privacy, memberRedIds) }
    )
}

private fun roleOf(member: GroupMember): GroupRole {
    return when (member.role.uppercase()) {
        "OWNER" -> GroupRole.OWNER
        "ADMIN" -> GroupRole.ADMIN
        "MODERATOR" -> GroupRole.MODERATOR
        else -> GroupRole.MEMBER
    }
}

@Composable
fun SovereignGroupInfoScreen(
    group: Group?,
    groups: GroupViewModel,
    friends: List<PublicRedProfile> = emptyList(),
    ownRedId: String = "",
    onBack: () -> Unit = {},
    onMessage: (String) -> Unit = {}
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val localMessages = remember(ctx) { MessageStore(ctx) }
    val safety: SafetyViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val groupId = group?.id.orEmpty()
    var mutedUntil by remember(groupId) { mutableStateOf(localMessages.conversationPreference(groupId).third) }
    var disappearingDurationMs by remember(groupId) {
        mutableStateOf(localMessages.conversationDisappearingDuration(groupId).takeIf { it > 0L })
    }
    var showAddMembers by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showMuteOptions by remember { mutableStateOf(false) }
    var showDisappearingOptions by remember { mutableStateOf(false) }
    var receiptsMode by remember(groupId) { mutableStateOf(localMessages.conversationReadReceipts(groupId)) }
    var showReceiptsOptions by remember { mutableStateOf(false) }
    // إدارة عضو محدد (ترقية/تخفيض/إزالة) — للمشرفين فقط.
    var manageMember by remember { mutableStateOf<GroupMember?>(null) }
    // ── P1-E: إدارة المجموعات — SlowMode + إنهاء + حذف أدمن ──
    // slowModeSeconds واجهة محلية (آخر رسالة + X) تُحفظ في SharedPreferences
    // offline-first؛ تُزامَن مع الخادم عبر updateSlowMode عند توفر الشبكة.
    var showSlowMode by remember { mutableStateOf(false) }
    var confirmEndGroup by remember { mutableStateOf(false) }
    var slowModeSeconds by remember(groupId) { mutableStateOf<Int>(getGroupSlowModeSeconds(ctx, groupId)) }
    var isEndedLocally by remember(groupId) { mutableStateOf(localMessages.conversationPreference(groupId).second) }
    // تعديل معلومات المجموعة (اسم/وصف) عبر GroupViewModel.updateInfo (PATCH).
    var showEditInfo by remember { mutableStateOf(false) }
    var editName by remember(groupId) { mutableStateOf("") }
    var editDesc by remember(groupId) { mutableStateOf("") }
    // طلبات الانضمام — تظهر فقط للمشرفين عندما يكون requireJoinApproval مفعّل
    val myRole = group?.members?.firstOrNull { it.redId == ownRedId }?.role
    val isManager = myRole == "OWNER" || myRole == "ADMIN"
    LaunchedEffect(groupId, group?.settings?.requireJoinApproval) { if (groupId.isNotBlank() && isManager && group?.settings?.requireJoinApproval == true) group.let { groups.loadJoinRequests(it) } }
    val tabs = if (isManager && group?.settings?.requireJoinApproval == true) listOf("الأعضاء", "الطلبات (${groups.joinRequests.size})", "الاستطلاعات", "الدعوات", "الإعدادات") else listOf("الأعضاء", "الاستطلاعات", "الدعوات", "الإعدادات")
    var selectedTab by remember { mutableIntStateOf(0) }

    // ◀️ رجوع هرمي في معلومات المجموعة — يغلق النوافذ قبل العودة
    BackHandler {
        when {
            showEditInfo -> showEditInfo = false
            showAddMembers -> showAddMembers = false
            showMuteOptions -> showMuteOptions = false
            showDisappearingOptions -> showDisappearingOptions = false
            showReceiptsOptions -> showReceiptsOptions = false
            showSlowMode -> showSlowMode = false
            confirmEndGroup -> confirmEndGroup = false
            confirmLeave -> confirmLeave = false
            confirmDelete -> confirmDelete = false
            else -> onBack()
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // رأس المجموعة
        Box(
            Modifier.fillMaxWidth().height(190.dp)
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.surface))),
            contentAlignment = Alignment.BottomStart
        ) {
            Row(modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = MaterialTheme.colorScheme.onSurface)
                }
            }
            Row(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                // مدخل مكالمات دردشة المجموعة عبر المنسق (يفحص العضوية والصلاحية) — منفصل عن مكالمات الأصدقاء.
                val callLauncherVoice = com.red.sovereign.ui.rememberCallPermissionLauncher(
                    needCamera = false,
                    onGranted = {
                        group?.let { g ->
                            when (com.red.sovereign.calls.GroupChatCallCoordinator.start(ctx, g, ownRedId, false)) {
                                com.red.sovereign.calls.GroupChatCallCoordinator.StartResult.NoOtherMembers ->
                                    android.widget.Toast.makeText(ctx, "لا يوجد أعضاء آخرون للاتصال بهم", android.widget.Toast.LENGTH_SHORT).show()
                                com.red.sovereign.calls.GroupChatCallCoordinator.StartResult.MissingIdentity ->
                                    android.widget.Toast.makeText(ctx, "هويتك غير متاحة — سجّل الدخول أولًا", android.widget.Toast.LENGTH_SHORT).show()
                                com.red.sovereign.calls.GroupChatCallCoordinator.StartResult.NotAllowedBySettings ->
                                    android.widget.Toast.makeText(ctx, "بدء المكالمات للمشرفين فقط في هذه المجموعة", android.widget.Toast.LENGTH_LONG).show()
                                else -> Unit
                            }
                        }
                    },
                    onDenied = { android.widget.Toast.makeText(ctx, "الصلاحيات مطلوبة للاتصال", android.widget.Toast.LENGTH_SHORT).show() }
                )
                val callLauncherVideo = com.red.sovereign.ui.rememberCallPermissionLauncher(
                    needCamera = true,
                    onGranted = {
                        group?.let { g ->
                            when (com.red.sovereign.calls.GroupChatCallCoordinator.start(ctx, g, ownRedId, true)) {
                                com.red.sovereign.calls.GroupChatCallCoordinator.StartResult.NoOtherMembers ->
                                    android.widget.Toast.makeText(ctx, "لا يوجد أعضاء آخرون للاتصال بهم", android.widget.Toast.LENGTH_SHORT).show()
                                com.red.sovereign.calls.GroupChatCallCoordinator.StartResult.MissingIdentity ->
                                    android.widget.Toast.makeText(ctx, "هويتك غير متاحة — سجّل الدخول أولًا", android.widget.Toast.LENGTH_SHORT).show()
                                com.red.sovereign.calls.GroupChatCallCoordinator.StartResult.NotAllowedBySettings ->
                                    android.widget.Toast.makeText(ctx, "بدء المكالمات للمشرفين فقط في هذه المجموعة", android.widget.Toast.LENGTH_LONG).show()
                                else -> Unit
                            }
                        }
                    },
                    onDenied = { android.widget.Toast.makeText(ctx, "الصلاحيات مطلوبة للاتصال", android.widget.Toast.LENGTH_SHORT).show() }
                )
                IconButton(onClick = { callLauncherVoice() }) {
                    Icon(Icons.Rounded.Phone, "مكالمة صوتية جماعية — ترن الجميع", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { callLauncherVideo() }) {
                    Icon(Icons.Rounded.Videocam, "مكالمة فيديو جماعية — ترن الجميع", tint = MaterialTheme.colorScheme.primary)
                }
            }
            Row(Modifier.padding(top = 40.dp, start = 16.dp, end = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                // أيقونة/صورة المجموعة — موحدة عبر SovereignGroupAvatar
                // (إصلاح LaunchedEffect(g.avatarUrl) المنفرد → مفتاح مركب id+url).
                group?.let { g ->
                    com.red.sovereign.ui.components.SovereignGroupAvatar(
                        group = g,
                        groups = groups,
                        size = 68.dp,
                        themed = false
                    )
                } ?: Surface(Modifier.size(68.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Groups, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp)) }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(group?.name ?: "المجموعة", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(group?.description?.takeIf(String::isNotBlank) ?: "مجموعة مشفرة", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lock, null, tint = SovereignColors.Success, modifier = Modifier.size(14.dp))
                        Text(" ${group?.members?.size ?: 0} أعضاء • تشفير Sender Keys", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    // تعديل الاسم/الوصف — للمشرفين، أو للأعضاء إن سمحت الإعدادات.
                    val canEditInfo = group != null && (isManager || group.settings.onlyAdminsCanEditInfo == false)
                    if (canEditInfo) {
                        TextButton(
                            onClick = {
                                editName = group.name
                                editDesc = group.description.orEmpty()
                                showEditInfo = true
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) { Text("تعديل الاسم/الوصف", color = SovereignColors.Cyan, fontSize = 12.sp) }
                    }
                }
            }
        }

        TabRow(selectedTabIndex = selectedTab, containerColor = SovereignColors.SurfaceNavy) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(title, fontSize = 13.sp) })
            }
        }

        val currentTab = tabs.getOrNull(selectedTab)?.substringBefore(" ") ?: "الأعضاء"
        when {
            currentTab == "الأعضاء" -> {
                // زر إضافة أعضاء — يظهر فقط للمشرفين أو إذا كانت الإعدادات تسمح للأعضاء.
                // (كان الشرط يقرأ onlyAdminsCanEditInfo خطأً فيوسّع/يضيّق الإضافة بلا علاقة بالإعداد الحقيقي).
                val canAdd = group != null && (isManager || group.settings.onlyAdminsCanAddMembers == false)
                if (group != null && friends.isNotEmpty() && canAdd) {
                    Button(
                        onClick = { showAddMembers = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SovereignColors.Cyan),
                        shape = RoundedCornerShape(12.dp)
                    ) { Icon(Icons.Rounded.PersonAdd, null, tint = Color.Black); Text(" إضافة أعضاء من أصدقائك", color = Color.Black, fontWeight = FontWeight.Bold) }
                }
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(group?.members ?: emptyList(), key = { it.redId }) { member ->
                        val role = roleOf(member)
                        val isSelf = member.redId == ownRedId
                        // من يستطيع إدارة هذا العضو؟ المالك يدير الجميع (عدا نفسه)، المشرف يزيل الأعضاء فقط.
                        val manageable = !isSelf && (myRole == "OWNER" || (myRole == "ADMIN" && role == GroupRole.MEMBER))
                        Surface(
                            onClick = { if (manageable) manageMember = member else if (!isSelf) onMessage(member.redId) },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(Modifier.size(42.dp), shape = CircleShape, color = SovereignColors.Navy) {
                                    Box(contentAlignment = Alignment.Center) { Text(member.username.take(1), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) }
                                }
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    Text(if (isSelf) "${member.username} (أنت)" else member.username, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                                    Text(member.redId, color = SovereignColors.Cyan, fontSize = 11.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                                Surface(shape = RoundedCornerShape(8.dp), color = role.color.copy(alpha = 0.18f)) {
                                    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(role.icon, null, tint = role.color, modifier = Modifier.size(14.dp))
                                        Text(" ${role.label}", fontSize = 11.sp, color = role.color, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                if (!isSelf) {
                                    Spacer(Modifier.width(6.dp))
                                    Icon(
                                        Icons.Rounded.ChatBubble,
                                        contentDescription = "مراسلة ${member.username}",
                                        tint = SovereignColors.Cyan,
                                        modifier = Modifier.size(20.dp).clickable { onMessage(member.redId) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            currentTab == "الطلبات" -> {
                // طلبات الانضمام المعلقة — للمشرفين فقط
                if (groups.joinRequests.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("لا توجد طلبات معلقة", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(groups.joinRequests, key = { it.id }) { req ->
                            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(req.username, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                        Text(req.redId, color = SovereignColors.Cyan, fontSize = 11.sp)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { group?.let { groups.resolveJoin(it, req, true) } }, colors = ButtonDefaults.buttonColors(containerColor = SovereignColors.Success), shape = RoundedCornerShape(10.dp)) { Text("قبول", color = MaterialTheme.colorScheme.onSurface) }
                                        OutlinedButton(onClick = { group?.let { groups.resolveJoin(it, req, false) } }, shape = RoundedCornerShape(10.dp)) { Text("رفض", color = SovereignColors.Danger) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            currentTab == "الاستطلاعات" -> {
                // LEGENDARY: تبويب الاستطلاعات مربوط بالخادم (كان مكوناً بلا ربط)
                if (group != null) {
                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        item { GroupPollsSection(group = group, groups = groups) }
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("المجموعة غير متاحة", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            currentTab == "الدعوات" -> {
                // LEGENDARY: تبويب الدعوات — رابط + QR + نسخ + إلغاء (كان زراً ميتاً)
                if (group != null) {
                    GroupInvitesTab(group = group, groups = groups)
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("المجموعة غير متاحة", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            else -> {
                // الإعدادات
                LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (group != null) {
                        // صلاحيات المجموعة — للمشرفين فقط (كانت 4 أعلام بلا أي واجهة).
                        if (isManager) {
                            item {
                                Text("صلاحيات المجموعة", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                            val s = group.settings
                            item { PermissionSwitch("الإرسال للمشرفين فقط", "الأعضاء يقرأون دون كتابة", s.onlyAdminsCanSend) { groups.updateSettings(group, s.copy(onlyAdminsCanSend = it)) } }
                            item { PermissionSwitch("تحرير المعلومات للمشرفين", "الاسم والوصف والصورة", s.onlyAdminsCanEditInfo) { groups.updateSettings(group, s.copy(onlyAdminsCanEditInfo = it)) } }
                            item { PermissionSwitch("موافقة على الانضمام", "طلبات بدل الدخول المباشر", s.requireJoinApproval) { groups.updateSettings(group, s.copy(requireJoinApproval = it)) } }
                            item { PermissionSwitch("إضافة الأعضاء للمشرفين", "الأعضاء العاديون لا يضيفون", s.onlyAdminsCanAddMembers) { groups.updateSettings(group, s.copy(onlyAdminsCanAddMembers = it)) } }
                            item { PermissionSwitch("الدعوات للمشرفين فقط", "من ينشئ روابط الدعوة", s.onlyAdminsCanInvite) { groups.updateSettings(group, s.copy(onlyAdminsCanInvite = it)) } }
                            item { PermissionSwitch("التثبيت للمشرفين فقط", "تثبيت الرسائل", s.onlyAdminsCanPin) { groups.updateSettings(group, s.copy(onlyAdminsCanPin = it)) } }
                            item { PermissionSwitch("المكالمات للمشرفين فقط", "بدء مكالمة جماعية", s.onlyAdminsCanCall) { groups.updateSettings(group, s.copy(onlyAdminsCanCall = it)) } }
                            // ── P1-E: مصفوفة الصلاحيات الدقيقة (من يرسل/يعدل/يضيف/يثبت) ──
                            item {
                                GroupPermissionMatrix(
                                    settings = s,
                                    myRole = myRole,
                                    slowModeSeconds = slowModeSeconds
                                )
                            }
                            // ── P1-E: SlowMode واجهة (فحص محلي: آخر رسالة + X ثانية) ──
                            item {
                                InfoRow(
                                    Icons.Rounded.Timer,
                                    "الوضع البطيء",
                                    slowModeLabel(slowModeSeconds) + " — فحص محلي قبل الإرسال"
                                ) { showSlowMode = true }
                            }
                            // ── P1-E: حذف الأدمن للجميع (24h + رسالة نظام) ──
                            item {
                                InfoRow(
                                    Icons.Rounded.DeleteSweep,
                                    "حذف المشرف للجميع",
                                    "أي رسالة خلال 24 ساعة مع رسالة نظام — من سجل المجموعة"
                                ) {
                                    Toast.makeText(ctx, "اضغط مطولاً على رسالة المجموعة ثم حذف للجميع (مشرف، خلال 24h)", Toast.LENGTH_LONG).show()
                                }
                            }
                            // LEGENDARY: ربط المجتمع (خادمي — كان حقل عميل وهمياً)
                            item {
                                var communityInput by remember(group.id) { mutableStateOf(group.communityId.orEmpty()) }
                                Surface(shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("المجتمع المرتبط", fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface)
                                        Text(
                                            if (group.communityId.isNullOrBlank()) "غير مرتبطة — أدخل معرف مجتمع لربطها"
                                            else "مرتبطة: ${group.communityId}",
                                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        OutlinedTextField(
                                            value = communityInput,
                                            onValueChange = { communityInput = it.take(64) },
                                            label = { Text("معرف المجتمع (فارغ للفك)") },
                                            singleLine = true, modifier = Modifier.fillMaxWidth()
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(onClick = {
                                                groups.setCommunity(group,
                                                    communityInput.trim().takeIf { it.isNotBlank() }) {}
                                            }) { Text("حفظ الربط") }
                                            if (!group.communityId.isNullOrBlank()) {
                                                OutlinedButton(onClick = {
                                                    communityInput = ""
                                                    groups.setCommunity(group, null) {}
                                                }) { Text("فك الربط", color = SovereignColors.Danger) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        // للأعضاء: ملخص قراءة فقط لمن يرسل/يثبت (شفافية الصلاحيات).
                        if (!isManager) {
                            item {
                                GroupPermissionMatrix(
                                    settings = group.settings,
                                    myRole = myRole,
                                    slowModeSeconds = slowModeSeconds
                                )
                            }
                            item {
                                val allowed = canGroupSend(myRole, group.settings.onlyAdminsCanSend)
                                if (!allowed) {
                                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = SovereignColors.Cyan.copy(alpha = 0.10f)) {
                                        Text(
                                            "الإرسال للمشرفين فقط — أنت في وضع القراءة",
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 12.sp,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                } else if (slowModeSeconds > 0) {
                                    val remaining = slowModeRemainingSeconds(getGroupLastSentMillis(ctx, group.id), slowModeSeconds)
                                    if (remaining > 0) {
                                        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = SovereignColors.Gold.copy(alpha = 0.12f)) {
                                            Text(
                                                "الوضع البطيء: انتظر $remaining ث قبل رسالتك التالية",
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        item {
                            InfoRow(
                                Icons.Rounded.Schedule,
                                "رسائل مؤقتة",
                                disappearingDurationLabel(disappearingDurationMs)
                            ) { showDisappearingOptions = true }
                        }
                        item {
                            val isMuted = mutedUntil > System.currentTimeMillis()
                            InfoRow(
                                if (isMuted) Icons.Rounded.VolumeOff else Icons.Rounded.Notifications,
                                if (isMuted) "الإشعارات مكتومة" else "كتم الإشعارات",
                                if (isMuted) "حتى ${formatMuteUntil(mutedUntil)}" else "اختَر مدة لإيقاف تنبيهات هذه المجموعة"
                            ) { showMuteOptions = true }
                        }
                        item {
                            val receiptsLabel = when (receiptsMode) { 1 -> "مفعّلة"; 2 -> "معطّلة"; else -> "حسب الإعداد العام" }
                            InfoRow(
                                Icons.Rounded.DoneAll,
                                "إيصالات القراءة",
                                receiptsLabel
                            ) { showReceiptsOptions = true }
                        }
                        item {
                            InfoRow(Icons.Rounded.Shield, "رمز أمان مالك المجموعة", "تحقق من هوية مالك المجموعة قبل الوثوق بالدعوة") {
                                group.ownerRedId.takeIf { it.isNotBlank() }?.let(safety::open)
                            }
                        }
                        if (myRole != "OWNER") {
                            item {
                                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = SovereignColors.Danger.copy(alpha = 0.12f)) {
                                    Row(Modifier.padding(12.dp).clickable { confirmLeave = true }, verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.ExitToApp, null, tint = SovereignColors.Danger)
                                        Text(" مغادرة المجموعة", color = SovereignColors.Danger, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        if (myRole == "OWNER") {
                            // ── P1-E: إنهاء المجموعة (أرشفة + تعطيل الرابط) — بديل ناعم عن الحذف ──
                            item {
                                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = SovereignColors.Gold.copy(alpha = 0.12f)) {
                                    Row(Modifier.padding(12.dp).clickable { confirmEndGroup = true }, verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.Archive, null, tint = SovereignColors.Gold)
                                        Column(Modifier.padding(start = 8.dp)) {
                                            Text(" إنهاء المجموعة", color = SovereignColors.Gold, fontWeight = FontWeight.Bold)
                                            Text(
                                                if (isEndedLocally) "مُنهاة ومؤرشفة — الروابط معطّلة" else "أرشفة + تعطيل روابط الدعوة (تبقى الرسائل)",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                            item {
                                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = SovereignColors.Danger.copy(alpha = 0.12f)) {
                                    Row(Modifier.padding(12.dp).clickable { confirmDelete = true }, verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.DeleteForever, null, tint = SovereignColors.Danger)
                                        Text(" حذف المجموعة نهائياً", color = SovereignColors.Danger, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else if (myRole != "OWNER" && group.members.size == 1) {
                            item { Text("أنت المالك الوحيد — انقل الملكية أولاً قبل المغادرة", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
                        }
                    }
                }
            }
        }
    }

    // نافذة تعديل معلومات المجموعة (اسم/وصف) — PATCH عبر GroupViewModel.updateInfo.
    if (showEditInfo && group != null) {
        AlertDialog(
            onDismissRequest = { showEditInfo = false },
            containerColor = SovereignColors.SurfaceNavy,
            title = { Text("تعديل معلومات المجموعة", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editName, onValueChange = { editName = it.take(100) },
                        label = { Text("اسم المجموعة") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editDesc, onValueChange = { editDesc = it.take(500) },
                        label = { Text("الوصف (اختياري)") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { groups.updateInfo(group, editName, editDesc.takeIf { it.isNotBlank() }) { showEditInfo = false } },
                    enabled = editName.trim().length in 2..100
                ) { Text("حفظ", color = SovereignColors.Cyan) }
            },
            dismissButton = { TextButton({ showEditInfo = false }) { Text("إلغاء", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        )
    }

    // نافذة إدارة عضو (ترقية/تخفيض/نقل ملكية/إزالة) — الخادم يفرض الصلاحيات نهائياً.
    val target = manageMember
    if (target != null && group != null) {
        val targetRole = roleOf(target)
        AlertDialog(
            onDismissRequest = { manageMember = null },
            containerColor = SovereignColors.SurfaceNavy,
            title = { Text("إدارة ${target.username}", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("الدور الحالي: ${targetRole.label}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    if (myRole == "OWNER" && targetRole != GroupRole.ADMIN) {
                        TextButton({ groups.updateRole(group, target, "ADMIN"); manageMember = null }) { Text("منح مشرف", color = SovereignColors.Cyan) }
                    }
                    if (myRole == "OWNER" && targetRole != GroupRole.MODERATOR) {
                        TextButton({ groups.updateRole(group, target, "MODERATOR"); manageMember = null }) { Text("منح مراقب", color = SovereignColors.Success) }
                    }
                    if (myRole == "OWNER" && targetRole != GroupRole.MEMBER) {
                        TextButton({ groups.updateRole(group, target, "MEMBER"); manageMember = null }) { Text("تخفيض إلى عضو", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    if (myRole == "OWNER") {
                        TextButton({
                            groups.transferOwnership(group, target) {}; manageMember = null
                        }) { Text("نقل الملكية إليه", color = SovereignColors.Gold) }
                    }
                    if (myRole == "OWNER" || (myRole == "ADMIN" && targetRole == GroupRole.MEMBER)) {
                        TextButton({ groups.removeMember(group, target); manageMember = null }) { Text("إزالة من المجموعة", color = SovereignColors.Danger) }
                    }
                    // LEGENDARY: حظر+طرد بزر سبب (الخادم كان جاهزاً بلا واجهة)
                    if (myRole == "OWNER" || myRole == "ADMIN") {
                        var banReason by remember { mutableStateOf("") }
                        var showBanField by remember { mutableStateOf(false) }
                        if (showBanField) {
                            OutlinedTextField(
                                value = banReason, onValueChange = { banReason = it.take(200) },
                                label = { Text("سبب الحظر (اختياري)") }, singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        TextButton({
                            if (!showBanField) showBanField = true
                            else { groups.banMember(group, target, banReason) {}; manageMember = null }
                        }) { Text(if (showBanField) "تأكيد الحظر + الطرد" else "حظر + طرد", color = SovereignColors.Danger, fontWeight = FontWeight.Bold) }
                    }
                    TextButton({ onMessage(target.redId); manageMember = null }) { Text("مراسلة خاصة", color = SovereignColors.Cyan) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton({ manageMember = null }) { Text("إغلاق", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        )
    }

    // نافذة إضافة أعضاء
    if (showAddMembers && group != null) {
        val selectedAdd = remember { androidx.compose.runtime.mutableStateMapOf<String, PublicRedProfile>() }
        AlertDialog(
            onDismissRequest = { showAddMembers = false },
            containerColor = SovereignColors.SurfaceNavy,
            title = { Text("إضافة أعضاء", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val existing = group.members.map { it.redId }.toSet()
                    val addable = friends.filter { it.redId !in existing }
                    if (addable.isEmpty()) item { Text("كل أصدقائك بالفعل في المجموعة", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(addable, key = { it.redId }) { friend ->
                        val checked = selectedAdd.containsKey(friend.redId)
                        Surface(onClick = { if (checked) selectedAdd.remove(friend.redId) else selectedAdd[friend.redId] = friend }, shape = RoundedCornerShape(10.dp), color = if (checked) SovereignColors.Cyan.copy(alpha = 0.12f) else SovereignColors.Navy) {
                            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(friend.displayName, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 8.dp))
                                Checkbox(checked = checked, onCheckedChange = { if (it) selectedAdd[friend.redId] = friend else selectedAdd.remove(friend.redId) }, colors = CheckboxDefaults.colors(checkedColor = SovereignColors.Cyan))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton({
                    val ids = selectedAdd.values.map { it.redId }
                    groups.addMembers(group, ids) { added, total ->
                        val msg = if (added == total) "تمت إضافة $added من $total"
                        else "تمت إضافة $added من $total — تعذر إضافة الباقي"
                        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                    }
                    showAddMembers = false
                }) { Text("إضافة (${selectedAdd.size})", color = SovereignColors.Cyan) }
            },
            dismissButton = { TextButton({ showAddMembers = false }) { Text("إلغاء", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        )
    }

    if (showMuteOptions && group != null) {
        // LEGENDARY: كتم منفصل لـ @all (واتساب 8/2026: @all يرن حتى في المكتومة إلا عند كتمه)
        var muteAll by remember(group.id) {
            mutableStateOf(
                MuteAtAllPrefs.isMuted(ctx, group.id)
            )
        }
        AlertDialog(
            onDismissRequest = { showMuteOptions = false },
            containerColor = SovereignColors.SurfaceNavy,
            title = { Text("كتم إشعارات المجموعة", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("يبقى المحتوى متاحاً، لكن لن تصلك تنبيهات هذه المجموعة خلال المدة التي تختارها.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = muteAll, onCheckedChange = {
                            muteAll = it
                            MuteAtAllPrefs.setMuted(ctx, group.id, it)
                        }, colors = CheckboxDefaults.colors(checkedColor = SovereignColors.Cyan))
                        Text("كتم @all أيضاً (وإلا سيرن حتى في المكتومة)", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("ساعة" to 3_600_000L, "8 ساعات" to 28_800_000L, "أسبوع" to 604_800_000L).forEach { (label, duration) ->
                        TextButton(onClick = {
                            mutedUntil = System.currentTimeMillis() + duration
                            localMessages.setConversationPreference(group.id, "muted_until", mutedUntil)
                            showMuteOptions = false
                        }) { Text(label, color = SovereignColors.Cyan) }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    mutedUntil = 0L
                    localMessages.setConversationPreference(group.id, "muted_until", 0L)
                    showMuteOptions = false
                }) { Text("إلغاء الكتم", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        )
    }

    if (showDisappearingOptions && group != null) {
        AlertDialog(
            onDismissRequest = { showDisappearingOptions = false },
            containerColor = SovereignColors.SurfaceNavy,
            title = { Text("الرسائل المؤقتة", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = { Text("تُطبّق المدة المختارة على الرسائل الجديدة المرسلة من هذا الجهاز إلى المجموعة.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    listOf("ساعة" to 3_600_000L, "يوم" to 86_400_000L, "أسبوع" to 604_800_000L).forEach { (label, duration) ->
                        TextButton(onClick = {
                            disappearingDurationMs = duration
                            localMessages.setConversationDisappearingDuration(group.id, duration)
                            if (isManager) groups.updateDisappearing(group, duration)
                            showDisappearingOptions = false
                        }) { Text(label, color = SovereignColors.Cyan) }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    disappearingDurationMs = null
                    localMessages.setConversationDisappearingDuration(group.id, null)
                    if (isManager) groups.updateDisappearing(group, null)
                    showDisappearingOptions = false
                }) { Text("إيقاف", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        )
    }

    if (showReceiptsOptions && group != null) {
        AlertDialog(
            onDismissRequest = { showReceiptsOptions = false },
            containerColor = SovereignColors.SurfaceNavy,
            title = { Text("إيصالات القراءة", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = { Text("تجاوز الإعداد العام لهذه المجموعة فقط.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    listOf("عام" to 0, "تشغيل" to 1, "إيقاف" to 2).forEach { (label, mode) ->
                        TextButton(onClick = {
                            receiptsMode = mode
                            localMessages.setConversationReadReceipts(group.id, mode)
                            showReceiptsOptions = false
                        }) { Text(label, color = if (receiptsMode == mode) SovereignColors.Cyan else MaterialTheme.colorScheme.onSurface) }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showReceiptsOptions = false }) { Text("إغلاق", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        )
    }

    when (val safetyState = safety.state) {
        SafetyState.Closed -> Unit
        is SafetyState.Loading -> AlertDialog(
            onDismissRequest = safety::close,
            containerColor = SovereignColors.SurfaceNavy,
            title = { Text("رمز أمان مالك المجموعة", color = MaterialTheme.colorScheme.onSurface) },
            text = { Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = SovereignColors.Cyan) } },
            confirmButton = { TextButton(safety::close) { Text("إلغاء", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        )
        is SafetyState.Error -> AlertDialog(
            onDismissRequest = safety::close,
            containerColor = SovereignColors.SurfaceNavy,
            title = { Text("تعذر التحقق", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text(safetyState.message, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = { TextButton(safety::close) { Text("إغلاق", color = SovereignColors.Cyan) } }
        )
        is SafetyState.Ready -> AlertDialog(
            onDismissRequest = safety::close,
            containerColor = SovereignColors.SurfaceNavy,
            title = { Text(if (safetyState.verified) "هوية مالك المجموعة مؤكدة" else "قارن رمز أمان مالك المجموعة", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    androidx.compose.foundation.Image(safetyState.qr, "رمز QR لمالك المجموعة", Modifier.size(220.dp).clip(RoundedCornerShape(12.dp)))
                    Text(safetyState.number, color = SovereignColors.Gold, fontWeight = FontWeight.Bold)
                    Text("قارن الرقم عبر قناة موثوقة قبل اعتماد هوية المالك.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            },
            confirmButton = {
                if (safetyState.verified) TextButton(safety::close) { Text("تم", color = SovereignColors.Cyan) }
                else Button(onClick = safety::markVerified, colors = ButtonDefaults.buttonColors(containerColor = SovereignColors.Cyan)) { Text("الأرقام متطابقة", color = Color.Black) }
            },
            dismissButton = { if (!safetyState.verified) TextButton(safety::close) { Text("إلغاء", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        )
    }

    // ── P1-E: نافذة الوضع البطيء (واجهة slowModeSeconds + فحص محلي) ──
    if (showSlowMode && group != null) {
        AlertDialog(
            onDismissRequest = { showSlowMode = false },
            containerColor = SovereignColors.SurfaceNavy,
            title = { Text("الوضع البطيء", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "الفاصل بين رسائل العضو الواحد — يُفحص محلياً قبل الإرسال (آخر رسالة + X ثانية).",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    SLOW_MODE_OPTIONS.forEach { seconds ->
                        val selected = slowModeSeconds == seconds
                        Surface(
                            onClick = {
                                slowModeSeconds = seconds
                                setGroupSlowModeSeconds(ctx, group.id, seconds)
                                showSlowMode = false
                                val hint = if (seconds == 0) "تم إيقاف الوضع البطيء" else "الوضع البطيء: $seconds ث بين الرسائل"
                                Toast.makeText(ctx, "$hint (حُفظ محلياً)", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) SovereignColors.Cyan.copy(alpha = 0.15f) else Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    slowModeLabel(seconds),
                                    color = if (selected) SovereignColors.Cyan else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )
                                if (selected) Icon(Icons.Rounded.Check, null, tint = SovereignColors.Cyan, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton({ showSlowMode = false }) { Text("إغلاق", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        )
    }

    // ── P1-E: تأكيد إنهاء المجموعة (أرشفة + تعطيل الرابط) للمالك ──
    if (confirmEndGroup && group != null) {
        AlertDialog(
            onDismissRequest = { confirmEndGroup = false },
            containerColor = SovereignColors.SurfaceNavy,
            title = { Text("إنهاء المجموعة؟", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "تُؤرشف المجموعة محلياً وتُعطَّل روابط الدعوة النشطة عبر سحبها. تبقى الرسائل للمراجعة — بخلاف الحذف النهائي.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // أرشفة محلية فورية (offline-first) + سحب الدعوات النشطة.
                        localMessages.setConversationPreference(group.id, "archived", 1L)
                        isEndedLocally = true
                        groups.listInvites(group)
                        groups.invites.toList().forEach { inv -> groups.revokeInvite(group, inv.id) }
                        Toast.makeText(ctx, "تم إنهاء المجموعة: أرشفة + تعطيل الروابط", Toast.LENGTH_LONG).show()
                        confirmEndGroup = false
                    }
                ) { Text("إنهاء وأرشفة", color = SovereignColors.Gold) }
            },
            dismissButton = { TextButton({ confirmEndGroup = false }) { Text("إلغاء", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        )
    }

    // تأكيد المغادرة
    if (confirmLeave && group != null) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            containerColor = SovereignColors.SurfaceNavy,
            title = { Text("مغادرة المجموعة؟", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("لن تستقبل رسائل المجموعة بعد الآن.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = { TextButton({ groups.leave(group) { onBack() }; confirmLeave = false }) { Text("مغادرة", color = SovereignColors.Danger) } },
            dismissButton = { TextButton({ confirmLeave = false }) { Text("إلغاء", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        )
    }

    // تأكيد الحذف
    if (confirmDelete && group != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = SovereignColors.SurfaceNavy,
            title = { Text("حذف المجموعة نهائياً؟", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("سيُحذف للمجموعة لدى الجميع. لا يمكن التراجع.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = { TextButton({ groups.deleteGroup(group) { onBack() }; confirmDelete = false }) { Text("حذف", color = SovereignColors.Danger) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("إلغاء", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        )
    }
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(12.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = SovereignColors.Cyan, modifier = Modifier.size(22.dp))
            Column(Modifier.padding(start = 14.dp)) {
                Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun PermissionSwitch(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

private fun disappearingDurationLabel(durationMs: Long?): String = when (durationMs) {
    null, 0L -> "متوقفة — الرسائل الجديدة لا تنتهي تلقائياً"
    3_600_000L -> "الرسائل الجديدة تختفي بعد ساعة"
    86_400_000L -> "الرسائل الجديدة تختفي بعد يوم"
    604_800_000L -> "الرسائل الجديدة تختفي بعد أسبوع"
    else -> "الرسائل الجديدة تختفي حسب المدة المحددة"
}

private fun formatMuteUntil(until: Long): String {
    val remaining = (until - System.currentTimeMillis()).coerceAtLeast(0L)
    return when {
        remaining >= 86_400_000L -> "${(remaining + 86_399_999L) / 86_400_000L} أيام"
        remaining >= 3_600_000L -> "${(remaining + 3_599_999L) / 3_600_000L} ساعات"
        remaining > 0L -> "أقل من ساعة"
        else -> "انتهى الكتم"
    }
}

// ════════════════════════════════════════════════════
// P1-E: إدارة المجموعات — صلاحيات دقيقة + SlowMode + حذف أدمن + إنهاء
// ════════════════════════════════════════════════════

/** هل الدور مشرف (مالك/أدمن)؟ */
private fun isGroupManager(role: String?): Boolean = role == "OWNER" || role == "ADMIN"

/** من يرسل: إن كان onlyAdminsCanSend فالمشرفون فقط، وإلا كل الأعضاء. */
fun canGroupSend(role: String?, onlyAdminsCanSend: Boolean): Boolean {
    if (role.isNullOrBlank()) return false
    if (!onlyAdminsCanSend) return true
    return isGroupManager(role)
}

/** من يعدّل المعلومات: المشرفون، أو الأعضاء إن فُتحت لهم. */
fun canGroupEditInfo(role: String?, onlyAdminsCanEditInfo: Boolean): Boolean {
    if (role.isNullOrBlank()) return false
    if (!onlyAdminsCanEditInfo) return true
    return isGroupManager(role)
}

/** من يضيف أعضاء: المشرفون، أو الأعضاء إن فُتحت لهم. */
fun canGroupAddMembers(role: String?, onlyAdminsCanAddMembers: Boolean): Boolean {
    if (role.isNullOrBlank()) return false
    if (!onlyAdminsCanAddMembers) return true
    return isGroupManager(role)
}

/** من يثبت: المشرفون، أو الأعضاء إن فُتحت لهم. */
fun canGroupPin(role: String?, onlyAdminsCanPin: Boolean): Boolean {
    if (role.isNullOrBlank()) return false
    if (!onlyAdminsCanPin) return true
    return isGroupManager(role)
}

/** من يدعو برابط: المشرفون، أو الأعضاء إن فُتحت لهم. */
fun canGroupInvite(role: String?, onlyAdminsCanInvite: Boolean): Boolean {
    if (role.isNullOrBlank()) return false
    if (!onlyAdminsCanInvite) return true
    return isGroupManager(role)
}

/** من يبدأ مكالمة: المشرفون، أو الأعضاء إن فُتحت لهم. */
fun canGroupCall(role: String?, onlyAdminsCanCall: Boolean): Boolean {
    if (role.isNullOrBlank()) return false
    if (!onlyAdminsCanCall) return true
    return isGroupManager(role)
}

/** حذف الأدمن لرسائل الآخرين للجميع: مالك/أدمن فقط. */
fun canAdminDeleteForAll(role: String?): Boolean = isGroupManager(role)

/** نافذة حذف الأدمن: 24 ساعة من إنشاء الرسالة. */
fun isWithinAdminDeleteWindow(messageCreatedAtMillis: Long, nowMillis: Long = System.currentTimeMillis()): Boolean {
    if (messageCreatedAtMillis <= 0L) return false
    return nowMillis - messageCreatedAtMillis < 24L * 60L * 60L * 1000L
}

/** المتبقي في نافذة 24h بالساعات (0 عند الانتهاء). */
fun adminDeleteWindowRemainingHours(messageCreatedAtMillis: Long, nowMillis: Long = System.currentTimeMillis()): Long {
    val remaining = 24L * 60L * 60L * 1000L - (nowMillis - messageCreatedAtMillis)
    return if (remaining <= 0L) 0L else (remaining + 3_599_999L) / 3_600_000L
}

/** خيارات الوضع البطيء بالثواني (0 = معطّل). */
val SLOW_MODE_OPTIONS: List<Int> = listOf(0, 5, 10, 30, 60, 300, 900, 3600)

fun slowModeLabel(seconds: Int): String = when (seconds) {
    0 -> "معطّل"
    in 1..59 -> "كل $seconds ث"
    60 -> "كل دقيقة"
    300 -> "كل 5 دقائق"
    900 -> "كل 15 دقيقة"
    3600 -> "كل ساعة"
    else -> "كل $seconds ث"
}

/**
 * فحص SlowMode محلي قبل الإرسال: آخر رسالة + X ثانية.
 * lastSentMillis = 0 تعني لا رسالة سابقة (مسموح).
 */
fun checkSlowModeAllowsSend(lastSentMillis: Long, slowModeSeconds: Int, nowMillis: Long = System.currentTimeMillis()): Boolean {
    if (slowModeSeconds <= 0) return true
    if (lastSentMillis <= 0L) return true
    return nowMillis - lastSentMillis >= slowModeSeconds * 1000L
}

/** الثواني المتبقية قبل السماح بالإرسال (0 = مسموح الآن). */
fun slowModeRemainingSeconds(lastSentMillis: Long, slowModeSeconds: Int, nowMillis: Long = System.currentTimeMillis()): Long {
    if (slowModeSeconds <= 0 || lastSentMillis <= 0L) return 0L
    val remaining = slowModeSeconds * 1000L - (nowMillis - lastSentMillis)
    return if (remaining <= 0L) 0L else (remaining + 999L) / 1000L
}

private const val GROUP_ADMIN_PREFS = "sovereign_group_admin"

fun getGroupSlowModeSeconds(context: Context, groupId: String): Int {
    if (groupId.isBlank()) return 0
    return runCatching {
        context.getSharedPreferences(GROUP_ADMIN_PREFS, Context.MODE_PRIVATE)
            .getInt("slow_$groupId", 0)
    }.getOrDefault(0).coerceIn(0, 3600)
}

fun setGroupSlowModeSeconds(context: Context, groupId: String, seconds: Int) {
    if (groupId.isBlank()) return
    runCatching {
        context.getSharedPreferences(GROUP_ADMIN_PREFS, Context.MODE_PRIVATE)
            .edit().putInt("slow_$groupId", seconds.coerceIn(0, 3600)).apply()
    }
}

fun getGroupLastSentMillis(context: Context, groupId: String): Long {
    if (groupId.isBlank()) return 0L
    return runCatching {
        context.getSharedPreferences(GROUP_ADMIN_PREFS, Context.MODE_PRIVATE)
            .getLong("last_$groupId", 0L)
    }.getOrDefault(0L).coerceAtLeast(0L)
}

/** يُستدعى بعد كل إرسال ناجح في المجموعة لبدء عدّاد SlowMode. */
fun recordGroupSentNow(context: Context, groupId: String, nowMillis: Long = System.currentTimeMillis()) {
    if (groupId.isBlank()) return
    runCatching {
        context.getSharedPreferences(GROUP_ADMIN_PREFS, Context.MODE_PRIVATE)
            .edit().putLong("last_$groupId", nowMillis).apply()
    }
}

/**
 * بطاقة مصفوفة الصلاحيات الدقيقة: من يرسل/يعدل/يضيف/يثبت (+ الدعوات/المكالمات)
 * من الحقول السبعة الموجودة + عرض SlowMode. للقراءة والعرض فقط — الفرض
 * النهائي على الخادم، والفحص المحلي للإرسال عبر [checkSlowModeAllowsSend].
 */
@Composable
fun GroupPermissionMatrix(
    settings: com.red.sovereign.groups.GroupSettings,
    myRole: String?,
    slowModeSeconds: Int = 0
) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("من يفعل ماذا؟", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            PermissionLine("الإرسال", if (settings.onlyAdminsCanSend) "المشرفون فقط" else "كل الأعضاء", canGroupSend(myRole, settings.onlyAdminsCanSend))
            PermissionLine("تعديل المعلومات", if (settings.onlyAdminsCanEditInfo) "المشرفون فقط" else "كل الأعضاء", canGroupEditInfo(myRole, settings.onlyAdminsCanEditInfo))
            PermissionLine("إضافة الأعضاء", if (settings.onlyAdminsCanAddMembers) "المشرفون فقط" else "كل الأعضاء", canGroupAddMembers(myRole, settings.onlyAdminsCanAddMembers))
            PermissionLine("التثبيت", if (settings.onlyAdminsCanPin) "المشرفون فقط" else "كل الأعضاء", canGroupPin(myRole, settings.onlyAdminsCanPin))
            PermissionLine("الدعوات", if (settings.onlyAdminsCanInvite) "المشرفون فقط" else "كل الأعضاء", canGroupInvite(myRole, settings.onlyAdminsCanInvite))
            PermissionLine("المكالمات", if (settings.onlyAdminsCanCall) "المشرفون فقط" else "كل الأعضاء", canGroupCall(myRole, settings.onlyAdminsCanCall))
            PermissionLine("حذف رسائل الآخرين", "المشرفون (خلال 24h + رسالة نظام)", canAdminDeleteForAll(myRole))
            Text(
                "الوضع البطيء: ${slowModeLabel(slowModeSeconds)}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun PermissionLine(action: String, who: String, allowedForMe: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (allowedForMe) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
            null,
            tint = if (allowedForMe) SovereignColors.Success else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Text(
            " $action: $who",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}
