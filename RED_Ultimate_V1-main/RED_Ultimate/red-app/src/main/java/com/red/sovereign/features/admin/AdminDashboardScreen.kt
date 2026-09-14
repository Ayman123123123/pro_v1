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

@Composable
fun AdminDashboardScreen(viewModel: AdminViewModel, onBack: () -> Unit) {
    val stats by viewModel.systemStats.collectAsState()
    val pendingUsers by viewModel.pendingUsers.collectAsState()
    val users by viewModel.users.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

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
        }

        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = AqyalGold)
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                    Badge(containerColor = AqyalGold) { Text("${pendingUsers.size}", color = Color.Black) }
                }
                Spacer(Modifier.height(8.dp))
                if (pendingUsers.isEmpty()) {
                    Text("لا توجد حسابات معلقة.", color = Color.Gray)
                }
            }

            items(pendingUsers, key = { "pending_${it.id}" }) { user ->
                PendingUserCard(user) {
                    viewModel.approveUser(user.id)
                }
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
                if (users.isEmpty()) {
                    Text("لا يوجد مستخدمون حالياً.", color = Color.Gray)
                }
            }

            items(users, key = { "user_${it.id}" }) { user ->
                UserOverviewCard(user) {
                    viewModel.deleteUser(user.id)
                }
            }
        }
    }
}

@Composable
private fun UserOverviewCard(user: UserOverview, onDelete: () -> Unit) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(if (user.isOnline) Color(0xFF00C98C) else Color.Gray))
                    Spacer(Modifier.width(8.dp))
                    Text(user.displayName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(4.dp))
                Text(user.phoneNumber, color = Color.Gray, fontSize = 12.sp)
                Text("آخر ظهور: ${if (user.isOnline) "الآن" else formatDate(user.lastSeenAt)}", color = Color.Gray, fontSize = 10.sp)
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
                Text(formatDate(user.registeredAt), color = Color.Gray, fontSize = 12.sp)
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
