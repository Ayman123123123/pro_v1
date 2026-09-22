package com.red.sovereign.features.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.red.sovereign.ui.theme.AqyalCyanGlow
import com.red.sovereign.ui.theme.AqyalGold
import com.red.sovereign.ui.theme.YounesEmerald
import com.red.sovereign.util.QrCodeGenerator

/**
 * شاشة البروفايل الكاملة — صورة + اسم معروض + username + بايو + QR للهوية.
 * الصورة تُرفع مشفّرة E2EE والخادم يخزّن objectKey فقط.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    redId: String,
    username: String,
    displayName: String,
    onBack: () -> Unit
) {
    val viewModel: ProfileViewModel = viewModel()
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { viewModel.updateAvatar(it) }
    }

    LaunchedEffect(redId, username, displayName) {
        viewModel.load(redId, username, displayName)
    }

    var editingName by remember { mutableStateOf(displayName) }
    var editingBio by remember { mutableStateOf("") }
    var showQr by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    // حالة الخطأ عند فشل تحميل البروفايل
    if (viewModel.loadError != null) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.ErrorOutline, "خطأ التحميل", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text("تعذّر تحميل البروفايل", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            Text(viewModel.loadError ?: "", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Button(onClick = { viewModel.clearLoadError(); viewModel.load(redId, username, displayName) }) {
                Icon(Icons.Default.Refresh, "إعادة المحاولة", Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("إعادة المحاولة")
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("رجوع")
            }
        }
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // رأس: زر العودة + العنوان
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
            Text("البروفايل", fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton({ showQr = !showQr }) { Icon(Icons.Default.QrCode, "رمز الهوية", tint = AqyalGold) }
        }

        // الصورة + زر التغيير
        Box(contentAlignment = Alignment.BottomEnd, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(120.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    val avatar = viewModel.avatar
                    if (avatar != null) {
                        Image(avatar, "صورة البروفايل", Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                    } else {
                        Text(displayName.take(1).ifBlank { "ي" }, fontSize = 48.sp, fontWeight = FontWeight.Black, color = AqyalGold)
                    }
                }
            }
            // شارة الكاميرا
            Surface(
                shape = CircleShape,
                color = YounesEmerald,
                modifier = Modifier.size(36.dp).clickable { imagePicker.launch(arrayOf("image/*")) }
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(Icons.Default.CameraAlt, "تغيير الصورة", tint = Color(0xFF002118), modifier = Modifier.size(20.dp))
                }
            }
        }

        if (viewModel.isUploading) {
            Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), color = YounesEmerald, strokeWidth = 2.dp)
                Text("جارٍ رفع الصورة مشفّرة…", fontSize = 12.sp, color = YounesEmerald)
            }
        }

        // حذف الصورة إن وُجدت
        if (viewModel.avatar != null) {
            OutlinedButton(
                { viewModel.removeAvatar() },
                Modifier.align(Alignment.CenterHorizontally),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Icon(Icons.Default.Delete, "إزالة الصورة", Modifier.size(18.dp)); Text(" إزالة الصورة", fontSize = 12.sp) }
        }

        // بطاقة الهوية
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("معرّف يونس", color = AqyalGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(redId, fontSize = 28.sp, fontWeight = FontWeight.Black, color = AqyalCyanGlow)
                OutlinedButton({ clipboard.setText(AnnotatedString(redId)) }, Modifier.align(Alignment.End)) {
                    Icon(Icons.Default.Check, "نسخ المعرّف", Modifier.size(16.dp)); Text(" نسخ")
                }
            }
        }

        // اسم المستخدم (للقراءة فقط — يُغيّر من الإعدادات)
        OutlinedTextField(
            value = "@$username",
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            label = { Text("اسم المستخدم") },
            readOnly = true,
            singleLine = true
        )

        // الاسم المعروض (قابل للتعديل)
        OutlinedTextField(
            value = editingName,
            onValueChange = { editingName = it.take(50) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("الاسم المعروض") },
            singleLine = true,
            supportingText = { Text("${editingName.length}/50") }
        )

        // البايو (قابل للتعديل)
        OutlinedTextField(
            value = editingBio,
            onValueChange = { editingBio = it.take(280) },
            modifier = Modifier.fillMaxWidth().height(100.dp),
            label = { Text("نبذة تعريفية (بايو)") },
            maxLines = 4,
            supportingText = { Text("${editingBio.length}/280") }
        )

        // زر الحفظ
        Button(
            {
                viewModel.updateProfile(editingName, editingBio) {
                    // عند النجاح: تحديث الاسم المحلي
                }
            },
            Modifier.fillMaxWidth(),
            enabled = !viewModel.isSaving && editingName.isNotBlank() && editingName != displayName
        ) {
            if (viewModel.isSaving) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            else { Icon(Icons.Default.Check, "حفظ"); Text(" حفظ التغييرات") }
        }

        // رسالة الحالة — نجاح أو خطأ مع زر إعادة المحاولة (لا spinner صامت)
        AnimatedVisibility(
            visible = viewModel.message != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            viewModel.message?.let { msg ->
                val isError = msg.startsWith("تعذر")
                if (isError) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ErrorOutline, "خطأ التحميل", tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                msg,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedButton(onClick = {
                                viewModel.clearMessage()
                                viewModel.load(redId, username, displayName)
                            }) {
                                Icon(Icons.Default.Refresh, "إعادة المحاولة", Modifier.size(16.dp))
                                Text(" إعادة")
                            }
                        }
                    }
                } else {
                    Text(msg, color = YounesEmerald, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        // QR للهوية (يظهر عند الطلب)
        if (showQr) {
            val qrBitmap = remember(redId) { QrCodeGenerator.generate("red-id:$redId") }
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("رمز هويتك", fontWeight = FontWeight.Bold, color = AqyalGold)
                    Spacer(Modifier.height(12.dp))
                    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, modifier = Modifier.size(200.dp)) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            androidx.compose.foundation.Image(
                                bitmap = qrBitmap.asImageBitmap(),
                                contentDescription = stringResource(com.red.sovereign.R.string.qr_code_desc, redId),
                                modifier = Modifier.fillMaxSize().padding(8.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("شارك معرّفك مع من تريد إضافته — لا رقم هاتف مطلوب", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    OutlinedButton({ clipboard.setText(AnnotatedString(redId)) }, Modifier.padding(top = 8.dp)) {
                        Text("نسخ معرّف يونس")
                    }
                }
            }
        }
    }
}
