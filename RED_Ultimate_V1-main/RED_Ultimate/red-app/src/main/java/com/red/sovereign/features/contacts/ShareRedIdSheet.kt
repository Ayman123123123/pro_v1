package com.red.sovereign.features.contacts

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.red.sovereign.ui.theme.YounesEmerald
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * ════════════════════════════════════════════════════════════════════════
 *  ShareRedIdSheet — مشاركة RED ID الخاص بالمستخدم
 *  - رمز QR حقيقي (ZXing) بتنسيق `RED-12345` متوافق مع ماسح التطبيق
 *  - لمس الرمز يشاركه كصورة PNG عبر FileProvider
 *  - خيارات: نسخ، مشاركة نصية، مشاركة QR
 * ════════════════════════════════════════════════════════════════════════
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareRedIdSheet(
    onDismiss: () -> Unit,
    redId: String,
    displayName: String
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    val qrBitmap by produceState<Bitmap?>(null, redId) {
        value = withContext(Dispatchers.Default) { buildQrBitmap(redId) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                "مشاركة هويتك السيادية",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "اسمح للآخرين بإضافتك عبر RED ID الخاص بك",
                color = Color.Gray,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(20.dp))

            // QR Code — مولّد حقيقي، واللمس يشاركه كصورة
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .clickable(enabled = qrBitmap != null) {
                        qrBitmap?.let { shareQrCode(context, it, redId) }
                    }
                    .padding(vertical = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val bitmap = qrBitmap
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "رمز QR لمعرّف يونس $redId",
                            modifier = Modifier.size(180.dp),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Icon(
                            Icons.Default.QrCode2,
                            null,
                            tint = Color.Black,
                            modifier = Modifier.size(80.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        redId,
                        color = Color.Black,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                    if (bitmap != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "اضغط للمشاركة كصورة",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))

            // RED ID display
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(YounesEmerald),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Tag, null, tint = Color.White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(displayName, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Text(
                            redId,
                            color = Color.Gray,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    IconButton(onClick = {
                        copyToClipboard(context, redId)
                        copied = true
                    }) {
                        Icon(
                            if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                            null,
                            tint = if (copied) YounesEmerald else Color.White
                        )
                    }
                }
            }
            if (copied) {
                Text(
                    "تم النسخ ✓",
                    color = YounesEmerald,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(Modifier.height(16.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { shareRedId(context, displayName, redId) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, null)
                    Spacer(Modifier.width(4.dp))
                    Text("مشاركة")
                }
                OutlinedButton(
                    onClick = {
                        copyToClipboard(context, redId)
                        copied = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ContentCopy, null)
                    Spacer(Modifier.width(4.dp))
                    Text("نسخ")
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * يولّد رمز QR حقيقي عبر ZXing — نفس الصيغة التي
 * يقبلها ماسح التطبيق ([QrScannerSheet]) والمعرّف الموحّد [com.red.sovereign.core.YounesId].
 * لا يُضاعِف البادئة: إن كان redId مخزّنًا بصيغة `RED-12345` يُرمَّز كما هو،
 * وإلا يُرمَّز الخام (الماسح يطبّع عبر YounesId.normalizeInput أيًا كانت الصيغة).
 */
private fun buildQrBitmap(redId: String, size: Int = 512): Bitmap? = runCatching {
    val clean = redId.trim()
    val payload = if (clean.startsWith("RED-", ignoreCase = true) || clean.startsWith("YNS-", ignoreCase = true)) clean.uppercase() else clean
    val hints = mapOf(
        EncodeHintType.MARGIN to 2,
        EncodeHintType.CHARACTER_SET to "UTF-8"
    )
    val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size, hints)
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        val rowOffset = y * size
        for (x in 0 until size) {
            pixels[rowOffset + x] = if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
    }
    Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}.getOrNull()

/** يشارك رمز QR كصورة PNG عبر FileProvider (authority: com.red.sovereign.fileprovider). */
private fun shareQrCode(context: Context, bitmap: Bitmap, redId: String) {
    val directory = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(directory, "qr_red_id_$redId.png")
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "com.red.sovereign.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, "أضفني على RED Ultimate\n$redId")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, "مشاركة رمز QR")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("RED ID", text))
}

private fun shareRedId(context: Context, name: String, redId: String) {
    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(
            Intent.EXTRA_TEXT,
            "أضفني على RED Ultimate\n$name\nRED ID: $redId"
        )
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, "مشاركة RED ID")
    shareIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    context.startActivity(shareIntent)
}