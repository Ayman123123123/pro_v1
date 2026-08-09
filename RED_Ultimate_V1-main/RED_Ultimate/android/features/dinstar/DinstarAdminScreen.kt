package com.red.features.dinstar

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.red.core.theme.SovereignColors

/**
 * 🏛️ YOUNES Dinstar Admin Screen — لوحة إدارة البوابة الكاملة
 * 
 * شاشة إدارية شاملة لإدارة جهاز Dinstar UC2000-VE-8G
 * 
 * الأقسام:
 * ┌──────────────────────────────────────────────────────────┐
 * │ 1. رأس الصفحة — العنوان + زر الرجوع                     │
 * │ 2. حالة الاتصال — البوابة + الباكند                      │
 * │ 3. اختيار المنفذ الذكي — خوارزمية اختيار أفضل شريحة      │
 * │ 4. إدارة المنافذ — إعادة تعيين / USSD / تفاصيل           │
 * │ 5. مراقبة الإشارات — رسم بياني مبسط                      │
 * │ 6. إحصائيات المكالمات — CDR + تكاليف                     │
 * │ 7. إعدادات البوابة — عنوان IP + بيانات الدخول             │
 * └──────────────────────────────────────────────────────────┘
 */
@Composable
fun DinstarAdminScreen(
    viewModel: DinstarViewModel,
    onBack: () -> Unit = {},
    onDialWithPort: (port: Int, number: String) -> Unit = { _, _ -> }
) {
    val gatewayStatus by viewModel.gatewayStatus.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val statistics by viewModel.statistics.collectAsStateWithLifecycle()
    val commandResult by viewModel.commandResult.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("نظرة عامة", "المنافذ", "الإحصائيات", "إعدادات")

    // بدء المراقبة
    LaunchedEffect(Unit) {
        viewModel.discoverGateway()
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
                Icon(Icons.Rounded.ArrowBack, null, tint = SovereignColors.DinstarGold)
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.Router, null, tint = SovereignColors.DinstarGold, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("إدارة DINSTAR", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White)
                Text("UC2000-VE-8G — بوابة GSM اليمن", fontSize = 11.sp, color = Color.Gray)
            }

            // شارة الحالة
            if (gatewayStatus.isOnline) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = SovereignColors.Success.copy(alpha = 0.15f)
                ) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).background(SovereignColors.Success, CircleShape))
                        Spacer(Modifier.width(4.dp))
                        Text("متصل", fontSize = 11.sp, color = SovereignColors.Success, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // ═══ تبويبات ═══
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = SovereignColors.Navy,
            contentColor = SovereignColors.DinstarGold,
            edgePadding = 16.dp,
            divider = { HorizontalDivider(color = Color.Gray.copy(alpha = 0.1f)) }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            title,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == index) SovereignColors.DinstarGold else Color.Gray
                        )
                    }
                )
            }
        }

        // ═══ المحتوى ═══
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
            label = "DinstarTabContent"
        ) { tab ->
            when (tab) {
                0 -> OverviewTab(viewModel, onDialWithPort)
                1 -> PortsTab(viewModel)
                2 -> StatisticsTab(viewModel)
                3 -> SettingsTab(viewModel)
            }
        }
    }
}

// ━━━━━━━━━━━━ تبويب نظرة عامة ━━━━━━━━━━━━

@Composable
private fun OverviewTab(
    viewModel: DinstarViewModel,
    onDialWithPort: (Int, String) -> Unit
) {
    val status by viewModel.gatewayStatus.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // حالة البوابة
        item {
            DinstarGatewayBanner(
                status = status,
                connectionState = connectionState,
                onDiscover = { viewModel.discoverGateway() },
                onRefresh = { viewModel.refreshStatus() },
                isLoading = isLoading
            )
        }

        // اختيار المنفذ الذكي
        item {
            SmartPortSelectionSection(
                viewModel = viewModel,
                onDial = onDialWithPort
            )
        }

        // ملخص سريع
        item {
            DinstarSummaryCards(
                status = status,
                statistics = viewModel.statistics.collectAsStateWithLifecycle().value
            )
        }

        // أزرار سريعة
        item {
            SectionHeader("أوامر سريعة", Icons.Rounded.Bolt, SovereignColors.Cyan)
            QuickActionsRow(
                onRefresh = { viewModel.refreshStatus() },
                onDiscover = { viewModel.discoverGateway() },
                onQueryCdr = { viewModel.queryCdr() },
                onGetCapabilities = { viewModel.getCapabilities() },
                isLoading = isLoading
            )
        }

        item { Spacer(Modifier.height(60.dp)) }
    }
}

// ━━━━━━━━━━━━ اختيار المنفذ الذكي ━━━━━━━━━━━━

@Composable
private fun SmartPortSelectionSection(
    viewModel: DinstarViewModel,
    onDial: (Int, String) -> Unit
) {
    val status by viewModel.gatewayStatus.collectAsStateWithLifecycle()
    var targetNumber by remember { mutableStateOf("") }

    val optimalPort = viewModel.selectOptimalPort(targetNumber.ifBlank { null })
    val selectionDesc = viewModel.getSelectionDescription(targetNumber.ifBlank { null })

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceNavy),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Psychology, null, tint = SovereignColors.DinstarGold, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("اختيار المنفذ الذكي", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = SovereignColors.DinstarGold)
            }

            Spacer(Modifier.height(10.dp))

            // حقل إدخال الرقم
            OutlinedTextField(
                value = targetNumber,
                onValueChange = { targetNumber = it },
                label = { Text("الرقم اليمني (اختياري)") },
                placeholder = { Text("777123456") },
                leadingIcon = { Icon(Icons.Rounded.Phone, null, tint = SovereignColors.DinstarGold) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            // المنفذ المختار
            if (optimalPort != null) {
                val opColor = Color(optimalPort.simType.colorHex)
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = opColor.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, opColor.copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "المنفذ المختار: ${optimalPort.index} — ${optimalPort.operatorName}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                            Text(
                                "إشارة: ${optimalPort.signalPercent}% • $selectionDesc",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }

                        if (targetNumber.isNotBlank()) {
                            FilledTonalButton(
                                onClick = { onDial(optimalPort.index, targetNumber) },
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = opColor.copy(alpha = 0.2f))
                            ) {
                                Icon(Icons.Rounded.Call, null, modifier = Modifier.size(16.dp), tint = opColor)
                                Spacer(Modifier.width(4.dp))
                                Text("اتصال", color = opColor)
                            }
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SovereignColors.Danger.copy(alpha = 0.06f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "لا توجد منافذ متاحة للمكالمات",
                        fontSize = 12.sp,
                        color = SovereignColors.Danger,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

// ━━━━━━━━━━━━ تبويب المنافذ ━━━━━━━━━━━━

@Composable
private fun PortsTab(viewModel: DinstarViewModel) {
    val status by viewModel.gatewayStatus.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            SectionHeader("المنافذ الثمانية — تفاصيل كاملة", Icons.Rounded.SimCard, SovereignColors.DinstarGold)
        }

        if (status.ports.isEmpty()) {
            item {
                EmptyPortsState(onDiscover = { viewModel.discoverGateway() })
            }
        } else {
            items(status.ports) { port ->
                DinstarPortCard(
                    port = port,
                    onReset = { viewModel.resetPort(port.index) },
                    onUssd = { code -> viewModel.sendUssd(port.index, code) },
                    onDial = null,
                    isExpanded = true // توسيع الكل في هذا التبويب
                )
            }
        }

        // إشارات مرئية
        item {
            Spacer(Modifier.height(8.dp))
            SectionHeader("رسم الإشارات", Icons.Rounded.BarChart, SovereignColors.Success)
            SignalBarChart(status.ports)
        }

        item { Spacer(Modifier.height(60.dp)) }
    }
}

// ━━━━━━━━━━━━ رسم الإشارات ━━━━━━━━━━━━

@Composable
private fun SignalBarChart(ports: List<DinstarPort>) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceNavy),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ports.forEach { port ->
                    val signalColor = when {
                        port.signalPercent >= 60 -> SovereignColors.Success
                        port.signalPercent >= 30 -> SovereignColors.Warning
                        port.signalPercent > 0 -> SovereignColors.Danger
                        else -> Color.Gray.copy(alpha = 0.3f)
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        // العمود
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .height(120.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Gray.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(port.signalPercent / 100f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(signalColor)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("P${port.index}", fontSize = 9.sp, color = Color.Gray)
                        Text("${port.signalPercent}%", fontSize = 9.sp, color = signalColor, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ━━━━━━━━━━━━ تبويب الإحصائيات ━━━━━━━━━━━━

@Composable
private fun StatisticsTab(viewModel: DinstarViewModel) {
    val statistics by viewModel.statistics.collectAsStateWithLifecycle()
    val cdrRecords by viewModel.cdrRecords.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            SectionHeader("إحصائيات المكالمات", Icons.Rounded.Analytics, SovereignColors.Cyan)
        }

        // بطاقات إحصائية
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard("مكالمات اليوم", "${statistics.totalCallsToday}", SovereignColors.VoipBlue, Icons.Rounded.Phone, Modifier.weight(1f))
                StatCard("مدة اليوم", "${statistics.totalDurationMinutesToday}د", SovereignColors.Success, Icons.Rounded.Schedule, Modifier.weight(1f))
                StatCard("تكلفة", "${statistics.totalCostYerToday} ر.ي", SovereignColors.DinstarGold, Icons.Rounded.AttachMoney, Modifier.weight(1f))
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard("نسبة النجاح", "${(statistics.successRate * 100).toInt()}%",
                    if (statistics.successRate >= 0.8f) SovereignColors.Success else SovereignColors.Warning,
                    Icons.Rounded.TrendingUp, Modifier.weight(1f))
                StatCard("أقصى تزامن", "${statistics.peakConcurrency}", SovereignColors.Danger, Icons.Rounded.Groups, Modifier.weight(1f))
                StatCard("متوسط إشارة", "${statistics.avgSignalAllPorts}%", SovereignColors.DinstarGold, Icons.Rounded.SignalCellularAlt, Modifier.weight(1f))
            }
        }

        // CDR
        item {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SectionHeader("سجل المكالمات (CDR)", Icons.Rounded.ReceiptLong, SovereignColors.VoipBlue)
                TextButton(onClick = { viewModel.queryCdr() }) {
                    Text("تحديث", color = SovereignColors.Cyan, fontSize = 12.sp)
                }
            }
        }

        if (cdrRecords.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceNavy),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Rounded.History, null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("لا يوجد سجل مكالمات", color = Color.Gray, fontSize = 14.sp)
                        TextButton(onClick = { viewModel.queryCdr() }) {
                            Text("جلب السجل", color = SovereignColors.Cyan)
                        }
                    }
                }
            }
        } else {
            items(cdrRecords.take(30)) { record ->
                CdrRecordItem(record)
            }
        }

        item { Spacer(Modifier.height(60.dp)) }
    }
}

// ━━━━━━━━━━━━ تبويب الإعدادات ━━━━━━━━━━━━

@Composable
private fun SettingsTab(viewModel: DinstarViewModel) {
    val status by viewModel.gatewayStatus.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            SectionHeader("معلومات البوابة", Icons.Rounded.Info, SovereignColors.Cyan)
        }

        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceNavy),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingRow("الطراز", status.model)
                    SettingRow("عنوان IP", status.gatewayIp)
                    SettingRow("البروتوكول", "HTTPS (Digest Auth)")
                    SettingRow("المنافذ", "${status.ports.size}")
                    SettingRow("المسجلة", "${status.registeredCount}")
                    SettingRow("المتاحة", "${status.availableCount}")
                    SettingRow("متوسط الإشارة", "${status.averageSignal}%")
                }
            }
        }

        item {
            SectionHeader("تعليمات الإعداد", Icons.Rounded.HelpOutline, SovereignColors.Warning)
        }

        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceNavy),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("خطوات التفعيل:", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = SovereignColors.DinstarGold)
                    Spacer(Modifier.height(8.dp))
                    
                    val steps = listOf(
                        "1. افتح واجهة Dinstar: https://${status.gatewayIp}",
                        "2. اذهب إلى: Mobile Configuration → Basic Configuration",
                        "3. اختر: New-version API",
                        "4. تأكد أن firmware ≥ 1102",
                        "5. بيانات الدخول الافتراضية: admin / admin",
                        "6. الباكند يستخدم HTTP Digest Auth تلقائياً",
                        "7. المكالمات تمر عبر: التطبيق → الباكند → Asterisk → Dinstar"
                    )
                    
                    steps.forEach { step ->
                        Text(step, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 2.dp))
                    }
                    
                    Spacer(Modifier.height(12.dp))
                    
                    Text("ملاحظات مهمة:", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = SovereignColors.Warning)
                    Spacer(Modifier.height(4.dp))
                    Text("• شهادة SSL موقعة ذاتياً — الباكند يتقبلها تلقائياً", fontSize = 11.sp, color = Color.Gray)
                    Text("• لا تستخدم /api/dial مباشرة — المكالمات عبر Asterisk AMI فقط", fontSize = 11.sp, color = Color.Gray)
                    Text("• إعادة التعيين تأخذ ~3 ثواني حتى يعود المنفذ", fontSize = 11.sp, color = Color.Gray)
                }
            }
        }

        item { Spacer(Modifier.height(60.dp)) }
    }
}

@Composable
private fun SettingRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = Color.Gray)
        Text(value, fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium)
    }
}
