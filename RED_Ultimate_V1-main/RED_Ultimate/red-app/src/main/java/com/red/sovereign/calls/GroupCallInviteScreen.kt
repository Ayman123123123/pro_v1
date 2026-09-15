package com.red.sovereign.calls

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.red.sovereign.contacts.PublicRedProfile
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.SovereignColors
import com.red.sovereign.ui.theme.YounesEmerald
import kotlinx.coroutines.delay

/**
 * شاشة دعوة المكالمة الجماعية للأصدقاء (نمط IMO) — مدخل مستقل عن مكالمات دردشة المجموعات (Liquid Glass 2026).
 *
 * - جهات حقيقية من الدليل (متصل أولاً).
 * - حد 32 مشاركاً مع تنبيه عند التجاوز.
 * - رابط younes://groupcall/{id}.
 * - توليد وتخزين رمز QR فوري للدعوة عبر ZXing.
 * - نسخ/مشاركة عبر intent النظام مباشرة، بلا runBlocking على الخيط الرئيسي.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupCallInviteScreen(
    groupCallId: String = "",
    groupName: String = "",
    friends: List<PublicRedProfile> = emptyList(),
    onlineIds: Set<String> = emptySet(),
    maxMembers: Int = 32,
    onBack: () -> Unit = {},
    onInvite: (List<String>) -> Unit = {},
    onCreateBreakoutRoom: (String, List<String>) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedParticipants by remember { mutableStateOf<Set<String>>(emptySet()) }
    var inviteLinkCopied by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }

    LaunchedEffect(inviteLinkCopied) {
        if (inviteLinkCopied) {
            delay(2000)
            inviteLinkCopied = false
        }
    }

    val inviteLink = remember(groupCallId) { "younes://groupcall/$groupCallId" }

    val filteredFriends = remember(friends, searchQuery, onlineIds) {
        friends.filter { f ->
            searchQuery.isBlank() ||
                f.displayName.contains(searchQuery, ignoreCase = true) ||
                f.username.contains(searchQuery, ignoreCase = true) ||
                f.redId.contains(searchQuery, ignoreCase = true)
        }.sortedWith(compareByDescending<PublicRedProfile> { it.redId in onlineIds }.thenBy { it.displayName })
    }

    fun shareLink() {
        runCatching {
            context.startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "انضم لمكالمة يونس الجماعية: $inviteLink")
                },
                "مشاركة دعوة المكالمة"
            ))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("دعوة المكالمة الجماعية", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SovereignColors.SurfaceDark
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showQrDialog = true }) {
                        Icon(Icons.Default.QrCode, "رمز QR", tint = AqyalGold)
                    }
                    if (selectedParticipants.isNotEmpty()) {
                        IconButton(onClick = { onInvite(selectedParticipants.toList()) }) {
                            Icon(Icons.AutoMirrored.Filled.Send, "إرسال الدعوات (${selectedParticipants.size})", tint = AqyalGold)
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
                    Text("معلومات المكالمة", color = AqyalGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("اسم المجموعة: ${groupName.ifBlank { "مكالمة أصدقاء" }}", color = Color.White, fontSize = 13.sp)
                    Text("معرف المكالمة: $groupCallId", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                    Text("حتى $maxMembers مشاركاً", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
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
                                inviteLink,
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(inviteLink))
                                inviteLinkCopied = true
                                android.widget.Toast.makeText(context, "تم نسخ رابط الدعوة", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                if (inviteLinkCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                "نسخ",
                                tint = if (inviteLinkCopied) YounesEmerald else AqyalGold
                            )
                        }
                        IconButton(
                            onClick = { shareLink() },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Share, "مشاركة", tint = AqyalGold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Search and select
            Text("اختيار المشاركين (${selectedParticipants.size}/$maxMembers)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("بحث بالاسم أو المعرف...", color = Color.White.copy(alpha = 0.5f)) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White.copy(alpha = 0.5f)) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, "مسح", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = AqyalGold,
                    unfocusedBorderColor = Color.White.copy(0.2f),
                    cursorColor = AqyalGold
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(onSearch = {
                    focusManager.clearFocus()
                }),
                singleLine = true,
                shape = RoundedCornerShape(24.dp)
            )

            Spacer(Modifier.height(12.dp))

            if (filteredFriends.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(if (friends.isEmpty()) "لا توجد جهات اتصال بعد — أضف أصدقاء أولاً" else "لا نتائج مطابقة", color = Color.White.copy(alpha = 0.5f))
                }
            }

            // Friends list
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredFriends, key = { it.redId }) { friend ->
                    val isOnline = friend.redId in onlineIds
                    val isSelected = friend.redId in selectedParticipants
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = {
                                selectedParticipants = if (isSelected) {
                                    selectedParticipants - friend.redId
                                } else if (selectedParticipants.size >= maxMembers) {
                                    android.widget.Toast.makeText(context, "الحد الأقصى $maxMembers مشاركاً", android.widget.Toast.LENGTH_SHORT).show()
                                    selectedParticipants
                                } else {
                                    selectedParticipants + friend.redId
                                }
                            }),
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
                            Box(contentAlignment = Alignment.BottomEnd) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(if (isOnline) YounesEmerald else Color.Gray.copy(alpha = 0.5f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        friend.displayName.firstOrNull()?.toString() ?: "?",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                if (isOnline) Box(
                                    Modifier.size(12.dp).clip(CircleShape)
                                        .background(YounesEmerald)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(friend.displayName, color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (isOnline) "متصل الآن" else "@${friend.username}",
                                    color = if (isOnline) YounesEmerald else Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp
                                )
                            }
                            if (isSelected) {
                                Icon(Icons.Default.Check, "محدد", tint = AqyalGold, modifier = Modifier.size(24.dp))
                            } else {
                                Icon(Icons.Default.PersonAdd, "إضافة", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }

            if (selectedParticipants.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.Button(
                    onClick = { onInvite(selectedParticipants.toList()) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = YounesEmerald),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Phone, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("دعوة للمكالمة (${selectedParticipants.size})", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    // QR Code Dialog
    if (showQrDialog) {
        AlertDialog(
            onDismissRequest = { showQrDialog = false },
            title = { Text("رمز QR لدعوة المكالمة الجماعية", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val qrBitmap = remember(inviteLink) {
                        runCatching {
                            val width = 512
                            val height = 512
                            val bitMatrix = QRCodeWriter().encode(inviteLink, BarcodeFormat.QR_CODE, width, height)
                            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
                            for (x in 0 until width) {
                                for (y in 0 until height) {
                                    bmp.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                                }
                            }
                            bmp
                        }.getOrNull()
                    }

                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier.size(220.dp).clip(RoundedCornerShape(12.dp))
                        )
                    } else {
                        Text("تعذر توليد رمز QR", color = Color.Red)
                    }
                    Text(inviteLink, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showQrDialog = false }) {
                    Text("إغلاق", color = AqyalGold, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

/** جهة اتصال للاختيار في شاشة دعوة المكالمة الجماعية.
 *
 * (2026-09-15) كان تعريفها في `CallTransferScreen.kt` المؤرشف
 * (الأرشيف/dead-code-2026-09-15/) — نُقل هنا لأن هذه الشاشة هي المستهلك
 * الحي الوحيد، وكان حذف المؤرشف سيكسر البناء بلاها.
 */
data class ContactInfo(
    val id: String,
    val name: String,
    val phone: String,
    val isActive: Boolean
)

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
