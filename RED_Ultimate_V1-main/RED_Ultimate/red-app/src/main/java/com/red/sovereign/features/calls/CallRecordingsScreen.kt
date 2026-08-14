package com.red.sovereign.features.calls

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.calls.CallRecording
import com.red.sovereign.calls.CallRecordingManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val AqyalGold = Color(0xFFD4AF37)

/**
 * 📼 تسجيلات المكالمات — ملفات AES-256-GCM مشفرة محلياً، لا تُفك إلا بطلب مستخدم صريح.
 * الملفات في filesDir/recordings حتى لا يمسحها كاش النظام.
 */
@Composable
fun CallRecordingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val manager = remember { CallRecordingManager(context, "browse") }
    var recordings by remember { mutableStateOf<List<CallRecording>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<CallRecording?>(null) }

    fun refresh() {
        loading = true
        error = null
        scope.launch {
            val items = withContext(Dispatchers.IO) {
                val dir = File(context.filesDir, "recordings")
                dir.listFiles()?.filter { it.name.endsWith(".m4a.enc") }?.sortedByDescending { it.lastModified() }
                    ?.map { file ->
                        val parts = file.nameWithoutExtension.split("_")
                        val created = file.lastModified()
                        CallRecording(
                            callId = parts.firstOrNull().orEmpty(),
                            filePath = file.absolutePath,
                            sizeBytes = file.length(),
                            encrypted = true,
                            createdAt = created,
                            durationMs = 0L
                        )
                    }.orEmpty()
            }
            recordings = items
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    fun togglePlay(rec: CallRecording) {
        if (playing == rec.filePath) {
            playing = null
            return
        }
        scope.launch {
            error = null
            val bytes = manager.decryptForPlayback(rec.filePath)
            if (bytes == null) {
                error = "تعذر فك التشفير (مفتاح الجهاز أو ملف تالف)"
                playing = null
                return@launch
            }
            val temp = withContext(Dispatchers.IO) {
                File(context.cacheDir, "playback_${System.currentTimeMillis()}.m4a").apply { writeBytes(bytes) }
            }
            playing = rec.filePath
            runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    setDataSource(temp.absolutePath)
                    setOnCompletionListener { playing = null; it.release(); temp.delete() }
                    setOnErrorListener { mp, _, _ -> playing = null; mp.release(); temp.delete(); true }
                    prepare()
                    start()
                }
            }.onFailure {
                playing = null
                error = "تعذر التشغيل"
                temp.delete()
            }
        }
    }

    fun delete(rec: CallRecording) {
        scope.launch {
            withContext(Dispatchers.IO) { File(rec.filePath).delete() }
            if (playing == rec.filePath) playing = null
            refresh()
        }
    }

    Column(
        Modifier.fillMaxSize().background(Color(0xFF0B1120)).padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع", tint = AqyalGold)
            }
            Spacer(Modifier.width(6.dp))
            Column {
                Text("تسجيلات المكالمات", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("مشفّرة AES-256-GCM على جهازك فقط", color = Color.Gray, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        error?.let {
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
                Text(it, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
            }
            Spacer(Modifier.height(10.dp))
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AqyalGold) }
            recordings.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.FiberManualRecord, null, tint = AqyalGold.copy(0.6f), modifier = Modifier.size(54.dp))
                    Text("لا توجد تسجيلات", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("سجّل أي مكالمة بموافقة صريحة وستظهر هنا", color = Color.Gray, fontSize = 13.sp)
                }
            }
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recordings, key = { it.filePath }) { rec ->
                    RecordingRow(
                        rec = rec,
                        isPlaying = playing == rec.filePath,
                        onToggle = { togglePlay(rec) },
                        onDelete = { confirmDelete = rec }
                    )
                }
            }
        }

        // تأكيد قبل حذف ملف مشفّر غير قابل للاسترجاع
        confirmDelete?.let { rec ->
            AlertDialog(
                onDismissRequest = { confirmDelete = null },
                containerColor = Color(0xFF151C2E),
                title = { Text("حذف التسجيل؟", color = Color.White) },
                text = { Text("سيُحذف الملف المشفّر نهائياً من جهازك ولا يمكن استرجاعه.", color = Color.Gray) },
                confirmButton = {
                    TextButton(onClick = { confirmDelete = null; delete(rec) }) {
                        Text("حذف", color = Color(0xFFE57373), fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = null }) { Text("إلغاء", color = Color.Gray) }
                }
            )
        }
    }
}

@Composable
private fun RecordingRow(rec: CallRecording, isPlaying: Boolean, onToggle: () -> Unit, onDelete: () -> Unit) {
    val date = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.US).format(Date(rec.createdAt))
    val sizeKb = rec.sizeBytes / 1024
    Surface(
        color = Color.White.copy(0.06f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                onClick = onToggle,
                color = if (isPlaying) AqyalGold else AqyalGold.copy(0.18f),
                shape = RoundedCornerShape(50)
            ) {
                Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        null,
                        tint = if (isPlaying) Color(0xFF0B1120) else AqyalGold
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(rec.callId.ifBlank { "مكالمة" }, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text("$date • ${sizeKb}KB", color = Color.Gray, fontSize = 12.sp)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, "حذف", tint = Color(0xFFE57373))
            }
        }
    }
}
