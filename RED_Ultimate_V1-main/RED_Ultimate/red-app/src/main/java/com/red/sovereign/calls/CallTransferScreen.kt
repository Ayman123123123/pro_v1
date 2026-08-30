package com.red.sovereign.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TransferWithinAStation
import androidx.compose.material.icons.filled.User
import androidx.compose.material.icons.filled.UserAdd
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.SovereignColors
import com.red.sovereign.ui.theme.YounesEmerald

/**
 * شاشة تحويل المكالمة — Call Transfer Screen
 *
 * تتيح للمستخدم:
 * - تحويل المكالمة الحالية إلى مستخدم آخر (Attended Transfer)
 * - تحويل دون حوار ( Blind Transfer)
 * - عرض قائمة جهات الاتصال للاختيار منها
 * - عرض حالة التحويل (جارٍ، نجح، فشل)
 */
@Composable
fun CallTransferScreen(
    currentCallId: String = "",
    currentPeer: String = "",
    onBack: () -> Unit = {},
    onTransfer: (String, String) -> Unit = { _, _ -> }
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedContact by remember { mutableStateOf<ContactInfo?>(null) }
    var transferMode by remember { mutableStateOf(TransferMode.BLIND) }
    var isTransferring by remember { mutableStateOf(false) }
    var transferResult by remember { mutableStateOf<TransferResult?>(null) }

    // Mock contacts — في التطبيق الفعلي يتم جلبها من قاعدة البيانات
    val contacts = remember {
        listOf(
            ContactInfo("user-001", "أحمد محمد", "+9677XXXXXXXX", true),
            ContactInfo("user-002", "محمد علي", "+9677XXXXXXXX", true),
            ContactInfo("user-003", "فاطمة أحمد", "+9677XXXXXXXX", false),
            ContactInfo("user-004", "خالد عبدالله", "+9677XXXXXXXX", true),
            ContactInfo("user-005", "نورة سعيد", "+9677XXXXXXXX", false),
        )
    }

    val filteredContacts = contacts.filter { c ->
        c.name.contains(searchQuery, ignoreCase = true) ||
        c.phone.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تحويل المكالمة", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SovereignColors.SurfaceDark
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "رجوع", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Clear, "إلغاء", tint = Color.Red)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            // Current call info
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceDarkVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Call, "مكالمة حالية", tint = YounesEmerald, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("المكالمة الحالية", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                        Text(currentPeer, color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Transfer mode selector
            Text("نمط التحويل", color = AqyalGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TransferModeButton(
                    mode = TransferMode.BLIND,
                    selected = transferMode == TransferMode.BLIND,
                    onClick = { transferMode = TransferMode.BLIND }
                )
                TransferModeButton(
                    mode = TransferMode.ATTENDED,
                    selected = transferMode == TransferMode.ATTENDED,
                    onClick = { transferMode = TransferMode.ATTENDED }
                )
            }

            Spacer(Modifier.height(16.dp))

            // Search bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(SovereignColors.SurfaceDark),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = { /* search */ }) {
                    Icon(Icons.Default.Search, "بحث", tint = Color.White.copy(alpha = 0.5f))
                }
                Text(
                    "بحث عن مستخدم...",
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(16.dp))

            // Contacts list
            Text("جهات الاتصال", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))

            if (isTransferring) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AqyalGold)
                        Spacer(Modifier.height(8.dp))
                        Text("جارٍ التحويل...", color = Color.White.copy(alpha = 0.7f))
                    }
                }
            } else {
                filteredContacts.forEach { contact ->
                    ContactItem(
                        contact = contact,
                        isSelected = selectedContact?.id == contact.id,
                        onClick = {
                            selectedContact = contact
                            transferResult = null
                        }
                    )
                }
            }

            if (transferResult != null) {
                Spacer(Modifier.height(16.dp))
                TransferResultCard(result = transferResult!!)
            }

            Spacer(Modifier.height(16.dp))

            // Transfer button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(if (selectedContact != null && !isTransferring) AqyalGold else Color.Gray.copy(alpha = 0.5f))
                    .clickable(enabled = selectedContact != null && !isTransferring) {
                        isTransferring = true
                        // Simulate transfer
                        kotlinx.coroutines.runBlocking {
                            kotlinx.coroutines.delay(1500)
                        }
                        isTransferring = false
                        transferResult = if (selectedContact != null) {
                            onTransfer(currentCallId, selectedContact!!.id)
                            TransferResult.SUCCESS
                        } else {
                            TransferResult.ERROR
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (isTransferring) "جارٍ التحويل..." else "تحويل المكالمة",
                    color = if (selectedContact != null && !isTransferring) Color(0xFF0A0F18) else Color.White.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

enum class TransferMode { BLIND, ATTENDED }

data class ContactInfo(
    val id: String,
    val name: String,
    val phone: String,
    val isActive: Boolean
)

sealed class TransferResult {
    data object SUCCESS : TransferResult()
    data object ERROR : TransferResult()
    data class IN_PROGRESS(val peer: String) : TransferResult()
}

@Composable
fun TransferModeButton(mode: TransferMode, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) AqyalGold else SovereignColors.SurfaceDarkVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (mode == TransferMode.BLIND) "تحويل أعمى" else "تحويل مع حوار",
                color = if (selected) Color(0xFF0A0F18) else Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun ContactItem(contact: ContactInfo, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) AqyalGold.copy(alpha = 0.2f) else SovereignColors.SurfaceDarkVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (contact.isActive) YounesEmerald else Color.Gray.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        contact.name.firstOrNull()?.toString() ?: "?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                Column {
                    Text(contact.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(contact.phone, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            }
            if (isSelected) {
                Icon(Icons.Default.Check, "محدد", tint = AqyalGold)
            }
        }
    }
}

@Composable
fun TransferResultCard(result: TransferResult) {
    val (icon, color, text) = when (result) {
        is TransferResult.SUCCESS -> Icons.Default.Check to YounesEmerald to "تم التحويل بنجاح"
        is TransferResult.ERROR -> Icons.Default.Clear to Color.Red to "فشل التحويل"
        is TransferResult.IN_PROGRESS -> Icons.Default.Call to AqyalGold to "جارٍ التحويل إلى ${result.peer}..."
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(icon, text, tint = color, modifier = Modifier.size(24.dp))
            Text(text, color = color, fontWeight = FontWeight.SemiBold)
        }
    }
}
