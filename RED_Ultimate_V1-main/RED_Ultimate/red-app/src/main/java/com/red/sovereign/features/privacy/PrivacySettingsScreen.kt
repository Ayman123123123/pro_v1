package com.red.sovereign.features.privacy

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.ui.theme.SovereignColors
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 🔒 YOUNES Sovereign Privacy System
 * إعدادات الخصوصية تُحفظ وتُقرأ من الخادم (GET/PUT /api/social/privacy).
 * المستويات: EVERYONE / CONTACTS / NOBODY (على أسماء PrivacyLevel).
 */

enum class PrivacyLevel(val label: String, val icon: ImageVector, val description: String) {
    EVERYONE("الجميع", Icons.Rounded.Public, "أي شخص يمكنه الرؤية"),
    CONTACTS("جهات الاتصال", Icons.Rounded.Contacts, "فقط من في جهات الاتصال"),
    NOBODY("لا أحد", Icons.Rounded.Lock, "لن يرى أحد")
}

data class StatusPrivacyItem(val setting: String, val currentLevel: PrivacyLevel, val icon: ImageVector, val arabicLabel: String)

@Serializable
private data class PrivacyPayload(
    val lastSeen: String? = null,
    val onlineStatus: String? = null,
    val profilePhoto: String? = null,
    val about: String? = null,
    val status: String? = null,
    val readReceipts: String? = null,
    val calls: String? = null,
    val groups: String? = null,
    val liveLocation: String? = null
)

@Composable
fun PrivacySettingsScreen(
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val json = remember { Json { ignoreUnknownKeys = true; explicitNulls = false } }

    var serverSettings by remember { mutableStateOf<PrivacyPayload?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    suspend fun fetch() {
        loading = true; error = null
        val client = AuthorizedApiClient(TokenStore(context))
        when (val r = client.request("GET", "/api/social/privacy")) {
            is ApiResult.Success -> {
                val parsed = runCatching { json.decodeFromString<PrivacyPayload>(r.value) }.getOrNull()
                if (parsed != null) serverSettings = parsed else error = "استجابة غير صالحة"
            }
            is ApiResult.Error -> error = "تعذر تحميل إعدادات الخصوصية: ${r.message}"
        }
        loading = false
    }

    suspend fun push(field: String, level: PrivacyLevel) {
        saving = true; error = null
        val client = AuthorizedApiClient(TokenStore(context))
        val body = json.encodeToString(
            PrivacyPayload.serializer(),
            when (field) {
                "lastSeen" -> PrivacyPayload(lastSeen = level.name)
                "onlineStatus" -> PrivacyPayload(onlineStatus = level.name)
                "profilePhoto" -> PrivacyPayload(profilePhoto = level.name)
                "about" -> PrivacyPayload(about = level.name)
                "status" -> PrivacyPayload(status = level.name)
                "readReceipts" -> PrivacyPayload(readReceipts = level.name)
                "calls" -> PrivacyPayload(calls = level.name)
                "groups" -> PrivacyPayload(groups = level.name)
                "liveLocation" -> PrivacyPayload(liveLocation = level.name)
                else -> PrivacyPayload()
            }
        )
        when (val r = client.request("PUT", "/api/social/privacy", body)) {
            is ApiResult.Success -> {
                val parsed = runCatching { json.decodeFromString<PrivacyPayload>(r.value) }.getOrNull()
                if (parsed != null) serverSettings = parsed
            }
            is ApiResult.Error -> error = "تعذر الحفظ: ${r.message}"
        }
        saving = false
    }

    LaunchedEffect(Unit) { fetch() }

    fun levelOf(key: String): PrivacyLevel =
        PrivacyLevel.entries.firstOrNull { it.name == when (key) {
            "lastSeen" -> serverSettings?.lastSeen; "onlineStatus" -> serverSettings?.onlineStatus
            "profilePhoto" -> serverSettings?.profilePhoto; "about" -> serverSettings?.about
            "status" -> serverSettings?.status; "readReceipts" -> serverSettings?.readReceipts
            "calls" -> serverSettings?.calls; "groups" -> serverSettings?.groups
            "liveLocation" -> serverSettings?.liveLocation; else -> null
        } } ?: when (key) {
            "status", "calls" -> PrivacyLevel.CONTACTS
            "liveLocation" -> PrivacyLevel.NOBODY
            else -> PrivacyLevel.EVERYONE
        }

    val privacyItems = listOf(
        StatusPrivacyItem("lastSeen", levelOf("lastSeen"), Icons.Rounded.Schedule, "آخر ظهور"),
        StatusPrivacyItem("onlineStatus", levelOf("onlineStatus"), Icons.Rounded.Circle, "الحالة المتصلة"),
        StatusPrivacyItem("profilePhoto", levelOf("profilePhoto"), Icons.Rounded.Face, "صورة الملف الشخصي"),
        StatusPrivacyItem("about", levelOf("about"), Icons.Rounded.Info, "نبذة عني"),
        StatusPrivacyItem("status", levelOf("status"), Icons.Rounded.Public, "حالتي"),
        StatusPrivacyItem("readReceipts", levelOf("readReceipts"), Icons.Rounded.DoneAll, "إيصالات القراءة"),
        StatusPrivacyItem("calls", levelOf("calls"), Icons.Rounded.Call, "المكالمات الواردة"),
        StatusPrivacyItem("groups", levelOf("groups"), Icons.Rounded.Groups, "المجموعات"),
        StatusPrivacyItem("liveLocation", levelOf("liveLocation"), Icons.Rounded.LocationOn, "الموقع المباشر")
    )

    var expandedItem by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(SovereignColors.Obsidian).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, null, tint = Color.White) }
            Text("الخصوصية والسيادة", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(Modifier.height(16.dp))
        Surface(shape = RoundedCornerShape(12.dp), color = SovereignColors.Cyan.copy(alpha = 0.08f), border = BorderStroke(1.dp, SovereignColors.Cyan.copy(alpha = 0.2f)), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Shield, null, tint = SovereignColors.Cyan, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text("إعداداتك السيادية تُحفظ مشفرة على خادمك الخاص — لا يراها أحد.", fontSize = 12.sp, color = SovereignColors.Cyan)
            }
        }
        Spacer(Modifier.height(16.dp))
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = SovereignColors.Cyan) }
            else -> {
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                }
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(privacyItems, key = { it.setting }) { item ->
                        PrivacySettingCard(
                            item, isExpanded = expandedItem == item.setting,
                            onExpand = { expandedItem = if (expandedItem == item.setting) null else item.setting },
                            onSelect = { level ->
                                scope.launch { push(item.setting, level) }
                                expandedItem = null
                            },
                            saving = saving
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacySettingCard(item: StatusPrivacyItem, isExpanded: Boolean, onExpand: () -> Unit, onSelect: (PrivacyLevel) -> Unit, saving: Boolean) {
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceNavy), modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().clickable(onClick = onExpand).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).background(SovereignColors.Cyan.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) { Icon(item.icon, null, tint = SovereignColors.Cyan, modifier = Modifier.size(22.dp)) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text(item.arabicLabel, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White); Text(item.currentLevel.label, fontSize = 12.sp, color = Color.Gray) }
                if (saving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = SovereignColors.Cyan)
                Icon(if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = Color.Gray)
            }
            AnimatedVisibility(visible = isExpanded) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    PrivacyLevel.entries.forEach { level ->
                        val isSelected = level == item.currentLevel
                        Surface(onClick = { onSelect(level) }, shape = RoundedCornerShape(10.dp), color = if (isSelected) SovereignColors.Cyan.copy(alpha = 0.15f) else Color.Transparent, border = BorderStroke(1.dp, if (isSelected) SovereignColors.Cyan else Color.Gray.copy(alpha = 0.2f)), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(level.icon, null, tint = if (isSelected) SovereignColors.Cyan else Color.Gray, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) { Text(level.label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp, color = if (isSelected) SovereignColors.Cyan else Color.White); Text(level.description, fontSize = 11.sp, color = Color.Gray) }
                                if (isSelected) Icon(Icons.Rounded.CheckCircle, null, tint = SovereignColors.Cyan, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}