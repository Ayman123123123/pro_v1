package com.red.features.dinstar

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.red.core.theme.SovereignColors

/**
 * 📡 YOUNES Dinstar Tab — شريحة DINSTAR الكاملة
 * 
 * البنية: 5 أقسام رئيسية
 * ┌─────────────────────────────────────────────────────┐
 * │ 1. شريط الحالة — اتصال البوابة + IP + حالة الباكند   │
 * │ 2. ملخص إحصائي — 6 بطاقات (مسجلة/نشطة/متاحة/إشارة)  │
 * │ 3. المنافذ الثمانية — بطاقات تفاعلية لكل منفذ SIM     │
 * │ 4. سجل المكالمات CDR — آخر 20 مكالمة مع التفاصيل     │
 * │ 5. أوامر سريعة — Reset/USSD/Discover/Refresh          │
 * └─────────────────────────────────────────────────────┘
 */
@Composable
fun DinstarTab(
    viewModel: DinstarViewModel,
    onDialViaPort: ((port: Int, number: String) -> Unit)? = null,
    onNavigateToCdr: (() -> Unit)? = null
) {
    val gatewayStatus by viewModel.gatewayStatus.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val commandResult by viewModel.commandResult.collectAsStateWithLifecycle()
    val statistics by viewModel.statistics.collectAsStateWithLifecycle()

    // بدء المراقبة عند أول تركيب
    LaunchedEffect(Unit) {
        viewModel.startLiveMonitoring()
    }

    // إظهار نتيجة الأوامر كـ Snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(commandResult) {
        when (val result = commandResult) {
            is DinstarCommandResult.Success -> {
                snackbarHostState.showSnackbar(result.message, duration = SnackbarDuration.Short)
                viewModel.clearCommandResult()
            }
            is DinstarCommandResult.Error -> {
                snackbarHostState.showSnackbar("❌ ${result.message}", duration = SnackbarDuration.Long)
                viewModel.clearCommandResult()
            }
            else -> Unit
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = SovereignColors.Obsidian
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // ═══ 1. شريط حالة البوابة ═══
            item {
                DinstarGatewayBanner(
                    status = gatewayStatus,
                    connectionState = connectionState,
                    onDiscover = { viewModel.discoverGateway() },
                    onRefresh = { viewModel.refreshStatus() },
                    isLoading = isLoading
                )
            }

            // ═══ 2. ملخص إحصائي ═══
            item {
                DinstarSummaryCards(
                    status = gatewayStatus,
                    statistics = statistics
                )
            }

            // ═══ 3. توزيع المشغلين ═══
            if (gatewayStatus.ports.isNotEmpty()) {
                item {
                    OperatorDistributionSection(
                        distribution = gatewayStatus.operatorDistribution,
                        ports = gatewayStatus.ports
                    )
                }
            }

            // ═══ 4. المنافذ الثمانية ═══
            if (gatewayStatus.ports.isNotEmpty()) {
                item {
                    SectionHeader("شرائح SIM الثمانية", Icons.Rounded.SimCard, SovereignColors.DinstarGold)
                }
                
                items(gatewayStatus.ports) { port ->
                    DinstarPortCard(
                        port = port,
                        onReset = { viewModel.resetPort(port.index) },
                        onUssd = { code -> viewModel.sendUssd(port.index, code) },
                        onDial = onDialViaPort?.let { callback -> { callback(port.index, "") } },
                        isExpanded = gatewayStatus.ports.size <= 4 // توسيع تلقائي إذا ≤ 4 منافذ
                    )
                }
            } else if (connectionState == BackendConnectionState.CONNECTED && !isLoading) {
                item {
                    EmptyPortsState(onDiscover = { viewModel.discoverGateway() })
                }
            }

            // ═══ 5. أوامر سريعة ═══
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

            // ═══ 6. آخر المكالمات (CDR) ═══
            item {
                val cdr by viewModel.cdrRecords.collectAsStateWithLifecycle()
                if (cdr.isNotEmpty()) {
                    SectionHeader("آخر المكالمات", Icons.Rounded.History, SovereignColors.VoipBlue)
                    cdr.take(10).forEach { record ->
                        CdrRecordItem(record)
                    }
                    if (cdr.size > 10 && onNavigateToCdr != null) {
                        TextButton(onClick = onNavigateToCdr) {
                            Text("عرض الكل (${cdr.size})", color = SovereignColors.Cyan)
                        }
                    }
                }
            }

            // ═══ Spacer سفلي ═══
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

// ━━━━━━━━━━━━ شريط حالة البوابة ━━━━━━━━━━━━

@Composable
private fun DinstarGatewayBanner(
    status: DinstarGatewayStatus,
    connectionState: BackendConnectionState,
    onDiscover: () -> Unit,
    onRefresh: () -> Unit,
    isLoading: Boolean
) {
    val isOnline = status.isOnline
    val pulseAlpha = rememberInfiniteTransition().animateFloat(
        initialValue = 0.5f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "OnlinePulse"
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isOnline) SovereignColors.DinstarGold.copy(alpha = 0.08f) 
                            else SovereignColors.Danger.copy(alpha = 0.08f)
        ),
        border = BorderStroke(
            1.dp,
            if (isOnline) SovereignColors.DinstarGold.copy(alpha = 0.3f) 
            else SovereignColors.Danger.copy(alpha = 0.3f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // الصف الأول: اسم الجهاز + حالة الاتصال
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // أيقونة مع نبض
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (isOnline) SovereignColors.DinstarGold.copy(alpha = 0.15f)
                            else SovereignColors.Danger.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Router,
                        contentDescription = null,
                        tint = if (isOnline) SovereignColors.DinstarGold else SovereignColors.Danger,
                        modifier = Modifier.size(24.dp)
                    )
                    if (isOnline) {
                        // نقطة خضراء نابضة
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .align(Alignment.TopEnd)
                                .background(SovereignColors.Success.copy(alpha = pulseAlpha.value), CircleShape)
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "DINSTAR UC2000-VE-8G",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    Text(
                        "${status.model} • ${status.gatewayIp}",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                // شارة الحالة
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when (connectionState) {
                        BackendConnectionState.CONNECTED -> SovereignColors.Success.copy(alpha = 0.15f)
                        BackendConnectionState.CONNECTING -> SovereignColors.Warning.copy(alpha = 0.15f)
                        BackendConnectionState.DISCONNECTED -> Color.Gray.copy(alpha = 0.15f)
                        BackendConnectionState.ERROR -> SovereignColors.Danger.copy(alpha = 0.15f)
                    }
                ) {
                    Text(
                        connectionState.labelAr,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (connectionState) {
                            BackendConnectionState.CONNECTED -> SovereignColors.Success
                            BackendConnectionState.CONNECTING -> SovereignColors.Warning
                            BackendConnectionState.DISCONNECTED -> Color.Gray
                            BackendConnectionState.ERROR -> SovereignColors.Danger
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // الصف الثاني: أزرار اكتشاف + تحديث
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isOnline) {
                    SovereignGlassButton(
                        text = "🔍 اكتشاف البوابة",
                        onClick = onDiscover,
                        tintColor = SovereignColors.DinstarGold,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    SovereignGlassButton(
                        text = "🔄 تحديث",
                        onClick = onRefresh,
                        tintColor = SovereignColors.Cyan,
                        modifier = Modifier.weight(1f)
                    )
                }
                
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = SovereignColors.DinstarGold
                    )
                }
            }

            // آخر تحديث
            if (status.lastUpdated > 0) {
                Spacer(Modifier.height(6.dp))
                val elapsed = (System.currentTimeMillis() - status.lastUpdated) / 1000
                val timeAgo = when {
                    elapsed < 5 -> "الآن"
                    elapsed < 60 -> "منذ ${elapsed}ث"
                    else -> "منذ ${elapsed / 60}د"
                }
                Text(
                    "آخر تحديث: $timeAgo",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

// ━━━━━━━━━━━━ ملخص إحصائي (6 بطاقات) ━━━━━━━━━━━━

@Composable
private fun DinstarSummaryCards(
    status: DinstarGatewayStatus,
    statistics: DinstarStatistics
) {
    Column {
        SectionHeader("ملخص البوابة", Icons.Rounded.Dashboard, SovereignColors.Cyan)
        Spacer(Modifier.height(4.dp))

        // الصف الأول
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard("مسجلة", "${status.registeredCount}/8", SovereignColors.Success, Icons.Rounded.CheckCircle, Modifier.weight(1f))
            StatCard("نشطة", "${status.activeCallCount}", SovereignColors.Danger, Icons.Rounded.PhoneInTalk, Modifier.weight(1f))
            StatCard("متاحة", "${status.availableCount}", SovereignColors.Cyan, Icons.Rounded.Call, Modifier.weight(1f))
        }

        Spacer(Modifier.height(8.dp))

        // الصف الثاني
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard("متوسط الإشارة", "${status.averageSignal}%", SovereignColors.DinstarGold, Icons.Rounded.SignalCellularAlt, Modifier.weight(1f))
            StatCard("مكالمات اليوم", "${statistics.totalCallsToday}", SovereignColors.VoipBlue, Icons.Rounded.History, Modifier.weight(1f))
            StatCard("نسبة النجاح", "${(statistics.successRate * 100).toInt()}%", 
                if (statistics.successRate >= 0.8f) SovereignColors.Success else SovereignColors.Warning,
                Icons.Rounded.TrendingUp, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceNavy),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(6.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = color)
            Text(label, fontSize = 10.sp, color = Color.Gray, textAlign = TextAlign.Center)
        }
    }
}

// ━━━━━━━━━━━━ توزيع المشغلين ━━━━━━━━━━━━

@Composable
private fun OperatorDistributionSection(
    distribution: Map<YemenOperator, Int>,
    ports: List<DinstarPort>
) {
    Column {
        SectionHeader("توزيع المشغلين", Icons.Rounded.PieChart, SovereignColors.DinstarGold)
        Spacer(Modifier.height(4.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(YemenOperator.entries.filter { it != YemenOperator.UNKNOWN && it in distribution }) { operator ->
                val count = distribution[operator] ?: 0
                val operatorColor = Color(operator.colorHex)
                val avgSignal = ports.filter { it.simType == operator }.map { it.signalPercent }.average().toInt()

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = operatorColor.copy(alpha = 0.08f)),
                    border = BorderStroke(1.dp, operatorColor.copy(alpha = 0.2f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(operator.arabicName, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = operatorColor)
                        Spacer(Modifier.height(4.dp))
                        Text("$count شرائح", fontSize = 11.sp, color = Color.Gray)
                        if (count > 0) {
                            Text("إشارة: $avgSignal%", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

// ━━━━━━━━━━━━ بطاقة منفذ SIM ━━━━━━━━━━━━

@Composable
private fun DinstarPortCard(
    port: DinstarPort,
    onReset: () -> Unit,
    onUssd: (String) -> Unit,
    onDial: (() -> Unit)? = null,
    isExpanded: Boolean = false
) {
    var expanded by remember { mutableStateOf(isExpanded) }
    var ussdInput by remember { mutableStateOf("") }
    var showUssdDialog by remember { mutableStateOf(false) }

    val operatorColor = Color(port.simType.colorHex)
    val signalColor = when {
        port.signalPercent >= 60 -> SovereignColors.Success
        port.signalPercent >= 30 -> SovereignColors.Warning
        port.signalPercent > 0 -> SovereignColors.Danger
        else -> Color.Gray
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (port.callState == "ACTIVE") SovereignColors.DinstarGold.copy(alpha = 0.06f)
                           else SovereignColors.SurfaceNavy
        ),
        border = BorderStroke(
            width = when {
                port.callState == "ACTIVE" -> 1.5.dp
                !port.isAvailable -> 1.dp
                else -> 1.dp
            },
            color = when {
                port.callState == "ACTIVE" -> SovereignColors.DinstarGold.copy(alpha = 0.5f)
                !port.isAvailable -> Color.Gray.copy(alpha = 0.2f)
                else -> operatorColor.copy(alpha = 0.2f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // الصف الرئيسي
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // رقم المنفذ
                Surface(
                    shape = CircleShape,
                    color = operatorColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "${port.index}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = operatorColor
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                // المعلومات الرئيسية
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            port.operatorName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        Spacer(Modifier.width(8.dp))
                        // شارة الحالة
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = when {
                                port.callState == "ACTIVE" -> SovereignColors.DinstarGold.copy(alpha = 0.2f)
                                port.isAvailable -> SovereignColors.Success.copy(alpha = 0.15f)
                                else -> Color.Gray.copy(alpha = 0.15f)
                            }
                        ) {
                            Text(
                                port.statusDescriptionAr,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    port.callState == "ACTIVE" -> SovereignColors.DinstarGold
                                    port.isAvailable -> SovereignColors.Success
                                    else -> Color.Gray
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(2.dp))

                    // الإشارة
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // أشرطة الإشارة (0-4)
                        repeat(4) { bar ->
                            val filled = port.signalPercent > (bar * 25)
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height((8 + bar * 4).dp)
                                    .background(
                                        if (filled) signalColor else Color.Gray.copy(alpha = 0.2f),
                                        RoundedCornerShape(1.dp)
                                    )
                            )
                            Spacer(Modifier.width(2.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("${port.signalPercent}%", fontSize = 12.sp, color = signalColor, fontWeight = FontWeight.Medium)
                        if (port.numberMasked != null) {
                            Spacer(Modifier.width(12.dp))
                            Text(port.numberMasked, fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }

                // زر التوسيع
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    null,
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
            }

            // ═══ القسم الموسع ═══
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.1f))
                    Spacer(Modifier.height(10.dp))

                    // تفاصيل إضافية
                    DetailRow("نوع الراديو", port.radioType)
                    DetailRow("حالة التسجيل", port.registrationState)
                    DetailRow("حالة المكالمة", port.callState)
                    DetailRow("GPRS", port.gprsState)
                    if (port.imsiMasked != null) DetailRow("IMSI", port.imsiMasked)
                    if (port.iccidMasked != null) DetailRow("ICCID", port.iccidMasked)

                    Spacer(Modifier.height(10.dp))

                    // أزرار التحكم
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // زر إعادة تعيين
                        OutlinedButton(
                            onClick = onReset,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, SovereignColors.Warning.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SovereignColors.Warning),
                            modifier = Modifier.weight(1f).height(40.dp)
                        ) {
                            Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("إعادة تعيين", fontSize = 11.sp)
                        }

                        // زر USSD
                        OutlinedButton(
                            onClick = { showUssdDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, SovereignColors.Cyan.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SovereignColors.Cyan),
                            modifier = Modifier.weight(1f).height(40.dp)
                        ) {
                            Icon(Icons.Rounded.Dialpad, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("USSD", fontSize = 11.sp)
                        }

                        // زر اتصال (إذا متاح)
                        if (onDial != null && port.isAvailable) {
                            OutlinedButton(
                                onClick = onDial,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, SovereignColors.DinstarGold.copy(alpha = 0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = SovereignColors.DinstarGold),
                                modifier = Modifier.weight(1f).height(40.dp)
                            ) {
                                Icon(Icons.Rounded.Call, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("اتصال", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // حوار USSD
    if (showUssdDialog) {
        AlertDialog(
            onDismissRequest = { showUssdDialog = false },
            title = { Text("إرسال USSD — منفذ ${port.index}") },
            text = {
                OutlinedTextField(
                    value = ussdInput,
                    onValueChange = { ussdInput = it },
                    label = { Text("الكود (مثل: *123#)") },
                    placeholder = { Text("*123#") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (ussdInput.isNotBlank()) {
                            onUssd(ussdInput)
                            ussdInput = ""
                            showUssdDialog = false
                        }
                    }
                ) { Text("إرسال", color = SovereignColors.Cyan) }
            },
            dismissButton = {
                TextButton(onClick = { showUssdDialog = false }) { Text("إلغاء") }
            }
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, fontSize = 11.sp, color = Color.Gray, modifier = Modifier.width(100.dp))
        Text(value, fontSize = 11.sp, color = Color.White)
    }
}

// ━━━━━━━━━━━━ حالة فارغة ━━━━━━━━━━━━

@Composable
private fun EmptyPortsState(onDiscover: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceNavy),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Rounded.Router,
                null,
                tint = SovereignColors.DinstarGold.copy(alpha = 0.3f),
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text("لم يتم اكتشاف منافذ", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text(
                "تأكد من أن الباكند متصل بجهاز Dinstar\nوأن New Version API مُفعّل في إعدادات الجهاز",
                fontSize = 12.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            SovereignGlassButton(
                text = "🔍 اكتشاف الآن",
                onClick = onDiscover,
                tintColor = SovereignColors.DinstarGold
            )
        }
    }
}

// ━━━━━━━━━━━━ أوامر سريعة ━━━━━━━━━━━━

@Composable
private fun QuickActionsRow(
    onRefresh: () -> Unit,
    onDiscover: () -> Unit,
    onQueryCdr: () -> Unit,
    onGetCapabilities: () -> Unit,
    isLoading: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuickActionChip("🔄 تحديث", SovereignColors.Cyan, onRefresh, isLoading, Modifier.weight(1f))
        QuickActionChip("🔍 اكتشاف", SovereignColors.DinstarGold, onDiscover, isLoading, Modifier.weight(1f))
        QuickActionChip("📋 سجل", SovereignColors.VoipBlue, onQueryCdr, isLoading, Modifier.weight(1f))
        QuickActionChip("⚙️ قدرات", SovereignColors.Success, onGetCapabilities, isLoading, Modifier.weight(1f))
    }
}

@Composable
private fun QuickActionChip(
    text: String,
    color: Color,
    onClick: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = { if (!isLoading) onClick() },
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.2f)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = color)
            } else {
                Text(text, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = color)
            }
        }
    }
}

// ━━━━━━━━━━━━ سجل CDR ━━━━━━━━━━━━

@Composable
private fun CdrRecordItem(record: DinstarCdr) {
    val operatorColor = Color(record.operator.colorHex)
    
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceNavy.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // أيقونة المشغل
            Box(
                modifier = Modifier.size(36.dp).background(operatorColor.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (record.direction == "outgoing") Icons.Rounded.CallMade else Icons.Rounded.CallReceived,
                    null, tint = operatorColor, modifier = Modifier.size(16.dp)
                )
            }

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(record.phoneNumber, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White)
                Text(
                    "${record.operator.arabicName} • منفذ ${record.port}",
                    fontSize = 10.sp, color = Color.Gray
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(record.formattedDuration, fontSize = 12.sp, color = Color.White)
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (record.callState == "COMPLETED") SovereignColors.Success.copy(alpha = 0.12f) else SovereignColors.Danger.copy(alpha = 0.12f)
                ) {
                    Text(
                        if (record.callState == "COMPLETED") "مكتمل" else record.callState,
                        fontSize = 9.sp,
                        color = if (record.callState == "COMPLETED") SovereignColors.Success else SovereignColors.Danger,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

// ━━━━━━━━━━━━ مكونات مشتركة ━━━━━━━━━━━━

@Composable
private fun SectionHeader(
    title: String,
    icon: ImageVector,
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
private fun SovereignGlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tintColor: Color = SovereignColors.Cyan
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(42.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, tintColor.copy(alpha = 0.4f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = tintColor.copy(alpha = 0.06f),
            contentColor = tintColor
        )
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}
