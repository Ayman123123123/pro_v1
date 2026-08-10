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
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.DevicesApi
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.ui.theme.SovereignColors
import kotlinx.coroutines.launch

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
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { DevicesApi(TokenStore(context.applicationContext)) }
    var devices by remember { mutableStateOf<List<DeviceSession>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var reloadTrigger by remember { mutableIntStateOf(0) }

    // 📡 جلب الأجهزة الحقيقية من الخادم (GET /api/devices) — لا بيانات وهمية
    suspend fun reload() {
        loading = true
        when (val result = api.list()) {
            is ApiResult.Success -> {
                loadError = null
                devices = result.value.map { remote ->
                    DeviceSession(
                        id = remote.id,
                        deviceName = remote.deviceName,
                        deviceType = when {
                            remote.platform.contains("android", true) -> DeviceType.ANDROID
                            remote.platform.contains("ios", true) -> DeviceType.IOS
                            remote.platform.contains("web", true) -> DeviceType.WEB
                            else -> DeviceType.UNKNOWN
                        },
                        platform = remote.platform,
                        lastActiveAt = if (remote.status == "APPROVED") "نشط" else remote.status,
                        ipAddress = remote.identityFingerprint?.take(11) ?: "—",
                        isCurrentDevice = false
                    )
                }
            }
            is ApiResult.Error -> loadError = result.message
        }
        loading = false
    }
    LaunchedEffect(reloadTrigger) { reload() }

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
                Text(if (loading) "جارٍ التحميل…" else "${devices.size} جلسات نشطة", fontSize = 12.sp, color = Color.Gray)
            }
            // 🔄 زر تحديث يدوي
            IconButton(onClick = { /* إعادة الجلب */ reloadTrigger++ }) {
                Icon(Icons.Rounded.Refresh, "تحديث", tint = SovereignColors.Cyan)
            }
        }

        HorizontalDivider(color = Color.Gray.copy(alpha = 0.1f))

        // ⚠️ شريط خطأ الشبكة — مع إعادة المحاولة
        loadError?.let { err ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF43F5E).copy(alpha = 0.12f)),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("تعذر جلب الأجهزة: $err", color = Color(0xFFF43F5E), fontSize = 12.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { reloadTrigger++ }) { Text("إعادة") }
                }
            }
        }

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
                        // 🔐 إلغاء حقيقي عبر DELETE /api/devices/{id} — يبطل توكنات الجهاز في الخادم
                        scope.launch {
                            when (api.revoke(device.id)) {
                                is ApiResult.Success -> {
                                    devices = devices.filter { it.id != device.id }
                                    onLogoutDevice(device.id)
                                }
                                is ApiResult.Error -> loadError = "تعذر فصل الجهاز"
                            }
                        }
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
