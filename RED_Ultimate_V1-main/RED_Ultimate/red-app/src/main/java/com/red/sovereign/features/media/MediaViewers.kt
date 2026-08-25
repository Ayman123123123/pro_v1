package com.red.sovereign.features.media

import android.widget.Toast
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.red.sovereign.media.FileTypeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.abs
import java.io.File

@Composable
fun ImageViewerScreen(
    file: File,
    fileName: String,
    mimeType: String,
    onDismiss: () -> Unit,
    onSave: (() -> Unit)? = null,
    onOpenWith: ((File) -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var showControls by remember { mutableStateOf(true) }
    var showSave by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset = if (scale > 1f) offset + pan else Offset.Zero
                }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    if (scale <= 1.02f && abs(dragAmount) > 4f) {
                        change.consume()
                        onDismiss()
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onDoubleTap = {
                        if (scale > 1.5f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                    }
                )
            }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(file).crossfade(true).build(),
            contentDescription = fileName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )

        if (showControls) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "إغلاق", tint = Color.White) }
                Text(fileName, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), fontSize = 14.sp)
                onSave?.let {
                    IconButton(onClick = { showSave = true }) { Icon(Icons.Default.Download, "حفظ", tint = Color.White) }
                }
                onOpenWith?.let {
                    IconButton(onClick = { it(file) }) { Icon(Icons.Default.OpenInNew, "فتح بـ", tint = Color.White) }
                }
                if (onDelete != null) {
                    IconButton(onClick = { showDelete = true }) { Icon(Icons.Default.Delete, "حذف", tint = Color(0xFFFF6E6E)) }
                }
            }
            Text(
                "اضغط مرتين للتكبير · اسحب لأسفل للإغلاق",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
            )
        }
    }

    if (showSave) {
        SaveDestinationDialog(
            fileName = fileName,
            onDismiss = { showSave = false },
            onSave = { location ->
                showSave = false
                CoroutineScopeHolder.launchIO {
                    val uri = createSaveUri(context, fileName, location) ?: return@launchIO
                    saveFileToDestination(file, uri, context) { ok, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    if (showDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("حذف الملف") },
            text = { Text("هل تريد حذف هذا الملف من المحادثة؟") },
            confirmButton = {
                Button(onClick = { showDelete = false; onDelete() }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))) { Text("حذف", color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("إلغاء") } }
        )
    }
}

internal object CoroutineScopeHolder {
    fun launchIO(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) {
        CoroutineScope(Dispatchers.IO).launch(block = block)
    }
}

@Composable
fun ThumbnailImage(file: File, modifier: Modifier = Modifier) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current).data(file).crossfade(true).build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}

@Composable
fun ThumbnailVideo(file: File, modifier: Modifier = Modifier, durationMs: Long = 0L) {
    val context = LocalContext.current
    val bitmap = remember(file.absolutePath) {
        kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(file.absolutePath)
                    val frame = retriever.getFrameAtTime(1_000_000L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    retriever.release()
                    frame
                }.getOrNull()
            }
        }
    }
    Box(modifier.background(Color.Black)) {
        bitmap?.let {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        setImageBitmap(it)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } ?: Icon(Icons.Default.Videocam, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(36.dp))
        Icon(Icons.Default.PlayArrow, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.align(Alignment.Center).size(40.dp))
        if (durationMs > 0) {
            Surface(color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(6.dp), modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)) {
                Text(formatMediaTime(durationMs), color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
    }
}

@Composable
fun ThumbnailAudio(modifier: Modifier = Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.primaryContainer)) {
        Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.align(Alignment.Center).size(38.dp))
    }
}

@Composable
fun ThumbnailDocument(fileName: String, mimeType: String, modifier: Modifier = Modifier) {
    val icon = FileTypeUtil.getFileIcon(mimeType, fileName)
    val color = FileTypeUtil.getFileColor(mimeType)
    Box(modifier.background(color.copy(alpha = 0.15f))) {
        Icon(icon, null, tint = color, modifier = Modifier.align(Alignment.Center).size(38.dp))
    }
}

@Composable
fun FileIconItem(fileName: String, mimeType: String, sizeBytes: Long, onClick: () -> Unit) {
    val icon = FileTypeUtil.getFileIcon(mimeType, fileName)
    val color = FileTypeUtil.getFileColor(mimeType)
    Surface(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(fileName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(FileTypeUtil.formatFileSize(sizeBytes), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        }
    }
}
