package com.red.sovereign.calls

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.SovereignColors

/**
 * محرر جهات طوارئ SOS — عرض/إضافة/حذف حقيقي عبر [EmergencyCallManager]
 * (تخزين `red_emergency_prefs`) مع زر تجربة [EmergencyCallManager.triggerEmergencySos]
 * وطلب إذن [Manifest.permission.CALL_PHONE] عند الحاجة.
 *
 * لا Snackbar وهمية: كل زر ينفذ فعلياً (حفظ/حذف/اتصال).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SosEditorScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    var contacts by remember { mutableStateOf(EmergencyCallManager.getEmergencyContacts(context)) }
    var name by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var isGsm by remember { mutableStateOf(true) }

    fun refresh() {
        contacts = EmergencyCallManager.getEmergencyContacts(context)
    }

    val callPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            EmergencyCallManager.triggerEmergencySos(context)
        } else {
            // بدون الإذن: triggerEmergencySos يسقط على ACTION_DIAL بنفسه، فنطلقه الآن.
            EmergencyCallManager.triggerEmergencySos(context)
            Toast.makeText(context, "بدون إذن الاتصال سيُفتح طالب الاتصال للتأكيد", Toast.LENGTH_LONG).show()
        }
    }

    fun onTestSos() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            EmergencyCallManager.triggerEmergencySos(context)
        } else {
            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    fun onAdd() {
        val n = name.trim()
        val num = number.trim()
        if (n.isEmpty() || num.isEmpty()) {
            Toast.makeText(context, "أدخل الاسم والرقم أولاً", Toast.LENGTH_SHORT).show()
            return
        }
        if (contacts.any { it.numberOrRedId == num }) {
            Toast.makeText(context, "هذا الرقم مسجل مسبقاً", Toast.LENGTH_SHORT).show()
            return
        }
        val updated = contacts + EmergencyContact(name = n, numberOrRedId = num, isGsm = isGsm)
        EmergencyCallManager.saveEmergencyContacts(context, updated)
        refresh()
        name = ""
        number = ""
        isGsm = true
        Toast.makeText(context, "تمت إضافة جهة الطوارئ", Toast.LENGTH_SHORT).show()
    }

    fun onDelete(contact: EmergencyContact) {
        val updated = contacts.filterNot { it.numberOrRedId == contact.numberOrRedId && it.name == contact.name }
        EmergencyCallManager.saveEmergencyContacts(context, updated)
        refresh()
        Toast.makeText(context, "تم حذف ${contact.name}", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("طوارئ SOS", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SovereignColors.SurfaceDark),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", tint = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(16.dp))
            Text(
                "جهات الطوارئ الحالية (${contacts.size})",
                color = AqyalGold, fontWeight = FontWeight.Bold, fontSize = 13.sp
            )
            Spacer(Modifier.height(8.dp))

            if (contacts.isEmpty()) {
                Text("لا توجد جهات — أضف واحدة بالأسفل.", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
            } else {
                contacts.forEach { c ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = SovereignColors.SurfaceDarkVariant),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (c.isGsm) Icons.Default.Phone else Icons.Default.Call,
                                    c.name, tint = AqyalGold, modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(c.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text(
                                        "${c.numberOrRedId} • ${if (c.isGsm) "GSM" else "RED مشفر"}",
                                        color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp
                                    )
                                }
                            }
                            IconButton(onClick = { onDelete(c) }) {
                                Icon(Icons.Default.Delete, "حذف ${c.name}", tint = Color.Red)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("إضافة جهة جديدة", color = AqyalGold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("الاسم (مثال: النجدة)") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = number, onValueChange = { number = it },
                label = { Text(if (isGsm) "الرقم (مثال: 199)" else "معرف RED") },
                modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = isGsm, onCheckedChange = { isGsm = it })
                Text("رقم GSM (أطفئه لجهة RED مشفرة)", color = Color.White, fontSize = 13.sp)
            }
            Button(
                onClick = ::onAdd,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AqyalGold, contentColor = Color(0xFF0A0F18))
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("إضافة جهة طوارئ", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = ::onTestSos,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C), contentColor = Color.White)
            ) {
                Icon(Icons.Default.Emergency, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("تجربة نداء SOS الآن", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    val has = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
                        PackageManager.PERMISSION_GRANTED
                    if (!has) callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                    else Toast.makeText(context, "إذن الاتصال ممنوح مسبقاً", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("طلب إذن الاتصال CALL_PHONE", color = Color.White)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
