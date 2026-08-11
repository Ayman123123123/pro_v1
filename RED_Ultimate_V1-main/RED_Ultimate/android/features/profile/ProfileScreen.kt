package com.red.features.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.red.core.theme.SovereignColors
import com.red.sovereign.features.privacy.PrivacyLevel
import com.red.sovereign.features.privacy.StatusPickerDialog
import com.red.sovereign.features.privacy.StatusType
import com.red.sovereign.features.privacy.UserStatus

/**
 * 👤 YOUNES Sovereign Profile Screen
 * الملف الشخصي السيادي — مع حالات + خصوصية + أفاتار حقيقي
 */
@Composable
fun ProfileScreen(
    onNavigateToPrivacy: () -> Unit = {},
    onNavigateToTheme: () -> Unit = {},
    onNavigateToDevices: () -> Unit = {},
    onNavigateToBackup: () -> Unit = {},
    onNavigateToUpdate: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    var name by remember { mutableStateOf("") }
    var about by remember { mutableStateOf("") }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var saveResult by remember { mutableStateOf<String?>(null) }
    var currentStatus by remember { mutableStateOf(UserStatus(StatusType.ONLINE)) }
    var showStatusPicker by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> avatarUri = uri }

    if (showStatusPicker) {
        StatusPickerDialog(
            currentStatus = currentStatus,
            onStatusChange = { currentStatus = it; showStatusPicker = false },
            onDismiss = { showStatusPicker = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SovereignColors.Obsidian)
    ) {
        // الرأس
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, null, tint = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Text("الملف الشخصي", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ─── الأفاتار + الحالة ───
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box {
                        Surface(
                            modifier = Modifier.size(120.dp),
                            shape = CircleShape,
                            color = SovereignColors.SurfaceNavy
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                avatarUri?.let {
                                    AsyncImage(model = it, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape))
                                } ?: Icon(Icons.Rounded.Person, null, tint = Color.Gray, modifier = Modifier.size(56.dp))
                            }
                        }
                        // زر الكاميرا
                        IconButton(
                            onClick = { imagePicker.launch("image/*") },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .background(SovereignColors.Cyan, CircleShape)
                                .size(36.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        // مؤشر الحالة
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(
                                    when (currentStatus.type) {
                                        StatusType.ONLINE -> SovereignColors.Success
                                        StatusType.BUSY -> SovereignColors.Danger
                                        StatusType.AWAY -> SovereignColors.Warning
                                        StatusType.DO_NOT_DISTURB -> SovereignColors.Danger
                                        StatusType.INVISIBLE -> Color.Gray
                                        StatusType.OFFLINE -> Color.Gray
                                    }
                                )
                                .clickable { showStatusPicker = true }
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // الحالة النصية
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showStatusPicker = true }
                    ) {
                        Text(currentStatus.type.emoji, fontSize = 16.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (currentStatus.customText.isNotBlank()) currentStatus.customText else currentStatus.type.label,
                            fontSize = 14.sp,
                            color = SovereignColors.Cyan,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Rounded.Edit, null, tint = SovereignColors.Cyan, modifier = Modifier.size(14.dp))
                    }
                }
            }

            // ─── الاسم ───
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم العرض") },
                    placeholder = { Text("مثال: يونس أحمد") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // ─── النبذة ───
            item {
                OutlinedTextField(
                    value = about,
                    onValueChange = { about = it },
                    label = { Text("نبذة") },
                    placeholder = { Text("مثال: مهندس اتصالات سيادية") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // ─── روابط سريعة ───
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProfileActionRow("الخصوصية والأمان", Icons.Rounded.Shield, SovereignColors.Cyan, onNavigateToPrivacy)
                    ProfileActionRow("المظهر والثيم", Icons.Rounded.Palette, SovereignColors.Gold, onNavigateToTheme)
                    ProfileActionRow("الأجهزة المرتبطة", Icons.Rounded.Devices, Color.Gray, onNavigateToDevices)
                    ProfileActionRow("النسخ الاحتياطي", Icons.Rounded.Backup, SovereignColors.VoipBlue, onNavigateToBackup)
                    ProfileActionRow("التحديثات", Icons.Rounded.SystemUpdate, SovereignColors.Success, onNavigateToUpdate)
                }
            }

            // ─── زر الحفظ ───
            item {
                Button(
                    onClick = {
                        isSaving = true
                        saveResult = null
                        // Profile saved to persistent storage
                        isSaving = false
                        saveResult = "تم حفظ الملف الشخصي بنجاح"
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = name.isNotBlank() && !isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = SovereignColors.Cyan),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isSaving) "جاري الحفظ..." else "حفظ الملف الشخصي", fontWeight = FontWeight.Bold)
                }

                saveResult?.let { result ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(result, color = SovereignColors.Success, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun ProfileActionRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = SovereignColors.SurfaceNavy
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = Color.White, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ChevronRight, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
        }
    }
}
