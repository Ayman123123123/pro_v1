package com.red.sovereign.features.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import com.red.sovereign.R
import androidx.compose.ui.res.stringResource
import com.red.sovereign.ui.theme.AqyalGold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(viewModel: AdminViewModel, onBack: () -> Unit) {
    val stats by viewModel.systemStats.collectAsState()
    val pendingUsers by viewModel.pendingUsers.collectAsState()
    val users by viewModel.users.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()
    var search by remember { mutableStateOf("") }

    val q = search.trim()
    val filteredPending = remember(pendingUsers, q) {
        if (q.length < 2) pendingUsers
        else pendingUsers.filter {
            it.phoneNumber.contains(q, true) || it.username.contains(q, true) ||
                it.displayName.contains(q, true) || it.redId.contains(q, true)
        }
    }
    val filteredUsers = remember(users, q) {
        if (q.length < 2) users
        else users.filter {
            it.displayName.contains(q, true) || it.phoneNumber.contains(q, true) ||
                it.username.contains(q, true) || it.redId.contains(q, true) ||
                it.status.contains(q, true)
        }
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF0F172A))) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.admin_back), tint = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.Security, contentDescription = null, tint = AqyalGold, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(8.dp))
            Text("لوحة التحكم السيادية", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { viewModel.refreshDashboard(q.trim().takeIf { it.length >= 2 } ?: "") }) {
                Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = AqyalGold)
            }
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = it.take(40) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            singleLine = true,
            label = { Text("بحث: اسم / username / معرّف / حالة") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSearch = { viewModel.refreshDashboard(search.trim().takeIf { it.length >= 2 }) }
            ),
            trailingIcon = {
                if (search.isNotEmpty()) TextButton({
                    search = ""
                    viewModel.refreshDashboard("")
                }) { Text("مسح") }
            }
        )
        Spacer(Modifier.height(8.dp))

        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = AqyalGold)
        }

        actionMessage?.let { msg ->
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0B3D2E))
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(msg, color = Color.White, modifier = Modifier.weight(1f), fontSize = 13.sp)
                    TextButton({ viewModel.clearActionMessage() }) { Text("إخفاء", color = AqyalGold) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // تمييز الخطأ عن الفراغ: بطاقة حمراء + retry عند الفشل، ونص رمادي عند الفراغ الحقيقي.
        if (error != null) {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF4A0E0E))
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ErrorOutline, null, tint = Color(0xFFFF8A80))
                    Spacer(Modifier.width(8.dp))
                    Text(error ?: "", color = Color.White, modifier = Modifier.weight(1f), fontSize = 13.sp)
                    TextButton({ viewModel.clearError(); viewModel.refreshDashboard(q.ifBlank { null }) }) {
                        Text("إعادة المحاولة", color = AqyalGold)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = { viewModel.refreshDashboard(q.ifBlank { null }) },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Stats Section
                item {
                    Text("إحصائيات النظام 📊", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard("المستخدمين", "${stats.usersCount}", Icons.Default.Group, Modifier.weight(1f))
                        StatCard("المكالمات", "${stats.activeCalls}", Icons.Default.Call, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard("البثوث المباشرة", "${stats.activeStreams}", Icons.Default.LiveTv, Modifier.weight(1f))
                    }
                }

                // Pending Users
                item {
                    Divider(color = Color(0xFF333333), modifier = Modifier.padding(vertical = 12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("حسابات قيد الانتظار ⏳", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Badge(containerColor = AqyalGold) { Text("${filteredPending.size}", color = Color.Black) }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (error == null && filteredPending.isEmpty()) {
                        Text(
                            if (q.length >= 2) "لا نتائج معلقة مطابقة للبحث." else "لا توجد حسابات معلقة.",
                            color = Color.Gray
                        )
                    }
                }

                items(filteredPending, key = { "pending_${it.id}" }) { user ->
                    PendingUserCard(
                        user = user,
                        onApprove = { viewModel.approveUser(user.id) },
                        onReject = { reason -> viewModel.rejectUser(user.id, reason) }
                    )
                }

                // All Users & Real-Time Presence
                item {
                    Divider(color = Color(0xFF333333), modifier = Modifier.padding(vertical = 12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("المستخدمون وحالة الاتصال 🟢", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Badge(containerColor = AqyalGold) { Text("${filteredUsers.size}", color = Color.Black) }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (error == null && filteredUsers.isEmpty()) {
                        Text(
                            if (q.length >= 2) "لا نتائج مطابقة للبحث." else "لا يوجد مستخدمون حالياً.",
                            color = Color.Gray
                        )
                    }
                }

                items(filteredUsers, key = { "user_${it.id}" }) { user ->
                    UserOverviewCard(
                        user = user,
                        onDelete = { viewModel.deleteUser(user.id) },
                        onSuspend = { viewModel.suspendUser(user.id, "مخالفة سياسة") },
                        onBan = { viewModel.banUser(user.id, "مخالفة جسيمة") },
                        onUnban = { viewModel.unbanUser(user.id) },
                        onRole = { role -> viewModel.setUserRole(user.id, role) }
                    )
                }
            }
        }
    }
}

@Composable
private fun UserOverviewCard(
    user: UserOverview,
    onDelete: () -> Unit,
    onSuspend: () -> Unit = {},
    onBan: () -> Unit = {},
    onUnban: () -> Unit = {},
    onRole: (String) -> Unit = {}
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("حذف مستخدم") },
            text = { Text("هل أنت متأكد من حذف ${user.displayName}؟ سيتم مسح حساب المستخدم وكل بياناته من قواعد البيانات نهائياً.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text("حذف نهائي", color = Color(0xFFE53935))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (showActions) {
        AlertDialog(
            onDismissRequest = { showActions = false },
            title = { Text("إجراءات: ${user.displayName}") },
            text = { Text("الحالة: ${user.status.ifBlank { "—" }} · الدور: ${user.role.ifBlank { "—" }}") },
            confirmButton = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton({ showActions = false; onSuspend() }) { Text("تعليق") }
                    TextButton({ showActions = false; onBan() }) { Text("حظر", color = Color(0xFFE53935)) }
                    TextButton({ showActions = false; onUnban() }) { Text("فك الحظر") }
                    TextButton({ showActions = false; onRole(if (user.role == "ADMIN") "USER" else "ADMIN") }) {
                        Text(if (user.role == "ADMIN") "تخفيض لـ USER" else "ترقية لـ ADMIN")
                    }
                    TextButton({ showActions = false }) { Text("إغلاق") }
                }
            },
            dismissButton = {}
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(if (user.isOnline) Color(0xFF00C98C) else Color.Gray))
                    Spacer(Modifier.width(8.dp))
                    Text(user.displayName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Text(user.phoneNumber, color = Color.Gray, fontSize = 12.sp)
                if (user.status.isNotBlank() || user.role.isNotBlank()) {
                    Text("${user.status} · ${user.role}", color = AqyalGold, fontSize = 11.sp)
                }
                Text("آخر ظهور: ${if (user.isOnline) "الآن" else formatDate(user.lastSeenAt)}", color = Color.Gray, fontSize = 10.sp)
            }
            IconButton(onClick = { showActions = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "إجراءات", tint = Color.White)
            }
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(Icons.Default.Delete, contentDescription = "حذف المستخدم", tint = Color(0xFFE53935))
            }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(100.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = AqyalGold, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(4.dp))
            Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(title, color = Color.Gray, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PendingUserCard(user: PendingUser, onApprove: () -> Unit, onReject: (String) -> Unit) {
    var showReject by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    if (showReject) {
        AlertDialog(
            onDismissRequest = { showReject = false },
            title = { Text("رفض الحساب") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("رفض ${user.displayName.ifBlank { user.phoneNumber }} — السبب مطلوب ويُحفظ في rejectionReason.")
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it.take(300) },
                        singleLine = false,
                        label = { Text("سبب الرفض") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showReject = false; onReject(reason); reason = "" },
                    enabled = reason.trim().isNotEmpty()
                ) { Text("رفض بسبب", color = Color(0xFFE53935)) }
            },
            dismissButton = { TextButton({ showReject = false; reason = "" }) { Text("إلغاء") } }
        )
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        user.displayName.ifBlank { user.username.ifBlank { user.phoneNumber } },
                        color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold
                    )
                    Text(user.phoneNumber, color = Color.Gray, fontSize = 12.sp)
                    Text(formatDate(user.registeredAt), color = Color.Gray, fontSize = 12.sp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = onApprove,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C98C)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("توثيق (Approve)")
                }
                OutlinedButton(
                    onClick = { showReject = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("رفض بسبب", color = Color(0xFFE53935))
                }
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    if (timestamp <= 0) return "—"
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
