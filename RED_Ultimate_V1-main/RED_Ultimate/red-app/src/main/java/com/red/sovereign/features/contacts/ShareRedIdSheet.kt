package com.red.sovereign.features.contacts

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.ui.theme.YounesEmerald
import com.red.sovereign.util.QrCodeGenerator

/**
 * ════════════════════════════════════════════════════════════════════════
 *  ShareRedIdSheet — مشاركة RED ID الخاص بالمستخدم
 *  - خيارات: نسخ، مشاركة عبر Intent، QR code
 *  - الـ RED ID يأتي من TokenStore.userRedId
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

            // Real QR Code
            val qrBitmap = remember(redId) { QrCodeGenerator.generate("red-id:$redId") }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "QR Code for $redId",
                    modifier = Modifier.size(180.dp)
                )
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
