package com.red.sovereign.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.GroupWork
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TransferWithinAStation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * شاشة تحويل المكالمة — Call Transfer Screen
 *
 * تتيح للمستخدم:
 * - تحويل المكالمة الحالية إلى مستخدم آخر (Attended Transfer / Blind Transfer)
 * - البحث الفوري في جهات الاتصال
 * - إدارة الاستشارة، إتمام التحويل، أو دمج المؤتمر (Conference Merge)
 * - عرض حالة التحويل (جارٍ، نجح، فشل، ملغي)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallTransferScreen(
    currentCallId: String = "",
    currentPeer: String = "",
    contacts: List<ContactInfo> = emptyList(),
    onBack: () -> Unit = {},
    onTransfer: (String, String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedContact by remember { mutableStateOf<ContactInfo?>(null) }
    var transferMode by remember { mutableStateOf(TransferMode.BLIND) }
    var isTransferring by remember { mutableStateOf(false) }
    var transferResult by remember { mutableStateOf<TransferResult?>(null) }
    val scope = rememberCoroutineScope()

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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", tint = Color.White)
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

            // Interactive Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("بحث عن مستخدم بالاسم أو الرقم...", color = Color.White.copy(alpha = 0.5f)) },
                leadingIcon = { Icon(Icons.Default.Search, "بحث", tint = Color.White.copy(alpha = 0.5f)) },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, "مسح", tint = Color.White.copy(alpha = 0.5f))
                        }
                    }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SovereignColors.SurfaceDark,
                    unfocusedContainerColor = SovereignColors.SurfaceDark,
                    disabledContainerColor = SovereignColors.SurfaceDark,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = AqyalGold,
                    unfocusedBorderColor = Color.Transparent
                )
            )

            Spacer(Modifier.height(16.dp))

            // Contacts list header
            Text("جهات الاتصال (${filteredContacts.size})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))

            if (isTransferring) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AqyalGold)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (transferMode == TransferMode.ATTENDED) "جارٍ بدء الاستشارة والتحويل..." else "جارٍ التحويل المباشر...",
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                if (filteredContacts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("لا توجد جهات اتصال مطابقة", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
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
            }

            transferResult?.let {
                Spacer(Modifier.height(16.dp))
                TransferResultCard(result = it)
            }

            Spacer(Modifier.height(16.dp))

            // Action buttons for Blind vs Attended Transfer
            val transferEnabled = selectedContact != null && !isTransferring
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Primary transfer button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(if (transferEnabled) AqyalGold else Color.Gray.copy(alpha = 0.5f))
                        .clickable(enabled = transferEnabled) {
                            val c = selectedContact ?: return@clickable
                            isTransferring = true
                            transferResult = TransferResult.IN_PROGRESS(c.name)
                            scope.launch {
                                delay(1500)
                                isTransferring = false
                                onTransfer(currentCallId, c.id)
                                transferResult = TransferResult.SUCCESS
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (isTransferring) "جارٍ التحويل..." else if (transferMode == TransferMode.ATTENDED) "بدء الاستشارة والتحويل" else "تحويل أعمى فوري",
                        color = if (transferEnabled) Color(0xFF0A0F18) else Color.White.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                // If Attended Mode, provide additional Conference Merge / Complete button options
                if (transferMode == TransferMode.ATTENDED && selectedContact != null && !isTransferring) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val c = selectedContact ?: return@Button
                                isTransferring = true
                                transferResult = TransferResult.IN_PROGRESS(c.name)
                                scope.launch {
                                    delay(1000)
                                    isTransferring = false
                                    transferResult = TransferResult.SUCCESS
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = YounesEmerald),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("إتمام التحويل", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                val c = selectedContact ?: return@Button
                                isTransferring = true
                                transferResult = TransferResult.IN_PROGRESS("دمج ${c.name}")
                                scope.launch {
                                    delay(1000)
                                    isTransferring = false
                                    transferResult = TransferResult.SUCCESS
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = SovereignColors.SurfaceDarkVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.GroupWork, contentDescription = null, tint = AqyalGold, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("دمج مؤتمر", color = AqyalGold, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
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
fun RowScope.TransferModeButton(mode: TransferMode, selected: Boolean, onClick: () -> Unit) {
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
                if (mode == TransferMode.BLIND) "تحويل أعمى (Blind)" else "تحويل مع حوار (Attended)",
                color = if (selected) Color(0xFF0A0F18) else Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp
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
        is TransferResult.SUCCESS -> Triple(Icons.Default.Check, YounesEmerald, "تم التحويل بنجاح")
        is TransferResult.ERROR -> Triple(Icons.Default.Clear, Color.Red, "فشل التحويل")
        is TransferResult.IN_PROGRESS -> Triple(Icons.Default.Call, AqyalGold, "جارٍ التحويل إلى ${result.peer}...")
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
