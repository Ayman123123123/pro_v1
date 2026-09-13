package com.red.sovereign.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.red.sovereign.calls.CallTelemetry
import com.red.sovereign.calls.WebRtcBootstrap
import com.red.sovereign.core.LocalServerDiscovery
import com.red.sovereign.core.RedQualityManager
import com.red.sovereign.core.ServerEndpoint
import java.net.InetAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private data class DiagRow(val label: String, val value: String)

/**
 * شاشة تشخيص الشبكة: Ping (وصول TCP + isReachable) + DNS + زمن HTTP
 * + اكتشاف تلقائي + إحصاءات WebRTC + نسخ التقرير.
 * كل القياسات حقيقية على Dispatchers.IO بمهلات صريحة — لا قيم وهمية.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkDiagnosticsScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf(ServerEndpoint.host().ifBlank { "10.0.2.2" }) }
    var running by remember { mutableStateOf(false) }
    var rows by remember { mutableStateOf<List<DiagRow>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    fun setRow(label: String, value: String) {
        rows = (rows.filterNot { it.label == label } + DiagRow(label, value))
    }

    fun runAll() {
        if (running) return
        running = true
        error = null
        rows = emptyList()
        scope.launch {
            // DNS
            runCatching {
                val t0 = System.currentTimeMillis()
                val addrs = withContext(Dispatchers.IO) { InetAddress.getAllByName(host) }
                val ms = System.currentTimeMillis() - t0
                setRow("DNS", "${addrs.size} عناوين في ${ms}ms: " + addrs.take(4).joinToString { it.hostAddress.orEmpty() })
            }.onFailure { setRow("DNS", "فشل: ${it.message?.take(120)}") }
            // Ping: isReachable + TCP connect للمنفذ الفعلي
            runCatching {
                val port = runCatching { java.net.URI(ServerEndpoint.url()).port.takeIf { it > 0 } ?: 8088 }.getOrDefault(8088)
                val reachable = withContext(Dispatchers.IO) {
                    runCatching {
                        val t0 = System.nanoTime()
                        val ok = InetAddress.getByName(host).isReachable(1500)
                        Triple(ok, (System.nanoTime() - t0) / 1_000_000, "")
                    }.getOrElse { Triple(false, -1L, it.message.orEmpty()) }
                }
                val tcp = withContext(Dispatchers.IO) {
                    runCatching {
                        val t0 = System.currentTimeMillis()
                        Socket().use { s -> s.connect(java.net.InetSocketAddress(host, port), 2000) }
                        System.currentTimeMillis() - t0
                    }.getOrNull()
                }
                val reachStr = if (reachable.first) "وصول ${reachable.second}ms" else "لا ICMP (${reachable.third.take(60)})"
                setRow("Ping", "$reachStr • TCP:$port=${tcp?.let { "${it}ms" } ?: "مغلق/مهلة"}")
            }.onFailure { setRow("Ping", "فشل: ${it.message?.take(120)}") }
            // HTTP /health
            runCatching {
                val res = withContext(Dispatchers.IO) {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(2500, TimeUnit.MILLISECONDS)
                        .readTimeout(3000, TimeUnit.MILLISECONDS)
                        .callTimeout(5000, TimeUnit.MILLISECONDS)
                        .build()
                    val base = ServerEndpoint.url().trimEnd('/')
                    val t0 = System.currentTimeMillis()
                    client.newCall(Request.Builder().url("$base/health").get().build()).execute().use { r ->
                        val body = runCatching { r.body.string().take(160) }.getOrDefault("")
                        Triple(r.code, System.currentTimeMillis() - t0, body)
                    }
                }
                setRow("HTTP", "${res.first} في ${res.second}ms • ${res.third.take(120)}")
            }.onFailure { setRow("HTTP", "فشل: ${it.message?.take(160)}") }
            // الشبكة الحالية
            runCatching {
                val tier = RedQualityManager.tier(context)
                val prof = RedQualityManager.videoProfile(context)
                setRow("الشبكة", "$tier • فيديو ${prof.videoWidth}x${prof.videoHeight}@${prof.fps} • ${prof.videoKbps}kbps")
            }
            // WebRTC
            runCatching {
                val snap = CallTelemetry.snapshot()
                val ice = WebRtcBootstrap.getCachedIce()
                val iceStr = ice?.let { "${it.iceServers.size} خادم حتى ${it.expiresAt}" } ?: "لا ICE مخزّن"
                setRow("WebRTC", "RTT ${snap.avgRttMs}ms • فقد ${"%.1f".format(snap.maxLoss)}% • ${snap.quality} • معلق ${snap.pending} • ICE: $iceStr")
            }
            running = false
        }
    }

    fun runDiscover() {
        if (running) return
        running = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val res = LocalServerDiscovery(context).discover(LocalServerDiscovery.Mode.FAST)
                    res is com.red.sovereign.auth.ApiResult.Success
                }.getOrDefault(false)
            }
            setRow("اكتشاف", if (ok) "نجح — ${ServerEndpoint.url()}" else "لم يُعثر على خادم (${ServerEndpoint.url()})")
            running = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تشخيص الشبكة") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = host, onValueChange = { host = it.trim().take(253) },
                label = { Text("المضيف") }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = ::runAll, enabled = !running && host.isNotBlank(), modifier = Modifier.weight(1f)) {
                    Text(if (running) "يفحص…" else "تشغيل الفحص")
                }
                OutlinedButton(onClick = ::runDiscover, enabled = !running, modifier = Modifier.weight(1f)) {
                    Text("اكتشاف تلقائي")
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (rows.isEmpty() && !running) {
                Text("لم يُشغَّل فحص بعد — النتائج تظهر هنا بزمن حقيقي.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            rows.forEach { r ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(r.label, fontWeight = FontWeight.Bold)
                        Text(r.value, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        val report = buildString {
                            appendLine("تشخيص RED ${java.util.Date()}")
                            appendLine("المضيف: $host")
                            appendLine("النقطة: ${ServerEndpoint.url()}")
                            rows.forEach { appendLine("${it.label}: ${it.value}") }
                        }
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        if (cm == null) {
                            error = "تعذر النسخ: الحافظة غير متاحة"
                        } else {
                            cm.setPrimaryClip(ClipData.newPlainText("netdiag", report))
                        }
                    },
                    enabled = rows.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("نسخ التقرير") }
            }
        }
    }
}
