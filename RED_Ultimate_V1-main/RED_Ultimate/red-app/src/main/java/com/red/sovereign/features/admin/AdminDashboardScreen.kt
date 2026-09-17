package com.red.sovereign.features.admin

import androidx.compose.material3.MaterialTheme
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
    val pendingError by viewModel.pendingError.collectAsState()
    val usersError by viewModel.usersError.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var pendingQuery by remember { mutableStateOf("") }

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
            Text("لوحة التحكم السيادية", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { viewModel.refreshDashboard() }) {
                Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = Color.White)
            }
        }

        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = AqyalGold)
        }

        actionMessage?.let { msg ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(msg, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.clearActionMessage() }) { Text("إخفاء") }
                }
            }
        }

        // بحث في المستخدمين (خادم) والمعلقين (محلي)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                viewModel.setSearchQuery(it)
                pendingQuery = it
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("بحث بالاسم أو المعرف…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "بحث") },
            singleLine = true
        )

        val visiblePending = remember(pendingUsers, pendingQuery) {
            val q = pendingQuery.trim()
            if (q.isEmpty()) pendingUsers
            else pendingUsers.filter {
                it.username.contains(q, ignoreCase = true) ||
                    it.displayName.contains(q, ignoreCase = true) ||
                    it.redId.contains(q, ignoreCase = true)
            }
        }

        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = { viewModel.refreshDashboard() },
            modifier = Modifier.fillMaxSize()
        ) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Stats Section
            item {
                Text("إحصائيات النظام 📊", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("المستخدمون", "${stats.usersCount}", Icons.Default.Group, Modifier.weight(1f))
                    StatCard("متصل الآن", "${stats.onlineCount}", Icons.Default.Wifi, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("معلّق", "${stats.pendingCount}", Icons.Default.HourglassEmpty, Modifier.weight(1f))
                    StatCard("المكالمات النشطة", "${stats.activeCalls}", Icons.Default.Call, Modifier.weight(1f))
                }
            }

            // Pending Users
            item {
                Divider(color = Color(0xFF333333), modifier = Modifier.padding(vertical = 12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("حسابات قيد الانتظار ⏳", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Badge(containerColor = AqyalGold) { Text("${visiblePending.size}", color = Color.Black) }
                }
                Spacer(Modifier.height(8.dp))
                // تمييز الخطأ عن القائمة الفارغة: خطأ = بطاقة حمراء + إعادة، فارغ = نص محايد.
                if (pendingError != null) {
                    ErrorCard(pendingError ?: "", onRetry = { viewModel.refreshDashboard() })
                } else if (visiblePending.isEmpty()) {
                    Text(
                        if (pendingQuery.isBlank()) "لا توجد حسابات معلقة."
                        else "لا نتائج مطابقة لبحث المعلقين.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(visiblePending, key = { "pending_${it.id}" }) { user ->
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
                    Badge(containerColor = AqyalGold) { Text("${users.size}", color = Color.Black) }
                }
                Spacer(Modifier.height(8.dp))
                if (usersError != null) {
                    ErrorCard(usersError ?: "", onRetry = { viewModel.refreshDashboard() })
                } else if (users.isEmpty()) {
                    Text(
                        if (searchQuery.isBlank()) "لا يوجد مستخدمون حالياً."
                        else "لا نتائج مطابقة لبحثك.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(users, key = { "user_${it.id}" }) { user ->
                UserOverviewCard(
                    user = user,
                    onDelete = { viewModel.deleteUser(user.id) },
                    onSuspend = { reason -> viewModel.suspendUser(user.id, reason) },
                    onBan = { reason -> viewModel.banUser(user.id, reason) },
                    onUnban = { viewModel.unbanUser(user.id) },
                    onSetRole = { role -> viewModel.setUserRole(user.id, role) }
                )
            }
        }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ErrorOutline, contentDescription = "خطأ", tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(8.dp))
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.sp, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, contentDescription = "إعادة المحاولة", modifier = Modifier.size(16.dp))
                Text(" إعادة")
            }
        }
    }
}

@Composable
private fun UserOverviewCard(
    user: UserOverview,
    onDelete: () -> Unit,
    onSuspend: (String?) -> Unit,
    onBan: (String?) -> Unit,
    onUnban: () -> Unit,
    onSetRole: (String) -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var reasonTarget by remember { mutableStateOf<String?>(null) } // "SUSPEND" أو "BAN"
    var reason by remember { mutableStateOf("") }
    var showRoleConfirm by remember { mutableStateOf(false) }
    val nextRole = if (user.role == "ADMIN") "USER" else "ADMIN"

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

    if (reasonTarget != null) {
        val isSuspend = reasonTarget == "SUSPEND"
        AlertDialog(
            onDismissRequest = { reasonTarget = null; reason = "" },
            title = { Text(if (isSuspend) "تعليق حساب" else "حظر حساب") },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(300) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("السبب (اختياري)") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val r = reason.takeIf { it.isNotBlank() }
                    reasonTarget = null; reason = ""
                    if (isSuspend) onSuspend(r) else onBan(r)
                }) {
                    Text(if (isSuspend) "تعليق" else "حظر", color = Color(0xFFE53935))
                }
            },
            dismissButton = {
                TextButton(onClick = { reasonTarget = null; reason = "" }) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (showRoleConfirm) {
        AlertDialog(
            onDismissRequest = { showRoleConfirm = false },
            title = { Text("تغيير الدور") },
            text = { Text("تغيير دور ${user.displayName} إلى $nextRole؟") },
            confirmButton = {
                TextButton(onClick = { showRoleConfirm = false; onSetRole(nextRole) }) {
                    Text("تأكيد")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRoleConfirm = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(if (user.isOnline) Color(0xFF00C98C) else Color.Gray))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(user.displayName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("@${user.username} · ${user.redId}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text(
                        "آخر ظهور: ${if (user.isOnline) "الآن" else if (user.lastSeenAt > 0) formatDate(user.lastSeenAt) else "غير متاح"} · ${user.status} · ${user.role}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp
                    )
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "حذف المستخدم", tint = Color(0xFFE53935))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (user.status == "BANNED" || user.status == "SUSPENDED") {
                    OutlinedButton(onClick = onUnban, modifier = Modifier.weight(1f)) { Text("فك الحظر") }
                } else {
                    OutlinedButton(onClick = { reasonTarget = "SUSPEND" }, modifier = Modifier.weight(1f)) { Text("تعليق") }
                    OutlinedButton(onClick = { reasonTarget = "BAN" }, modifier = Modifier.weight(1f)) { Text("حظر") }
                }
                OutlinedButton(onClick = { showRoleConfirm = true }, modifier = Modifier.weight(1f)) { Text(nextRole) }
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
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PendingUserCard(user: PendingUser, onApprove: () -> Unit) {
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
            Column {
                Text(user.phoneNumber, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(formatDate(user.registeredAt), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            Button(
                onClick = onApprove,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00C98C))
            ) {
                Text("توثيق (Approve)")
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
