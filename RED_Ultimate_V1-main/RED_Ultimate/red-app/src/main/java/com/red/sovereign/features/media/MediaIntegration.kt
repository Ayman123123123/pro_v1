package com.red.sovereign.features.media

import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.red.sovereign.crypto.DecryptedMessage
import com.red.sovereign.media.AttachmentState
import com.red.sovereign.media.AttachmentViewModel
import com.red.sovereign.media.FileTypeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

private fun saveToDownloads(context: android.content.Context, source: File, hintName: String) {
    CoroutineScope(Dispatchers.IO).launch {
        val name = hintName.ifBlank { source.name }
        val uri: Uri? = createSaveUri(context, name, SaveLocation.DOWNLOADS)
        if (uri == null) {
            CoroutineScope(Dispatchers.Main).launch { Toast.makeText(context, "تعذّر إنشاء ملف الوجهة", Toast.LENGTH_SHORT).show() }
            return@launch
        }
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                source.inputStream().use { it.copyTo(out, 64 * 1024) }
            } ?: error("OPEN_FAILED")
        }.isSuccess
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(context, if (ok) "تم الحفظ في التنزيلات" else "فشل الحفظ", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun AttachmentBubble(
    item: DecryptedMessage,
    attachments: AttachmentViewModel,
    isSent: Boolean,
    onForward: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var viewer by remember { mutableStateOf<Triple<File, String, String>?>(null) }
    var infoTarget by remember { mutableStateOf<Pair<File, Pair<String, String>>?>(null) }
    val json = remember { Json { ignoreUnknownKeys = true } }

    ChatAttachmentBubble(
        item = item,
        attachments = attachments,
        json = json,
        isSent = isSent,
        onOpen = { f, n, m -> viewer = Triple(f, n, m) },
        onRequestDownload = { manifestJson, msg -> attachments.download(msg.id, manifestJson) },
        onDelete = onDelete,
        onForward = onForward,
        onInfo = {
            val state = attachments.getDownloadState(item.id)
            val f = (state as? AttachmentState.Downloaded)?.let { s -> File(s.path).takeIf(File::isFile) }
            if (f != null) {
                val m = parseManifest(item, json)
                infoTarget = f to ((m?.name ?: f.name) to (m?.mimeType ?: FileTypeUtil.getMimeFromExtension(f.name)))
            } else if (state !is AttachmentState.Working) {
                attachments.download(item.id, item.plaintext.toString(Charsets.UTF_8))
            }
        },
        onSaveTo = { f -> saveToDownloads(context, f, parseManifest(item, json)?.name ?: f.name) },
        onOpenWith = { f -> openFileWithSystemPicker(f, context) }
    )

    viewer?.let { (f, n, m) ->
        FileViewerScreen(
            file = f,
            fileName = n,
            mimeType = m,
            onDismiss = { viewer = null },
            onSave = { saveToDownloads(context, f, n) },
            onOpenWith = { openFileWithSystemPicker(it, context) },
            onDelete = { viewer = null; onDelete?.invoke() }
        )
    }

    infoTarget?.let { (f, nm) ->
        FileInfoDialog(
            file = f,
            fileName = nm.first,
            mimeType = nm.second,
            onDismiss = { infoTarget = null },
            onDelete = { infoTarget = null; onDelete?.invoke() }
        )
    }
}

@Composable
fun GalleryOverlay(
    title: String,
    messages: List<DecryptedMessage>,
    attachments: AttachmentViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var viewer by remember { mutableStateOf<Triple<File, String, String>?>(null) }

    MediaGalleryScreen(
        title = title,
        messages = messages,
        attachments = attachments,
        onDismiss = onDismiss,
        onOpenFile = { f, n, m -> viewer = Triple(f, n, m) },
        onSaveFile = null,
        onDeleteMessage = null
    )

    viewer?.let { (f, n, m) ->
        FileViewerScreen(
            file = f,
            fileName = n,
            mimeType = m,
            onDismiss = { viewer = null },
            onSave = { saveToDownloads(context, f, n) },
            onOpenWith = { openFileWithSystemPicker(it, context) }
        )
    }
}
