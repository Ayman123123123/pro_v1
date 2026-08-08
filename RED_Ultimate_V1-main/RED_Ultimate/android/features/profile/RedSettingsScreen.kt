package com.red.features.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RedSettingsScreen(
    onManageDinstar: () -> Unit,
    onLogout: () -> Unit
) {
    var isScanning by remember { mutableStateOf(false) }
    var scanResult by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            text = "الإعدادات السيادية",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // DINSTAR Yemeni Line Status Card (Gold Theme)
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF4B400).copy(alpha = 0.12f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color(0xFFF4B400), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.SimCard, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("خطي اليمني (DINSTAR Gateway)", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFFF4B400))
                            Text("موديل: UC2000-VE-8G • GSM", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                    }
                    Badge(containerColor = Color(0xFF4CAF50)) { Text("متصل", color = Color.White) }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Color(0xFFF4B400).copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("الرقم اليمني:", fontSize = 11.sp, color = Color.Gray)
                        Text("+967 77 123 4567", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("قوة الإشارة:", fontSize = 11.sp, color = Color.Gray)
                        Text("85% (أبراج كاملة)", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Column {
                        Text("الرصيد المتبقي:", fontSize = 11.sp, color = Color.Gray)
                        Text("1,250 ريال", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onManageDinstar,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF4B400)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.SettingsEthernet, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إدارة الهاردوير", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            isScanning = true
                            scanResult = null
                            // Simulate network discovery scan
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                isScanning = false
                                scanResult = "تم اكتشاف بوابة DINSTAR بنجاح"
                            }, 2000)
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF4B400)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF4B400)),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFFF4B400), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("جاري البحث...", fontSize = 12.sp)
                        } else {
                            Icon(Icons.Default.Radar, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("اكتشاف ذكي", fontSize = 12.sp)
                        }
                    }
                }

                if (scanResult != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = scanResult!!,
                        fontSize = 11.sp,
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Settings Items
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SettingItem(icon = Icons.Default.Person, title = "الملف الشخصي والحساب", subtitle = "إدارة المعرف والبريد الإلكتروني")
            SettingItem(icon = Icons.Default.Security, title = "الأمان والخصوصية السيادية", subtitle = "التشفير التام وخوادم السيرفر المحلي")
            SettingItem(icon = Icons.Default.Notifications, title = "الإشعارات والأصوات", subtitle = "تخصيص نغمات المكالمات والرسائل")
            SettingItem(icon = Icons.Default.Storage, title = "التخزين والبيانات", subtitle = "إدارة وسائط المحادثات والذاكرة المؤقتة")
            SettingItem(icon = Icons.Default.Language, title = "اللغة والمظهر", subtitle = "العربية (الافتراضية) • الوضع الليلي")
        }

        Spacer(modifier = Modifier.weight(1f))

        // Logout Button
        OutlinedButton(
            onClick = onLogout,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Logout, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("تسجيل الخروج من المنظومة السيادية", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SettingItem(icon: ImageVector, title: String, subtitle: String) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
        }
    }
}
