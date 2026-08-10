package com.red.sovereign.crypto

import android.annotation.SuppressLint
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** CameraX scanner restricted to YOUNES safety-number QR codes. Frames never leave the device. */
@SuppressLint("UnsafeOptInUsageError")
@Composable
fun SafetyQrScanner(onCode: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val delivered = remember { AtomicBoolean(false) }
    val providerFuture = remember { ProcessCameraProvider.getInstance(context) }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { viewContext ->
            PreviewView(viewContext).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also { it.surfaceProvider = surfaceProvider }
                    val analyzer = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    val reader = MultiFormatReader().apply {
                        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
                    }
                    analyzer.setAnalyzer(executor) { image ->
                        try {
                            if (!delivered.get()) decodeQr(image, reader)?.let { code ->
                                if (delivered.compareAndSet(false, true)) {
                                    ContextCompat.getMainExecutor(viewContext).execute { onCode(code) }
                                }
                            }
                        } finally {
                            image.close()
                        }
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analyzer)
                }, ContextCompat.getMainExecutor(viewContext))
            }
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            runCatching { if (providerFuture.isDone) providerFuture.get().unbindAll() }
            executor.shutdownNow()
        }
    }
}

private fun decodeQr(image: ImageProxy, reader: MultiFormatReader): String? {
    val plane = image.planes.firstOrNull() ?: return null
    val width = image.width
    val height = image.height
    val compact = ByteArray(width * height)
    val buffer = plane.buffer.duplicate()
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    var output = 0
    for (row in 0 until height) {
        val rowStart = row * rowStride
        for (column in 0 until width) compact[output++] = buffer.get(rowStart + column * pixelStride)
    }
    val source = PlanarYUVLuminanceSource(compact, width, height, 0, 0, width, height, false)
    return runCatching { reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text }
        .also { reader.reset() }
        .getOrNull()
}
