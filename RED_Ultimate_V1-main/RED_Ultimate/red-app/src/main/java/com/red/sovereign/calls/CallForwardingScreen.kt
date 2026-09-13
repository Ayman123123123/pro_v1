package com.red.sovereign.calls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.red.sovereign.R
import com.red.sovereign.auth.ApiResult
import com.red.sovereign.auth.CallForwardApi
import com.red.sovereign.auth.ForwardRule
import com.red.sovereign.auth.ForwardSettings
import com.red.sovereign.auth.TokenStore
import com.red.sovereign.ui.theme.SovereignColors
import com.red.sovereign.ui.theme.YounesEmerald
import kotlinx.coroutines.launch

private data class RuleUi(val key: String, val titleAr: String, val descAr: String)

/**
 * شاشة تحويل المكالمات — 4 قواعد عبر CallForwardApi + تعطيل الكل.
 *
 * - القواعد: always (دائم) / busy (مشغول) / noAnswer (لا رد) / unreachable (خارج التغطية).
 * - القراءة عبر GET /api/calls/forward/status، والحفظ عبر PUT /api/calls/forward،
 *   والتعطيل عبر DELETE /api/calls/forward (disableAll) — كلها شبكة حقيقية.
 * - التحقق محلي: الرقم الهدف 7–15 رقماً (يمني +967 أو دولي +) قبل أي PUT.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallForwardingScreen(
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val api = remember { CallForwardApi(TokenStore(context)) }

    var settings by remember { mutableStateOf(ForwardSettings()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        loading = true
        errorMsg = null
        scope.launch {
            when (val res = api.getStatus()) {
                is ApiResult.Success -> { settings = res.value; loading = false }
                is ApiResult.Error -> { errorMsg = "تعذّر جلب القواعد: ${res.message}"; loading = false }
            }
        }
    }

    LaunchedEffect(Unit) { refresh() }

    fun save(next: ForwardSettings) {
        // تحقق محلي قبل الشبكة: كل قاعدة مفعّلة تحتاج هدفاً صالحاً.
        val bad = listOf(
            "always" to next.always, "busy" to next.busy,
            "noAnswer" to next.noAnswer, "unreachable" to next.unreachable
        ).firstOrNull { (_, r) -> r.enabled && !isValidForwardTarget(r.target) }
        if (bad != null) {
            statusMsg = null
            errorMsg = "قاعدة «${ruleTitleAr(bad.first)}» مفعّلة برقم غير صالح — 7 إلى 15 رقماً"
            return
        }
        saving = true
        errorMsg = null
        scope.launch {
            when (val res = api.update(next)) {
                is ApiResult.Success -> {
                    settings = res.value
                    statusMsg = if (res.value.hasAnyEnabled) "حُفظ التحويل — قاعدة مفعّلة واحدة على الأقل"
                    else "حُفظ — كل القواعد متوقفة"
                }
                is ApiResult.Error -> errorMsg = "فشل الحفظ: ${res.message}"
            }
            saving = false
        }
    }

    fun disableAll() {
        saving = true
        scope.launch {
            when (val res = api.disableAll()) {
                is ApiResult.Success -> {
                    settings = ForwardSettings()
                    statusMsg = "عُطِّل التحويل بالكامل (DELETE /api/calls/forward)"
                    errorMsg = null
                }
                is ApiResult.Error -> errorMsg = "فشل التعطيل: ${res.message}"
            }
            saving = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.call_forwarding)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SovereignColors.SurfaceDark)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (loading) {
                CircularProgressIndicator()
            } else {
                if (settings.hasAnyEnabled) {
                    Text("التحويل نشط — مكالماتك تُحوَّل حسب القواعد المفعّلة", color = YounesEmerald, fontWeight = FontWeight.Bold)
                } else {
                    Text("التحويل متوقف — كل القواعد معطّلة", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ForwardRuleCard("always", "تحويل دائم", "كل المكالمات الواردة تُحوَّل فوراً", settings.always, saving) {
                    val next = settings.copy(always = it); settings = next; save(next)
                }
                ForwardRuleCard("busy", "عند الانشغال", "يُحوَّل عندما تكون في مكالمة أخرى", settings.busy, saving) {
                    val next = settings.copy(busy = it); settings = next; save(next)
                }
                ForwardRuleCard("noAnswer", "عند عدم الرد", "يُحوَّل بعد انتهاء مهلة الرنين", settings.noAnswer, saving) {
                    val next = settings.copy(noAnswer = it); settings = next; save(next)
                }
                ForwardRuleCard("unreachable", "خارج التغطية", "يُحوَّل عندما يكون جهازك غير متاح", settings.unreachable, saving) {
                    val next = settings.copy(unreachable = it); settings = next; save(next)
                }
                statusMsg?.let { Text(it, color = YounesEmerald) }
                errorMsg?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedButton(onClick = { refresh() }, modifier = Modifier.fillMaxWidth(), enabled = !saving) {
                    Text("تحديث من الخادم")
                }
                Button(
                    onClick = { disableAll() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving && settings.hasAnyEnabled
                ) { Text(if (saving) "جارٍ…" else "تعطيل الكل (disableAll)") }
                Text(
                    "المسارات: GET /api/calls/forward/status للقراءة، PUT للحفظ، DELETE للتعطيل — عبر CallForwardApi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** حوار مضمّن للتحويل داخل DeviceSettings — نفس القواعد الأربع عبر CallForwardApi. */
@Composable
fun CallForwardingDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val api = remember { CallForwardApi(TokenStore(context)) }
    var settings by remember { mutableStateOf<ForwardSettings?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        busy = true
        when (val res = api.getStatus()) {
            is ApiResult.Success -> settings = res.value
            is ApiResult.Error -> msg = "تعذّر الجلب: ${res.message}"
        }
        busy = false
    }

    fun save(next: ForwardSettings) {
        val bad = listOf(next.always, next.busy, next.noAnswer, next.unreachable)
            .firstOrNull { r -> r.enabled && !isValidForwardTarget(r.target) }
        if (bad != null) {
            msg = "رقم التحويل غير صالح — 7 إلى 15 رقماً"
            return
        }
        busy = true
        scope.launch {
            when (val res = api.update(next)) {
                is ApiResult.Success -> { settings = res.value; msg = "حُفظ التحويل" }
                is ApiResult.Error -> msg = "فشل الحفظ: ${res.message}"
            }
            busy = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تحويل المكالمات — 4 قواعد", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val s = settings
                if (s == null) {
                    if (busy) CircularProgressIndicator() else Text(msg ?: "جارٍ الجلب…")
                } else {
                    ForwardRuleRowInline("دائم", s.always) { settings = s.copy(always = it); save(s.copy(always = it)) }
                    ForwardRuleRowInline("مشغول", s.busy) { settings = s.copy(busy = it); save(s.copy(busy = it)) }
                    ForwardRuleRowInline("لا رد", s.noAnswer) { settings = s.copy(noAnswer = it); save(s.copy(noAnswer = it)) }
                    ForwardRuleRowInline("خارج التغطية", s.unreachable) { settings = s.copy(unreachable = it); save(s.copy(unreachable = it)) }
                    OutlinedButton(
                        onClick = {
                            busy = true
                            scope.launch {
                                when (val res = api.disableAll()) {
                                    is ApiResult.Success -> { settings = ForwardSettings(); msg = "عُطِّل الكل" }
                                    is ApiResult.Error -> msg = "فشل التعطيل: ${res.message}"
                                }
                                busy = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy && s.hasAnyEnabled
                    ) { Text("تعطيل الكل") }
                }
                msg?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("تم") } }
    )
}

@Composable
private fun ForwardRuleCard(
    key: String,
    title: String,
    desc: String,
    rule: ForwardRule,
    saving: Boolean,
    onChange: (ForwardRule) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceDarkVariant)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold)
                    Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = { onChange(rule.copy(enabled = it)) },
                    enabled = !saving
                )
            }
            OutlinedTextField(
                value = rule.target,
                onValueChange = { onChange(rule.copy(target = it.filter { c -> c.isDigit() || c == '+' }.take(16))) },
                label = { Text("الرقم الهدف (+967…)") },
                singleLine = true,
                enabled = !saving,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ForwardRuleRowInline(title: String, rule: ForwardRule, onChange: (ForwardRule) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Switch(checked = rule.enabled, onCheckedChange = { onChange(rule.copy(enabled = it)) })
        }
        OutlinedTextField(
            value = rule.target,
            onValueChange = { onChange(rule.copy(target = it.filter { c -> c.isDigit() || c == '+' }.take(16))) },
            label = { Text("الهدف") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun isValidForwardTarget(target: String): Boolean {
    val digits = target.filter(Char::isDigit)
    return digits.length in 7..15
}

private fun ruleTitleAr(key: String): String = when (key) {
    "busy" -> "مشغول"
    "noAnswer" -> "لا رد"
    "unreachable" -> "خارج التغطية"
    else -> "دائم"
}
