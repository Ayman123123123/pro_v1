package com.red.sovereign.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.ui.theme.AppThemeMode
import com.red.sovereign.ui.theme.AppThemePreset
import com.red.sovereign.ui.theme.AppThemeState
import com.red.sovereign.ui.theme.YounesPrimary
import com.red.sovereign.ui.theme.YounesAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val settingsVm: com.red.sovereign.settings.SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    Scaffold(
        containerColor = Color(0xFF0A0A0A),
        topBar = {
            TopAppBar(
                title = { Text("الإعدادات", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { }) {
                        Icon(Icons.Rounded.QrCodeScanner, contentDescription = "QR", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF141414))
                        .clickable { }
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFB71C1C)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("AY", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Ayman", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text("+967 77X XXX XXX", color = Color.Gray, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Online | RED Sovereign", color = Color(0xFF00E676), fontSize = 12.sp)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
                    }
                }
            }

            item {
                SettingsCard {
                    SettingsRow(Icons.Outlined.Lock, "الخصوصية والأمان", "قفل التطبيق، التحقق بخطوتين")
                    Divider(color = Color(0xFF222222), modifier = Modifier.padding(start = 56.dp))
                    SettingsRow(Icons.Outlined.ChatBubbleOutline, "الدردشات", "الخلفيات، سجل الدردشات")
                    Divider(color = Color(0xFF222222), modifier = Modifier.padding(start = 56.dp))
                    SettingsRow(Icons.Outlined.Notifications, "الإشعارات", "نغمات الرسائل والمكالمات")
                }
            }

            item {
                // ─── المظهر الأسطوري — 6 ثيمات + ديناميكي + مخصص ───────────────
                val currentTheme = AppThemeState.currentPreset
                val currentMode = AppThemeState.themeMode
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF141414))
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Palette, contentDescription = "Theme", tint = YounesPrimary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("المظهر — Liquid Glass 2026", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("اختر الأجمل — ديناميكي يستخرج الألوان من خلفية هاتفك تلقائياً", color = Color.Gray, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(14.dp))

                    // شبكة 3×2 — 6 ثيمات
                    val themes = listOf(
                        AppThemePreset.SOVEREIGN to Triple("سيادي", Color(0xFF14C79A), Color(0xFF0A0F18)),
                        AppThemePreset.TELEGRAM_DARK to Triple("تلجرام", Color(0xFF2AABEE), Color(0xFF0E1621)),
                        AppThemePreset.WHATSAPP_DARK to Triple("واتساب", Color(0xFF00A884), Color(0xFF0B141A)),
                        AppThemePreset.OLED_BLACK to Triple("أوليد", Color(0xFF00E676), Color(0xFF000000)),
                        AppThemePreset.DYNAMIC to Triple("ديناميكي", Color(0xFF4FC3F7), Color(0xFF1A263D)),
                        AppThemePreset.CUSTOM to Triple("مخصص", AppThemeState.customPrimary ?: Color(0xFFE0B551), Color(0xFF131C29))
                    )
                    // صف أول 3
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        themes.take(3).forEach { (preset, info) ->
                            ThemeChipWithPersist(preset, info, currentTheme == preset, Modifier.weight(1f), settingsVm)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        themes.drop(3).forEach { (preset, info) ->
                            ThemeChipWithPersist(preset, info, currentTheme == preset, Modifier.weight(1f), settingsVm)
                        }
                    }

                    // منتقي مخصص — 6 ألوان مقترحة تضمن ≥4.5:1
                    if (currentTheme == AppThemePreset.CUSTOM) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("اختر لونك المخصص:", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AppThemeState.customPresets.forEach { (color, label) ->
                                val selected = AppThemeState.customPrimary == color
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else Color(0xFF333333), CircleShape)
                                        .clickable {
                                            AppThemeState.customPrimary = color
                                            settingsVm.setCustomPrimary(color.value.toInt())
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selected) Icon(Icons.Default.Check, null, tint = Color(0xFF06090F), modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = Color(0xFF222222))
                    Spacer(modifier = Modifier.height(12.dp))

                    // نمط الإضاءة: فاتح/ليلي/نظام
                    Text("نمط الإضاءة", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppThemeMode.entries.forEach { mode ->
                            val sel = currentMode == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (sel) YounesPrimary else Color(0xFF222222))
                                    .border(1.dp, if (sel) YounesPrimary else Color(0xFF333333), RoundedCornerShape(12.dp))
                                    .clickable {
                                        AppThemeState.themeMode = mode
                                        settingsVm.setThemeMode(mode.name)
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(mode.label, color = if (sel) Color(0xFF06090F) else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    // مفاتيح Liquid Glass / تباين عالي / تقليل الحركة
                    SettingsToggleRow("Liquid Glass", "زجاج سائل شفاف كتليجرام/واتساب 2026", AppThemeState.liquidGlassEnabled) {
                        AppThemeState.liquidGlassEnabled = it
                        settingsVm.setLiquidGlass(it)
                    }
                    Divider(color = Color(0xFF222222), modifier = Modifier.padding(vertical = 8.dp))
                    SettingsToggleRow("تباين عالي", "نص أوضح 7:1 AAA لضعاف البصر", AppThemeState.highContrast) {
                        AppThemeState.highContrast = it
                        settingsVm.setHighContrast(it)
                    }
                    Divider(color = Color(0xFF222222), modifier = Modifier.padding(vertical = 8.dp))
                    SettingsToggleRow("تقليل الحركة", "يثبت الأنيميشن — يحترم الجهاز البطيء", AppThemeState.reduceMotion) {
                        AppThemeState.reduceMotion = it
                        settingsVm.setReduceMotion(it)
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Divider(color = Color(0xFF222222))
                    Spacer(modifier = Modifier.height(12.dp))
                    // حجم الخط
                    Text("حجم الخط", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0.85f to "صغير", 1.0f to "عادي", 1.15f to "كبير", 1.30f to "كبير جداً").forEach { (scale, label) ->
                            val sel = AppThemeState.fontScale == scale
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (sel) Color(0xFF1E293B) else Color(0xFF222222))
                                    .border(1.dp, if (sel) YounesPrimary else Color.Transparent, RoundedCornerShape(10.dp))
                                    .clickable {
                                        AppThemeState.fontScale = scale
                                        settingsVm.setFontScale(scale)
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label, color = if (sel) YounesPrimary else Color.Gray, fontSize = 11.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }

            item {
                SettingsCard {
                    SettingsRow(Icons.Outlined.Storage, "البيانات والتخزين", "استهلاك الشبكة، التحميل التلقائي")
                    Divider(color = Color(0xFF222222), modifier = Modifier.padding(start = 56.dp))
                    SettingsRow(Icons.Default.Language, "لغة التطبيق", "العربية")
                }
            }

            item {
                var showServerDialog by remember { mutableStateOf(false) }
                val context = androidx.compose.ui.platform.LocalContext.current
                SettingsCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showServerDialog = true }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Dns, contentDescription = "Server Connection", tint = Color(0xFFB71C1C), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("اتصال الخادم", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("الحالي: ${com.red.sovereign.core.ServerEndpoint.url()}", color = Color.Gray, fontSize = 12.sp)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
                    }
                }
                if (showServerDialog) {
                    var serverIp by remember { mutableStateOf(com.red.sovereign.core.ServerEndpoint.url()) }
                    AlertDialog(
                        onDismissRequest = { showServerDialog = false },
                        containerColor = Color(0xFF141414),
                        title = { Text("عنوان الخادم", color = Color.White) },
                        text = {
                            OutlinedTextField(
                                value = serverIp,
                                onValueChange = { serverIp = it },
                                label = { Text("مثال: http://192.168.1.10:8088", color = Color.Gray) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFFB71C1C)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                if (serverIp.isNotBlank()) {
                                    runCatching {
                                        com.red.sovereign.core.ServerEndpoint.update(context, serverIp)
                                        showServerDialog = false
                                    }
                                }
                            }) { Text("حفظ", color = Color(0xFFB71C1C)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showServerDialog = false }) { Text("إلغاء", color = Color.Gray) }
                        }
                    )
                }
            }

            item {
                SettingsCard {
                    SettingsRow(Icons.Outlined.HelpOutline, "المساعدة", "الأسئلة الشائعة، سياسة الخصوصية")
                    Divider(color = Color(0xFF222222), modifier = Modifier.padding(start = 56.dp))
                    SettingsRow(Icons.Default.Info, "حول RED", "الإصدار 1.0 Ultimate — Liquid Glass 2026")
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "from\nRED TEAM — Sovereign Liquid Glass",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun ThemeChip(preset: AppThemePreset, info: Triple<String, Color, Color>, isSelected: Boolean, modifier: Modifier = Modifier) {
    val (label, accent, bg) = info
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(
                width = if (isSelected) 2.dp else 0.5.dp,
                color = if (isSelected) accent else Color(0xFF333333),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { AppThemeState.currentPreset = preset }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(accent),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) Icon(Icons.Default.Check, null, tint = Color(0xFF06090F), modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ThemeChipWithPersist(preset: AppThemePreset, info: Triple<String, Color, Color>, isSelected: Boolean, modifier: Modifier = Modifier, vm: com.red.sovereign.settings.SettingsViewModel) {
    val (label, accent, bg) = info
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(
                width = if (isSelected) 2.dp else 0.5.dp,
                color = if (isSelected) accent else Color(0xFF333333),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable {
                AppThemeState.currentPreset = preset
                vm.setThemePreset(preset.name)
            }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(accent),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) Icon(Icons.Default.Check, null, tint = Color(0xFF06090F), modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SettingsToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Color.Gray, fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF06090F), checkedTrackColor = YounesPrimary, uncheckedThumbColor = Color.Gray, uncheckedTrackColor = Color(0xFF333333)))
    }
}

@Composable
fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF141414))
    ) {
        content()
    }
}

@Composable
fun SettingsRow(icon: ImageVector, title: String, subtitle: String = "") {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = title, tint = Color(0xFFB71C1C), modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, color = Color.Gray, fontSize = 12.sp)
            }
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
    }
}
