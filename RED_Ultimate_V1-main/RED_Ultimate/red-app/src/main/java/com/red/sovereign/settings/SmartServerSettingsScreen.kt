package com.red.sovereign.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.BuildConfig
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.core.LocalServerDiscovery
import com.red.sovereign.core.ServerEndpoint
import com.red.sovereign.core.YounesServerSignature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class ServerStatus { IDLE, CHECKING, DISCOVERING, SUCCESS, ERROR }

data class DiscoveredServer(
    val host: String,
    val port: Int,
    val source: String,
    val isCurrent: Boolean
)

@Composable
fun SmartServerSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<ServerStatus>(ServerStatus.IDLE) }
    var showAdvanced by remember { mutableStateOf(false) }
    var discoveredServers by remember { mutableStateOf<List<DiscoveredServer>>(emptyList()) }
    var showDiscoveredList by remember { mutableStateOf(false) }

    val currentUrl = ServerEndpoint.url()

    // Initialize input text from current URL
    LaunchedEffect(Unit) {
        val host = YounesServerSignature.hostOf(currentUrl) ?: ""
        val port = YounesServerSignature.portOf(currentUrl)
        if (port == YounesServerSignature.DEFAULT_PORT && host.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$"))) {
            inputText = host
        } else if (port != YounesServerSignature.DEFAULT_PORT && host.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$"))) {
            inputText = "$host:$port"
        } else {
            inputText = host
        }
    }

    // Define callback functions using remember to avoid recompilation issues
    val validateAndSave = remember {
        { 
            status = ServerStatus.CHECKING
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                val discovery = LocalServerDiscovery(context.applicationContext)
                val result = discovery.verifyUserInput(inputText)
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    when (result) {
                        is ApiResult.Success -> {
                            ServerEndpoint.update(context.applicationContext, result.value)
                            status = ServerStatus.SUCCESS
                            Toast.makeText(context, "تم حفظ: ${result.value}", Toast.LENGTH_SHORT).show()
                        }
                        is ApiResult.Error -> {
                            status = ServerStatus.ERROR
                            val message = when (result.message) {
                                "WIFI_NOT_CONNECTED" -> "لا يوجد اتصال WiFi — الاكتشاف يعمل فقط على الشبكة المحلية. تواصل مع شبكة WiFi ثم جرب مجدداً."
                                else -> "فشل التحقق: ${result.message}"
                            }
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            Unit
        }
    }

    val autoDiscover = remember {
        {
            status = ServerStatus.DISCOVERING
            discoveredServers = emptyList()
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                val discovery = LocalServerDiscovery(context.applicationContext)
                
                // أولاً: تحقق من العناوين المعروفة بسرعة
                val quick = discovery.quickVerifyKnown()
                if (quick != null) {
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        discoveredServers = listOf(DiscoveredServer(
                            host = YounesServerSignature.hostOf(quick) ?: "",
                            port = YounesServerSignature.portOf(quick),
                            source = "معروف",
                            isCurrent = quick == currentUrl
                        ))
                        status = ServerStatus.IDLE
                    }
                    return@launch
                }

                // ثانياً: مسح شامل
                val result = discovery.discover(LocalServerDiscovery.Mode.THOROUGH)
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    when (result) {
                        is ApiResult.Success -> {
                            discoveredServers = listOf(DiscoveredServer(
                                host = YounesServerSignature.hostOf(result.value) ?: "",
                                port = YounesServerSignature.portOf(result.value),
                                source = "اكتشاف LAN",
                                isCurrent = result.value == currentUrl
                            ))
                            ServerEndpoint.update(context.applicationContext, result.value)
                            status = ServerStatus.SUCCESS
                            Toast.makeText(context, "تم اكتشاف الخادم وحفظه تلقائياً", Toast.LENGTH_SHORT).show()
                        }
                        is ApiResult.Error -> {
                            status = ServerStatus.ERROR
                            val message = when (result.message) {
                                "WIFI_NOT_CONNECTED" -> "لا يوجد اتصال WiFi — الاكتشاف يعمل فقط على الشبكة المحلية. تواصل مع شبكة WiFi ثم جرب مجدداً."
                                else -> "لم يتم العثور على خادم: ${result.message}"
                            }
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
            Unit
        }
    }

    val checkCurrentConnection = remember {
        {
            status = ServerStatus.CHECKING
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                val discovery = LocalServerDiscovery(context.applicationContext)
                val result = discovery.verifyUserInput(currentUrl)
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    status = if (result is ApiResult.Success) ServerStatus.SUCCESS else ServerStatus.ERROR
                }
            }
            Unit
        }
    }

    val resetToDefault = remember {
        {
            ServerEndpoint.update(context.applicationContext, BuildConfig.RED_SERVER_URL)
            val host = YounesServerSignature.hostOf(BuildConfig.RED_SERVER_URL) ?: ""
            val port = YounesServerSignature.portOf(BuildConfig.RED_SERVER_URL)
            inputText = if (port == YounesServerSignature.DEFAULT_PORT) host else "$host:$port"
            status = ServerStatus.SUCCESS
            Toast.makeText(context, "تم إعادة التعيين للإعدادات الافتراضية", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("إعدادات خادم يونس", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("أدخل IP الخادم فقط - البورت والمسار يُضافان تلقائياً", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "رجوع", tint = MaterialTheme.colorScheme.onSurface)
            }
        }

        // Current Status Card
        StatusCard(
            currentUrl = currentUrl,
            status = status,
            onRefresh = checkCurrentConnection
        )

        // Input Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("عنوان الخادم", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        label = { Text("مثال: 192.168.1.100 أو myserver.local") },
                        placeholder = { Text("IP أو اسم المضيف") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { validateAndSave() }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    IconButton(onClick = { showDiscoveredList = true }, enabled = status != ServerStatus.CHECKING) {
                        Icon(Icons.Default.WifiFind, "اكتشاف تلقائي", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                // Helper text
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    HelperChip(Icons.Default.Info, "IP فقط: 192.168.1.100")
                    HelperChip(Icons.Default.Info, "مع بورت: 192.168.1.100:8088")
                    HelperChip(Icons.Default.Info, "اسم مضيف: myserver.local")
                }

                // Action buttons
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = validateAndSave,
                        enabled = status != ServerStatus.CHECKING && inputText.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        if (status == ServerStatus.CHECKING) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
                        } else {
                            Text("تحقق واحفظ", fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = autoDiscover,
                        enabled = status != ServerStatus.CHECKING,
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        if (status == ServerStatus.DISCOVERING) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
                        } else {
                            Text("اكتشاف تلقائي", fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        // Discovered Servers List
        if (showDiscoveredList && discoveredServers.isNotEmpty()) {
            DiscoveredServersList(
                servers = discoveredServers,
                onSelect = { server ->
                    inputText = "${server.host}:${server.port}"
                    showDiscoveredList = false
                    validateAndSave()
                },
                onDismiss = { showDiscoveredList = false }
            )
        }

        // Advanced Options
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("خيارات متقدمة", fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { showAdvanced = !showAdvanced }) {
                        Icon(if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
                if (showAdvanced) {
                    AdvancedOptions(
                        currentUrl = currentUrl,
                        onResetToDefault = resetToDefault
                    )
                }
            }
        }

        // Build Info
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("معلومات البناء", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                InfoRow("عنوان البناء الافتراضي", BuildConfig.RED_SERVER_URL)
                InfoRow("بورت الإنتاج", "${YounesServerSignature.DEFAULT_PORT} (HTTP) / ${YounesServerSignature.DEFAULT_HTTPS_PORT} (HTTPS)")
                InfoRow("مسار API", "/api")
                InfoRow("مسار WebSocket", "/ws")
                InfoRow("فحص الصحة", "/health")
            }
        }

        // Status Message
        when (status) {
            ServerStatus.SUCCESS -> {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    color = Color(0xFF00C853).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF00C853), modifier = Modifier.size(24.dp).padding(end = 12.dp))
                        Text("تم حفظ إعدادات الخادم بنجاح", color = Color(0xFF00C853), fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    }
                }
            }
            ServerStatus.ERROR -> {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    color = Color(0xFFD32F2F).copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, null, tint = Color(0xFFD32F2F), modifier = Modifier.size(24.dp).padding(end = 12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("فشل الاتصال بالخادم", color = Color(0xFFD32F2F), fontWeight = FontWeight.Medium)
                            Text("تأكد من تشغيل الخادم وأن الهاتف على نفس الشبكة", color = Color(0xFFD32F2F), fontSize = 12.sp)
                        }
                    }
                }
            }
            else -> Unit
        }
    }
}

@Composable
fun StatusCard(
    currentUrl: String,
    status: ServerStatus,
    onRefresh: () -> Unit
) {
    val host = YounesServerSignature.hostOf(currentUrl) ?: "غير معروف"
    val port = YounesServerSignature.portOf(currentUrl)
    val isLocal = isLocalHost(host)
    val scheme = if (port == 443 || port == YounesServerSignature.DEFAULT_HTTPS_PORT) "https" else "http"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = when (status) {
                ServerStatus.SUCCESS -> Color(0xFF00C853).copy(alpha = 0.1f)
                ServerStatus.ERROR -> Color(0xFFD32F2F).copy(alpha = 0.1f)
                ServerStatus.CHECKING, ServerStatus.DISCOVERING -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
            }
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("الخادم الحالي", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(currentUrl, fontWeight = FontWeight.Medium, fontSize = 14.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusBadge(status)
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, "تحديث", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                InfoPill(Icons.Default.Lan, if (isLocal) "شبكة محلية" else "خادم بعيد", MaterialTheme.colorScheme.primary)
                InfoPill(Icons.Default.Lock, if (scheme == "https") "HTTPS آمن" else "HTTP (محلي)", if (scheme == "https") Color(0xFF00C853) else MaterialTheme.colorScheme.onSurfaceVariant)
                InfoPill(Icons.Default.SettingsInputComponent, "بورت: $port", MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
fun StatusBadge(status: ServerStatus) {
    val entry = when (status) {
        ServerStatus.IDLE -> Triple(Icons.Default.RadioButtonUnchecked, MaterialTheme.colorScheme.onSurfaceVariant, "غير مفحوص")
        ServerStatus.CHECKING -> Triple(Icons.Default.Sync, MaterialTheme.colorScheme.primary, "جاري التحقق...")
        ServerStatus.DISCOVERING -> Triple(Icons.Default.WifiFind, MaterialTheme.colorScheme.secondary, "اكتشاف...")
        ServerStatus.SUCCESS -> Triple(Icons.Default.CheckCircle, Color(0xFF00C853), "متصل ✓")
        ServerStatus.ERROR -> Triple(Icons.Default.Error, Color(0xFFD32F2F), "فشل الاتصال")
    }
    val (icon, color, text) = entry
    Surface(color = color.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp).padding(end = 6.dp))
            Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun InfoPill(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(14.dp).padding(end = 4.dp))
            Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun HelperChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Surface(
        modifier = Modifier.padding(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp).padding(end = 4.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
}

@Composable
fun DiscoveredServersList(
    servers: List<DiscoveredServer>,
    onSelect: (DiscoveredServer) -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("الخوادم المكتشفة (${servers.size})", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            servers.forEach { server ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clickable { onSelect(server) }
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Dns, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Column {
                            Text("${server.host}:${server.port}", fontWeight = FontWeight.Medium, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
                            Text("المصدر: ${server.source}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (server.isCurrent) {
                        Icon(Icons.Default.CheckCircle, "الحالي", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    } else {
                        Icon(Icons.Default.RadioButtonUnchecked, "اختر", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun AdvancedOptions(
    currentUrl: String,
    onResetToDefault: () -> Unit
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onResetToDefault, modifier = Modifier.weight(1f), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Restore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp).padding(end = 8.dp))
                    Text("إعادة للتعريف", fontSize = 13.sp)
                }
            }
        }

        // Quick actions
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickAction(Icons.Default.PhoneAndroid, "محاكي", "10.0.2.2") { }
            QuickAction(Icons.Default.Computer, "Localhost", "127.0.0.1") { }
            QuickAction(Icons.Default.ContentCopy, "نسخ الحالي", "") { }
            QuickAction(Icons.Default.Usb, "adb reverse", "adb reverse tcp:8088 tcp:8088") {
                try {
                    val process = Runtime.getRuntime().exec("adb reverse tcp:8088 tcp:8088")
                    process.waitFor()
                    Toast.makeText(context, "adb reverse tcp:8088 tcp:8088 تم", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "فشل adb reverse: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

@Composable
fun QuickAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Text(label, fontWeight = FontWeight.Medium, fontSize = 12.sp)
            if (value.isNotBlank()) Text(value, fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun isLocalHost(host: String): Boolean {
    return host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2" || host.endsWith(".local") ||
           host.split('.').mapNotNull { it.toIntOrNull() }.let { octets ->
               octets.size == 4 && (octets[0] == 10 || octets[0] == 127 || (octets[0] == 192 && octets[1] == 168) || (octets[0] == 172 && octets[1] in 16..31))
           }
}