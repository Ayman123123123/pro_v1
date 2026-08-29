package com.red.sovereign.settings

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.AuthorizedApiClient
import com.red.sovereign.auth.PstnApi
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.features.dinstar.DinstarFleetStatus
import com.red.sovereign.features.dinstar.DinstarGatewayStatus
import com.red.sovereign.features.dinstar.DinstarPort
import com.red.sovereign.core.ServerEndpoint
import com.red.sovereign.features.dinstar.DinstarViewModel
import com.red.sovereign.features.dinstar.YemenOperator
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.SovereignColors
import com.red.sovereign.ui.theme.YounesEmerald
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.math.min

data class DialogState(var showSmsDialog: DinstarPort?, var showUssdDialog: DinstarPort?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PstnConfigScreen(
    onBack: () -> Unit,
    tokenStore: TokenStore,
    snackbarHostState: SnackbarHostState
) {
    val context = LocalContext.current
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    val api = remember { AuthorizedApiClient(tokenStore) }
    val pstnApi = remember { PstnApi(tokenStore) }
    
var fleetStatus by remember { mutableStateOf<DinstarFleetStatus?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedGateway by remember { mutableStateOf<DinstarGatewayStatus?>(null) }
    var showDialDialog by remember { mutableStateOf(false) }
    var dialNumber by remember { mutableStateOf("") }
    var ussdCode by remember { mutableStateOf("") }
    var smsNumber by remember { mutableStateOf("") }
    var smsText by remember { mutableStateOf("") }
    var testBridgeResult by remember { mutableStateOf<String?>(null) }
    var showBridgeTest by remember { mutableStateOf(false) }
    var bridgeTestNumber by remember { mutableStateOf("") }
    var bridgeTestPort by remember { mutableStateOf("") }
    
    val dialogState = remember { DialogState(null, null) }
    
    val json = Json { ignoreUnknownKeys = true }
    
    fun parsePort(raw: Map<String, Any>): DinstarPort {
        val operator = raw["operator"]?.toString()
        return DinstarPort(
            index = (raw["index"] as? Number)?.toInt() ?: (raw["port"] as? Number)?.toInt() ?: 0,
            radioType = raw["radioType"]?.toString() ?: "GSM",
            registrationState = raw["status"]?.toString() ?: "UNREGISTERED",
            callState = raw["callState"]?.toString() ?: "IDLE",
            signalPercent = (raw["signal"] as? Number)?.toInt(),
            signalDbm = (raw["signalDbm"] as? Number)?.toInt(),
            signalRaw = (raw["signalRaw"] as? Number)?.toInt(),
            signalUsable = raw["signalUsable"] as? Boolean ?: false,
            gprsState = raw["gprs"]?.toString() ?: "DETACH",
            operatorName = operator ?: "غير معروف",
            numberMasked = raw["numberMasked"]?.toString(),
            imsiMasked = raw["imsiMasked"]?.toString(),
            iccidMasked = raw["iccidMasked"]?.toString(),
            simType = YemenOperator.fromApiOperatorName(operator)
        )
    }
    
    fun refreshStatus() {
        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                val response = api.request("GET", "/api/admin/dinstar/fleet/ports")
                when (response) {
                    is ApiResult.Success -> {
                        val root = json.decodeFromString<Map<String, Any>>(response.value)
                        val gatewaysData = (root["gateways"] as? List<Map<String, Any>>).orEmpty()
                        val gateways = gatewaysData.map { entry ->
                            val gw = (entry["gateway"] as? Map<String, Any>).orEmpty()
                            val rawPorts = (entry["ports"] as? List<Map<String, Any>>).orEmpty()
                            DinstarGatewayStatus(
                                gatewayId = gw["id"]?.toString(),
                                name = gw["name"]?.toString().orEmpty(),
                                isOnline = entry["error"] == null && gw["healthState"]?.toString() == "ONLINE",
                                gatewayIp = gw["host"]?.toString().orEmpty(),
                                model = gw["model"]?.toString().orEmpty(),
                                firmware = gw["firmwareVersion"]?.toString().orEmpty(),
                                ports = rawPorts.map { parsePort(it) },
                                lastUpdated = System.currentTimeMillis()
                            )
                        }
                        fleetStatus = DinstarFleetStatus(gateways = gateways, lastUpdated = System.currentTimeMillis())
                    }
                    is ApiResult.Error -> {
                        errorMessage = response.message ?: "Failed to load gateway status"
                    }
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: "Unknown error"
            } finally {
                isLoading = false
            }
        }
    }
    
    fun sendSms(number: String, text: String) {
        scope.launch {
            val response = pstnApi.sendSms(number, text)
            when (response) {
                is ApiResult.Success -> {
                    snackbarHostState.showSnackbar("تم إرسال الرسالة بنجاح")
                }
                is ApiResult.Error -> {
                    snackbarHostState.showSnackbar("فشل الإرسال: ${response.message ?: "غير معروف"}")
                }
            }
        }
    }
    
    fun sendUssd(portIndex: Int, code: String) {
        scope.launch {
            val response = api.request("POST", "/api/admin/dinstar/ports/$portIndex/ussd", 
                json.encodeToString(mapOf("code" to code)))
            when (response) {
                is ApiResult.Success -> {
                    snackbarHostState.showSnackbar("تم إرسال كود USSD: $code")
                }
                is ApiResult.Error -> {
                    snackbarHostState.showSnackbar("فشل USSD: ${response.message ?: "غير معروف"}")
                }
            }
        }
    }
    
    fun resetPort(portIndex: Int) {
        scope.launch {
            val response = api.request("POST", "/api/admin/dinstar/ports/$portIndex/reset", "{}")
            when (response) {
                is ApiResult.Success -> {
                    snackbarHostState.showSnackbar("تم إعادة تشغيل المنفذ $portIndex")
                    refreshStatus()
                }
                is ApiResult.Error -> {
                    snackbarHostState.showSnackbar("فشل إعادة التشغيل: ${response.message ?: "غير معروف"}")
                }
            }
        }
    }
    
    suspend fun testBridge() {
        if (bridgeTestNumber.isBlank()) {
            snackbarHostState.showSnackbar("أدخل رقم للاختبار")
            return
        }
        testBridgeResult = "جاري الاختبار..."
val response = api.request("POST", "/api/pstn/bridge", 
            json.encodeToString(buildMap<String, Any> { put("number", bridgeTestNumber); bridgeTestPort.toIntOrNull()?.let { put("port", it) } }))
        when (response) {
            is ApiResult.Success -> {
                testBridgeResult = "نجح: ${response.value.substring(0, min(200, response.value.length))}"
            }
            is ApiResult.Error -> {
                testBridgeResult = "فشل: ${response.message ?: "غير معروف"}"
            }
        }
    }
    
    LaunchedEffect(Unit) {
        refreshStatus()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PSTN / DINSTAR Configuration") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { refreshStatus() }, enabled = !isLoading) {
                        Icon(if (isLoading) Icons.Filled.Refresh else Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SovereignColors.ObsidianDeep
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Error banner
            if (errorMessage != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF44336).copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Error, contentDescription = null, tint = Color(0xFFF44336), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = errorMessage!!, color = Color(0xFFF44336), modifier = Modifier.weight(1f))
                            IconButton(onClick = { errorMessage = null }) {
                                Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = Color(0xFFF44336))
                            }
                        }
                    }
                }
            }
            
            // Bridge Test Section
            item {
                PstnSectionItem(title = "SIP Bridge Test", icon = Icons.Filled.CloudQueue, color = AqyalGold)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.TextField(
                                value = bridgeTestNumber,
                                onValueChange = { bridgeTestNumber = it },
                                label = { Text("Number") },
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                                singleLine = true,
                                placeholder = { Text("777123456") }
                            )
                            androidx.compose.material3.TextField(
                                value = bridgeTestPort,
                                onValueChange = { bridgeTestPort = it },
                                label = { Text("Port") },
                                modifier = Modifier.weight(0.4f).padding(end = 12.dp),
                                singleLine = true,
                                placeholder = { Text("0-15") }
                            )
                            Button(onClick = { scope.launch { testBridge() } }, colors = ButtonDefaults.buttonColors(containerColor = YounesEmerald)) {
                                Icon(Icons.Filled.Dialpad, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Test Bridge")
                            }
                        }
                        if (testBridgeResult != null) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceCard),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = testBridgeResult!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
            
            // Fleet Status
            if (fleetStatus != null) {
                fleetStatus?.gateways?.forEach { gateway ->
                    item {
                        GatewayCard(
                            gateway = gateway,
                            onPortClick = { port, gateway -> selectedGateway = gateway },
                            onSendSms = { number, text -> sendSms(number, text) },
                            onSendUssd = { port, code -> sendUssd(port, code) },
                            onResetPort = { port -> resetPort(port) },
                            dialogState = dialogState
                        )
                    }
                }
            } else if (!isLoading) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.NetworkCheck, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
                                Text("لا توجد بوابات DINSTAR مُكتشفة", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("تأكد من تشغيل البوابة واتصالها بالخادم", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }
            
            // Loading indicator
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = YounesEmerald)
                    }
                }
            }
        }
        
        // Dialog Host
        DialogHost(
            showSmsDialog = dialogState.showSmsDialog,
            showUssdDialog = dialogState.showUssdDialog,
            onDismissSms = { dialogState.showSmsDialog = null },
            onDismissUssd = { dialogState.showUssdDialog = null },
            onSendSms = { number, text -> sendSms(number, text) },
            onSendUssd = { port, code -> sendUssd(port, code) }
        )
    }
}

@Composable
fun GatewayCard(
    gateway: DinstarGatewayStatus,
    onPortClick: (DinstarPort, DinstarGatewayStatus) -> Unit,
    onSendSms: (String, String) -> Unit,
    onSendUssd: (Int, String) -> Unit,
    onResetPort: (Int) -> Unit,
    dialogState: DialogState
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Gateway Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(10.dp).clip(RoundedCornerShape(5.dp))
                            .background(if (gateway.isOnline) YounesEmerald else Color(0xFFF44336))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(gateway.name.ifBlank { "Gateway ${gateway.gatewayId?.take(8) ?: "Unknown"}" }, 
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        InfoChip(icon = Icons.Filled.Phone, text = gateway.model.ifBlank { "UC2000" })
                        InfoChip(icon = Icons.Filled.Info, text = gateway.firmware.ifBlank { "N/A" })
                        InfoChip(icon = Icons.Filled.CloudQueue, text = gateway.gatewayIp.ifBlank { "N/A" })
                        InfoChip(icon = Icons.Filled.SignalCellular4Bar, text = "${gateway.registeredCount}/${gateway.ports.size} مسجلة")
                    }
                }
                if (gateway.isOnline) {
                    Text("ONLINE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = YounesEmerald)
                } else {
                    Text("OFFLINE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFFF44336))
                }
            }
            
            // Ports Grid
            if (gateway.ports.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    gateway.ports.forEach { port ->
PortCard(
                            port = port,
                            gateway = gateway,
                            onClick = { onPortClick(port, gateway) },
                            onSendSms = { number, text -> onSendSms(number, text) },
                            onSendUssd = { code -> onSendUssd(port.index, code) },
                            onReset = { onResetPort(port.index) },
                            dialogState = dialogState
                        )
                    }
                }
            } else {
                Text("لا توجد منافذ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun InfoChip(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun PortCard(
    port: DinstarPort,
    gateway: DinstarGatewayStatus,
    onClick: () -> Unit,
    onSendSms: (String, String) -> Unit,
    onSendUssd: (String) -> Unit,
    onReset: () -> Unit,
    dialogState: DialogState
) {
    val isRegistered = port.registrationState == "REGISTERED"
    val isActive = port.callState == "ACTIVE"
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(0.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) YounesEmerald.copy(alpha = 0.1f) 
            else if (isRegistered) SovereignColors.SurfaceCard 
            else SovereignColors.SurfaceCard.copy(alpha = 0.7f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isRegistered) YounesEmerald.copy(alpha = 0.15f) else Color(0xFFF44336).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SimCard,
                            contentDescription = null,
                            tint = if (isRegistered) YounesEmerald else Color(0xFFF44336),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Port ${port.index}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatusBadge(text = port.registrationState, isPositive = isRegistered)
                            StatusBadge(text = port.callState, isPositive = port.callState == "IDLE")
                            if (port.simType != YemenOperator.UNKNOWN) {
                                StatusBadge(text = port.simType.arabicName, isPositive = true, color = port.simType.color)
                            }
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (port.signalDbm != null) {
                        Text("${port.signalDbm} dBm", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = if (port.signalUsable) YounesEmerald else Color(0xFFF44336))
                    }
                    if (port.numberMasked != null) {
                        Text(port.numberMasked!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            
            // Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = { dialogState.showSmsDialog = port }) {
                    Icon(Icons.Filled.Sms, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("SMS")
                }
                OutlinedButton(onClick = { dialogState.showUssdDialog = port }) {
                    Icon(Icons.Filled.Dialpad, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("USSD")
                }
                OutlinedButton(onClick = onReset) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset")
                }
            }
        }
    }
}

@Composable
fun StatusBadge(text: String, isPositive: Boolean, color: Color = MaterialTheme.colorScheme.primary) {
    Box(
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp).padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, 
            color = if (isPositive) color else Color(0xFFF44336))
    }
}

@Composable
fun PstnSectionItem(title: String, icon: ImageVector, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogHost(
    showSmsDialog: DinstarPort?,
    showUssdDialog: DinstarPort?,
    onDismissSms: () -> Unit,
    onDismissUssd: () -> Unit,
    onSendSms: (String, String) -> Unit,
    onSendUssd: (Int, String) -> Unit
) {
    showSmsDialog?.let { port ->
        SmsDialog(
            port = port,
            onDismiss = onDismissSms,
            onSend = onSendSms
        )
    }
    showUssdDialog?.let { port ->
        UssdDialog(
            port = port,
            onDismiss = onDismissUssd,
            onSend = onSendUssd
        )
    }
}

@Composable
fun SmsDialog(
    port: DinstarPort,
    onDismiss: () -> Unit,
    onSend: (String, String) -> Unit
) {
    var number by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إرسال SMS عبر Port ${port.index}") },
        text = {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.TextField(
                    value = number,
                    onValueChange = { number = it },
                    label = { Text("رقم المستلم (مثال: 777123456)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                androidx.compose.material3.TextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("نص الرسالة") },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSend(number, text); onDismiss() }) { Text("إرسال") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

@Composable
fun UssdDialog(
    port: DinstarPort,
    onDismiss: () -> Unit,
    onSend: (Int, String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إرسال USSD عبر Port ${port.index}") },
        text = {
            androidx.compose.material3.TextField(
                value = code,
                onValueChange = { code = it },
                label = { Text("كود USSD (مثال: *122#)") },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = { onSend(port.index, code); onDismiss() }) { Text("إرسال") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
