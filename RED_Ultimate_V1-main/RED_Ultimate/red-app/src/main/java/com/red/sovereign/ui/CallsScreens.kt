package com.red.sovereign.ui

/**
 * شاشات المكالمات: السجلّ الموحَّد ولوحة الاتصال عبر بوابة DINSTAR.
 *
 * استُخرجت من `RedDashboard.kt` ضمن تفكيك الملف الضخم. المجموعة
 * مستقلّة: لا تحتاج من بقيّة اللوحة سوى الثابت `RED_ID_PATTERN`،
 * وتُصدِّر مدخلين اثنين فقط تستدعيهما اللوحة.
 *
 * مكالمة البوابة (PSTN) لا تمرّ بمحرّك WebRTC، لذا لا تُعرض هنا
 * أزرار الكتم أو مكبّر الصوت أو التسجيل — العتاد لا يدعمها.
 *
 * لم يتغيّر أي سطر منطق أثناء النقل — النقل بنيوي بحت.
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.red.sovereign.auth.AuthState
import com.red.sovereign.auth.AuthViewModel
import com.red.sovereign.auth.PstnState
import com.red.sovereign.calls.CallHistoryItem
import com.red.sovereign.calls.CallHistoryViewModel
import com.red.sovereign.calls.ConferenceService
import com.red.sovereign.calls.LiveStreamService
import com.red.sovereign.calls.YemeniOperatorDetector
import com.red.sovereign.calls.YounesCallService
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.YounesEmerald
import java.util.UUID

@Composable
internal fun UnifiedCallsScreen(ownUserId: String, history: CallHistoryViewModel, onExplore: () -> Unit, onPstn: () -> Unit = {}) {
    var filter by remember { mutableStateOf("الكل") }
    var showNewCallDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showLiveDialog by remember { mutableStateOf(false) }
    var showSpaceDialog by remember { mutableStateOf(false) }
    var showDinstarDialog by remember { mutableStateOf(false) }
    var dinstarNumberInput by remember { mutableStateOf("") }
    var newCallTargetInput by remember { mutableStateOf("") }
    var isSpaceHost by remember { mutableStateOf(false) }
    var isBroadcaster by remember { mutableStateOf(false) }
    var roomInput by remember { mutableStateOf("") }
    val context = LocalContext.current
    val visible = history.calls.filter { call -> when (filter) {
        "فائتة" -> call.status == "MISSED"; "صوت" -> call.type == "VOICE"; "فيديو" -> call.type == "VIDEO"
        "جماعية" -> call.type == "GROUP"; "بث" -> call.type == "LIVE"; "مساحات" -> call.type == "SPACE"
        "DINSTAR" -> call.route == "DINSTAR"; else -> true
    } }
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Text("مركز المكالمات", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("الفردي يرن. المؤتمر والمساحة والبث أزرار مستقلة هنا — ليست أزرار الدردشة.", color = Color.LightGray, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        CallsHubLaunchers(
            onNewCall = { showNewCallDialog = true },
            onGroupCallPicker = { showNewCallDialog = true },
            onConference = { showJoinDialog = true },
            onSpace = { showSpaceDialog = true },
            onLive = { showLiveDialog = true },
            onScheduledCalls = { },
            onExplore = onExplore,
            onPstn = onPstn
        )
        Spacer(Modifier.height(16.dp))
        Text("السجل", color = Color.White.copy(0.7f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(listOf("الكل", "فائتة", "صوت", "فيديو", "جماعية", "بث", "مساحات", "DINSTAR")) { title -> FilterChip(filter == title, { filter = title }, { Text(title) }) }
        }
        when {
            history.loading -> Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AqyalGold) }
            history.error != null -> EmptyState(Icons.Default.History, "تعذر تحميل السجل", history.error.orEmpty())
            visible.isEmpty() -> EmptyState(Icons.Default.History, "لا توجد مكالمات", "ستظهر هنا كل المكالمات مع شارة توضح مسار يونس أو DINSTAR.")
            else -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { items(visible, key = { it.id }) { CallHistoryRow(it) } }
        }
    }

    if (showJoinDialog) {
        AlertDialog(
            onDismissRequest = { showJoinDialog = false; roomInput = "" },
            title = { Text("الانضمام إلى مؤتمر جماعي") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("أدخل اسم الغرفة أو معرف المؤتمر للاتصال الآمن عبر SFU:", color = Color.Gray, fontSize = 14.sp)
                    OutlinedTextField(
                        value = roomInput,
                        onValueChange = { roomInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("معرف الغرفة (مثال: red-room-123)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showJoinDialog = false
                        ConferenceService.join(context, roomInput.trim(), ownUserId, true, asHost = true)
                        roomInput = ""
                    },
                    enabled = roomInput.trim().isNotBlank()
                ) {
                    Text("انضمام")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinDialog = false; roomInput = "" }) {
                    Text("إلغاء")
                }
            }
        )
    }

    var streamTitleInput by remember { mutableStateOf("") }
    var isPrivateStream by remember { mutableStateOf(false) }
    var streamPasswordInput by remember { mutableStateOf("") }

    if (showLiveDialog) {
        AlertDialog(
            onDismissRequest = { showLiveDialog = false; roomInput = ""; streamTitleInput = ""; streamPasswordInput = ""; isPrivateStream = false; isBroadcaster = false },
            title = { Text("مركز البث المباشر 🔴") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("انضم لمشاهدة بث عام عبر البحث بالاسم/المعرف أو أنشئ بثك الخاص:", color = Color.Gray, fontSize = 13.sp)
                    
                    OutlinedTextField(
                        value = roomInput,
                        onValueChange = { roomInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("معرف البث أو رابط الدعوة (مثال: stream-123)") },
                        singleLine = true
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(checked = isBroadcaster, onCheckedChange = { isBroadcaster = it })
                        Text("بدء البث كمنتج (Broadcaster)", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }

                    if (isBroadcaster) {
                        OutlinedTextField(
                            value = streamTitleInput,
                            onValueChange = { streamTitleInput = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("عنوان البث (مثال: بث سيادي عام)") },
                            singleLine = true
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(checked = isPrivateStream, onCheckedChange = { isPrivateStream = it })
                            Text("بث خاص بكلمة سر 🔒", fontSize = 14.sp)
                        }

                        if (isPrivateStream) {
                            OutlinedTextField(
                                value = streamPasswordInput,
                                onValueChange = { streamPasswordInput = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("كلمة سر البث الخاص") },
                                singleLine = true
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLiveDialog = false
                        val finalStreamId = roomInput.trim().ifBlank { "stream_${UUID.randomUUID().toString().take(8)}" }
                        LiveStreamService.start(context, finalStreamId, ownUserId, isBroadcaster, streamTitleInput.trim().ifBlank { "بث مباشر يونس" })
                        roomInput = ""
                        streamTitleInput = ""
                        streamPasswordInput = ""
                        isPrivateStream = false
                    },
                    enabled = roomInput.trim().isNotBlank() || isBroadcaster
                ) {
                    Text(if (isBroadcaster) "إنشاء وبدء البث" else "انضمام للبث")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLiveDialog = false; roomInput = ""; streamTitleInput = ""; streamPasswordInput = ""; isPrivateStream = false; isBroadcaster = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    // 🎙️ حوار المساحات الصوتية — غرفة صوتية جماعية (مؤتمر بلا فيديو)
    if (showSpaceDialog) {
        AlertDialog(
            onDismissRequest = { showSpaceDialog = false; roomInput = ""; isSpaceHost = false },
            title = { Text("مساحة صوتية يونس") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "مساحة صوتية مشفرة عبر خادم SFU — صوت فقط، بلا كاميرا.\nاترك الحقل فارغًا لإنشاء غرفة جديدة بمعرّف تلقائي.",
                        color = Color.Gray, fontSize = 14.sp
                    )
                    OutlinedTextField(
                        value = roomInput,
                        onValueChange = { roomInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("معرف المساحة (اختياري — مثال: majlis-01)") },
                        singleLine = true
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(checked = isSpaceHost, onCheckedChange = { isSpaceHost = it })
                        Text("الانضمام كمضيف (متحدث)", fontSize = 14.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSpaceDialog = false
                        // معرف تلقائي فريد إن لم يُدخل المستخدم واحدًا
                        val spaceId = roomInput.trim().ifBlank { "space-${ownUserId.lowercase()}-${System.currentTimeMillis() % 100000}" }
                        // video=false → مسار صوتي صرف — هذا هو الفرق بين المساحة والمؤتمر المرئي
                        ConferenceService.join(context, spaceId, ownUserId, false, asHost = isSpaceHost || roomInput.isBlank())
                        roomInput = ""
                        isSpaceHost = false
                    }
                ) {
                    Text(if (roomInput.isBlank()) "إنشاء مساحة جديدة" else "دخول المساحة")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSpaceDialog = false; roomInput = ""; isSpaceHost = false }) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (showDinstarDialog) {
        val operator = YemeniOperatorDetector.getOperatorInfo(dinstarNumberInput)
        AlertDialog(
            onDismissRequest = { showDinstarDialog = false; dinstarNumberInput = "" },
            title = { Text("لوحة اتصال الهاتف اليمني (DINSTAR GSM)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("اتصال آمن ومباشر بأي رقم هاتف يمني ثابت أو محمول عبر بوابات Dinstar GSM:", color = Color.Gray, fontSize = 13.sp)
                    
                    OutlinedTextField(
                        value = dinstarNumberInput,
                        onValueChange = { dinstarNumberInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("أدخل الرقم (مثال: 777123456)") },
                        singleLine = true
                    )

                    if (operator != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(operator.brandColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("الشبكة المكتشفة: ${operator.name}", color = operator.brandColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(operator.technology, color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDinstarDialog = false
                        dinstarNumberInput = ""
                        onPstn()
                    }
                ) {
                    Text("فتح الهاتف اليمني")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDinstarDialog = false; dinstarNumberInput = "" }) {
                    Text("إلغاء")
                }
            }
        )
    }

    if (showNewCallDialog) {
        AlertDialog(
            onDismissRequest = { showNewCallDialog = false; newCallTargetInput = "" },
            title = { Text("مكالمة جديدة مشفرة E2EE 📞") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("أدخل معرّف يونس (RED ID) الخاص بصديقك للاتصال المشفر الفوري:", color = Color.Gray, fontSize = 13.sp)
                    OutlinedTextField(
                        value = newCallTargetInput,
                        onValueChange = { newCallTargetInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("معرف يونس (مثال: red-user-123)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            showNewCallDialog = false
                            YounesCallService.start(context, newCallTargetInput.trim(), video = false)
                            newCallTargetInput = ""
                        },
                        enabled = newCallTargetInput.trim().isNotBlank()
                    ) {
                        Text("صوتية")
                    }
                    Button(
                        onClick = {
                            showNewCallDialog = false
                            YounesCallService.start(context, newCallTargetInput.trim(), video = true)
                            newCallTargetInput = ""
                        },
                        enabled = newCallTargetInput.trim().isNotBlank()
                    ) {
                        Text("فيديو")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewCallDialog = false; newCallTargetInput = "" }) {
                    Text("إلغاء")
                }
            }
        )
    }

}

@Composable

private fun CallHistoryRow(call: CallHistoryItem) {
    val context = androidx.compose.ui.platform.LocalContext.current
    return Card(Modifier.fillMaxWidth().clickable {
        when (call.type) {
            "LIVE" -> LiveStreamService.start(context, call.id, call.peerId, false)
            "SPACE" -> ConferenceService.join(context, call.id, call.peerId, false, asHost = false)
            "GROUP" -> ConferenceService.join(context, call.id, call.peerId, true, asHost = false)
            else -> if (call.peerId.matches(Regex(com.red.sovereign.core.YounesId.PATTERN)) && call.route != "DINSTAR") {
                YounesCallService.start(context, call.peerId, call.type == "VIDEO")
            }
        }
    }) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        val glyph = callTypeGlyph(call.type, call.route)
        Box(Modifier.size(44.dp).clip(CircleShape).background(glyph.second.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
            Icon(glyph.first, null, tint = glyph.second)
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(call.peerLabel.ifBlank { call.peerId }, fontWeight = FontWeight.Bold)
            Text(
                buildString {
                    append(if (call.direction == "OUTGOING") "صادرة" else "واردة")
                    append(" · ")
                    append(call.status)
                    val durationSec = (call.endedAt?.toLongOrNull() ?: 0L) - (call.answeredAt?.toLongOrNull() ?: 0L)
                    if (durationSec > 0) {
                        append(" · ")
                        val mm = durationSec / 60; val ss = durationSec % 60
                        append("%d:%02d".format(mm, ss))
                    }
                },
                color = if (call.status == "MISSED") Color.Red else Color.Gray,
                fontSize = 12.sp
            )
        }
        AssistChip({}, { Text(if (call.route == "DINSTAR") "DINSTAR صوت" else "يونس ${call.type}") }, enabled = false)
        }
    }
}

@Composable

private fun RoundCallAction(icon: ImageVector, title: String, color: Color, enabled: Boolean, onClick: () -> Unit = {}) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    FilledIconButton(onClick, Modifier.size(62.dp), enabled = enabled) { Icon(icon, title, tint = if (enabled) color else Color.Gray, modifier = Modifier.size(30.dp)) }
    Text(title, fontSize = 11.sp); if (!enabled) Text("قيد الربط", color = Color.Gray, fontSize = 9.sp)
}

@Composable

internal fun DinstarPhoneScreen(account: AuthState.Authenticated, viewModel: AuthViewModel, history: CallHistoryViewModel? = null) {
    var tab by remember { mutableIntStateOf(0) }
    // 📞 أكثر الأرقام اليمنية اتصالًا — تُشتق من سجل DINSTAR الحقيقي (لا بيانات وهمية)
    val dinstarCalls = history?.calls?.filter { it.route == "DINSTAR" }.orEmpty()
    val favorites = dinstarCalls.groupingBy { it.peerLabel.ifBlank { it.peerId } }.eachCount()
        .entries.sortedByDescending { it.value }.take(8).map { it.key }
    Column(Modifier.fillMaxSize()) {
        Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp), colors = CardDefaults.cardColors(containerColor = AqyalGold.copy(alpha = .14f))) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SimCard, null, tint = AqyalGold, modifier = Modifier.size(35.dp)); Column(Modifier.padding(start = 12.dp)) {
                    Text("الهاتف اليمني عبر DINSTAR", fontWeight = FontWeight.Bold, color = AqyalGold)
                    Text(if (account.pstnEnabled) "مصرح لك — مكالمات صوتية فقط" else "غير مفعل — يفعله المسؤول من اللوحة", fontSize = 12.sp)
                }
            }
        }
        PrimaryTabRow(tab) {
            listOf(Icons.Default.Dialpad to "الأرقام", Icons.Default.Star to "المفضلة", Icons.Default.History to "السجل", Icons.Default.Contacts to "جهات الاتصال").forEachIndexed { i, item -> Tab(tab == i, { tab = i }, icon = { Icon(item.first, null) }, text = { Text(item.second, fontSize = 10.sp) }) }
        }
        when (tab) {
            0 -> DialPad(account.pstnEnabled, viewModel)
            // ⭐ المفضلة — أكثر الأرقام اتصالًا عبر DINSTAR مع إعادة اتصال بنقرة
            1 -> if (favorites.isEmpty()) {
                EmptyState(Icons.Default.Star, "لا مفضلة بعد", "ستظهر هنا أكثر الأرقام اليمنية اتصالًا عبر DINSTAR تلقائيًا")
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(favorites.size) { i ->
                        val number = favorites[i]
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, null, tint = AqyalGold)
                                Text(number, Modifier.weight(1f).padding(horizontal = 10.dp), fontWeight = FontWeight.Bold)
                                com.red.sovereign.calls.YemeniOperatorDetector.getOperatorInfo(number)?.let { op ->
                                    Text(op.name, color = op.brandColor, fontSize = 11.sp, modifier = Modifier.padding(end = 8.dp))
                                }
                                IconButton(onClick = { if (account.pstnEnabled) { viewModel.clearPstnState(); viewModel.dialPstn(number) } }, enabled = account.pstnEnabled) {
                                    Icon(Icons.Default.Call, "اتصال", tint = if (account.pstnEnabled) YounesEmerald else Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
            // 🗂️ سجل DINSTAR الحقيقي — مفلتر من السجل الموحد
            2 -> if (dinstarCalls.isEmpty()) {
                EmptyState(Icons.Default.History, "لا مكالمات DINSTAR بعد", "ستظهر هنا كل مكالماتك الهاتفية اليمنية")
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(dinstarCalls.size) { i -> CallHistoryRow(dinstarCalls[i]) }
                }
            }
            else -> EmptyState(Icons.Default.Contacts, "جهات الاتصال", "اختر جهة من تبويب جهات الاتصال الرئيسي ثم اطلبها عبر DINSTAR")
        }
    }
}

@Composable

private fun DialPad(enabled: Boolean, viewModel: AuthViewModel) {
    var number by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(number.ifEmpty { "أدخل الرقم" }, fontSize = 27.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            IconButton({ if (number.isNotEmpty()) number = number.dropLast(1) }) { Icon(Icons.AutoMirrored.Filled.Backspace, "حذف") }
        }
        com.red.sovereign.calls.YemeniOperatorDetector.getOperatorInfo(number)?.let { op ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                Box(Modifier.size(8.dp).background(op.brandColor, CircleShape))
                Text("  ${op.name} (${op.technology})", color = op.brandColor, style = MaterialTheme.typography.labelSmall)
            }
        }
        listOf(listOf("1","2","3"), listOf("4","5","6"), listOf("7","8","9"), listOf("*","0","#")).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { row.forEach { digit -> FilledIconButton({ number += digit }, Modifier.size(64.dp)) { Text(digit, fontSize = 23.sp) } } }
        }
        Button({ viewModel.clearPstnState(); viewModel.dialPstn(number) }, enabled = enabled && number.filter(Char::isDigit).length >= 6, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Call, null); Text(" اتصال صوتي عبر DINSTAR") }
        when (val state = viewModel.pstnState) {
            PstnState.Dialing -> CircularProgressIndicator(color = AqyalGold)
            PstnState.Bridging, PstnState.Registering, PstnState.Ringing -> Text("جارٍ الاتصال…", color = AqyalGold)
            is PstnState.Incoming -> Text("مكالمة واردة من ${state.fromNumber}", color = AqyalGold)
            is PstnState.Started -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("بدأ الاتصال · ${state.usedToday}/${state.dailyLimit} اليوم", color = AqyalGold)
                // 📴 زر إنهاء فعلي — يستدعي POST /api/pstn/calls/{callId}/hangup ويحرّر منفذ GSM
                OutlinedButton(
                    onClick = { viewModel.hangupPstn() },
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Icon(Icons.Default.Call, null, tint = MaterialTheme.colorScheme.error); Text(" إنهاء المكالمة") }
            }
            is PstnState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
            PstnState.Idle -> Unit
        }
    }
}

