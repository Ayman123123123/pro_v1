package com.red.sovereign.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.red.sovereign.crypto.SafetyQrScanner
import com.red.sovereign.crypto.SafetyState
import com.red.sovereign.crypto.SafetyViewModel
import com.red.sovereign.ui.theme.AqyalGold

/**
 * حوار رمز الأمان (Safety Number) — التحقّق من هوية الطرف الآخر.
 *
 * فُصل عن `ChatHubScreen` لأنه كتلة قائمة بذاتها: مدخلها حالة واحدة
 * (`SafetyViewModel.state`) ومخرجها حوار، ولا يشارك بقيّة الشاشة إلا
 * أربعة أشياء. وهذا هو معيار الفصل المتّبع في هذا الملف — لا مجرّد
 * تقليل عدد الأسطر: كتل أخرى في `ChatHubScreen` تحتاج 15 متغيّرًا
 * مشتركًا (9 منها حالة متغيّرة)، وفصلها بتمريرها كلّها معاملاتٍ
 * يُنتج توقيعًا أسوأ من الحالة الراهنة، فتُركت في مكانها عمدًا.
 *
 * المعالجة كلّها على الجهاز: الماسح لا يرفع صور الكاميرا إلى الخادم.
 */
@Composable
internal fun SafetyNumberDialogs(
    safety: SafetyViewModel,
    context: Context,
    showSafetyScanner: Boolean,
    onScannerVisibilityChange: (Boolean) -> Unit,
    cameraPermission: ManagedActivityResultLauncher<String, Boolean>,
) {
    when (val safetyState = safety.state) {
        SafetyState.Closed -> Unit
        is SafetyState.Loading -> AlertDialog(onDismissRequest = safety::close, title = { Text("رمز الأمان") }, text = { Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AqyalGold) } }, confirmButton = { TextButton(safety::close) { Text("إلغاء") } })
        is SafetyState.Error -> AlertDialog(onDismissRequest = safety::close, title = { Text("تعذر التحقق") }, text = { Text(safetyState.message) }, confirmButton = { TextButton(safety::close) { Text("إغلاق") } })
        is SafetyState.Ready -> if (showSafetyScanner) AlertDialog(
            onDismissRequest = { onScannerVisibilityChange(false) },
            title = { Text("امسح رمز الطرف الآخر") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.fillMaxWidth().height(360.dp).clip(RoundedCornerShape(16.dp))) {
                        SafetyQrScanner(onCode = { safety.verifyScanned(it); onScannerVisibilityChange(false) })
                    }
                    Text("تتم المعالجة على الجهاز فقط، ولا تُرفع صور الكاميرا إلى الخادم.", fontSize = 11.sp, textAlign = TextAlign.Center)
                }
            },
            confirmButton = { TextButton({ onScannerVisibilityChange(false) }) { Text("إلغاء") } }
        ) else AlertDialog(
            onDismissRequest = { safety.clearScanError(); safety.close() },
            title = { Text(if (safetyState.verified) "تم التحقق من الهوية" else "مقارنة رمز الأمان") },
            text = { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Image(safetyState.qr, "QR لرمز الأمان", Modifier.size(240.dp).clip(RoundedCornerShape(12.dp)))
                Text(safetyState.number, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = AqyalGold)
                Text("الجهاز ${safetyState.deviceId} · ${safetyState.fingerprint.chunked(8).joinToString(" ")}", fontSize = 9.sp, color = Color.Gray, textAlign = TextAlign.Center)
                safetyState.scanError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp, textAlign = TextAlign.Center) }
                Text("امسح رمز الطرف الآخر وجهًا لوجه، أو قارن الرقم عبر قناة موثوقة مستقلة.", fontSize = 11.sp, textAlign = TextAlign.Center)
                if (!safetyState.verified) OutlinedButton({
                    safety.clearScanError()
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) onScannerVisibilityChange(true)
                    else cameraPermission.launch(Manifest.permission.CAMERA)
                }, Modifier.fillMaxWidth()) { Icon(Icons.Default.QrCodeScanner, null); Text(" مسح رمز الطرف الآخر") }
            } },
            confirmButton = { if (!safetyState.verified) Button(safety::markVerified) { Text("الأرقام متطابقة يدويًا") } else TextButton(safety::close) { Text("تم") } },
            dismissButton = { if (!safetyState.verified) TextButton(safety::close) { Text("إلغاء") } }
        )
    }
}
