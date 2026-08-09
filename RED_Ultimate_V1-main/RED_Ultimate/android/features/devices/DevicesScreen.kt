package com.red.features.devices

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.core.theme.SovereignColors

/**
 * 📱 YOUNES Devices Screen — شاشة الأجهزة النشطة
 *
 * تعرض جميع الأجهزة/الجلسات النشطة على حساب المستخدم:
 * - أجهزة مسجّلة الدخول (هاتف، تابلت، ويب)
 * - جلسة DINSTAR (بوابة GSM)
 * - معلومات آخر نشاط + عنوان IP
 * - إمكانية إنهاء الجلسة عن بُعد
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
    // بيانات تجريبية — في الإنتاج: من DataStore/API
    var devices by remember {
        mutableStateOf(
            listOf(
                DeviceSession(
                    id = "current",
                    deviceName = "هاتف RED الرئيسي",
                    deviceType = DeviceType.ANDROID,
                    platform = "Android 14",
                    lastActiveAt = "الآن",
                    ipAddress = "192.168.1.5",
                    isCurrentDevice = true
                ),
                DeviceSession(
                    id = "dinstar_gw",
                    deviceName = "DINSTAR UC2000-VE-8G",
                    deviceType = DeviceType.DINSTAR,
                    platform = "Firmware 04240302",
                    lastActiveAt = "متصل",
                    ipAddress = "192.168.11.1",
                    isDinstar = true,
                    signalPercent = 65,
                    portCount = 8
                ),
                DeviceSession(
                    id = "web_session",
                    deviceName = "متصفح Chrome",
                    deviceType = DeviceType.WEB,
                    platform = "Chrome 126 / Windows",
                    lastActiveAt = "منذ 2 ساعة",
                    ipAddress = "192.168.1.10"
                )
            )
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SovereignColors.Obsidian)
    ) {
        // ═══ رأس الصفحة ═══
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
                Text("الأجهزة النشطة", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                Text("${devices.size} جهاز/جلسة", fontSize = 12.sp, color = Color.Gray)
            }
        }

        HorizontalDivider(color = Color.Gray.copy(alpha = 0.1f))

        // ═══ قائمة الأجهزة ═══
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // شارة الأمان
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
                            "يمكنك إنهاء أي جلسة عن بُعد لحماية حسابك. الجهاز الحالي لا يمكن إنهاؤه من هنا.",
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

            // نصائح أمان
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader("نصائح أمان", Icons.Rounded.Security, SovereignColors.Warning)
                Spacer(Modifier.height(4.dp))

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceNavy),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SecurityTip("لا تشارك كلمة المرور مع أحد")
                        SecurityTip("فعّل التحقق بخطوتين إن توفر")
                        SecurityTip("راجع الأجهزة النشطة بشكل دوري")
                        SecurityTip("أنهِ الجلسات غير المعروفة فوراً")
                    }
                }
            }

            item { Spacer(Modifier.height(60.dp)) }
        }
    }
}

@Composable
private fun DeviceCard(
    device: DeviceSession,
    onLogout: () -> Unit,
    onNavigateToDinstar: (() -> Unit)?
) {
    val typeColor = device.deviceType.color
    val pulseAlpha = rememberInfiniteTransition().animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "ActivePulse"
    )

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (device.isCurrentDevice) SovereignColors.Cyan.copy(alpha = 0.06f)
                           else if (device.isDinstar) SovereignColors.DinstarGold.copy(alpha = 0.06f)
                           else SovereignColors.SurfaceNavy
        ),
        border = BorderStroke(
            1.dp,
            when {
                device.isCurrentDevice -> SovereignColors.Cyan.copy(alpha = 0.3f)
                device.isDinstar -> SovereignColors.DinstarGold.copy(alpha = 0.3f)
                else -> Color.Gray.copy(alpha = 0.15f)
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // أيقونة الجهاز
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(typeColor.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(device.deviceType.icon, null, tint = typeColor, modifier = Modifier.size(24.dp))
                // نقطة نشاط
                if (device.isCurrentDevice) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .align(Alignment.TopEnd)
                            .background(SovereignColors.Success.copy(alpha = pulseAlpha.value), CircleShape)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // معلومات الجهاز
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(device.deviceName, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    if (device.isCurrentDevice) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = SovereignColors.Cyan.copy(alpha = 0.15f)
                        ) {
                            Text("هذا الجهاز", fontSize = 9.sp, fontWeight = FontWeight.Bold,
                                color = SovereignColors.Cyan, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(device.platform, fontSize = 11.sp, color = Color.Gray)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Schedule, null, tint = Color.Gray.copy(alpha = 0.5f), modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${device.lastActiveAt} • IP: ${device.ipAddress}", fontSize = 10.sp, color = Color.Gray)
                }

                // معلومات Dinstar إضافية
                if (device.isDinstar) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (device.signalPercent != null) {
                            Icon(Icons.Rounded.SignalCellularAlt, null, tint = SovereignColors.DinstarGold, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("إشارة: ${device.signalPercent}%", fontSize = 10.sp, color = SovereignColors.DinstarGold)
                            Spacer(Modifier.width(10.dp))
                        }
                        if (device.portCount != null) {
                            Icon(Icons.Rounded.SimCard, null, tint = SovereignColors.DinstarGold, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("${device.portCount} شرائح", fontSize = 10.sp, color = SovereignColors.DinstarGold)
                        }
                    }
                }
            }

            // أزرار
            if (device.isDinstar && onNavigateToDinstar != null) {
                IconButton(onClick = onNavigateToDinstar) {
                    Icon(Icons.Rounded.OpenInNew, null, tint = SovereignColors.DinstarGold, modifier = Modifier.size(18.dp))
                }
            }

            if (!device.isCurrentDevice) {
                IconButton(
                    onClick = onLogout,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Rounded.Logout, null,
                        tint = SovereignColors.Danger.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = color)
    }
}

@Composable
private fun SecurityTip(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.CheckCircle, null, tint = SovereignColors.Warning.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 12.sp, color = Color.Gray)
    }
}
