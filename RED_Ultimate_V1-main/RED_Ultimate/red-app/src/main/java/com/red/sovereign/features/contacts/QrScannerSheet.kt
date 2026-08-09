package com.red.sovereign.features.contacts

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.red.sovereign.ui.theme.YounesEmerald

/**
 * ════════════════════════════════════════════════════════════════════════
 *  QrScannerSheet — ماسح RED ID
 *  - يستخدم كاميرا الجهاز بمساعدة CameraX + ML Kit
 *  - هنا نوفر واجهة الـ fallback (manual entry + permission flow)
 *    ML Kit integration يمكن ربطه لاحقاً — حالياً نوفر manual entry
 *  - يتحقق من صيغة RED ID (YNS-XXXX-XXXX)
 * ════════════════════════════════════════════════════════════════════════
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerSheet(
    onDismiss: () -> Unit,
    onScanned: (redId: String) -> Unit
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var manualRedId by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
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
                "مسح RED ID",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "وجّه الكاميرا نحو رمز QR للشخص أو أدخل RED ID يدوياً",
                color = Color.Gray,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(16.dp))

            // Camera preview area (placeholder)
            if (hasCameraPermission) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    // Camera preview placeholder — integrate CameraX later
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.QrCodeScanner,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "الكاميرا جاهزة",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            "سيتم الربط مع ML Kit Barcode قريباً",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CameraAlt, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "يحتاج التطبيق إذن الكاميرا لمسح رموز QR",
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("السماح")
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Manual entry fallback
            Text(
                "أو أدخل RED ID يدوياً",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = manualRedId,
                onValueChange = {
                    manualRedId = it.uppercase()
                    error = null
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("YNS-XXXX-XXXX") },
                leadingIcon = { Icon(Icons.Default.Tag, null) },
                singleLine = true,
                isError = error != null,
                supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val normalized = manualRedId.trim().uppercase()
                    if (isValidRedId(normalized)) {
                        onScanned(normalized)
                    } else {
                        error = "صيغة RED ID غير صحيحة (يجب أن تكون YNS-XXXX-XXXX)"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = manualRedId.isNotBlank()
            ) {
                Icon(Icons.Default.Check, null)
                Spacer(Modifier.width(4.dp))
                Text("تحقق وانتقل")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * RED ID format: YNS-XXXX-XXXX (uppercase alphanumeric)
 * Example: YNS-9F2A-BC11
 */
private val RED_ID_REGEX = Regex("^YNS-[A-Z0-9]{4}-[A-Z0-9]{4}$")

fun isValidRedId(redId: String): Boolean = RED_ID_REGEX.matches(redId)

/** Normalize any input to RED ID form (auto-uppercase + dash insertion) */
fun normalizeRedIdInput(input: String): String {
    val cleaned = input.uppercase().filter { it.isLetterOrDigit() }
    return when {
        cleaned.length <= 3 -> "YNS-$cleaned"
        cleaned.length <= 7 -> "YNS-${cleaned.substring(3)}"
        cleaned.length <= 11 -> "YNS-${cleaned.substring(3, 7)}-${cleaned.substring(7)}"
        else -> "YNS-${cleaned.substring(3, 7)}-${cleaned.substring(7, 11)}"
    }
}
