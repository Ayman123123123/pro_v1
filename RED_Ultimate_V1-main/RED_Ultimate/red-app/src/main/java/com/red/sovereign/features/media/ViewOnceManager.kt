package com.red.sovereign.features.media

import android.content.Context
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import java.io.File

data class ViewOnceMedia(
    val id: String,
    val filePath: String,
    val mimeType: String,
    val isVideo: Boolean = false,
    val senderId: String,
    val timestamp: Long
)

class ViewOnceManager(private val context: Context) {
    
    private val viewedMedia = mutableSetOf<String>()
    
    fun markAsViewOnce(media: ViewOnceMedia): ViewOnceMedia {
        return media.copy(id = "viewonce_${media.id}")
    }
    
    fun hasBeenViewed(mediaId: String): Boolean {
        return viewedMedia.contains(mediaId)
    }
    
    fun markAsViewed(mediaId: String) {
        viewedMedia.add(mediaId)
    }
    
    fun deleteMedia(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            if (file.exists()) {
                file.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    fun preventScreenshots(activity: android.app.Activity) {
        activity.window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
    }
    
    fun allowScreenshots(activity: android.app.Activity) {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

@Composable
fun ViewOnceMediaViewer(
    media: ViewOnceMedia,
    onDismiss: () -> Unit,
    onMediaViewed: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewOnceManager = remember { ViewOnceManager(context) }
    var hasViewed by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    
    DisposableEffect(Unit) {
        val activity = context as? android.app.Activity
        activity?.let { viewOnceManager.preventScreenshots(it) }
        
        onDispose {
            activity?.let { viewOnceManager.allowScreenshots(it) }
            if (!hasViewed) {
                viewOnceManager.deleteMedia(media.filePath)
                onMediaViewed(media.id)
            }
        }
    }
    
    LaunchedEffect(Unit) {
        if (!hasViewed) {
            hasViewed = true
            viewOnceManager.markAsViewed(media.id)
            onMediaViewed(media.id)
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (media.isVideo) {
            VideoPlayerOnce(
                filePath = media.filePath,
                onDismiss = { showDeleteConfirmation = true }
            )
        } else {
            ImageViewOnce(
                filePath = media.filePath,
                onDismiss = { showDeleteConfirmation = true }
            )
        }
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Rounded.Visibility,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "مشاهدة لمرة واحدة فقط",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "سيتم حذف الوسائط فوراً بعد الإغلاق",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
        }
        
        IconButton(
            onClick = { showDeleteConfirmation = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "إغلاق",
                tint = Color.White
            )
        }
    }
    
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("حذف الوسائط") },
            text = { Text("سيتم حذف هذه الوسائط نهائياً. هل أنت متأكد؟") },
            confirmButton = {
                Button(
                    onClick = {
                        viewOnceManager.deleteMedia(media.filePath)
                        showDeleteConfirmation = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("حذف", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
fun ImageViewOnce(
    filePath: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(File(filePath))
                .crossfade(true)
                .build(),
            contentDescription = "صورة لمرة واحدة",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
        )
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("حذف وإغلاق")
            }
        }
    }
}

@Composable
fun VideoPlayerOnce(
    filePath: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                android.widget.VideoView(ctx).apply {
                    setVideoPath(filePath)
                    setOnPreparedListener { mp ->
                        mp.isLooping = false
                        start()
                    }
                    setOnCompletionListener {
                        onDismiss()
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("حذف وإغلاق")
            }
        }
    }
}

@Composable
fun ViewOnceToggle(
    isViewOnce: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Rounded.Visibility,
            contentDescription = null,
            tint = if (isViewOnce) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "مشاهدة لمرة واحدة",
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
            Text(
                "سيتم حذف الوسائط فوراً بعد المشاهدة",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = isViewOnce,
            onCheckedChange = onToggle
        )
    }
}

@Composable
fun ViewOnceBadge(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Visibility,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                "مرة واحدة",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
