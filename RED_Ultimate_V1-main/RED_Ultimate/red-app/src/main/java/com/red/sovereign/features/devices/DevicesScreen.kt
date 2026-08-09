package com.red.sovereign.features.devices

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.red.sovereign.ui.theme.SovereignColors

/**
 * 📱 YOUNES Devices Screen — الأجهزة النشطة
 */
data class DeviceSession(
    val id: String,
    val deviceName: String,
    val deviceType: DeviceType,
    val platform: String,
    val lastActiveAt: String,
    val ipAddress: String,
    val isCurrentDevice: Boolean = false,
    val isDinstar: Boolean = false,
    val signalPercent: Int? = null,
    val portCount: Int? = null
)

enum class DeviceType(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val color: Color) {
    ANDROID("أندرويد", Icons.Rounded.PhoneAndroid, Color(0xFF3DDC84)),
    IOS("iOS", Icons.Rounded.PhoneIphone, Color(0xFF007AFF)),
    WEB("ويب", Icons.Rounded.Language, SovereignColors.Cyan),
    DESKTOP("سطح المكتب", Icons.Rounded.Computer, Color(0xFF7C4DFF)),
    DINSTAR("DINSTAR", Icons.Rounded.Router, SovereignColors.DinstarGold),
    UNKNOWN("غير معروف", Icons.Rounded.Devices, Color.Gray)
}

@Composable
fun DevicesScreen(
    onBack: () -> Unit = {},
    onLogoutDevice: (String) -> Unit = {},
    onNavigateToDinstar: (() -> Unit)? = null
) {
    var devices by remember {
        mutableStateOf(
            listOf(
                DeviceSession(
                    id = "current",
                    deviceName = "هاتف يونس الرئيسي",
                    deviceType = DeviceType.ANDROID,
                    platform = "Android 14",
                    lastActiveAt = "الآن",
                    ipAddress = "192.168.1.5",
                    isCurrentDevice = true
                ),
                DeviceSession(
                    id = "dinstar_gw",
                    deviceName = "بوابة DINSTAR السيادية",
                    deviceType = DeviceType.DINSTAR,
                    platform = "Firmware 04240302",
                    lastActiveAt = "متصل",
                    ipAddress = "192.168.11.1",
                    isDinstar = true,
                    signalPercent = 85,
                    portCount = 8
                )
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SovereignColors.Obsidian)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, null, tint = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.Devices, null, tint = SovereignColors.Cyan, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("الأجهزة المتصلة", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                Text("${devices.size} جلسات نشطة", fontSize = 12.sp, color = Color.Gray)
            }
        }

        HorizontalDivider(color = Color.Gray.copy(alpha = 0.1f))

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SovereignColors.Success.copy(alpha = 0.06f)),
                    border = BorderStroke(1.dp, SovereignColors.Success.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Shield, null, tint = SovereignColors.Success, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "يتم تشفير كافة الجلسات بمفاتيح فريدة. يمكنك فصل أي جهاز مشبوه فوراً.",
                            fontSize = 11.sp, color = Color.Gray
                        )
                    }
                }
            }

            items(devices) { device ->
                DeviceCard(
                    device = device,
                    onLogout = {
                        onLogoutDevice(device.id)
                        devices = devices.filter { it.id != device.id }
                    },
                    onNavigateToDinstar = onNavigateToDinstar
                )
            }

            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader("إرشادات سيادية", Icons.Rounded.Security, SovereignColors.Warning)
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceNavy),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SecurityTip("لا تمنح صلاحية 'يونس ويب' إلا من جهازك الخاص")
                        SecurityTip("تحقق من عنوان الـ IP لكل جلسة نشطة")
                        SecurityTip("استخدم ميزة التدمير الذاتي في حالات الطوارئ")
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(device: DeviceSession, onLogout: () -> Unit, onNavigateToDinstar: (() -> Unit)?) {
    val typeColor = device.deviceType.color
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (device.isCurrentDevice) SovereignColors.Cyan.copy(alpha = 0.06f)
                           else SovereignColors.SurfaceNavy
        ),
        border = BorderStroke(1.dp, if (device.isCurrentDevice) SovereignColors.Cyan.copy(alpha = 0.3f) else Color.Gray.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(typeColor.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(device.deviceType.icon, null, tint = typeColor, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(device.deviceName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                Text(device.platform, fontSize = 11.sp, color = Color.Gray)
                Text("${device.lastActiveAt} • IP: ${device.ipAddress}", fontSize = 10.sp, color = Color.Gray)
            }
            if (!device.isCurrentDevice) {
                IconButton(onClick = onLogout) { Icon(Icons.Rounded.Logout, null, tint = SovereignColors.Danger) }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = color)
    }
}

@Composable
private fun SecurityTip(text: String) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.CheckCircle, null, tint = SovereignColors.Warning.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 12.sp, color = Color.Gray)
    }
}
