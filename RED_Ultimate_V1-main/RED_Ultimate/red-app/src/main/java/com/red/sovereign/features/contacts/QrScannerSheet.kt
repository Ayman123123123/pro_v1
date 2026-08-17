package com.red.sovereign.features.contacts

import com.red.sovereign.core.YounesId

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BinaryBitmap
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.red.sovereign.ui.theme.YounesEmerald
import java.util.concurrent.Executors

/**
 * ════════════════════════════════════════════════════════════════════════
 *  QrScannerSheet — ماسح RED ID
 *  - مسح حقيقي عبر CameraX (PreviewView + ImageAnalysis) + فك ZXing حي
 *  - مع إدخال يدوي كبديل، وطلب إذن الكاميرا عند الحاجة
 *  - يتحقق من صيغة معرّف يونس (خمسة أرقام)
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

            // 📷 ماسح QR حقيقي — CameraX Preview + تحليل ZXing حي
            if (hasCameraPermission) {
                var scannedOnce by remember { mutableStateOf(false) }
                val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
                val executor = remember { Executors.newSingleThreadExecutor() }
                DisposableEffect(Unit) { onDispose { executor.shutdownNow() } }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            val previewView = PreviewView(ctx)
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            cameraProviderFuture.addListener({
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }
                                val analysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                analysis.setAnalyzer(executor) { imageProxy ->
                                    if (!scannedOnce) {
                                        decodeQrFromImage(imageProxy)?.let { raw ->
                                            val normalized = normalizeRedIdInput(raw)
                                            if (isValidRedId(normalized)) {
                                                scannedOnce = true
                                                onScanned(normalized)
                                            }
                                        }
                                    }
                                    imageProxy.close()
                                }
                                try {
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        analysis
                                    )
                                } catch (_: Exception) { }
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        }
                    )
                    // إطار التصويب
                    Box(
                        Modifier
                            .size(140.dp)
                            .align(Alignment.Center)
                            .border(2.dp, YounesEmerald, RoundedCornerShape(12.dp))
                    )
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
                placeholder = { Text(YounesId.PLACEHOLDER) },
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
                        error = YounesId.ERROR_MESSAGE
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
 * يفك QR من إطار YUV_420_888 القادم من CameraX عبر ZXing الخالص.
 * يستخدم مستوى Y (الإضاءة) فقط — كافٍ تمامًا للباركود ولا يحتاج تحويل ألوان.
 */
private fun decodeQrFromImage(image: ImageProxy): String? = runCatching {
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val source = PlanarYUVLuminanceSource(
        bytes, image.planes[0].rowStride, image.height,
        0, 0, image.width, image.height, false
    )
    QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text
}.getOrNull()

/**
 * التحقق من معرّف يونس وتطبيعه.
 *
 * يفوّضان إلى [YounesId] — مصدر الحقيقة الوحيد. كانت النسخة السابقة
 * تحمل نمطها الخاص للمعرّف، وهو ما أنتج تباينًا مع
 * `RedDashboard`: معرّف يمر من الماسح ثم تظهر شاشة المحادثة بأزرار
 * معطّلة بلا سبب ظاهر.
 */
fun isValidRedId(redId: String): Boolean = YounesId.isValid(redId)

/** تطبيع مدخل المستخدم — يقبل اللصق بالبادئات القديمة أيضًا. */
fun normalizeRedIdInput(input: String): String = YounesId.normalizeInput(input)
