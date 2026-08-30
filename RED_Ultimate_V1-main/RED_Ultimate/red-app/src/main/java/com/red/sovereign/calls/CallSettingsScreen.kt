package com.red.sovereign.calls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Debug
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.HdrStrong
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Safe
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
 * شاشة إعدادات المكالمات — Call Settings Screen
 *
 * تتحكم في:
 * - جودة الفيديو (LOW/MEDIUM/HIGH/AUTO)
 * - كتم تلقائي عند الدخول لمكالمة
 * - حفظ سجل المكالمات مشفراً
 * - إعدادات الصوت (مكبر، بلوتوث)
 * - إعدادات الخصوصية (تسجيل المكالمات، الإشعارات)
 * - إعدادات المتصفح (debug, telemetry)
 */
@Composable
fun CallSettingsScreen(
    onBack: () -> Unit = {}
) {
    var videoQuality by remember { mutableStateOf("AUTO") }
    var autoMute onEntry by remember { mutableStateOf(false) }
    var encryptCallLog by remember { mutableStateOf(true) }
    var enableTelemetry by remember { mutableStateOf(false) }
    var enableRecording by remember { mutableStateOf(false) }
    var enableNotifications by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("إعدادات المكالمات", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SovereignColors.SurfaceDark
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "رجوع", tint = Color.White)
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

            // ── قسم الجودة ───────────────────────────────────────────────
            SettingsSectionTitle("جودة المكالمة")
            Spacer(Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceDarkVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    VideoQualitySelector(selected = videoQuality) { videoQuality = it }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.HdrStrong, "جودة", tint = AqyalGold, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("جودة الفيديو", color = Color.White, fontSize = 14.sp)
                        }
                        Text(videoQuality, color = AqyalGold, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── قسم الصوت ───────────────────────────────────────────────
            SettingsSectionTitle("الصوت والمكبر")
            Spacer(Modifier.height(8.dp))

            SettingRow(
                icon = Icons.Default.Speaker,
                title = "مكبر الصوت التلقائي",
                description = "تفعيل مكبر الصوت عند بدء المكالمة",
                defaultValue = false
            ) { /* toggle */ }

            SettingRow(
                icon = Icons.Default.Bluetooth,
                title = "أولوية البلوتوث",
                description = "توجيه الصوت إلى جهاز بلوتوث متصل عند توفره",
                defaultValue = true
            ) { /* toggle */ }

            SettingRow(
                icon = Icons.Default.MicOff,
                title = "كتم تلقائي عند الدخول",
                description = "كتم الميكروفون تلقائياً عند دخول مكالمة",
                checked = autoMute onEntry,
                onCheckedChange = { autoMute onEntry = it }
            )

            Spacer(Modifier.height(16.dp))

            // ── قسم الخصوصية ─────────────────────────────────────────────
            SettingsSectionTitle("الخصوصية والأمان")
            Spacer(Modifier.height(8.dp))

            SettingRow(
                icon = Icons.Default.Safe,
                title = "تشفير سجل المكالمات",
                description = "تشفير بيانات المكالمات محلياً (CallLogCipher)",
                checked = encryptCallLog,
                onCheckedChange = { encryptCallLog = it }
            )

            SettingRow(
                icon = Icons.Default.Power,
                title = "تسجيل المكالمات",
                description = "السماح بتسجيل المكالمات صوتياً (يتطلب موافقة الطرفين)",
                checked = enableRecording,
                onCheckedChange = { enableRecording = it }
            )

            SettingRow(
                icon = Icons.Default.RadioButtonChecked,
                title = "إشعارات المكالمات",
                description = "عرض إشعارات المكالمات الواردة على شاشة القفل",
                checked = enableNotifications,
                onCheckedChange = { enableNotifications = it }
            )

            Spacer(Modifier.height(16.dp))

            // ── قسم التطوير ───────────────────────────────────────────────
            SettingsSectionTitle("أدوات التطوير")
            Spacer(Modifier.height(8.dp))

            SettingRow(
                icon = Icons.Default.Debug,
                title = "تفعيل Telemetry",
                description = "إرسال إحصائيات الأداء لجودة المكالمة (CallQualityManager)",
                checked = enableTelemetry,
                onCheckedChange = { enableTelemetry = it }
            )

            SettingRow(
                icon = Icons.Default.Build,
                title = "وضع التصحيح",
                description = "عرض سجلات WebRTC مفصلة في Logcat",
                defaultValue = false
            ) { /* toggle */ }

            Spacer(Modifier.height(16.dp))

            // ── زر حذف السجل ─────────────────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { /* clear history */ },
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A0A0A)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Delete, "حذف سجل المكالمات", tint = Color.Red, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("حذف سجل المكالمات بالكامل", color = Color.Red, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        title,
        color = AqyalGold,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        modifier = Modifier.padding(start = 4.dp)
    )
}

@Composable
fun VideoQualitySelector(selected: String, onSelect: (String) -> Unit) {
    val qualities = listOf("LOW", "MEDIUM", "HIGH", "AUTO")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        qualities.forEach { q ->
            QualityChip(
                label = q,
                selected = selected == q,
                onClick = { onSelect(q) }
            )
        }
    }
}

@Composable
fun QualityChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (selected) AqyalGold else SovereignColors.SurfaceDark),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                color = if (selected) Color(0xFF0A0F18) else Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
        if (selected) {
            Icon(Icons.Default.Done, "محدد", tint = AqyalGold, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    checked: Boolean = false,
    onCheckedChange: ((Boolean) -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceDarkVariant),
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
                Icon(icon, title, tint = AqyalGold, modifier = Modifier.size(24.dp))
                Column {
                    Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(description, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                }
            }
            if (onCheckedChange != null) {
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AqyalGold,
                        checkedTrackColor = AqyalGold.copy(alpha = 0.3f)
                    )
                )
            }
        }
    }
}
