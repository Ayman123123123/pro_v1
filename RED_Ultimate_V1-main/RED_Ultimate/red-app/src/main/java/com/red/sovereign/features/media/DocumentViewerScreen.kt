package com.red.sovereign.features.media

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.sovereign.media.FileTypeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun DocumentViewerScreen(
    file: File,
    fileName: String,
    mimeType: String,
    onDismiss: () -> Unit,
    onSave: (() -> Unit)? = null,
    onOpenWith: ((File) -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var showSave by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchMode by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "إغلاق") }
            Text(fileName, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            if (isTextLike(mimeType)) {
                IconButton(onClick = { searchMode = !searchMode }) { Icon(Icons.Default.Search, "بحث") }
            }
            onSave?.let { IconButton(onClick = it) { Icon(Icons.Default.Download, "حفظ") } }
            onOpenWith?.let { IconButton(onClick = { it(file) }) { Icon(Icons.Default.OpenInNew, "فتح بـ") } }
            onDelete?.let { IconButton(onClick = it) { Icon(Icons.Default.Close, "حذف", tint = Color(0xFFD32F2F)) } }
        }

        if (searchMode && isTextLike(mimeType)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("ابحث في المستند…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary)
            )
        }

        when {
            mimeType == "application/pdf" -> PdfPages(file)
            isTextLike(mimeType) -> TextViewerPane(file, searchQuery)
            else -> UnsupportedPreview(fileName, mimeType, onOpenWith != null) { onOpenWith?.invoke(file) }
        }
    }

    if (showSave) {
        SaveDestinationDialog(
            fileName = fileName,
            onDismiss = { showSave = false },
            onSave = { location ->
                showSave = false
                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    val uri = createSaveUri(context, fileName, location) ?: return@launch
                    saveFileToDestination(file, uri, context) { ok, msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
}

@Composable
private fun PdfPages(file: File) {
    val context = LocalContext.current
    var pageCount by remember { mutableIntStateOf(0) }
    var currentPage by remember { mutableIntStateOf(1) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(file.absolutePath) {
        withContext(Dispatchers.IO) {
            runCatching {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    PdfRenderer(fd).use { renderer -> pageCount = renderer.pageCount }
                }
            }.onFailure { error = "تعذّر فتح PDF: ${it.message}" }
        }
    }

    LaunchedEffect(file.absolutePath, currentPage) {
        if (pageCount <= 0) return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    PdfRenderer(fd).use { renderer ->
                        renderer.openPage((currentPage - 1).coerceIn(0, pageCount - 1)).use { page ->
                            val targetW = 1080
                            val targetH = (targetW.toLong() * page.height / page.width).toInt()
                            val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bmp
                        }
                    }
                }
            }.getOrNull()
        }
    }

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (error != null) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(error!!, color = Color(0xFFD32F2F), textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val bmp = bitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "صفحة $currentPage",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(8.dp)
                    )
                } else {
                    Text("جارٍ عرض الصفحة…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(enabled = currentPage > 1, onClick = { currentPage-- }) {
                        Icon(Icons.AutoMirrored.Filled.NavigateBefore, "السابق")
                    }
                    Text("$currentPage / $pageCount", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    IconButton(enabled = currentPage < pageCount, onClick = { currentPage++ }) {
                        Icon(Icons.AutoMirrored.Filled.NavigateNext, "التالي")
                    }
                }
            }
        }
    }
}

@Composable
private fun TextViewerPane(file: File, query: String) {
    var content by remember { mutableStateOf<String?>(null) }
    var readError by remember { mutableStateOf(false) }

    LaunchedEffect(file.absolutePath) {
        withContext(Dispatchers.IO) {
            runCatching {
                if (file.length() <= 2L * 1024 * 1024) file.readText(Charsets.UTF_8)
                else file.bufferedReader(Charsets.UTF_8).useLines { lines -> lines.take(5000).joinToString("\n") + "\n… (اقتُطع العرض عند 5000 سطر)" }
            }.onSuccess { content = it }.onFailure { readError = true }
        }
    }

    when {
        readError -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("تعذّرت قراءة الملف كنص", color = Color(0xFFD32F2F))
        }
        content == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("جارٍ القراءة…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> SelectionContainer {
            LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
                val text = content!!
                if (query.isBlank()) {
                    item { Text(text, fontSize = 13.sp, fontFamily = FontFamily.Monospace, lineHeight = 20.sp) }
                } else {
                    val chunks = highlightChunks(text, query)
                    items(chunks.size) { i ->
                        val c = chunks[i]
                        Text(
                            c.second,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 20.sp,
                            color = if (c.first) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (c.first) FontWeight.Bold else FontWeight.Normal,
                            style = if (c.first) androidx.compose.ui.text.TextStyle(background = MaterialTheme.colorScheme.primaryContainer) else androidx.compose.ui.text.TextStyle()
                        )
                    }
                }
            }
        }
    }
}

private fun highlightChunks(text: String, query: String): List<Pair<Boolean, String>> {
    val result = mutableListOf<Pair<Boolean, String>>()
    val lowerText = text.lowercase()
    val lowerQuery = query.lowercase()
    var index = 0
    while (true) {
        val found = lowerText.indexOf(lowerQuery, index)
        if (found == -1) break
        if (found > index) result += false to text.substring(index, found)
        result += true to text.substring(found, found + query.length)
        index = found + query.length
        if (result.size > 4000) break
    }
    if (index < text.length) result += false to text.substring(index)
    return result
}

@Composable
private fun UnsupportedPreview(fileName: String, mimeType: String, canOpen: Boolean, onOpenExternal: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Description, null, tint = FileTypeUtil.getFileColor(mimeType), modifier = Modifier.size(64.dp))
            Text("لا تتوفر معاينة داخلية لهذا النوع", fontWeight = FontWeight.Medium)
            Text(fileName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (canOpen) {
                androidx.compose.material3.Button(onClick = onOpenExternal) { Text("افتح بتطبيق خارجي") }
            }
        }
    }
}

fun isTextLike(mimeType: String): Boolean = mimeType.lowercase().let {
    it.startsWith("text/") || it in setOf(
        "application/json", "application/xml", "application/yaml",
        "application/javascript", "application/x-sh", "text/csv"
    )
}
