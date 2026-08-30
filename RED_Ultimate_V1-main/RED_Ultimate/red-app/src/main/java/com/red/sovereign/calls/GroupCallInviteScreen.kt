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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Copy
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Partition
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.SovereignColors
import com.red.sovereign.ui.theme.YounesEmerald

/**
 * شاشة دعوة المكالمة الجماعية — Group Call Invite Screen
 *
 * تتيح للمضيف:
 * - اختيار المشاركين من قائمة جهات الاتصال
 * - نسخ رابط الدعوة
 * - مشاركة الرابط عبر تطبيقات خارجية
 * - إنشاء كود QR للدعوة
 * - إدارة الغرف الفرعية (Breakout Rooms)
 */
@Composable
fun GroupCallInviteScreen(
    groupCallId: String = "",
    groupName: String = "",
    onBack: () -> Unit = {},
    onInvite: (List<String>) -> Unit = {},
    onCopyLink: () -> Unit = {},
    onShareLink: () -> Unit = {},
    onCreateBreakoutRoom: (String, List<String>) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedParticipants by remember { mutableStateOf<MutableSet<String>>(mutableSetOf()) }
    var showInviteLink by remember { mutableStateOf(false) }
    var inviteLinkCopied by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }

    // Mock contacts — في التطبيق الفعلي يتم جلبها من قاعدة البيانات
    val contacts = remember {
        listOf(
            ContactInfo("user-001", "أحمد محمد", "+9677XXXXXXXX", true),
            ContactInfo("user-002", "محمد علي", "+9677XXXXXXXX", true),
            ContactInfo("user-003", "فاطمة أحمد", "+9677XXXXXXXX", false),
            ContactInfo("user-004", "خالد عبدالله", "+9677XXXXXXXX", true),
            ContactInfo("user-005", "نورة سعيد", "+9677XXXXXXXX", false),
            ContactInfo("user-006", "عمر حسن", "+9677XXXXXXXX", true),
            ContactInfo("user-007", "ليلى أحمد", "+9677XXXXXXXX", false),
            ContactInfo("user-008", "يوسف محمد", "+9677XXXXXXXX", true),
        )
    }

    val filteredContacts = contacts.filter { c ->
        c.name.contains(searchQuery, ignoreCase = true) ||
        c.phone.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("دعوة المكالمة", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SovereignColors.SurfaceDark
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "رجوع", tint = Color.White)
                    }
                },
                actions = {
                    if (selectedParticipants.isNotEmpty()) {
                        IconButton(onClick = {
                            val selected = contacts.filter { selectedParticipants.contains(it.id) }
                            onInvite(selected.map { it.id })
                        }) {
                            Icon(Icons.Default.Send, "إرسال الدعوات", tint = AqyalGold)
                        }
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

            // Group info
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = AqyalGold.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text("معلومات المجموعة", color = AqyalGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("اسم المجموعة: $groupName", color = Color.White, fontSize = 13.sp)
                    Text("معرف المكالمة: $groupCallId", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Invite link section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceDarkVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text("رابط الدعوة", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SovereignColors.SurfaceDark),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                "https://red.app/join/$groupCallId",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                onCopyLink()
                                inviteLinkCopied = true
                                kotlinx.coroutines.runBlocking {
                                    kotlinx.coroutines.delay(2000)
                                    inviteLinkCopied = false
                                }
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                if (inviteLinkCopied) Icons.Default.Check else Icons.Default.Copy,
                                "نسخ",
                                tint = if (inviteLinkCopied) YounesEmerald else AqyalGold
                            )
                        }
                        IconButton(
                            onClick = onShareLink,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Share, "مشاركة", tint = AqyalGold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Search and select
            Text("اختيار المشاركين", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))

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
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("بحث عن مشارك...", color = Color.White.copy(alpha = 0.5f)) },
                    colors = androidx.compose.material3.TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        disabledTextColor = Color.White.copy(alpha = 0.5f),
                        focusedPlaceholderColor = Color.White.copy(alpha = 0.5f),
                        unfocusedPlaceholderColor = Color.White.copy(alpha = 0.5f),
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = AqyalGold
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(onSearch = { /* perform search */ }),
                    singleLine = true
                )
            }

            Spacer(Modifier.height(12.dp))

            // Selected count
            if (selectedParticipants.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "تم اختيار ${selectedParticipants.size} مشارك",
                        color = AqyalGold,
                        fontSize = 12.sp
                    )
                    IconButton(onClick = { selectedParticipants.clear() }) {
                        Icon(Icons.Default.Clear, "مسح الاختيار", tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Contacts list
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredContacts) { contact ->
                    ContactSelectItem(
                        contact = contact,
                        isSelected = selectedParticipants.contains(contact.id),
                        onClick = {
                            if (selectedParticipants.contains(contact.id)) {
                                selectedParticipants.remove(contact.id)
                            } else {
                                selectedParticipants.add(contact.id)
                            }
                        }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
fun ContactSelectItem(contact: ContactInfo, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) AqyalGold.copy(alpha = 0.15f) else SovereignColors.SurfaceDarkVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
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
                    fontSize = 14.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(contact.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(contact.phone, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }
            if (isSelected) {
                Icon(Icons.Default.Check, "محدد", tint = AqyalGold, modifier = Modifier.size(24.dp))
            } else {
                Icon(Icons.Default.PersonAdd, "إضافة", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(24.dp))
            }
        }
    }
}
