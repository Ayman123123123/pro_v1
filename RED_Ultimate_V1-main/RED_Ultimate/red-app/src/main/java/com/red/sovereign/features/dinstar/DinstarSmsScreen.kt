package com.red.sovereign.features.dinstar

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.PlexArabicFamily
import com.red.sovereign.ui.theme.SovereignColors
import com.red.sovereign.ui.theme.YounesEmerald
import com.red.sovereign.ui.theme.YounesRose
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 📱 شاشة SMS الإدارية عبر بوابة DINSTAR — إرسال مجمّع، وارد، نتائج.
 *
 * التوحيد (2026-09-05) دمج نسختين متنافستين:
 * - نسخة 28 أغسطس: فقاعات محادثة بسيطة، رقم واحد فقط، بلا اختيار ترميز ولا
 *   منافذ، ولا عرض للوارد المخزّن على الجهاز.
 * - نسخة 4 سبتمبر: ثلاثة تبويبات (إرسال/واردة/نتائج) بترميز واختيار منافذ.
 *
 * هذه النسخة تحتفظ بالتبويبات الثلاثة وتضيف إليها ما كان حصراً في الأقدم:
 * زر الرجوع، سجل الجلسة كفقاعات في تبويب الإرسال، وهوية الحزمة البصرية
 * ([SovereignColors]/[PlexArabicFamily]) بدل ألوان مكتوبة يدوياً.
 *
 * عدّاد المقاطع يستخدم قاعدة 3GPP TS 23.038 الحقيقية: GSM‑7 يعدّ **بالحروف**
 * (160 ثم 153) وUCS2 يعدّ بالحروف أيضاً (70 ثم 67). النسخة السابقة كانت
 * تعدّ بايتات UTF‑8 وتحسب `160 - byteCount / 7 * 7` — وهذا لا يطابق أي
 * ترميز GSM، فكان الرقم المعروض خاطئاً لكل نص عربي.
 */
@Composable
fun DinstarSmsScreen(viewModel: DinstarViewModel, onBack: () -> Unit) {
    val gatewayStatus by viewModel.gatewayStatus.collectAsState()
    val incomingSms by viewModel.incomingSms.collectAsState()
    val smsSendResults by viewModel.smsSendResults.collectAsState()
    val smsQueueCount by viewModel.smsQueueCount.collectAsState()
    val commandResult by viewModel.commandResult.collectAsState()
    val smsHistory by viewModel.smsHistory.collectAsState()
    // بوابة العتاد الإدارية من GET /health/detailed — false تعني الإنترنت فقط.
    val dinstarEnabled by viewModel.dinstarEnabled.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var smsText by remember { mutableStateOf("") }
    var recipientsText by remember { mutableStateOf("") }
    var selectedPorts by remember { mutableStateOf(setOf<Int>()) }
    var encoding by remember { mutableStateOf("AUTO") }

    // جلب الوارد وطابور الجهاز مرة عند الدخول — المقبس الحي يوصل الجديد فقط،
    // فبدون هذا يبدو تبويب «واردة» فارغاً وإن كانت البوابة تحمل رسائل.
    LaunchedEffect(Unit) {
        viewModel.fetchDinstarEnabled()
        viewModel.fetchIncomingSms()
        viewModel.fetchQueueCount()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SovereignColors.ObsidianDeep)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(40.dp).clip(CircleShape).background(SovereignColors.SurfaceCard)
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "رجوع", tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Icon(Icons.Default.Sms, null, tint = AqyalGold, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "SMS عبر DINSTAR",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    fontFamily = PlexArabicFamily,
                    color = Color.White
                )
                Text(
                    "${gatewayStatus.registeredCount} شريحة مسجّلة · ${gatewayStatus.availableCount} جاهزة",
                    fontSize = 11.sp,
                    color = SovereignColors.GoldNeon
                )
            }
            if (smsQueueCount > 0 && dinstarEnabled != false) {
                Badge(containerColor = AqyalGold) {
                    Text("$smsQueueCount", color = SovereignColors.ObsidianDeep, fontSize = 10.sp)
                }
            }
        }

        // العتاد معطل إدارياً (red.dinstar.enabled=false) — وضع الإنترنت فقط:
        // تُخفى أقسام الأسطول كلها وتُعرض بطاقة تفسيرية بدل التبويبات.
        if (dinstarEnabled == false) {
            DinstarDisabledCard(
                onRetry = {
                    viewModel.fetchDinstarEnabled()
                    viewModel.refreshStatus()
                }
            )
            return@Column
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = SovereignColors.SurfaceCard,
            contentColor = AqyalGold
        ) {
            listOf(
                "إرسال" to null,
                "واردة" to incomingSms.size,
                "نتائج" to smsSendResults.size
            ).forEachIndexed { index, (title, count) ->
                Tab(selected = selectedTab == index, onClick = { selectedTab = index }) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            title,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == index) AqyalGold else Color.Gray
                        )
                        if (count != null && count > 0) {
                            Spacer(Modifier.width(5.dp))
                            Box(
                                Modifier.clip(CircleShape).background(AqyalGold).padding(horizontal = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "$count",
                                    color = SovereignColors.ObsidianDeep,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedContent(targetState = selectedTab, label = "SmsTab") { tab ->
            when (tab) {
                0 -> SmsSendTab(
                    viewModel = viewModel,
                    smsText = smsText, onSmsTextChange = { smsText = it },
                    recipientsText = recipientsText, onRecipientsChange = { recipientsText = it },
                    selectedPorts = selectedPorts, onPortsChange = { selectedPorts = it },
                    encoding = encoding, onEncodingChange = { encoding = it },
                    gatewayStatus = gatewayStatus,
                    commandResult = commandResult,
                    sessionHistory = smsHistory,
                    onSent = { smsText = "" },
                    onDismissResult = { viewModel.clearCommandResult() }
                )
                1 -> SmsIncomingTab(viewModel, incomingSms)
                else -> SmsResultsTab(smsSendResults)
            }
        }
    }
}

@Composable
private fun SmsSendTab(
    viewModel: DinstarViewModel,
    smsText: String, onSmsTextChange: (String) -> Unit,
    recipientsText: String, onRecipientsChange: (String) -> Unit,
    selectedPorts: Set<Int>, onPortsChange: (Set<Int>) -> Unit,
    encoding: String, onEncodingChange: (String) -> Unit,
    gatewayStatus: DinstarGatewayStatus,
    commandResult: DinstarCommandResult?,
    sessionHistory: List<DinstarSms>,
    onSent: () -> Unit,
    onDismissResult: () -> Unit
) {
    val isSending = commandResult is DinstarCommandResult.Loading
    val numbers = remember(recipientsText) {
        recipientsText.split(',', '\n', ';').map { it.trim() }.filter { it.isNotBlank() }
    }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            OutlinedTextField(
                value = recipientsText, onValueChange = onRecipientsChange,
                label = { Text("الأرقام (مفصولة بفاصلة)") },
                placeholder = { Text("777123456, 777987654") },
                leadingIcon = { Icon(Icons.Default.Phone, null, tint = AqyalGold) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = goldFieldColors(),
                minLines = 2, maxLines = 4
            )
        }

        item {
            OutlinedTextField(
                value = smsText, onValueChange = onSmsTextChange,
                label = { Text("محتوى الرسالة") },
                placeholder = { Text("اكتب رسالتك هنا…") },
                leadingIcon = { Icon(Icons.Default.Chat, null, tint = AqyalGold) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = goldFieldColors(),
                minLines = 3, maxLines = 8
            )
            val segments = gatewaySegmentCount(smsText, encoding)
            Text(
                if (segments <= 1) "${smsText.length} حرف · مقطع واحد"
                else "${smsText.length} حرف · $segments مقاطع (تُحاسب كـ $segments رسائل)",
                fontSize = 10.sp,
                color = if (segments > 1) AqyalGold else Color.Gray
            )
        }

        item {
            Text("الترميز:", fontSize = 13.sp, color = Color.Gray)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("AUTO" to "تلقائي", "GSM7BIT" to "GSM 7-bit", "UCS2" to "UCS2 (عربي)")
                    .forEach { (value, label) ->
                        FilterChip(
                            selected = encoding == value,
                            onClick = { onEncodingChange(value) },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
            }
            // العربية لا يحملها GSM‑7؛ الإجبار عليه يصل «?????» عند المستلم.
            if (encoding == "GSM7BIT" && smsText.any { it.code > 0x7F }) {
                Text(
                    "النص يحتوي حروفاً غير لاتينية — GSM 7-bit سيُفقدها. استخدم «تلقائي» أو UCS2.",
                    fontSize = 10.sp, color = YounesRose
                )
            }
        }

        item {
            Text("المنافذ (فارغ = يختار الجهاز تلقائياً):", fontSize = 13.sp, color = Color.Gray)
            Spacer(Modifier.height(6.dp))
            val available = gatewayStatus.ports.filter { it.isAvailable }
            if (available.isEmpty()) {
                Text("لا منافذ جاهزة حالياً — تحقق من التسجيل والإشارة", fontSize = 11.sp, color = YounesRose)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    available.take(8).forEach { port ->
                        val isSelected = port.index in selectedPorts
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                onPortsChange(
                                    if (isSelected) selectedPorts - port.index else selectedPorts + port.index
                                )
                            },
                            label = { Text("P${port.index} ${port.signalPercent ?: "--"}%", fontSize = 10.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.SimCard, null, modifier = Modifier.size(14.dp), tint = port.simType.color)
                            }
                        )
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    if (smsText.isNotBlank() && numbers.isNotEmpty()) {
                        viewModel.sendSms(
                            text = smsText, numbers = numbers,
                            ports = selectedPorts.toList(), encoding = encoding
                        )
                        onSent()
                    }
                },
                enabled = smsText.isNotBlank() && numbers.isNotEmpty() && !isSending,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AqyalGold),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp), strokeWidth = 2.dp,
                        color = SovereignColors.ObsidianDeep
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send, null,
                        modifier = Modifier.size(18.dp), tint = SovereignColors.ObsidianDeep
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (numbers.isEmpty()) "إرسال" else "إرسال إلى ${numbers.size} رقم",
                        fontWeight = FontWeight.Bold, color = SovereignColors.ObsidianDeep
                    )
                }
            }
        }

        commandResult?.let { result ->
            item {
                val (message, color) = when (result) {
                    is DinstarCommandResult.Success -> result.message to YounesEmerald
                    is DinstarCommandResult.Error -> result.message to YounesRose
                    DinstarCommandResult.Loading -> "جارٍ الإرسال عبر البوابة…" to Color.Gray
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(message, fontSize = 12.sp, color = color, modifier = Modifier.weight(1f))
                    if (result !is DinstarCommandResult.Loading) {
                        TextButton(onClick = onDismissResult) { Text("إخفاء", fontSize = 11.sp) }
                    }
                }
            }
        }

        // سجل الجلسة كفقاعات — الميزة الوحيدة التي كانت حصراً في نسخة أغسطس
        if (sessionHistory.isNotEmpty()) {
            item {
                HorizontalDivider(color = SovereignColors.GlassBorder)
                Spacer(Modifier.height(6.dp))
                Text("سجل هذه الجلسة", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AqyalGold)
            }
            items(sessionHistory, key = { it.id }) { msg ->
                val isOut = msg.direction == "OUT"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isOut) Arrangement.End else Arrangement.Start
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 300.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isOut) AqyalGold.copy(alpha = .18f) else SovereignColors.SurfaceCard)
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                if (isOut) "إلى ${msg.number}" else "من ${msg.number}",
                                fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SovereignColors.GoldNeon
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(msg.content, fontSize = 13.sp, color = Color.White)
                            Text(
                                timeFormat.format(Date(msg.timestamp)),
                                fontSize = 9.sp, color = Color.Gray,
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmsIncomingTab(viewModel: DinstarViewModel, incomingSms: List<DinstarIncomingSms>) {
    val timeFormat = remember { SimpleDateFormat("d/M HH:mm", Locale.getDefault()) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "الرسائل الواردة", color = AqyalGold,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
            )
            Button(
                onClick = { viewModel.fetchIncomingSms() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = SovereignColors.SurfaceCard, contentColor = AqyalGold
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("جلب الآن", fontSize = 12.sp)
            }
        }

        if (incomingSms.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Inbox, null,
                        tint = Color.Gray.copy(alpha = .3f), modifier = Modifier.size(64.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("لا توجد رسائل واردة", color = Color.Gray, fontSize = 14.sp)
                    Text(
                        "تصل هنا فوراً عبر الاتصال الحي، أو اضغط «جلب الآن»",
                        color = Color.Gray.copy(alpha = .7f), fontSize = 11.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(incomingSms, key = { "${it.number}:${it.receivedAt}:${it.text.hashCode()}" }) { sms ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceCard)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.SimCard, null, tint = AqyalGold, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(sms.number, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                                Spacer(Modifier.weight(1f))
                                Text(timeFormat.format(Date(sms.receivedAt)), fontSize = 10.sp, color = Color.Gray)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(sms.text, fontSize = 13.sp, color = Color.White.copy(alpha = .9f))
                            Row {
                                if (sms.port >= 0) Text("منفذ ${sms.port}", fontSize = 10.sp, color = Color.Gray)
                                sms.gatewayId?.let {
                                    if (sms.port >= 0) Text(" · ", fontSize = 10.sp, color = Color.Gray)
                                    Text("بوابة $it", fontSize = 10.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmsResultsTab(results: List<DinstarSmsResult>) {
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    if (results.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.History, null,
                    tint = Color.Gray.copy(alpha = .3f), modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text("لا توجد نتائج بعد", color = Color.Gray, fontSize = 14.sp)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(results, key = { it.hashCode() }) { result ->
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceCard)
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (result.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                            null,
                            tint = if (result.isSuccess) YounesEmerald else YounesRose,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                result.numbers.joinToString(", "),
                                fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Medium
                            )
                            val portsTxt = if (result.ports.isEmpty()) "منافذ تلقائية"
                                else "منافذ ${result.ports.joinToString(",")}"
                            Text(
                                "$portsTxt · ${result.numbers.size} رقم · ${result.encoding} · ${timeFormat.format(Date(result.at))}",
                                fontSize = 10.sp, color = Color.Gray
                            )
                            result.queueCount?.let {
                                Text("في طابور الجهاز: $it", fontSize = 10.sp, color = SovereignColors.GoldNeon)
                            }
                        }
                        Text(
                            result.status, fontSize = 11.sp,
                            color = if (result.isSuccess) YounesEmerald else YounesRose
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun goldFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = SovereignColors.GoldNeon,
    unfocusedBorderColor = SovereignColors.GlassBorder
)

/**
 * بطاقة «وضع الإنترنت فقط» — تُعرض بدل أقسام الأسطول عندما يقرأ
 * التطبيق `dinstar.enabled=false` من GET /health/detailed.
 * العتاد معطل إدارياً (red.dinstar.enabled=false) لا عطل شبكة.
 */
@Composable
private fun DinstarDisabledCard(onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceCard)
        ) {
            Column(
                Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.SimCard, null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                Text(
                    "وضع الإنترنت فقط — العتاد معطل إدارياً",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    fontFamily = PlexArabicFamily,
                    color = Color.White
                )
                Text(
                    "عطّل المسؤول بوابات DINSTAR من إعدادات الخادم (red.dinstar.enabled=false). " +
                        "المكالمات والرسائل عبر الإنترنت تعمل طبيعياً.",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                OutlinedButton(onClick = onRetry) { Text("إعادة التحقق من حالة العتاد", fontSize = 12.sp) }
            }
        }
    }
}

/**
 * عدد مقاطع SMS وفق 3GPP TS 23.038.
 *
 * GSM‑7: 160 حرفاً لمقطع واحد، ثم 153 لكل مقطع (7 خانات لرأس التسلسل).
 * UCS2: 70 حرفاً لمقطع واحد، ثم 67. `AUTO` يختار UCS2 عند وجود أي حرف
 * خارج ASCII — وهو نفس ما يفعله الخادم، فلا يتفرّع تقديران مختلفان.
 */
internal fun gatewaySegmentCount(text: String, encoding: String): Int {
    if (text.isEmpty()) return 0
    val ucs2 = when (encoding.uppercase(Locale.ROOT)) {
        "UCS2" -> true
        "GSM7BIT" -> false
        else -> text.any { it.code > 0x7F }
    }
    val single = if (ucs2) 70 else 160
    val multi = if (ucs2) 67 else 153
    return if (text.length <= single) 1 else 1 + (text.length - single + multi - 1) / multi
}
