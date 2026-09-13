package com.red.sovereign.features.profile

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.red.sovereign.R
import com.red.sovereign.security.RecoveryCodesStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * مركز الاستعادة العامل:
 * - تبويب 0: ملف النسخة المشفرة (BackupScreen الحقيقي: AES256-GCM + Keystore).
 * - تبويب 1: رموز الاستعادة الورقية — توليد SecureRandom حقيقي + حفظ مشفر
 *   في SecureStore (Keystore) + عرض/نسخ + زر اختبار استعادة يتحقق فعلياً.
 *
 * القاعدة: لا حذف ولا إخفاء ولا Snackbar «قريباً» — كل زر ينفّذ ويحفظ.
 */
@Composable
fun RecoveryHubScreen(
    onBack: () -> Unit = {}
) {
    var tab by remember { mutableIntStateOf(0) }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = stringResource(R.string.recovery_hub_title),
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = stringResource(R.string.recovery_hub_sub),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("ملف النسخة المشفرة") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("رموز الحساب الورقية") })
        }
        Spacer(Modifier.height(12.dp))
        if (tab == 0) {
            // ملف النسخة المشفرة — نفس شاشة النسخ الاحتياطي الحقيقية + زر فحص الاستعادة.
            // weight(1f) يمنح BackupScreen ارتفاعاً محدوداً فيعمل Spacer الداخلي بلا فيض.
            Column(Modifier.fillMaxSize()) {
                BackupRestoreTestRow()
                Spacer(Modifier.height(8.dp))
                androidx.compose.foundation.layout.Box(Modifier.weight(1f).fillMaxWidth()) {
                    BackupScreen(onBack = onBack)
                }
            }
        } else {
            RecoveryCodesTab()
        }
    }
}

/**
 * زر اختبار الاستعادة للنسخة: فحص جاف (dry-run) يفك التشفير في ملف مؤقت
 * ويتحقق من integrity_check + meta.json دون لمس DB الحالية.
 */
@Composable
private fun BackupRestoreTestRow() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { BackupManager(context) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.VerifiedUser, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("اختبار الاستعادة (فحص جاف آمن)", fontWeight = FontWeight.Bold)
            }
            Text(
                "يفك تشفير أحدث نسخة في ملف مؤقت ويتحقق من سلامتها — لا يمس بياناتك الحالية.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            result?.let {
                Text(
                    it,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(
                onClick = {
                    testing = true; result = null
                    scope.launch {
                        val latest = manager.getLastBackupInfo() ?: manager.listBackups().firstOrNull()
                        if (latest == null) {
                            withContext(Dispatchers.Main) {
                                testing = false; isError = true
                                result = "لا توجد نسخة لاختبارها — أنشئ نسخة من الأسفل أولاً"
                            }
                            return@launch
                        }
                        val res = manager.restoreDryRun(java.io.File(latest.absolutePath))
                        withContext(Dispatchers.Main) {
                            testing = false
                            res.onSuccess {
                                isError = false
                                result = "الفحص ناجح — النسخة ${latest.fileName} سليمة وقابلة للاستعادة"
                            }.onFailure { e ->
                                isError = true
                                result = "الفحص فشل: ${e.message?.take(140)}"
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !testing
            ) {
                if (testing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(if (testing) "جارٍ الفحص…" else "اختبار استعادة أحدث نسخة")
            }
        }
    }
}

/**
 * تبويب الرموز: توليد/عرض/نسخ حقيقي + حفظ مشفر + اختبار تحقق فعلي.
 * التخزين: SecureStore «red_recovery_codes» (AES/GCM + Keystore) — لا نص خام على القرص.
 */
@Composable
private fun RecoveryCodesTab() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var codes by remember { mutableStateOf(RecoveryCodesStore.load(context)) }
    var testInput by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testOk by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }

    fun copyAll() {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText("recovery-codes", codes.joinToString("\n")))
        testResult = "نُسخت ${codes.size} رموز إلى الحافظة — احفظها خارج الهاتف"
        testOk = true
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("رموز الحساب الورقية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "تُستخدم عند تسجيل الدخول من شاشة «رموز الحساب الورقية» لاستعادة الحساب وتغيير كلمة المرور وإلغاء الجلسات.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "تُحفظ مشفرة في هذا الجهاز (Keystore AES/GCM) — انسخها ورقياً خارج الهاتف. لاستعادة ملف النسخة المشفرة استخدم التبويب الأول.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (codes.isEmpty()) {
            Text(
                "لا رموز محفوظة بعد — ولّد 8 رموز حقيقية الآن.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${codes.size} رموز محفوظة مشفرة", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        IconButton(onClick = { copyAll() }) {
                            Icon(Icons.Filled.ContentCopy, "نسخ الكل")
                        }
                    }
                    codes.forEach { code ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                code,
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            IconButton(onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                cm?.setPrimaryClip(ClipData.newPlainText("recovery-code", code))
                                testResult = "نُسخ الرمز إلى الحافظة"
                                testOk = true
                            }) { Icon(Icons.Filled.ContentCopy, "نسخ $code", modifier = Modifier.size(18.dp)) }
                        }
                    }
                    OutlinedButton(onClick = { copyAll() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("نسخ كل الرموز")
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    working = true
                    scope.launch {
                        val fresh = withContext(Dispatchers.IO) { RecoveryCodesStore.generateFresh(context) }
                        withContext(Dispatchers.Main) {
                            codes = fresh; working = false
                            testResult = "وُلّدت ${fresh.size} رموز جديدة وحُفظت مشفرة — القديمة أُبطلت"
                            testOk = true
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !working
            ) {
                if (working) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (codes.isEmpty()) "توليد الرموز" else "إعادة التوليد")
            }
            if (codes.isNotEmpty()) {
                OutlinedButton(
                    onClick = {
                        RecoveryCodesStore.clear(context)
                        codes = emptyList()
                        testResult = "مُسحت الرموز من هذا الجهاز — الورقية المحفوظة خارجياً تبقى صالحة للخادم"
                        testOk = true
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("مسح من الجهاز") }
            }
        }

        // زر اختبار الاستعادة: يتحقق فعلياً من الرمز المدخل ضد المخزن المشفر.
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("اختبار رمز (تحقق فعلي)", fontWeight = FontWeight.Bold)
                Text(
                    "ألصق أحد رموزك هنا للتحقق أنه محفوظ ومقروء من المخزن المشفر قبل أن تحتاجه.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = testInput,
                    onValueChange = { testInput = it.uppercase(); testResult = null },
                    label = { Text("XXXX-XXXX-XXXX") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        val ok = RecoveryCodesStore.contains(context, testInput)
                        testOk = ok
                        testResult = if (ok) "الرمز صحيح ومطابق للمخزن المشفر — جاهز للاستعادة عند الدخول"
                        else "الرمز غير موجود في المخزن المشفر — تحقق من النسخ أو ولّد رموزاً جديدة"
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("اختبار هذا الرمز") }
                testResult?.let {
                    Text(
                        it,
                        color = if (testOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}
